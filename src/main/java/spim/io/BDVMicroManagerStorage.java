package spim.io;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import bdv.img.n5.BdvN5Format;
import bdv.img.n5.N5ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clijx.CLIJx;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.ImageSizeChecker;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static bdv.img.n5.BdvN5Format.DATA_TYPE_KEY;
import static bdv.img.n5.BdvN5Format.DOWNSAMPLING_FACTORS_KEY;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: August 2022
 */
public class BDVMicroManagerStorage implements Storage {
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
	private int angles_;
	private int channels_;
	private boolean fusionChannel_;

	int width;
	int height;
	N5FSWriter writer;
	N5FSReader reader;
	GzipCompression compression;
	ExecutorService exec;
	ImageStack[] imageStacks;
	private HashMap<Coords, String> coordsMetadata_ = new HashMap<>();
	private HashMap<String, ImageProcessor> cache_ = new HashMap<>();
	private HashMap<Integer, Boolean> timeFinished_ = new HashMap<>();
	private HashMap<String, RandomAccessibleInterval> impMap_ = new HashMap<>();
	private HashMap<String, String> datasetList_ = new HashMap<>();


	private HashMap< Integer, BasicViewSetup > setups = new HashMap<>();
	private Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
	private ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

	public BDVMicroManagerStorage(DefaultDatastore store, String directory, String prefix, int channels, int timeSeqs, boolean newDataSet, boolean fusionChannel) throws IOException {
		store_ = store;
		dir_ = directory;
		prefix_ = prefix;

		store_.setSavePath(dir_);
		store_.setName(new File(dir_).getName());
		fusionChannel_ = fusionChannel;
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
		channels_ = channels;
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

		reader = new N5FSReader( dir_ + "/" + prefix_ + ".n5" );
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

//		if (isDatasetWritable_) {
//			final N5MicroManagerMetadata metaWriter = new N5MicroManagerMetadata( dir_ + "/" + prefix_ + ".n5" );
//			PropertyMap map = ((DefaultSummaryMetadata)summaryMetadata_).toPropertyMap();
//
//			System.out.println(map.toJSON());
//
//	//		int[] dims = map.getIntegerList("dimensions");
//	//		System.out.println(Arrays.toString(dims));
//
//			for (String name : datasetList_.keySet() ) {
//				final DatasetAttributes attributes = writer.getDatasetAttributes( name );
//				final long[] dims = attributes.getDimensions();
//
//				PropertyMap.Builder pb = PropertyMaps.builder().putAll(map)
//						.putInteger("Width", width)
//						.putInteger("Height", height)
//						.putInteger("Channels", (int) dims[2])
//						.putInteger("Slices", (int) dims[3])
//						.putInteger("Frames", (int) dims[4])
//						.putString("title", name)
//						.putEnumAsString( "Storage", StorageType.N5 );
//
//				metaWriter.readMetadata( pb.build() );
//
//				try {
//					metaWriter.writeMetadata( metaWriter, writer, name );
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		}

		isDatasetWritable_ = false;

		if(writer != null) {
			writer.close();

			// Save SpimData format for N5 storage
			try {
				saveXml(dir_ + "/" + prefix_ + ".n5", timesteps_);
			} catch (SpimDataException e) {
				e.printStackTrace();
			}
		}

		if(reader != null) {
			reader.close();
		}
	}

	private void saveXml(String n5FilePath, int numTimepoints) throws SpimDataException {
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );


		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal(new TimePoints( timepoints ), setups, null, null);
		N5ImageLoader imageLoader = new N5ImageLoader(new File(n5FilePath), seq);
		seq.setImgLoader(imageLoader);

		String xmlFileName = dir_ + "/dataset.xml";

		File seqFilename = new File(xmlFileName);
		final SpimDataMinimal spimdata = new SpimDataMinimal(seqFilename.getParentFile(), seq, new ViewRegistrations(registrations));
		io.save( spimdata, xmlFileName);
	}

	public static void main( String[] args ) throws SpimDataException, IOException {
		int angles = 4;
		int channels = 2;

		final HashMap< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >( angles );
		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		double pixelSizeUm = 0.325;
		double zStepSize = 3.048;
		int numTimepoints = 30;

		for(int j = 0; j < channels; j++) {
			for(int i = 0; i < angles; i++) {
				final int width = 1280;
				final int height = 1080;
				final int depth = 92;

				String punit = "µm";
				final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pixelSizeUm, pixelSizeUm, zStepSize);
				final FinalDimensions size = new FinalDimensions( new int[] { width, height, depth } );


				final BasicViewSetup setup = new BasicViewSetup( angles * j + i, "" + (angles * j + i), size, voxelSize );
				setup.setAttribute( new Angle( i ) );
				setup.setAttribute( new Channel( j ) );
				setup.setAttribute( new Illumination( 0 ) );
				setup.setAttribute( new Tile( 0 ) );
				setups.put( angles * j + i, setup );

				final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );
				perSetupExportMipmapInfo.put(angles * j + i , autoMipmapSettings );

				// create SourceTransform from the images calibration
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				sourceTransform.set( 1.0, 0, 0, 0, 0, 1.0, 0, 0, 0, 0, 9.378, 0 );

				for ( int t = 0; t < numTimepoints; ++t )
					registrations.add( new ViewRegistration( t, angles * j + i, sourceTransform ) );
			}

		}

		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );


		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal(new TimePoints( timepoints ), setups, null, null);
		N5ImageLoader imageLoader = new N5ImageLoader(new File("/Users/moon/temp/t/dataset.n5"), seq);
		seq.setImgLoader(imageLoader);

		String xmlFileName = "/Users/moon/temp/t/dataset.xml";

		File seqFilename = new File(xmlFileName);
		final SpimDataMinimal spimdata = new SpimDataMinimal(seqFilename.getParentFile(), seq, new ViewRegistrations(registrations));
		io.save( spimdata, xmlFileName);
	}

	@SuppressWarnings("Duplicates")
	@Override
	public void putImage(Image image) throws IOException {
		// Require images to only have time/channel/z/position/view axes.
		for (String axis : image.getCoords().getAxes()) {
			if (!ALLOWED_AXES.contains(axis)) {
				ReportingUtils.showError("OpenSPIM BDV storage cannot handle images with axis \"" + axis + "\". Allowed axes are " + ALLOWED_AXES);
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

		final int channels = image.getMetadata().getUserData().getInteger("Channels", 1);
		final Coords coords = image.getCoords();
//		System.out.println(image.getMetadata());

		final int channel = coords.getChannel();
		final int time = coords.getT();
		final int angle = coords.getP();

		final int setupId = (fusionChannel_ ? channel / 2 : channel) + angle * (fusionChannel_ ? channels / 2 : channels);

		String dataset = BdvN5Format.getPathName(setupId, time, 0);

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
				saveImageFile(image, metadataJSON);
			} catch (Exception e) {
				e.printStackTrace();
			}
			writeFrameMetadata(image, metadataJSON, dataset);
		}

		// Each stack acquisition is done, save the stack in specific Setup and Timepoint
		if(coords.getZ() == image.getMetadata().getUserData().getInteger("Slices", 1) - 1) {
			int width = image.getWidth();
			int height = image.getHeight();
			int bytesPerPixel = image.getBytesPerPixel();
			int depth = image.getMetadata().getUserData().getInteger("Slices", 1);

			String pathName = BdvN5Format.getPathName(setupId);
			if (writer != null) {
				writer.createGroup( pathName );

				final int[][] factors = { {1, 1, 1} };
				writer.setAttribute( pathName, DOWNSAMPLING_FACTORS_KEY, factors );
				writer.setAttribute( pathName, DATA_TYPE_KEY, bytesPerPixel == 1 ? DataType.UINT8 : DataType.UINT16 );

				final double pixelSizeUm = image.getMetadata().getPixelSizeUm();
				final double zStepSize = image.getMetadata().getUserData().getDouble("Z-Step-um", 1);

				String punit = "µm";
				final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pixelSizeUm, pixelSizeUm, zStepSize);
				final FinalDimensions size = new FinalDimensions( new int[] { width, height, depth } );


				final BasicViewSetup setup = new BasicViewSetup( setupId, "" + (setupId), size, voxelSize );
				setup.setAttribute( new Angle( angle ) );
				setup.setAttribute( new Channel( fusionChannel_ ? channel / 2 : channel ) );
				setup.setAttribute( new Illumination( 0 ) );
				setup.setAttribute( new Tile( 0 ) );
				setups.put( setupId, setup );

				final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );
				perSetupExportMipmapInfo.put(setupId, autoMipmapSettings );

				// create SourceTransform from the images calibration
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				sourceTransform.set( 1.0, 0, 0, 0, 0, 1.0, 0, 0, 0, 0, 9.378, 0 );

				registrations.add( new ViewRegistration( time, setupId, sourceTransform ) );

				pathName = BdvN5Format.getPathName(setupId, time);
				writer.createGroup( pathName );
				writer.setAttribute( pathName, "multiScale", true );
				final double[] resolution = new double[ voxelSize.numDimensions() ];
				voxelSize.dimensions( resolution );
				writer.setAttribute( pathName, "resolution", resolution );
			}

			long[] dimensions = new long[] {width, height, depth};
			int[] blockSize = new int[] {width / 2, height / 2, depth / 2};

			final DatasetAttributes attributes = new DatasetAttributes(
					dimensions,
					blockSize,
					bytesPerPixel == 1 ? DataType.UINT8 : DataType.UINT16,
					compression);

			dataset = BdvN5Format.getPathName(setupId, time, 0);

			if (writer != null) {
				writer.createDataset(dataset, attributes);
				final int[] downsamplingFactor = {1, 1, 1};
				writer.setAttribute( dataset, DOWNSAMPLING_FACTORS_KEY, downsamplingFactor );
			}
			datasetList_.put(dataset, dataset);

			final long[] gridPosition = new long[3];

			RandomAccessibleInterval source;

			if(!amLoading_) {
				if(!fusionChannel_) {
					if (bytesPerPixel == 1) {
						final Img rai = ImageJFunctions.<UnsignedByteType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[channel]));
						source = Views.zeroMin(rai);
					} else {
						final Img rai = ImageJFunctions.<UnsignedShortType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[channel]));
						source = Views.zeroMin(rai);
					}

					if (writer != null) {
						try {
							N5Utils.saveBlock(source, writer, dataset, gridPosition, exec);
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (ExecutionException e) {
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}

//				System.out.println(coords.getC() + "/" + channels);
				if(coords.getC() == channels - 1) {
					if(fusionChannel_) {
						int nChannels = channels / 2;
						for(int i = 0; i < nChannels; i++) {
							CLIJx clijx = CLIJx.getInstance();
							ClearCLBuffer gpu_input1 = clijx.push(new ImagePlus("gpu_input", imageStacks[nChannels*i]));
							ClearCLBuffer gpu_input2 = clijx.push(new ImagePlus("gpu_input", imageStacks[nChannels*i + 1]));

							// create an image with correct size and type on GPU for output
							ClearCLBuffer gpu_output = clijx.create(gpu_input1);

							clijx.maximumImages(gpu_input1, gpu_input2, gpu_output);

							ClearCLBuffer background_substrackted_image = clijx.create(gpu_input1);
							float sigma1x = 1.0f;
							float sigma1y = 1.0f;
							float sigma1z = 1.0f;
							float sigma2x = 5.0f;
							float sigma2y = 5.0f;
							float sigma2z = 5.0f;
							clijx.differenceOfGaussian3D(gpu_output, background_substrackted_image, sigma1x, sigma1y, sigma1z, sigma2x, sigma2y, sigma2z);

							// uncomment the below if you want to see the result
							 ImagePlus imp_output = clijx.pull(gpu_output);
							imp_output.setTitle("t=" + time + "/angle=" + angle);
							// imp_output.getProcessor().resetMinAndMax();
							// imp_output.show();

							// clean up memory on the GPU
							gpu_input1.close();
							gpu_input2.close();
							gpu_output.close();
							background_substrackted_image.close();

							if (bytesPerPixel == 1) {
								final Img rai = ImageJFunctions.<UnsignedByteType>wrap(imp_output);
								source = Views.zeroMin(rai);
							} else {
								final Img rai = ImageJFunctions.<UnsignedShortType>wrap(imp_output);
								source = Views.zeroMin(rai);
							}

							if (writer != null) {
								try {
									N5Utils.saveBlock(source, writer, dataset, gridPosition, exec);
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
					// Save SpimData format for N5 storage
//					System.out.println("xml Saved - t:" + time);
					try {
						saveXml(dir_ + "/" + prefix_ + ".n5", time + 1);
					} catch (SpimDataException e) {
						e.printStackTrace();
					}
				}
			}

			if(coords.getC() == image.getMetadata().getUserData().getInteger("Channels", 1) - 1) {
				timeFinished_.put(time, true);
			}
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
	private void saveImageFile(Image image, String metadataJSON) throws Exception {
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
				ip.setPixels(pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 2) {
				// Short
				ip = new ShortProcessor(width, height);
				ip.setPixels(pixels);
			}
			else if (numComponents == 1 && bytesPerPixel == 4) {
				// Float
				ip = new FloatProcessor(width, height);
				ip.setPixels(pixels);
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
		jo.add("StorageType", new JsonPrimitive(StorageType.BDV.name()));
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
				RandomAccessibleInterval rai = (writer != null) ? N5Utils.open(writer, dataset) : N5Utils.open(reader, dataset);
				impMap_.put(dataset, rai);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return makeDefaultImage(coords, getProcessor(coords));
	}

	@SuppressWarnings("Duplicates")
	< T extends NumericType< T > & NativeType< T >> ImageProcessor getProcessor(Coords coords) {

		String dataset = coordsToFilename_.get(coords);

		int z = coords.getZ();

		RandomAccessibleInterval<T> rai = impMap_.get(dataset);

		DatasetAttributes attributes = null;
		try {
			if(writer != null)
				attributes = writer.getDatasetAttributes(dataset);
			else if(reader != null)
				attributes = reader.getDatasetAttributes(dataset);
		} catch (IOException e) {
			e.printStackTrace();
		}

		final long[] dims = attributes.getDimensions();
		final long[] dimensions = new long[] {dims[0], dims[1], 1};

		final ImagePlusImg<T, ?> impImg;
		switch (attributes.getDataType()) {
			case UINT8:
				impImg = (ImagePlusImg) ImagePlusImgs.unsignedBytes(dimensions);
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

		//long[] dimensions = new long[] {width, height, depth};
		long[] offset = new long[dims.length];
		offset[2] = z;
		dims[2] = 1;

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
