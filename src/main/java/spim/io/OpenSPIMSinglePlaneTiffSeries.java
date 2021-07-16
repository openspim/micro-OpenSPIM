package spim.io;

import com.google.common.base.Splitter;
import com.google.common.eventbus.Subscribe;
import com.google.gson.*;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.*;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2020
 */
public class OpenSPIMSinglePlaneTiffSeries implements Storage {
	private static final HashSet<String> ALLOWED_AXES = new HashSet<String>(
			Arrays.asList(Coords.CHANNEL, Coords.T, Coords.Z,
					Coords.STAGE_POSITION, "view"));
	private final DefaultDatastore store_;
	private final String dir_;
	private final String prefix_;
	private boolean firstElement_;
	private boolean amLoading_;
	private HashMap<Integer, Writer> metadataStreams_;
	private boolean isDatasetWritable_;
	private SummaryMetadata summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
	private ConcurrentHashMap<Coords, String> coordsToFilename_;
	private HashMap<Integer, String> positionIndexToName_;
	private ArrayList<String> orderedChannelNames_;
	private Coords maxIndices_;
	private Image firstImage_;

	public OpenSPIMSinglePlaneTiffSeries(DefaultDatastore store, String directory, String prefix, boolean newDataSet) throws IOException {
		store_ = store;
		dir_ = directory;
		prefix_ = prefix;

		store_.setSavePath(dir_);
		store_.setName(new File(dir_).getName());
		isDatasetWritable_ = newDataSet;

		// Must be informed of events before traditional consumers, so that we
		// can provide images on request.
		store_.registerForEvents(this, 0);
		coordsToFilename_ = new ConcurrentHashMap<Coords, String>();
		metadataStreams_ = new HashMap<Integer, Writer>();
		positionIndexToName_ = new HashMap<Integer, String>();
		orderedChannelNames_ = new ArrayList<String>();
		maxIndices_ = new DefaultCoords.Builder().build();
		amLoading_ = false;

		// Note: this will throw an error if there is no existing data set
		if (!isDatasetWritable_) {
			openExistingDataSet();
		}
	}

	@SuppressWarnings("Duplicates")
	@Override
	public void putImage(Image image) {
		// Require images to only have time/channel/z/position axes.
		for (String axis : image.getCoords().getAxes()) {
			if (!ALLOWED_AXES.contains(axis)) {
				ReportingUtils.showError("OpenSPIM Singleplane TIFF series storage cannot handle images with axis \"" + axis + "\". Allowed axes are " + ALLOWED_AXES);
				return;
			}
		}
		if (!isDatasetWritable_ && !amLoading_) {
			// This should never happen!
			ReportingUtils.logError("Attempted to add an image to a read-only fileset");
			return;
		}
		// We can't properly save multi-position datasets unless each image has
		// a PositionName property in its metadata.
		if (isDatasetWritable_ && image.getCoords().getStagePosition() > 0 &&
				(image.getMetadata() == null ||
						image.getMetadata().getPositionName("").equals(""))) {
			throw new IllegalArgumentException("Image " + image + " does not have a valid positionName metadata value");
		}
		// If we're in the middle of loading a file, then the code that writes
		// stuff to disk should not run; we only need to update our internal
		// records.

		image.getMetadata().getCamera();

		String fileName = createFileName(image.getCoords());
		if (amLoading_ && !(new File(dir_ + "/" + fileName).exists())) {
			// Try the 1.4 format instead. Since we may not have access to the
			// channel name property, we just have to arbitrarily assign an
			// ordering to the channel names that we find.
			assignChannelsToIndices(prefix_);
			fileName = create14FileName( image.getCoords());
		}
		if (!amLoading_) {
			int imagePos = 0;
			if (!metadataStreams_.containsKey(imagePos)) {
				// No metadata for image at this location, means we haven't
				// written to its location before.
				try {
					openNewDataSet(image);
				} catch (Exception ex) {
					ReportingUtils.logError(ex);
				}
			}

			JsonObject jo = new JsonObject();
			NonPropertyMapJSONFormats.imageFormat().addToGson(jo,
					((DefaultImage) image).formatToPropertyMap());
			NonPropertyMapJSONFormats.coords().addToGson(jo,
					((DefaultCoords) image.getCoords()).toPropertyMap());
			Metadata imgMetadata = image.getMetadata().copyBuilderPreservingUUID().
					fileName(fileName).build();
			NonPropertyMapJSONFormats.metadata().addToGson(jo,
					((DefaultMetadata) imgMetadata).toPropertyMap());

			Gson gson = new GsonBuilder().disableHtmlEscaping().
					setPrettyPrinting().create();
			String metadataJSON = gson.toJson(jo);

			saveImageFile(image, dir_, fileName, metadataJSON);
			writeFrameMetadata(image, metadataJSON, fileName);
		}

		Coords coords = image.getCoords();
		if (!coordsToFilename_.containsKey(coords)) {
			// TODO: is this in fact always the correct fileName? What if it
			// isn't?  See the above code that branches based on amLoading_.
			coordsToFilename_.put(coords, fileName);
		}
		// Update our tracking of the max index along each axis.
		for (String axis : coords.getAxes()) {
			if (coords.getIndex(axis) > maxIndices_.getIndex(axis)) {
				maxIndices_ = maxIndices_.copyBuilder().index(
						axis, coords.getIndex(axis)).build();
			}
		}
	}

	@Override
	public void freeze() {
		closeMetadataStreams();
		isDatasetWritable_ = false;
	}

	@SuppressWarnings("Duplicates")
	@Override
	public Image getImage(Coords coords) {
		if (coordsToFilename_.get(coords) == null) {
			// We don't have that image.
			ReportingUtils.logError("Asked for image at " + coords + " that we don't know about");
			return null;
		}
		String path = dir_ + "/" + coordsToFilename_.get(coords);
		ImagePlus imp = new Opener().openImage(path);
		if (imp == null) {
			// Loading failed.
			ReportingUtils.logError("Unable to load image at " + path);
			return null;
		}
		try {
			// Assemble an Image out of the pixels and JSON-ified metadata.
			ImageProcessor proc = imp.getProcessor();
			Metadata metadata = null;
			if (imp.getProperty("Info") != null) {
				try {
					String metadataJSON = (String) imp.getProperty("Info");
					metadata = DefaultMetadata.fromPropertyMap(
							NonPropertyMapJSONFormats.metadata().
									fromJSON(metadataJSON));
				}
				catch (IOException e) {
					ReportingUtils.logError(e, "Unable to extract image dimensions from JSON metadata");
					return null;
				}
			}
			else {
				ReportingUtils.logError("Unable to reconstruct metadata for image at " + coords);
			}

			int width = proc.getWidth();
			int height = proc.getHeight();
			int bytesPerPixel;
			int numComponents;
			if (proc instanceof ByteProcessor) {
				bytesPerPixel = 1;
				numComponents = 1;
			}
			else if (proc instanceof ShortProcessor) {
				bytesPerPixel = 2;
				numComponents = 1;
			}
			else if (proc instanceof ColorProcessor) {
				bytesPerPixel = 4;
				numComponents = 3;
			}
			else {
				ReportingUtils.logError("Received an ImageProcessor of unrecognized type " + proc);
				return null;
			}
			Object pixels = proc.getPixels();
			DefaultImage result = new DefaultImage(pixels, width, height,
					bytesPerPixel, numComponents, coords, metadata);
			return result;
		} catch (IllegalArgumentException ex) {
			ReportingUtils.logError(ex);
			return null;
		}
	}

	@Override
	public Image getAnyImage() {
		if (coordsToFilename_.isEmpty()) {
			return null;
		}
		Coords coords = new ArrayList<>(coordsToFilename_.keySet()).get(0);
		return getImage(coords);
	}

	@Override
	public Iterable<Coords> getUnorderedImageCoords() {
		return coordsToFilename_.keySet();
	}

	@SuppressWarnings("Duplicates")
	@Override
	public List<Image> getImagesMatching(Coords coords) {
		ArrayList<Image> result = new ArrayList<Image>();
		for (Coords altCoords : coordsToFilename_.keySet()) {
			boolean canUse = true;
			for (String axis : coords.getAxes()) {
				if (coords.getIndex(axis) != altCoords.getIndex(axis)) {
					canUse = false;
					break;
				}
			}
			if (canUse) {
				result.add(getImage(altCoords));
			}
		}
		return result;
	}

	@Override
	public List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes) throws IOException {
		ArrayList<Image> result = new ArrayList<Image>();
		for (Coords altCoords : coordsToFilename_.keySet()) {
			Coords strippedAltCoords = altCoords.copyRemovingAxes(ignoreTheseAxes);
			if (coords.equals(strippedAltCoords)) {
				result.add(getImage(altCoords));
			}
		}
		return result;
	}

	@Override
	public boolean hasImage(Coords coords) {
		return coordsToFilename_.containsKey(coords);
	}

	@Override
	public int getMaxIndex(String axis) {
      if (!getAxes().contains(axis)) {
         return -1;
      }
		return maxIndices_.getIndex(axis);
	}

	@Override
	public List<String> getAxes() {
		List<String> axes = summaryMetadata_.getOrderedAxes();
		axes.add("view");
		return axes;
	}

	@Override
	public Coords getMaxIndices() {
		return maxIndices_;
	}

	@Override
	public int getNumImages() {
		return coordsToFilename_.size();
	}

	/**
	 * Generate a filename based on the coordinates of an image.
	 */
	@SuppressWarnings("Duplicates")
	private String createFileName(Coords coords) {
		List<String> axes = getAxes();
		java.util.Collections.sort(axes);
		String filename = prefix_;
		for (String axis : axes) {
			// HACK: more precision for the time axis.
			if(coords.hasAxis(axis)) {
				String precision = "%03d";
				if (axis.contentEquals(Coords.T)) {
					precision = "%09d";
				}
				filename += String.format("_%s" + precision, axis,
						coords.getIndex(axis));
			} else {
				String precision = "%03d";
				if (axis.contentEquals(Coords.T)) {
					precision = "%09d";
				}
				filename += String.format("_%s" + precision, axis,
						0);
			}

		}
		return filename + ".tif";
	}

	/**
	 * Generate a filename based on image coordinates and the metadata from
	 * a 1.4-era JSON metadata.
	 */
	@SuppressWarnings("Duplicates")
	private String create14FileName(Coords coords) {
		int channelIndex = coords.getChannel();
		String channel = "";
		if (channelIndex < 0 || channelIndex >= orderedChannelNames_.size()) {
			ReportingUtils.logError("Invalid channel index " + channelIndex + " into channel list " + orderedChannelNames_);
		}
		else {
			channel = orderedChannelNames_.get(coords.getChannel());
		}
		return String.format( prefix_ + "_%09d_%s_%03d.tif",
				coords.getT(), channel, coords.getZ());
	}

	/**
	 * Examine the files in the given directory off of dir_, use regexes to
	 * pull out the channel names from files, sort the channel names, and assign
	 * indices to each one. Only relevant for loading 1.4-style files.
	 */
	@SuppressWarnings("Duplicates")
	private void assignChannelsToIndices(String prefix) {
		if (orderedChannelNames_.size() > 0) {
			// Assume we're already done.
			return;
		}
		// Only add on the position name if our dataset is split across
		// positions.
		File directory = new File(dir_);
		Pattern FILENAME_PATTERN_14 = Pattern.compile(prefix_ + "_(\\d+)_(.*)_(\\d+).tif");
		for (File file : directory.listFiles()) {
			Matcher matcher = FILENAME_PATTERN_14.matcher(file.getName());
			if (matcher.matches()) {
				String channel = matcher.group(2);
				if (!orderedChannelNames_.contains(channel)) {
					orderedChannelNames_.add(channel);
				}
			}
		}
		Collections.sort(orderedChannelNames_);
	}

	@SuppressWarnings("Duplicates")
	private void writeFrameMetadata(final Image image, final String metadataJSON,
									final String fileName) {
		try {
			String coordsKey = "Coords-" + fileName;

			// Use 0 for situations where there's no index information.
			int pos = Math.max(0, image.getCoords().getStagePosition());
			JsonObject jo = new JsonObject();
			NonPropertyMapJSONFormats.coords().addToGson(jo,
					((DefaultCoords) image.getCoords()).toPropertyMap());
			Gson gson = new GsonBuilder().disableHtmlEscaping().
					setPrettyPrinting().create();
			writeJSONMetadata(pos, gson.toJson(jo), coordsKey);

			String mdKey = "Metadata-" + fileName;
			writeJSONMetadata(pos, metadataJSON, mdKey);
		} catch (Exception ex) {
			ReportingUtils.logError(ex);
		}
	}

	@SuppressWarnings("Duplicates")
	private void writeJSONMetadata(int pos, String json, String title) {
		try {
			Writer metadataStream = metadataStreams_.get(pos);
			if (metadataStream == null) {
				ReportingUtils.logError("Failed to make a stream for location " + pos);
				return;
			}
			if (!firstElement_) {
				metadataStream.write(",\n");
			}
			metadataStream.write("\"" + title + "\": ");
			metadataStream.write(json);
			metadataStream.flush();
			firstElement_ = false;
		} catch (IOException e) {
			ReportingUtils.logError(e);
		}
	}

	@SuppressWarnings("Duplicates")
	private void saveImageFile(Image image, String path, String tiffFileName,
							   String metadataJSON) {
		if (firstImage_ == null) {
			firstImage_ = image;
		} else {
			ImageSizeChecker.checkImageSizes(firstImage_, image);
		}
		ImagePlus imp;
		try {
			ImageProcessor ip;
			int width = image.getWidth();
			int height = image.getHeight();
			Object pixels = image.getRawPixels();
			int bytesPerPixel = image.getBytesPerPixel();
			int numComponents = image.getNumComponents();
			ImageProcessor proc;
			if (numComponents == 3 && bytesPerPixel == 4) {
				// 32-bit RGB
				proc = new ColorProcessor(width, height);
				int[] rgbPixels = new int[width * height];
				byte[] rawPixels = (byte[]) pixels;
				for (int i=0; i < width * height; i++) {
					rgbPixels[i] = (rawPixels[4 * i + 3] << (Byte.SIZE * 3));
					rgbPixels[i] |= (rawPixels[4 * i + 2] & 0xFF) << (Byte.SIZE * 2);
					rgbPixels[i] |= (rawPixels[4 * i + 1] & 0xFF) << (Byte.SIZE * 1);
					rgbPixels[i] |= (rawPixels[4 * i] & 0xFF);
				}
				((ColorProcessor) proc).setPixels(rgbPixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 1) {
				// Byte
				proc = new ByteProcessor(width, height);
				proc.setPixels((byte[]) pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 2) {
				// Short
				proc = new ShortProcessor(width, height);
				proc.setPixels((short[]) pixels);
			}
			else {
				throw new IllegalArgumentException(String.format("Unexpected image format with %d bytes per pixel and %d components", bytesPerPixel, numComponents));
			}
			saveImageProcessor(proc, image, path, tiffFileName, metadataJSON);
		} catch (IllegalArgumentException ex) {
			ReportingUtils.logError(ex);
		}
	}

	@SuppressWarnings("Duplicates")
	private void saveImageProcessor(ImageProcessor ip, Image image,
									String path, String tiffFileName, String metadataJSON) {
		// TODO: why is this check here?
		if (ip == null) {
			return;
		}
		ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
		applyPixelSizeCalibration(imp, image.getMetadata());
		saveImagePlus(imp, image, path, tiffFileName, metadataJSON);
	}

	@SuppressWarnings("Duplicates")
	private void applyPixelSizeCalibration(final ImagePlus ip,
										   Metadata metadata) {
		Double pixSizeUm = metadata.getPixelSizeUm();
		if (pixSizeUm != null && pixSizeUm > 0) {
			ij.measure.Calibration cal = new ij.measure.Calibration();
			cal.setUnit("um");
			cal.pixelWidth = pixSizeUm;
			cal.pixelHeight = pixSizeUm;
			if (summaryMetadata_.getWaitInterval() != null) {
				cal.frameInterval = summaryMetadata_.getWaitInterval() / 1000.0;
			}
			if (summaryMetadata_.getZStepUm() != null) {
				cal.pixelDepth = summaryMetadata_.getZStepUm();
			}
			ip.setCalibration(cal);
		}
	}

	@SuppressWarnings("Duplicates")
	public void saveImagePlus(ImagePlus imp, Image image,
							  String path, String tiffFileName, String metadataJSON) {
		imp.setProperty("Info", metadataJSON);

		FileSaver fs = new FileSaver(imp);
		fs.saveAsTiff(path + "/" + tiffFileName);
	}

	@SuppressWarnings("Duplicates")
	private void openNewDataSet(Image image) throws IOException, Exception {
		String posName = image.getMetadata().getPositionName("");
		int pos = image.getCoords().getStagePosition();
		if (pos == -1) {
			// No stage position axis.
			pos = 0;
			posName = "";
		}

		if (positionIndexToName_.containsKey(pos)
				&& positionIndexToName_.get(pos) != null
				&& !positionIndexToName_.get(pos).contentEquals(posName)) {
			throw new IOException ("Position name changed during acquisition.");
		}

		positionIndexToName_.put(pos, posName);

		firstElement_ = true;
		Writer metadataStream = new BufferedWriter(new FileWriter(dir_ + "/" + prefix_ + "_metadata.txt"));
		metadataStreams_.put(0, metadataStream);
		metadataStream.write("{" + "\n");
		// TODO: this method of extracting the date is extremely hacky and
		// potentially locale-dependent.
		String time = image.getMetadata().getReceivedTime();
		// TODO: should we log if the date isn't available?
		SummaryMetadata summary = summaryMetadata_;
		if (time != null && summary.getStartDate() == null) {
			summary = summary.copyBuilder().startDate(time.split(" ")[0]).build();
		}

		PropertyMap.Builder b = PropertyMaps.builder()
				.putInteger("Width", image.getWidth())
				.putInteger("Height", image.getHeight());

		summary = DefaultSummaryMetadata.fromPropertyMap(((DefaultSummaryMetadata) summary).toPropertyMap().merge(b.build()));

		JsonObject jo = new JsonObject();
		NonPropertyMapJSONFormats.summaryMetadata().addToGson(jo,
				((DefaultSummaryMetadata) summary).toPropertyMap());
		// Augment the JSON with pixel type information, for backwards
		// compatibility.
		PropertyMap formatPmap = ((DefaultImage) image).formatToPropertyMap();
		PropertyKey.IJ_TYPE.storeInGsonObject(formatPmap, jo);
		PropertyKey.PIXEL_TYPE.storeInGsonObject(formatPmap, jo);
		jo.add("StorageType", new JsonPrimitive(StorageType.SinglePlaneTiff.name()));
		Gson gson = new GsonBuilder().disableHtmlEscaping().
				setPrettyPrinting().create();
		writeJSONMetadata(pos, gson.toJson(jo), "Summary");
	}

	@SuppressWarnings("Duplicates")
	private void closeMetadataStreams() {
		if (isDatasetWritable_) {
			try {
				for (Writer metadataStream:metadataStreams_.values()) {
					metadataStream.write("\n}\n");
					metadataStream.close();
				}
			} catch (IOException ex) {
				ReportingUtils.logError(ex);
			}
		}
	}

	@SuppressWarnings("Duplicates")
	private void openExistingDataSet() throws IOException {
		amLoading_ = true;
		ArrayList<String> positions = new ArrayList<String>();
		if (new File(dir_ + "/" + prefix_ + "_metadata.txt").exists()) {
			// Our base directory is a valid "position", i.e. there are no
			// positions in this dataset.
			positions.add("");
		}

		if (positions.isEmpty()) {
			// Couldn't find either a metadata.txt or any position directories in
			// our directory. We've been handed a bad directory.
			throw new IOException("Unable to find dataset at " + dir_);
		}

		for (int positionIndex = 0; positionIndex < positions.size();
			 ++positionIndex) {
			String position = positions.get(positionIndex);
			JsonObject data = readJSONMetadata(position);
			if (data == null) {
				ReportingUtils.logError("Couldn't load metadata for position " + position + " in directory " + dir_);
				continue;
			}
			try {
				if (data.has(PropertyKey.SUMMARY.key())) {
					summaryMetadata_ = DefaultSummaryMetadata.fromPropertyMap(
							NonPropertyMapJSONFormats.summaryMetadata().fromGson(
									data.get(PropertyKey.SUMMARY.key())));
				}

				// We have two methods to recover the image coordinates from the
				// metadata. The old 1.4 method uses a "FrameKey" key that holds
				// the time, channel, and Z indices specifically, and stows all
				// image metadata within that structure. The 2.0 method stores
				// image coordinate info in a mapping specific to the filename the
				// image is stored in. Naturally we have to be able to load both
				// methods. The 1.4 method requires a different technique for
				// reconstructing the SummaryMetadata too, since all that's in the
				// "data" variable directly is a bunch of FrameKeys -- the summary
				// metadata is duplicated within each JSONObject the FrameKeys
				// point to.
				// Note: We skip the bulk of metadata stored in metadata.txt and
				// instead use the metadata stored in the TIFF as ImagePlus Info.
				for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
					String key = entry.getKey();
					String fileName;
					Coords coords;
					if (key.startsWith("Coords-")) {
						// 2.0 method. SummaryMetadata is already valid.
						File f = new File(key);
						fileName = f.getName();
						coords = DefaultCoords.fromPropertyMap(
								NonPropertyMapJSONFormats.coords().fromGson(entry.getValue()));
					}
					else if (key.startsWith("FrameKey-")) {
						// 1.4 method. SummaryMetadata must be reconstructed.
						JsonObject jo = entry.getValue().getAsJsonObject();
						if (jo.has(PropertyKey.SUMMARY.key())) {
							summaryMetadata_ = DefaultSummaryMetadata.fromPropertyMap(
									NonPropertyMapJSONFormats.summaryMetadata().
											fromGson(jo.get(PropertyKey.SUMMARY.key())));
						}

						// Extract what coords are available in the metadata, which
						// should include the stage position if available.
						Coords c = DefaultCoords.fromPropertyMap(
								NonPropertyMapJSONFormats.coords().fromGson(jo));

						List<String> items = Splitter.on("-").splitToList(key);
						coords = Coordinates.builder().
								timePoint(Integer.parseInt(items.get(1))).
								channel(Integer.parseInt(items.get(2))).
								zSlice(Integer.parseInt(items.get(3))).
								stagePosition(c.getStagePosition()).
								build();

						assignChannelsToIndices(position);
						fileName = create14FileName(coords);
					}
					else { // Posibly "Metadata-*"
						// Not a key we can extract useful information from.
						continue;
					}

					try {
						// TODO: omitting pixel type information.
						if (position.length() > 0 &&
								!(new File(dir_ + "/" + fileName).exists())) {
							// Assume file is in a subdirectory.
							fileName = position + "/" + fileName;
						}
						if (!(new File(dir_ + "/" + fileName.replaceFirst("Coords-", "")).exists())) {
							ReportingUtils.logError("For key " + key + " tried to find file at " + fileName + " but it did not exist");
						}
						// This will update our internal records without touching
						// the disk, as amLoading_ is true.
						coordsToFilename_.put(coords, fileName.replaceFirst("Coords-", ""));
						Image image = getImage(coords);
						putImage(image);
					} catch (Exception ex) {
						ReportingUtils.showError(ex);
					}
				}
			} catch (NumberFormatException ex) {
				ReportingUtils.showError(ex);
			}
		}
		amLoading_ = false;
	}

   /*
   private int getChannelIndex(String channelName) {
      if (summaryMetadata_.getChannelNames() == null) {
         return 0;
      }
      int result = Arrays.asList(summaryMetadata_.getChannelNames()).indexOf(channelName);
      if (result == -1) {
         // Nothing for this channel.
         result = 0;
      }
      return result;
   }
*/

	private JsonObject readJSONMetadata(String pos) {
		return StorageOpener.readJSONMetadata(dir_, pos, prefix_);
	}

	@Override
	public SummaryMetadata getSummaryMetadata() {
		return summaryMetadata_;
	}

	@Subscribe
	public void onNewSummaryMetadata(DataProviderHasNewSummaryMetadataEvent event) {
		summaryMetadata_ = event.getSummaryMetadata();
	}

	@Override
	public void close() {
		// We don't maintain any state that needs to be cleaned up.
	}
}