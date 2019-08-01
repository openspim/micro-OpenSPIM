package spim.io;

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
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.io.imgloader.ImageProcessorStackImgLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * HDF5OutputHandler for the SPIMAcquisition
 */
public class HDF5OutputHandler implements OutputHandler, Thread.UncaughtExceptionHandler
{
	final private File outputDirectory;
	final private int tSize, xSize, ySize;

	private int rowSize;
	private int tileSize;

	private int channelSize;
	private int illuminationSize;


	// Each row can have different depth
	private int[] zSizes;
	private double[] zStepSize;
	private double[][] tileTransformMatrix;

	private long pixelDepth;
	private double pixelSizeUm;

	// Image property collections
	final HashMap< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
	final HashMap< ViewId, Partition > viewIdToPartition = new HashMap< ViewId, Partition >();


	// Image loader hashmap. Access angle -> timepoint order.
	final Map< Integer, HashMap< Integer, ImageProcessorStackImgLoader > > imgLoaders = Collections.synchronizedMap( new HashMap< Integer, HashMap< Integer, ImageProcessorStackImgLoader > >() );
	final ArrayList<Thread> finalizers = new ArrayList< Thread >();

	ArrayList<Partition> hdf5Partitions;
	ArrayList<TimePoint> timePoints;
	String baseFilename;

	/**
	 * Instantiates a new HDF 5 output handler.
	 *
	 * @param outDir the output directory
	 * @param xSize the x size
	 * @param ySize the y size
	 * @param tSize the time size
	 * @param rowSize the size of the processing rows
	 * @param tileSize the size of the tiles
	 */
	public HDF5OutputHandler( File outDir, int xSize, int ySize, int tSize, int rowSize, int tileSize )
	{
		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		this.outputDirectory = outDir;
		this.xSize = xSize;
		this.ySize = ySize;

		this.rowSize = rowSize;
		this.tileSize = tileSize;

		this.zSizes = new int[rowSize];
		this.zStepSize = new double[rowSize];
		this.tSize = tSize;

		this.pixelDepth = 8;
		this.pixelSizeUm = 0.043;
	}

	/**
	 * Initialize function to setup all the setups for HDF5 based dataset.
	 *
	 * @param datasetName the user-given dataset name
	 */
	public void init(String datasetName)
	{
		ArrayList<ViewId> viewIds = new ArrayList< ViewId >();
		ArrayList<BasicViewSetup> viewSetups = new ArrayList< BasicViewSetup >();
		timePoints = new ArrayList<TimePoint>();

		// Setup ViewID
		for(int t = 0; t < tSize; t++)
		{
			timePoints.add( new TimePoint( t ) );
			for(int v = 0; v < rowSize; v++)
			{
				viewIds.add(new ViewId( t, v ));
			}
		}

		// Setup ViewSetup

		// Row iteration
		for ( int i = 0; i < rowSize; i++ )
		{
			imgLoaders.put( i, new HashMap< Integer, ImageProcessorStackImgLoader >() );

			String punit = "um";
			final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pixelSizeUm, pixelSizeUm, zStepSize[ i ] );

			final FinalDimensions size = new FinalDimensions( new int[] { xSize, ySize, zSizes[ i ] } );

			final BasicViewSetup setup = new BasicViewSetup( i, "" + i, size, voxelSize );
			setup.setAttribute( new Angle( i % getAngleSize() ) );

			// Currently, Illumination and Channel sizes are assumed to be 1
			// TODO: if there are more channels and illuminations, deal with them here.
			setup.setAttribute( new Illumination( 0 ) );
			setup.setAttribute( new Channel( 0 ) );

			setups.put( i, setup );
			viewSetups.add( setup );
		}

		// Output file setup
		baseFilename = outputDirectory.getAbsolutePath() + "/" + datasetName + ".h5";
		String basename = baseFilename;
		if ( basename.endsWith( ".h5" ) )
			basename = basename.substring( 0, basename.length() - ".h5".length() );

		// HDF5 Partitions setup
		hdf5Partitions = Partition.split( timePoints, viewSetups, 1, 1, basename );

		for( ViewId viewId : viewIds )
			for ( Partition p : hdf5Partitions )
				if( p.contains( viewId ) )
					viewIdToPartition.put( viewId, p );
	}

	public int getAngleSize()
	{
		return rowSize / tileSize;
	}

	public int getRowSize()
	{
		return rowSize;
	}

	public int getChannelSize()
	{
		return channelSize;
	}

	public void setChannelSize( int channelSize )
	{
		this.channelSize = channelSize;
	}

	public int getIlluminationSize()
	{
		return illuminationSize;
	}

	public void setIlluminationSize( int illuminationSize )
	{
		this.illuminationSize = illuminationSize;
	}

	public int[] getzSizes()
	{
		return zSizes;
	}

	public void setzSizes( int[] zSizes )
	{
		this.zSizes = zSizes;
	}

	public double[] getzStepSize()
	{
		return zStepSize;
	}

	public void setzStepSize( double[] zStepSize )
	{
		this.zStepSize = zStepSize;
	}

	public long getPixelDepth()
	{
		return pixelDepth;
	}

	public void setPixelDepth( long pixelDepth )
	{
		this.pixelDepth = pixelDepth;
	}

	public double getPixelSizeUm()
	{
		return pixelSizeUm;
	}

	public void setPixelSizeUm( double pixelSizeUm )
	{
		this.pixelSizeUm = pixelSizeUm;
	}

	public void setTileTransformMatrix( double[][] tileTransformMatrix )
	{
		this.tileTransformMatrix = tileTransformMatrix;
	}

	/**
	 * Not supported.
	 * @return the image plus
	 * @throws Exception the exception
	 */
	@Override public ImagePlus getImagePlus() throws Exception
	{
		return null;
	}

	/**
	 * Begin stack with time and angle.
	 * @param time the time
	 * @param row the row
	 * @throws Exception the exception
	 */
	@Override public void beginStack( int time, int row ) throws Exception
	{
		//		ij.IJ.log("BeginStack:");
		//		ij.IJ.log("    Time "+ time);
		//		ij.IJ.log("    Angle: "+ angle);
		imgLoaders.get( row ).put( time, new ImageProcessorStackImgLoader( outputDirectory, viewIdToPartition, setups.get( row ),
				time, xSize, ySize, zSizes[ row ] ) );
		imgLoaders.get( row ).get( time ).start();
	}

	/**
	 * Process ImageProcessor slice.
	 * @param time the time
	 * @param angle the angle
	 * @param exp the exp
	 * @param c the c
	 * @param ip the ip
	 * @param X the x
	 * @param Y the y
	 * @param Z the z
	 * @param theta the theta
	 * @param deltaT the delta t
	 * @throws Exception the exception
	 */
	@Override public void processSlice( int time, int angle, int exp, int c, ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT ) throws Exception
	{
//		ij.IJ.log("Time "+ time + " Angle: "+ angle );
		imgLoaders.get(angle).get(time).process( ip );
	}

	/**
	 * Finalize stack and call the underlying image loader's finalizer.
	 * @param time the time
	 * @param angle the angle
	 * @throws Exception the exception
	 */
	@Override public void finalizeStack( final int time, final int angle ) throws Exception
	{
		Thread finalizer = new Thread ( new Runnable()
		{
			@Override public void run()
			{
				imgLoaders.get(angle).get(time).finalize();
			}
		});

		finalizers.add( finalizer );
		finalizer.start();
	}

	/**
	 * Finalize acquisition for specific time and angle.
	 * @param b true if successfully done in the acquisition.
	 * @throws Exception the exception
	 */
	@Override public void finalizeAcquisition( boolean b ) throws Exception
	{
		for(Thread thread : finalizers)
			thread.join();
		finalizers.clear();

		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timePoints ), setups, null, null );

		TreeMap< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new TreeMap< Integer, ExportMipmapInfo >(  );

		for(Integer key: setups.keySet())
		{
			perSetupExportMipmapInfo.put( key, ProposeMipmaps.proposeMipmaps( setups.get( key ) ) );
		}

		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, hdf5Partitions, new File( baseFilename ) );


		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();
		for(int i = 0; i < rowSize; i++)
		{
			for ( int t = 0; t < tSize; ++t )
			{
				final ViewRegistration viewRegistration = new ViewRegistration( t, i );
				// create SourceTransform from the images calibration
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				sourceTransform.set( pixelSizeUm, 0, 0, 0, 0, pixelSizeUm, 0, 0, 0, 0, zStepSize[i], 0 );

				final ViewTransform viewTransform = new ViewTransformAffine( "sourceTransformation", sourceTransform );
				viewRegistration.preconcatenateTransform( viewTransform );

				// if this is tile, there should be one more transformation
				if(tileSize > 1)
				{
					final AffineTransform3D tileTransform = new AffineTransform3D();
					tileTransform.set( 	1, 0, 0, tileTransformMatrix[i][0],
										0, 1, 0, tileTransformMatrix[i][1],
										0, 0, 1, tileTransformMatrix[i][2] );
					final ViewTransform tileViewTransform = new ViewTransformAffine( "tileTransformation", tileTransform );
					viewRegistration.preconcatenateTransform( tileViewTransform );
				}

				registrations.add( viewRegistration );
			}
		}

		seq.setImgLoader( new Hdf5ImageLoader( new File(baseFilename), hdf5Partitions, seq, false ) );
		final File basePath = outputDirectory;
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );

		String basename = baseFilename;
		if ( basename.endsWith( ".h5" ) )
			basename = basename.substring( 0, basename.length() - ".h5".length() );

		try
		{
			new XmlIoSpimDataMinimal().save( spimData, basename + ".xml" );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}


		// Clean up the all the temporary files
		for(int i = 0; i < rowSize; i++)
		{
			for ( int t = 0; t < tSize; t++ )
			{
				// Delete all the temp files when the acquisition is successfully done.
				//if(b) imgLoaders.get( i ).get( t ).cleanFiles();
				imgLoaders.get( i ).remove( t );
			}
			imgLoaders.remove( i );
		}

		imgLoaders.clear();
	}

	/**
	 * Not supported
	 * @param thread the thread
	 * @param throwable the throwable
	 */
	@Override public void uncaughtException( Thread thread, Throwable throwable )
	{

	}
}
