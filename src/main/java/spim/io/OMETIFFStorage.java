package spim.io;

import com.google.common.eventbus.Subscribe;
import com.google.gson.*;
import ij.ImagePlus;
import ij.process.*;
import javafx.application.Platform;
import loci.common.DataTools;
import loci.common.DebugTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.*;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.*;
import org.micromanager.data.internal.*;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.*;
import java.util.*;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class OMETIFFStorage implements Storage {
	final private static HashSet<String> ALLOWED_AXES = new HashSet<String>(
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
	private HashMap<Coords, String> coordsToFilename_;
	private HashMap<Integer, String> positionIndexToName_;
	private ArrayList<String> orderedChannelNames_;
	private Coords maxIndices_;
	private Image firstImage_;


	private IMetadata meta;
	private IFormatWriter writer;
	private int zSize = 1, cSize = 1;
	private HashMap<Coords, String> coordsMetadata_ = new HashMap<>();
	private HashMap<String, Boolean> closedFile_ = new HashMap<>();
	private HashMap<String, ImageProcessorReader> iprMap_ = new HashMap<>();
	private HashMap<String, ImageProcessor> cache_ = new HashMap<>();

	static {
		DebugTools.enableLogging( "OFF" );
		Platform.setImplicitExit( false );
	}

	public OMETIFFStorage(DefaultDatastore store, String directory, String prefix, boolean newDataSet) throws IOException {
		store_ = store;
		dir_ = directory;
		prefix_ = prefix;

		store_.setSavePath(dir_);
		store_.setName(new File(dir_).getName());
		isDatasetWritable_ = newDataSet;

		// Must be informed of events before traditional consumers, so that we
		// can provide images on request.
		store_.registerForEvents(this, 0);
		coordsToFilename_ = new HashMap<Coords, String>();
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
							closedFile_.put(fileName, true);
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
		if(writer != null) {
			writer.close();
		}
	}

	@SuppressWarnings("Duplicates")
	@Override
	public void putImage(Image image) throws IOException {
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

		String fileName = makeFilename(image.getCoords());
//		if (amLoading_ && !(new File(dir_ + "/" + fileName).exists())) {
//			// Try the 1.4 format instead. Since we may not have access to the
//			// channel name property, we just have to arbitrarily assign an
//			// ordering to the channel names that we find.
//			assignChannelsToIndices(prefix_);
//			fileName = create14FileName( image.getCoords());
//		}
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

			closedFile_.put(fileName, false);
			try {
				saveImageFile(image, dir_, fileName, metadataJSON);
			} catch (Exception e) {
				e.printStackTrace();
			}
			writeFrameMetadata(image, metadataJSON, fileName);
		}

		Coords coords = image.getCoords();

		if(coords.getC() == image.getMetadata().getUserData().getInteger("Channels", 1) - 1 &&
				coords.getZ() == image.getMetadata().getUserData().getInteger("Slices", 1) - 1) {
			closedFile_.put(fileName, true);
		}

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

	@SuppressWarnings("Duplicates")
	private void saveImageFile(Image image, String path, String tiffFileName, String metadataJSON) throws Exception {
		if (firstImage_ == null) {
			firstImage_ = image;
		} else {
			ImageSizeChecker.checkImageSizes(firstImage_, image);
		}
		byte[] data;
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

				data = DataTools.intsToBytes(rgbPixels, false);
				ip = new ColorProcessor(width, height, rgbPixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 1) {
				// Byte
				data = (byte[]) pixels;
				ip = new ByteProcessor(width, height);
				ip.setPixels((byte[])pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 2) {
				// Short
				data = DataTools.shortsToBytes((short[])pixels, false);
				ip = new ShortProcessor(width, height);
				ip.setPixels((short[])pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 4) {
				// Float
				data = DataTools.floatsToBytes((float[])pixels, false);
				ip = new FloatProcessor(width, height);
				ip.setPixels((float[])pixels);
			}
			else {
				throw new IllegalArgumentException(String.format("Unexpected image format with %d bytes per pixel and %d components", bytesPerPixel, numComponents));
			}

			try {
				Coords coords = image.getCoords();

				cache_.put(coords.getZ() + "" + coords.getC(), ip);
				int pos = coords.getP();
				int plane = coords.getZ();
				int time = coords.getT();

				Metadata m = image.getMetadata();

				td = doubleAnnotations;
				meta.setUUIDFileName( tiffFileName, pos, td );
				meta.setUUIDValue( "urn:uuid:" + UUID.nameUUIDFromBytes( tiffFileName.getBytes() ).toString(), pos, td );

				if(plane == 0) {
					meta.setImageID(MetadataTools.createLSID("Image", pos), pos);

					meta.setPixelsID(MetadataTools.createLSID("Pixels", 0), pos);
					meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, pos);
					meta.setPixelsBinDataBigEndian(Boolean.FALSE, pos, 0);
					meta.setPixelsType(image.getBytesPerPixel() == 1 ? ome.xml.model.enums.PixelType.UINT8 : PixelType.UINT16, pos);
					meta.setUUID(meta.getUUIDValue(pos, plane));
				}

				meta.setTiffDataPlaneCount(new NonNegativeInteger( 1 ), pos, td);
				meta.setTiffDataFirstT(new NonNegativeInteger( coords.getT() ), pos, td);
				meta.setTiffDataFirstC(new NonNegativeInteger( coords.getC() ), pos, td);
				meta.setTiffDataFirstZ(new NonNegativeInteger( coords.getZ() ), pos, td);

				meta.setChannelID( MetadataTools.createLSID( "Channel", coords.getC() ), pos, td );
				meta.setChannelSamplesPerPixel( new PositiveInteger( 1 ), pos, td );

				meta.setPlanePositionX(new Length(m.getXPositionUm(), UNITS.REFERENCEFRAME), pos, plane);
				meta.setPlanePositionY(new Length(m.getYPositionUm(), UNITS.REFERENCEFRAME), pos, plane);
				meta.setPlanePositionZ(new Length(m.getZPositionUm(), UNITS.REFERENCEFRAME), pos, plane);

				meta.setPlaneTheC(new NonNegativeInteger(coords.getC()), pos, plane);
				meta.setPlaneTheZ(new NonNegativeInteger(plane), pos, plane);
				meta.setPlaneTheT(new NonNegativeInteger(time), pos, plane);

				meta.setPlaneDeltaT(new Time(m.getElapsedTimeMs(0), UNITS.MS), pos, plane);
				meta.setPlaneExposureTime(new Time(m.getExposureMs(), UNITS.MS), pos, plane);

				// This is the placeholder for rotation angle
				storeDouble(pos, plane, 0, "Theta", 0);

				if(plane == 0) {
					if(writer != null) {
						writer.close();
					}
					meta.setPixelsSizeX(new PositiveInteger(image.getWidth()), pos);
					meta.setPixelsSizeY(new PositiveInteger(image.getHeight()), pos);
					meta.setPixelsSizeZ(new PositiveInteger(image.getMetadata().getUserData().getInteger("Slices", 1)), pos);
					meta.setPixelsSizeC(new PositiveInteger(image.getMetadata().getUserData().getInteger("Channels", 1)), pos);

					meta.setPixelsPhysicalSizeZ(FormatTools.getPhysicalSizeX(image.getMetadata().getUserData().getDouble("Z-Step-um", 1)), pos);
					meta.setPixelsSizeT(new PositiveInteger(1), pos);
					meta.setPixelsTimeIncrement(new Time( 1, UNITS.SECOND ), pos);

					writer.setMetadataRetrieve(meta);
					writer.changeOutputFile(new File(path, meta.getUUIDFileName(pos, td)).getAbsolutePath());
					writer.setSeries(pos);
				}

				writer.saveBytes(plane, data);
				coordsMetadata_.put(coords, metadataJSON);
			} catch(java.io.IOException ioe) {
				if(writer != null)
					writer.close();
				throw new Exception("Error writing OME-TIFF.", ioe);
			} catch (FormatException e) {
				e.printStackTrace();
			}

		} catch (IllegalArgumentException ex) {
			ReportingUtils.logError(ex);
		}
	}

	int td;

	private int doubleAnnotations = 0;
	@SuppressWarnings("Duplicates")
	private int storeDouble(int image, int plane, int n, String name, double val) {
		String key = String.format("%d/%d/%d: %s", image, plane, n, name);

		meta.setDoubleAnnotationID(key, doubleAnnotations);
		meta.setDoubleAnnotationValue(val, doubleAnnotations);
		meta.setPlaneAnnotationRef(key, image, plane, n);

		return doubleAnnotations++;
	}

	@SuppressWarnings("Duplicates")
	private String makeFilename(Coords coords) {
		String posString;
		posString=String.format("_Pos%02d", coords.getP());

		return String.format(prefix_ + "_TL%04d" + posString + ".tiff", coords.getT());
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
		jo.add("StorageType", new JsonPrimitive(StorageType.OMETiff.name()));
		Gson gson = new GsonBuilder().disableHtmlEscaping().
				setPrettyPrinting().create();
		writeJSONMetadata(pos, gson.toJson(jo), "Summary");


		meta = MetadataTools.createOMEXMLMetadata();
		meta.createRoot();
		meta.setDatasetID(MetadataTools.createLSID("Dataset", 0), 0);

		String fileName = makeFilename(image.getCoords());

		writer = new ImageWriter().getWriter(fileName);

		writer.setWriteSequentially(true);
		writer.setMetadataRetrieve(meta);
		writer.setInterleaved(false);
		writer.setValidBitsPerPixel(image.getMetadata().getBitDepth());
		writer.setCompression("Uncompressed");
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
	private void writeFrameMetadata(final Image image, final String metadataJSON,
									final String fileName) {
		try {
			String coords = image.getCoords().toString();
			String coordsKey = "Coords-" + coords;

			// Use 0 for situations where there's no index information.
			int pos = Math.max(0, image.getCoords().getStagePosition());
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

	public static ImageReader getImageReader( String path )
	{
		ImageReader reader = null;

		try
		{
			ServiceFactory factory = new ServiceFactory();
			OMEXMLService service = factory.getInstance(OMEXMLService.class);
			IMetadata meta = service.createOMEXMLMetadata();

			// create format reader
			reader = new ImageReader();
			reader.setMetadataStore(meta);

			// initialize file
//			System.out.println(path);
			reader.setId( path );
		}
		catch ( DependencyException e )
		{
			e.printStackTrace();
		}
		catch ( ServiceException e )
		{
			e.printStackTrace();
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		catch ( FormatException e )
		{
			e.printStackTrace();
		}

		return reader;
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

		int cLocal = coords.getC();
		int zLocal = coords.getZ();

		if (!closedFile_.get(coordsToFilename_.get(coords))) {
			// We wait for finishing the OMETIFF stack
			// But we can use the cached map
			String key = zLocal + "" + cLocal;
			if(cache_.containsKey(key)) {
				ImagePlus imp = new ImagePlus(prefix_, cache_.get(key));
				return makeDefaultImage(coords, imp.getProcessor());
			} else {
				return null;
			}
		}

		if(!iprMap_.containsKey(path) || coords.hasT() || coords.hasP()) {
			ImageReader reader = getImageReader( path );
			cSize = reader.getSizeC();
			zSize = reader.getSizeZ();

			iprMap_.put(path, new ImageProcessorReader(reader));
		}

		ImageProcessorReader ipr = iprMap_.get(path);

		try {
			ipr.setId( path );

			int seriesIndex = Arrays.asList( ipr.getUsedFiles() ).indexOf( path );
			if( seriesIndex != 0 )
			{
				if( seriesIndex < ipr.getSeriesCount() )
				{
					ipr.setSeries( seriesIndex );
				}
				else
					ipr.setSeries( ipr.getSeries() );
			}
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		int lZ = zLocal % zSize;
		int lC = cLocal % cSize;

		int index = 0;
		int channel = 0;

		if( ipr.getImageCount() == zSize * cSize )
		{
			// Add channel offset into index
			index += lC;
			// Add stack offset into index
			index += lZ * cSize;
		}
		else
		{
			// Channel should be accessed by the image processor array
			channel = lC;
			// Add stack offset into index
			index += lZ * cSize;
		}

		//				System.out.println( "Tried to access index = " + index );
		ImageProcessor ip = null;
		try {
			ip = ipr.openProcessors( index )[ channel ];
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ImagePlus imp = new ImagePlus(prefix_, ip);
		if (imp == null) {
			// Loading failed.
			ReportingUtils.logError("Unable to load image at " + path);
			return null;
		}
		try {
			// Assemble an Image out of the pixels and JSON-ified metadata.
			return makeDefaultImage(coords, imp.getProcessor());
		} catch (IllegalArgumentException ex) {
			ReportingUtils.logError(ex);
			return null;
		}
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
