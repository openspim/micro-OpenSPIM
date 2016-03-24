import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;

import net.imglib2.FinalDimensions;

import net.imglib2.realtransform.AffineTransform3D;
import org.micromanager.utils.ReportingUtils;
import spim.io.imgloader.ImageProcessorStackImgLoader;
import utils.ImageGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * Initial Test class for ImageProcessorStackImgLoader
 */
public class ImageProcessorStackImgLoaderTest implements Thread.UncaughtExceptionHandler
{
	private File outputDirectory = new File( "/Users/moon/Desktop/ome2" );

	private int angleSize;
	private int channelSize;
	private int tSize, zSize, xSize, ySize;

	ImageProcessorStackImgLoader stackImgLoader;
	final HashMap< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >( angleSize );
	final HashMap< ViewId, Partition > viewIdToPartition = new HashMap< ViewId, Partition >(  );

	ArrayList< Partition > hdf5Partitions;
	ArrayList<TimePoint> timepoints;
	String baseFilename;

	public ImageProcessorStackImgLoaderTest()
	{
		angleSize = 2;
		channelSize = 1;

		tSize = 9;
		zSize = 128;
		xSize = 128;
		ySize = 128;

		ArrayList<ViewId> viewIds = new ArrayList< ViewId >();

		ArrayList<BasicViewSetup > viewSetups = new ArrayList< BasicViewSetup>();
		timepoints = new ArrayList< TimePoint>();

		for(int t = 0; t < tSize; t++)
		{
			// Consider angle * zstack;
			timepoints.add( new TimePoint( t ) );

			for(int v = 0; v < angleSize; v++)
			{
				viewIds.add(new ViewId( t, v ));
			}
		}

		for(int i = 0; i < angleSize; i++)
		{
			String punit = "um";
			final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, 0.043, 0.043, 10 );
			final FinalDimensions size = new FinalDimensions( new int[] { xSize, ySize, zSize } );

			final BasicViewSetup setup = new BasicViewSetup( i, "" + i, size, voxelSize );
			setup.setAttribute( new Angle( i ) );

			// Illumination and Channel sizes are assumed to be 1
			setup.setAttribute( new Illumination( 0 ) );
			setup.setAttribute( new Channel( 0 ) );

			setups.put( i, setup );
			viewSetups.add( setup );
		}

		baseFilename = outputDirectory.getAbsolutePath() + "/dataset.h5";
		String basename = baseFilename;
		if ( basename.endsWith( ".h5" ) )
			basename = basename.substring( 0, basename.length() - ".h5".length() );


		hdf5Partitions = Partition.split( timepoints, viewSetups, 1, 1, basename );

		for(Partition par : hdf5Partitions)
		{
			System.out.println(par.getPath());
		}

		for( ViewId viewId : viewIds )
			for ( Partition p : hdf5Partitions )
				if( p.contains( viewId ) )
					viewIdToPartition.put( viewId, p );

		//WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, hdf5Partitions, params.getHDF5File() );
		System.out.println( hdf5Partitions.size() );
	}

	private static String makeFilename( int angleIndex, int timepoint )
	{
		return String.format( "Test_TL%02d_Angle%01d.ome.tiff", timepoint, angleIndex );
	}

	public void beginStack(int time, int angle) throws Exception
	{
		File outputDirectory = new File( "/Users/moon/Desktop/ome2" );
		stackImgLoader = new ImageProcessorStackImgLoader( outputDirectory, viewIdToPartition, setups.get( angle ), time, xSize, ySize, zSize );
		stackImgLoader.start();
	}

	public void processSlice( ImageProcessor ip, int channel, double X, double Y, double Z, double theta, double deltaT )
			throws Exception
	{
		stackImgLoader.process( ip );
	}

	public void finalizeStack() throws Exception
	{
		stackImgLoader.finalize();
	}

	public void finalizeAcquisition() throws Exception
	{
		// Make HDF5 master with linking all the fragments
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, null, null );


		TreeMap< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new TreeMap< Integer, ExportMipmapInfo >(  );

		for(Integer key: setups.keySet())
		{
			perSetupExportMipmapInfo.put( key, ProposeMipmaps.proposeMipmaps( setups.get( key ) ) );
		}

		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, hdf5Partitions, new File(baseFilename) );


		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		for(int i = 0; i < angleSize; i++)
		{
			// create SourceTransform from the images calibration
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			sourceTransform.set( 0.043, 0, 0, 0, 0, 0.043, 0, 0, 0, 0, 10, 0 );

			for ( int t = 0; t < tSize; ++t )
				registrations.add( new ViewRegistration( t, i, sourceTransform ) );
		}

		seq.setImgLoader( new Hdf5ImageLoader( new File(baseFilename), hdf5Partitions, seq, false ) );
		final File basePath = outputDirectory;
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );

		try
		{
			new XmlIoSpimDataMinimal().save( spimData, basePath + "/dataset.xml" );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
	}


	public void produceImageStack() throws Exception
	{
		int width = xSize, height = ySize, depth = zSize, time = tSize;

		for (int t=1; t <= time; t++)
		{
			// angle 0
			ImagePlus firstChannel0 = ImageGenerator.generateByteBlob( width, height, depth, 32 + 3 * t, 32 + 3 * t, 64, 12.5f, 12.5f, 10.0f );
			beginStack(t - 1, 0);
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel0.getStack().getProcessor( z ), 0, 0, 0, z - 1, 0, t - 1 );
			}
			finalizeStack();

			// angle 1
			ImagePlus firstChannel1 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 96 - 3*t, 64, 12.5f , 12.5f, 10.0f);
			beginStack(t - 1, 1);
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel1.getStack().getProcessor( z ), 0, 1, 0, z - 1, 1, t - 1 );
			}
			finalizeStack();
		}

		finalizeAcquisition();
	}

	@Override public void uncaughtException( Thread thread, Throwable throwable )
	{
		if(!(throwable instanceof Exception))
		{
			ReportingUtils.logError( throwable, "Non-exception throwable " + throwable.toString() + " caught from hdf5 resave thread. Wrapping." );
			throwable = new Exception("Wrapped throwable; see core log for details: " + throwable.getMessage(), throwable);
		}

		Exception rethrow = (Exception)throwable;
	}

	public static void main( String[] args )
	{
		ImageProcessorStackImgLoaderTest test = new ImageProcessorStackImgLoaderTest();
		try
		{
			test.produceImageStack();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}
}
