package spim.io;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

import ij.process.ShortProcessor;
import javafx.application.Platform;
import loci.common.DataTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;

import loci.formats.services.OMEXMLService;
import mmcorej.CMMCore;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import org.micromanager.PropertyMap;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import spim.acquisition.AcquisitionStatus;
import spim.acquisition.Program;
import spim.acquisition.Row;

import org.micromanager.internal.utils.ReportingUtils;
import spim.algorithm.MaxProjections;

public class OMETIFFHandler implements OutputHandler, Thread.UncaughtExceptionHandler
{
	private File outputDirectory;

	private IMetadata meta;
	private int imageCounter, sliceCounter;
	private IFormatWriter writer;

	private CMMCore core;
	private int stacks, timesteps, tiles, channels;
	private Row[] acqRows;
	private double deltat;
	private boolean exportToHDF5;
	private double[] zStepSize;
	private Thread hdf5ResaveThread;
	private Exception rethrow;
	private String currentFile;
	final private String prefix;
	final PropertyMap metadataMap;
	final boolean separateChannel;
	private int timepoint;
	final boolean maxProject;
	final boolean isMultiPosition;
	MaxProjections[] maxProjections;
	VirtualStack imageStack;
	ImagePlus imageViewer;

	public OMETIFFHandler( CMMCore iCore, File outDir, String filenamePrefix, Row[] acqRows, int channels,
			int iTimeSteps, double iDeltaT, int tileCount, SummaryMetadata metadata, boolean exportToHDF5, boolean separateChannel, boolean maxProjection) {

		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		this.exportToHDF5 = exportToHDF5;
		imageCounter = -1;
		sliceCounter = 0;

		stacks = acqRows.length;
		zStepSize = new double[stacks];
		core = iCore;
		timesteps = iTimeSteps;
		deltat = iDeltaT;
		outputDirectory = outDir;
		this.acqRows = acqRows;
		tiles = tileCount;
		this.channels = channels;
		this.prefix = filenamePrefix;
		this.metadataMap = (( DefaultSummaryMetadata) metadata).toPropertyMap();
		this.separateChannel = separateChannel;
		this.maxProject = maxProjection;
		this.isMultiPosition = ( stacks > 1 );

		if ( maxProject )
		{
			if ( separateChannel )
				maxProjections = new MaxProjections[ channels ];
			else
				maxProjections = new MaxProjections[ 1 ];

			for ( int i = 0; i < maxProjections.length; i++ )
				maxProjections[ i ] = new MaxProjections();

			imageStack = new VirtualStack( ( int ) core.getImageWidth(), ( int ) core.getImageHeight(), null, outDir.getPath() );
		}

		try {
			//meta = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();
			meta = MetadataTools.createOMEXMLMetadata();

			meta.createRoot();

			meta.setDatasetID(MetadataTools.createLSID("Dataset", 0), 0);

			for (int image = 0; image < stacks; ++image) {
				meta.setImageID(MetadataTools.createLSID("Image", image), image);

				Row row = acqRows[image];
				int depth = row.getDepth();

				meta.setPixelsID(MetadataTools.createLSID("Pixels", 0), image);
				meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, image);
				meta.setPixelsBinDataBigEndian(Boolean.FALSE, image, 0);
				meta.setPixelsType(core.getImageBitDepth() == 8 ? PixelType.UINT8 : PixelType.UINT16, image);

				int positionIndex = image;

				for (int t = 0; t < timesteps; ++t) {

					if(separateChannel) {
						for ( int c = 0; c < channels; ++c )
						{
							String fileName = makeFilename( filenamePrefix, image, t, positionIndex, c );

							for ( int z = 0; z < depth; ++z )
							{
								int td = channels * depth * t + depth * c + z;
								meta.setUUIDFileName( fileName, image, td );
								meta.setUUIDValue( "urn:uuid:" + UUID.nameUUIDFromBytes( fileName.getBytes() ).toString(), image, td );

								meta.setTiffDataPlaneCount( new NonNegativeInteger( 1 ), image, td );
								meta.setTiffDataFirstT( new NonNegativeInteger( t ), image, td );
								meta.setTiffDataFirstC( new NonNegativeInteger( c ), image, td );
								meta.setTiffDataFirstZ( new NonNegativeInteger( z ), image, td );

								meta.setChannelID( MetadataTools.createLSID( "Channel", 0 ), image, td );
								meta.setChannelSamplesPerPixel( new PositiveInteger( 1 ), image, td );
							}
						}
					} else
					{
						String fileName = makeFilename( filenamePrefix, image, t, positionIndex, 0 );

						for ( int z = 0; z < depth; ++z )
						{
							for ( int c = 0; c < channels; ++c )
							{
								int td = channels*depth*t + channels*z + c;
								meta.setUUIDFileName( fileName, image, td );
								meta.setUUIDValue( "urn:uuid:" + UUID.nameUUIDFromBytes( fileName.getBytes() ).toString(), image, td );

								meta.setTiffDataPlaneCount(new NonNegativeInteger( 1 ), image, td);
								meta.setTiffDataFirstT(new NonNegativeInteger( t ), image, td);
								meta.setTiffDataFirstC(new NonNegativeInteger( c ), image, td);
								meta.setTiffDataFirstZ(new NonNegativeInteger( z ), image, td);

								meta.setChannelID( MetadataTools.createLSID( "Channel", c ), image, td );
								meta.setChannelSamplesPerPixel( new PositiveInteger( 1 ), image, td );
							}
						}
					}
				}

				meta.setPixelsSizeX(new PositiveInteger((int)core.getImageWidth()), image);
				meta.setPixelsSizeY(new PositiveInteger((int)core.getImageHeight()), image);
				meta.setPixelsSizeZ(new PositiveInteger(depth), image);
				if(separateChannel)
					meta.setPixelsSizeC(new PositiveInteger(1), image);
				else
					meta.setPixelsSizeC(new PositiveInteger(channels == 0 ? 1 : channels), image);

				meta.setPixelsSizeT(new PositiveInteger(1), image);

				meta.setPixelsPhysicalSizeX(FormatTools.getPhysicalSizeX(core.getPixelSizeUm()), image);
				meta.setPixelsPhysicalSizeY(FormatTools.getPhysicalSizeX(core.getPixelSizeUm()), image);
				meta.setPixelsPhysicalSizeZ(FormatTools.getPhysicalSizeX(Math.max(row.getZStepSize(), 1.0D)), image);
				zStepSize[image] = Math.max(row.getZStepSize(), 1.0D);

				meta.setPixelsTimeIncrement(new Time( deltat, UNITS.SECOND ), image);
			}

			writer = new ImageWriter().getWriter(makeFilename(filenamePrefix, 0, 0, 0, 0));

			writer.setWriteSequentially(true);
			writer.setMetadataRetrieve(meta);
			writer.setInterleaved(false);
			writer.setValidBitsPerPixel((int) core.getImageBitDepth());
			writer.setCompression("Uncompressed");
		} catch(Throwable t) {
			t.printStackTrace();
			throw new IllegalArgumentException(t);
		}
	}

	private String makeFilename(String filenamePrefix, int angleIndex, int timepoint, int posIndex, int c) {
		String posString = "";
		if (isMultiPosition)
			posString=String.format("_Pos%02d", posIndex);

		if (separateChannel)
			posString+=String.format("_Ch%02d", c);

		return String.format(filenamePrefix+"TL%04d"+posString+".tiff", timepoint, angleIndex);
	}

	private void openWriter(int angleIndex, int timepoint) throws Exception {
		currentFile = new File(outputDirectory, meta.getUUIDFileName(angleIndex, acqRows[angleIndex].getDepth()*timepoint*channels)).getAbsolutePath();
		writer.setMetadataRetrieve(meta);
		writer.changeOutputFile(currentFile);
		writer.setSeries(angleIndex);
		meta.setUUID(meta.getUUIDValue(angleIndex, acqRows[angleIndex].getDepth()*timepoint*channels));

		sliceCounter = 0;
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void beginStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Beginning stack along time " + time + " / angle " + angle );

		++imageCounter;
		openWriter(angle, time);
		timepoint = time;

		if ( maxProject )
		{
			for ( int i = 0; i < maxProjections.length; i++ )
				maxProjections[ i ].reset();
		}
	}

	private int doubleAnnotations = 0;
	private int storeDouble(int image, int plane, int n, String name, double val) {
		String key = String.format("%d/%d/%d: %s", image, plane, n, name);

		meta.setDoubleAnnotationID(key, doubleAnnotations);
		meta.setDoubleAnnotationValue(val, doubleAnnotations);
		meta.setPlaneAnnotationRef(key, image, plane, n);

		return doubleAnnotations++;
	}

	@Override
	public void processSlice(int time, int angle, int expT, int c, ImageProcessor ip,
			double X, double Y, double Z, double theta, double deltaT)
			throws Exception
	{
		long bitDepth = core.getImageBitDepth();
		byte[] data = bitDepth == 8 ?
			(byte[])ip.getPixels() :
			DataTools.shortsToBytes((short[])ip.getPixels(), false);

		int image = imageCounter % stacks;
		int plane = sliceCounter;

		if(separateChannel)
		{
			plane = sliceCounter % channels;

			currentFile = new File(outputDirectory, meta.getUUIDFileName(angle, acqRows[angle].getDepth()*timepoint*channels + acqRows[angle].getDepth() * c)).getAbsolutePath();
			writer.changeOutputFile(currentFile);
			if(null == writer.getMetadataRetrieve())
				writer.setMetadataRetrieve(meta);
			writer.setSeries(angle);
			meta.setUUID(meta.getUUIDValue(angle, acqRows[angle].getDepth()*timepoint*channels + acqRows[angle].getDepth() * c));

			meta.setPlaneTheC(new NonNegativeInteger(0), image, plane);
		} else {
			meta.setPlaneTheC(new NonNegativeInteger(c), image, plane);
		}

		if ( maxProject )
		{
			if ( separateChannel )
				maxProjections[ plane ].addXYSlice( ip );
			else
				maxProjections[ 0 ].addXYSlice( ip );
		}

		meta.setPlanePositionX(new Length(X, UNITS.REFERENCEFRAME), image, plane);
		meta.setPlanePositionY(new Length(Y, UNITS.REFERENCEFRAME), image, plane);
		meta.setPlanePositionZ(new Length(Z, UNITS.REFERENCEFRAME), image, plane);

		meta.setPlaneTheZ(new NonNegativeInteger(plane), image, plane);
		meta.setPlaneTheT(new NonNegativeInteger(0), image, plane);

		meta.setPlaneDeltaT(new Time(deltaT, UNITS.SECOND), image, plane);
		meta.setPlaneExposureTime(new Time(expT, UNITS.MILLISECOND), image, plane);

		storeDouble(image, plane, 0, "Theta", theta);

		try {
			writer.saveBytes(plane, data);
		} catch(java.io.IOException ioe) {
			finalizeStack(time, angle);
			if(writer != null)
				writer.close();
			throw new Exception("Error writing OME-TIFF.", ioe);
		}

		++sliceCounter;
	}

	@Override
	public void finalizeStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Finished stack along time " + time + " / angle " + angle );
		if(writer != null)
		{
			writer.close();
		}

		if ( maxProject )
		{
			if ( outputDirectory != null )
			{
				File saveDir = new File( outputDirectory, "MIP" );

				if ( !saveDir.exists() && !saveDir.mkdirs() )
				{
					ij.IJ.log( "Couldn't create output directory " + saveDir.getAbsolutePath() );
				}
				else
				{
					for ( int i = 0; i < maxProjections.length; i++ )
					{
						String posString = "";
						if ( isMultiPosition )
							posString = String.format( "_Pos%02d", angle );

						if ( separateChannel )
							posString += String.format( "_Ch%02d", i );

						String fileString = String.format( prefix + "TL%04d" + posString + ".tiff", timepoint );

						maxProjections[ i ].write( new File( saveDir, fileString ) );

						//                  imageStack.addSlice( maxProjections[i].getProcessor() );
						imageStack.addSlice( new File( "MIP", fileString ).getPath() );
					}

					if ( imageStack.getSize() == maxProjections.length * ( angle + 1 ) * ( time + 1 ) )
					{
//						System.out.println(maxProjections.length * ( angle + 1 ) * ( time + 1 ));
						if ( imageViewer == null )
						{
							imageViewer = new CompositeImage( new ImagePlus( "MIP:" + prefix, imageStack ) );
							imageViewer.setDimensions( maxProjections.length, angle + 1, time + 1 );
							imageViewer.setOpenAsHyperStack( true );
							imageViewer.show();
						}
						else
						{
							int c = imageViewer.getC();
							int z = imageViewer.getZ();
							int t = imageViewer.getT();

							imageViewer.setDimensions( maxProjections.length, angle + 1, time + 1 );
							imageViewer.setStack( imageStack );
							imageViewer.setPositionWithoutUpdate( c, z, t );
						}

						imageViewer.resetDisplayRange();
						imageViewer.updateImage();
					}
				}
			}
		}
	}

	@Override
	public void finalizeAcquisition(boolean bSuccess) throws Exception {

		final File firstFile = new File(outputDirectory, meta.getUUIDFileName(0, 0));

		imageCounter = 0;

		writer = null;

		// save metadata
		{
			PrintWriter pw = new PrintWriter( outputDirectory + File.separator + prefix + "OMEXMLMetadata.ome" );
			pw.print(xmlToString());
			pw.close();

			PropertyMap.Builder pmb = metadataMap.copyBuilder();
			pmb.putInteger( "Width", (int) core.getImageWidth() );
			pmb.putInteger( "Height", (int) core.getImageHeight() );

			String summaryMetadataString = NonPropertyMapJSONFormats.summaryMetadata().toJSON( pmb.build() );

			File file = new File(outputDirectory, prefix + "metadata.json");
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.write( summaryMetadataString );
				writer.close();
			}
			catch (IOException e) {
				ReportingUtils.logError(e, "Error while saving DisplaySettings");
			}
			finally {
				if (writer != null) {
					try {
						writer.close();
					}
					catch (IOException e) {
						ReportingUtils.logError(e, "Error while closing writer");
					}
				}
			}
		}

		if (bSuccess && exportToHDF5 && Program.getStatus().equals( AcquisitionStatus.DONE ))
		{
			hdf5ResaveThread = new Thread( new Runnable() {
				@Override
				public void run() {
					try
					{
						new HDF5Generator( outputDirectory, firstFile, stacks, timesteps,
								core.getPixelSizeUm(), zStepSize );
					}
					catch ( IOException e )
					{
						e.printStackTrace();
					}
					catch ( FormatException e )
					{
						e.printStackTrace();
					}
				}
			}, "HDF5 Resave Thread");
			hdf5ResaveThread.setPriority( Thread.MAX_PRIORITY );
			hdf5ResaveThread.setUncaughtExceptionHandler(this);
			hdf5ResaveThread.start();
		}
	}

	String xmlToString() {
		try {
			OMEXMLService service = new ServiceFactory().getInstance( OMEXMLService.class);
			return service.getOMEXML(meta) + " ";
		} catch ( DependencyException | ServiceException ex) {
			ReportingUtils.logError(ex);
			return "";
		}
	}

	@Override public void uncaughtException( Thread thread, Throwable throwable )
	{
		if(thread != hdf5ResaveThread)
			throw new Error("Unexpected exception mis-caught.", throwable);

		if(!(throwable instanceof Exception))
		{
			ReportingUtils.logError(throwable, "Non-exception throwable " + throwable.toString() + " caught from hdf5 resave thread. Wrapping.");
			throwable = new Exception("Wrapped throwable; see core log for details: " + throwable.getMessage(), throwable);
		}

		rethrow = (Exception)throwable;
	}
}