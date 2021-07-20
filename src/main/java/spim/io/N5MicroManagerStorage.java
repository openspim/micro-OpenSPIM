package spim.io;

import com.google.common.eventbus.Subscribe;
import com.google.gson.*;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.*;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.*;
import org.micromanager.data.internal.*;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class N5MicroManagerStorage implements Storage {
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
	private final int timesteps_;

	int width;
	int height;
	N5FSWriter writer;
	GzipCompression compression;
	ExecutorService exec;
	ImageStack[] imageStacks;
	private HashMap<Coords, String> coordsMetadata_ = new HashMap<>();
	private HashMap<String, ImageProcessor> cache_ = new HashMap<>();
	private HashMap<Integer, Boolean> timeFinished_ = new HashMap<>();
	private HashMap<String, RandomAccessibleInterval> impMap_ = new HashMap<>();
	private HashMap<String, String> datasetList_ = new HashMap<>();

	public N5MicroManagerStorage(DefaultDatastore store, String directory, String prefix, int timeSeqs, boolean newDataSet) throws IOException {
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
		timesteps_ = timeSeqs;

		// Note: this will throw an error if there is no existing data set
		if (!isDatasetWritable_) {
			openExistingDataSet();
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

		writer = new N5FSWriter( dir_ + "/" + prefix_ + ".n5" );
		compression = new GzipCompression();
		exec = Executors.newFixedThreadPool(10);

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
				Coords coords = null;
				String fileName = null;
				for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
					String key = entry.getKey();
					if (key.startsWith("Coords-")) {
						// 2.0 method. SummaryMetadata is already valid.
						JsonElement je = entry.getValue();
						JsonObject jsonObject = je.getAsJsonObject();
						fileName = jsonObject.get("FileName").getAsString();
						jsonObject.remove("FileName");

						coords = DefaultCoords.fromPropertyMap(
								NonPropertyMapJSONFormats.coords().fromGson(jsonObject));
						timeFinished_.put(coords.getT(), true);
					}
					else if (key.startsWith("Metadata-")) { // Possibly "Metadata-*"
						// Not a key we can extract useful information from.
						coordsMetadata_.put(coords, entry.getValue().getAsJsonObject().toString());

						try {
							// TODO: omitting pixel type information.
//							if (!(new File(dir_ + "/" + fileName)).exists()) {
//								ReportingUtils.logError("For key " + key + " tried to find file at " + fileName + " but it did not exist");
//							}
							// This will update our internal records without touching
							// the disk, as amLoading_ is true.
							coordsToFilename_.put(coords, fileName);
							datasetList_.put(fileName, fileName);
							Image image = getImage(coords);
							putImage(image);
						} catch (Exception ex) {
							ReportingUtils.showError(ex);
						}
					}
				}
			} catch (NumberFormatException ex) {
				ReportingUtils.showError(ex);
			}
		}
		amLoading_ = false;
	}

	@Override
	public void freeze() throws IOException {
		closeMetadataStreams();
		isDatasetWritable_ = false;

//		final N5MicroManagerMetadata metaWriter = new N5MicroManagerMetadata( dir_ + "/" + prefix_ + ".n5" );
//
//		PropertyMap.Builder pb = PropertyMaps.builder().putAll(((DefaultSummaryMetadata)summaryMetadata_).toPropertyMap())
//				.putInteger("Width", width)
//				.putInteger("Height", height)
//				.putEnumAsString( "Storage", StorageType.N5 );
//
//		metaWriter.readMetadata( pb.build() );
//
//		for (String name : datasetList ) {
//			try {
//				metaWriter.writeMetadata( metaWriter, writer, name );
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}

		if(writer != null) {
			writer.close();
		}
	}

	@SuppressWarnings("Duplicates")
	private String makeDatasetName(Coords coords) {
		String posString;
		posString=String.format("_Pos%02d", coords.getP());

		if(coords.hasAxis("view")) {
			posString += String.format("_View%02d", coords.getIndex("view"));
		}

		return String.format("/Volumes/DataSet" + posString);
	}

	@SuppressWarnings("Duplicates")
	@Override
	public void putImage(Image image) throws IOException {
		// Require images to only have time/channel/z/position/view axes.
		for (String axis : image.getCoords().getAxes()) {
			if (!ALLOWED_AXES.contains(axis)) {
				ReportingUtils.showError("OpenSPIM N5 storage cannot handle images with axis \"" + axis + "\". Allowed axes are " + ALLOWED_AXES);
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

		String dataset = makeDatasetName(image.getCoords());

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
					fileName(dataset).build();
			NonPropertyMapJSONFormats.metadata().addToGson(jo,
					((DefaultMetadata) imgMetadata).toPropertyMap());

			Gson gson = new GsonBuilder().disableHtmlEscaping().
					setPrettyPrinting().create();
			String metadataJSON = gson.toJson(jo);

			try {
				saveImageFile(image, dir_, dataset, metadataJSON);
			} catch (Exception e) {
				e.printStackTrace();
			}
			writeFrameMetadata(image, metadataJSON, dataset);


		}

		Coords coords = image.getCoords();
//		System.out.println(image.getMetadata());

		if(coords.getC() == image.getMetadata().getUserData().getInteger("Channels", 1) - 1 &&
				coords.getZ() == image.getMetadata().getUserData().getInteger("Slices", 1) - 1) {

			int width = image.getWidth();
			int height = image.getHeight();
			int bytesPerPixel = image.getBytesPerPixel();
			int channels = image.getMetadata().getUserData().getInteger("Channels", 1);
			int depth = image.getMetadata().getUserData().getInteger("Slices", 1);


			long[] dimensions = new long[] {width, height, channels, depth, timesteps_};
			int[] blockSize = new int[] {width / 2, height / 2, 1, depth / 2, 1};

			final DatasetAttributes attributes = new DatasetAttributes(
					dimensions,
					blockSize,
					bytesPerPixel == 1 ? DataType.UINT8 : DataType.UINT16,
					compression);

			String datasetName = makeDatasetName(coords);
			writer.createDataset(datasetName, attributes);
			datasetList_.put(datasetName, datasetName);

			int time = coords.getT();
			int angle = coords.getP();
			final long[] gridPosition = new long[5];
			gridPosition[4] = time;

			RandomAccessibleInterval source;

			if(!amLoading_) {
				for (int ch = 0; ch < channels; ch++) {
					gridPosition[2] = ch;
					if (bytesPerPixel == 1) {
						final Img rai = ImageJFunctions.<UnsignedByteType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[ch]));
						source = Views.addDimension(Views.zeroMin(rai), 0, 0);
					} else {
						final Img rai = ImageJFunctions.<UnsignedShortType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[ch]));
						source = Views.addDimension(Views.zeroMin(rai), 0, 0);
					}

					source = Views.addDimension(source, 0, 0);
					source = Views.moveAxis(source, 2, 3);

					if (writer != null) {
						try {
							N5Utils.saveBlock(source, writer, datasetName, gridPosition, exec);
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (ExecutionException e) {
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}

			timeFinished_.put(time, true);
		}

		if (!coordsToFilename_.containsKey(coords)) {
			// TODO: is this in fact always the correct fileName? What if it
			// isn't?  See the above code that branches based on amLoading_.
			coordsToFilename_.put(coords, dataset);
		}
		// Update our tracking of the max index along each axis.
		for (String axis : coords.getAxes()) {
			if (coords.getIndex(axis) > maxIndices_.getIndex(axis)) {
				maxIndices_ = maxIndices_.copyBuilder().index(
						axis, coords.getIndex(axis)).build();
			}
		}
	}

	@SuppressWarnings("Duplicates")
	private void saveImageFile(Image image, String path, String tiffFileName, String metadataJSON) throws Exception {
		if (firstImage_ == null) {
			firstImage_ = image;
		} else {
			ImageSizeChecker.checkImageSizes(firstImage_, image);
		}

		try {
			int width = image.getWidth();
			int height = image.getHeight();
			Object pixels = image.getRawPixels();
			int bytesPerPixel = image.getBytesPerPixel();
			int numComponents = image.getNumComponents();

			ImageProcessor ip;
			if (numComponents == 3 && bytesPerPixel == 4) {
				// 32-bit RGB
				int[] rgbPixels = new int[width * height];
				byte[] rawPixels = (byte[]) pixels;
				for (int i=0; i < width * height; i++) {
					rgbPixels[i] = (rawPixels[4 * i + 3] << (Byte.SIZE * 3));
					rgbPixels[i] |= (rawPixels[4 * i + 2] & 0xFF) << (Byte.SIZE * 2);
					rgbPixels[i] |= (rawPixels[4 * i + 1] & 0xFF) << (Byte.SIZE * 1);
					rgbPixels[i] |= (rawPixels[4 * i] & 0xFF);
				}

				ip = new ColorProcessor(width, height, rgbPixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 1) {
				// Byte
				ip = new ByteProcessor(width, height);
				ip.setPixels((byte[])pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 2) {
				// Short
				ip = new ShortProcessor(width, height);
				ip.setPixels((short[])pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 4) {
				// Float
				ip = new FloatProcessor(width, height);
				ip.setPixels((float[])pixels);
			}
			else {
				throw new IllegalArgumentException(String.format("Unexpected image format with %d bytes per pixel and %d components", bytesPerPixel, numComponents));
			}


			Coords coords = image.getCoords();

			cache_.put(coords.getZ() + "" + coords.getC(), ip);
			int ch = coords.getC();
			int pos = coords.getP();
			int plane = coords.getZ();
			int time = coords.getT();

			Metadata m = image.getMetadata();


			if(ch == 0 && plane == 0) {
				timeFinished_.put(time, false);
				int channels = m.getUserData().getInteger("Channels", 1);

				imageStacks = new ImageStack[channels];
				for(int i = 0; i < channels; i++) {
					imageStacks[i] = new ImageStack(width, height);
				}
			}

			imageStacks[coords.getC()].addSlice(ip);
			coordsMetadata_.put(coords, metadataJSON);
		} catch (IllegalArgumentException ex) {
			ReportingUtils.logError(ex);
		}
	}

	@SuppressWarnings("Duplicates")
	private void openNewDataSet(Image image) throws Exception {
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

		width = image.getWidth();
		height = image.getWidth();
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
		jo.add("StorageType", new JsonPrimitive(StorageType.N5.name()));
		Gson gson = new GsonBuilder().disableHtmlEscaping().
				setPrettyPrinting().create();
		writeJSONMetadata(pos, gson.toJson(jo), "Summary");

		// Writer
		writer = new N5FSWriter( dir_ + "/" + prefix_ + ".n5" );
		compression = new GzipCompression();
		exec = Executors.newFixedThreadPool(10);
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
	private void writeFrameMetadata(final Image image, final String metadataJSON,
									final String fileName) {
		try {
			String coords = image.getCoords().toString();
			String coordsKey = "Coords-" + coords;

			// Use 0 for situations where there's no index information.
//			int pos = Math.max(0, image.getCoords().getStagePosition());
			int pos = 0;
			JsonObject jo = new JsonObject();
			NonPropertyMapJSONFormats.coords().addToGson(jo,
					((DefaultCoords) image.getCoords()).toPropertyMap());
			jo.add("FileName", new JsonPrimitive(fileName));

			Gson gson = new GsonBuilder().disableHtmlEscaping().
					setPrettyPrinting().create();
			writeJSONMetadata(pos, gson.toJson(jo), coordsKey);

			String mdKey = "Metadata-" + coords;
			writeJSONMetadata(pos, metadataJSON, mdKey);
		} catch (Exception ex) {
			ReportingUtils.logError(ex);
		}
	}

	@SuppressWarnings("Duplicates")
	@Override
	public Image getImage(Coords coords) {
		if (coordsToFilename_.get(coords) == null) {
			// We don't have that image.
			ReportingUtils.logError("Asked for image at " + coords + " that we don't know about");
			return null;
		}

		String dataset = coordsToFilename_.get(coords);

		int c = coords.getC();
		int z = coords.getZ();
		int t = coords.getT();

		if(!timeFinished_.get(t)) {
			String key = z + "" + c;
			if(cache_.containsKey(key)) {
				ImagePlus imp = new ImagePlus(prefix_, cache_.get(key));
				return makeDefaultImage(coords, imp.getProcessor());
			} else {
				return null;
			}
		}

		if(!impMap_.containsKey(dataset)) {
			try {
				RandomAccessibleInterval rai = N5Utils.open(writer, dataset);
				impMap_.put(dataset, rai);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return makeDefaultImage(coords, getProcessor(coords));
	}

	@SuppressWarnings("Duplicates")
	< T extends NumericType< T > & NativeType< T > > ImageProcessor getProcessor(Coords coords) {

		String dataset = coordsToFilename_.get(coords);

		int c = coords.getC();
		int z = coords.getZ();
		int t = coords.getT();

		RandomAccessibleInterval<T> rai = impMap_.get(dataset);

		DatasetAttributes attributes = null;
		try {
			attributes = writer.getDatasetAttributes(dataset);
		} catch (IOException e) {
			e.printStackTrace();
		}

		final long[] dims = attributes.getDimensions();
		final long[] dimensions = new long[] {dims[0], dims[1], 1};

		final ImagePlusImg<T, ?> impImg;
		switch (attributes.getDataType()) {
			case UINT8:
				impImg = (ImagePlusImg)ImagePlusImgs.unsignedBytes(dimensions);
				break;
			case INT8:
				impImg = (ImagePlusImg)ImagePlusImgs.bytes(dimensions);
				break;
			case UINT16:
				impImg = (ImagePlusImg)ImagePlusImgs.unsignedShorts(dimensions);
				break;
			case INT16:
				impImg = (ImagePlusImg)ImagePlusImgs.shorts(dimensions);
				break;
			case UINT32:
				impImg = (ImagePlusImg)ImagePlusImgs.unsignedInts(dimensions);
				break;
			case INT32:
				impImg = (ImagePlusImg)ImagePlusImgs.ints(dimensions);
				break;
			case FLOAT32:
				impImg = (ImagePlusImg) ImagePlusImgs.floats(dimensions);
				break;
			default:
				System.err.println("Data type " + attributes.getDataType() + " not supported in ImageJ.");
				return null;
		}

		//long[] dimensions = new long[] {width, height, channels, depth, timesteps_};
		long[] offset = new long[dims.length];
		offset[2] = c;
		offset[3] = z;
		offset[4] = t;

		dims[2] = 1;
		dims[3] = 1;
		dims[4] = 1;

		IntervalView v = Views.offsetInterval(rai, offset, dims);

		final Cursor<T> cursor = v.cursor();
		final Cursor<T> impCursor = impImg.cursor();

		while(cursor.hasNext()) {
			cursor.fwd();
			impCursor.fwd();
			impCursor.get().set(cursor.get());
		}

		return impImg.getImagePlus().getProcessor();
	}

	@SuppressWarnings("Duplicates")
	DefaultImage makeDefaultImage(Coords coords, ImageProcessor proc) {
		Metadata metadata;

		try {
			String metadataJSON = coordsMetadata_.get(coords);
			metadata = DefaultMetadata.fromPropertyMap(
					NonPropertyMapJSONFormats.metadata().
							fromJSON(metadataJSON));
		}
		catch (IOException e) {
			ReportingUtils.logError(e, "Unable to extract image dimensions from JSON metadata");
			return null;
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
		return new DefaultImage(pixels, width, height,
				bytesPerPixel, numComponents, coords, metadata);
	}


	@Override
	public boolean hasImage(Coords coords) {
		return coordsToFilename_.containsKey(coords);
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
	public List<Image> getImagesMatching(Coords coords) throws IOException {
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

	@SuppressWarnings("Duplicates")
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
	public int getNumImages() {
		return coordsToFilename_.size();
	}

	@Override
	public void close() throws IOException {
		// We don't maintain any state that needs to be cleaned up.
	}
}
