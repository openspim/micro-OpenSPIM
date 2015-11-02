package spim.io;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.IJ;
import ij.Prefs;
import ij.io.LogStream;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import spim.io.imgloader.OMETiffImageLoader;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by moon on 10/9/15.
 */
public class HDF5Generator
{
	private File outputDirectory;

	public HDF5Generator(File outDir, File path, int angles, int numTimepoints, double pixelSizeUm, double[] zStepSize) throws IOException, FormatException
	{
		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		outputDirectory = outDir;

		final IFormatReader r = new ChannelSeparator();

		if ( !OMETiffImageLoader.createOMEXMLMetadata( r ) )
		{
			try
			{
				r.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return;
		}

		final String id = path.getAbsolutePath();
		r.setId( id );

		final HashMap< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >( angles );
		int planeSize = 0;
		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();

		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		for(int i = 0; i < angles; i++)
		{
			r.setSeries(i);
			final int width = r.getSizeX();
			final int height = r.getSizeY();
			final int depth = r.getSizeZ();
			planeSize = width * height * r.getBitsPerPixel();

			String punit = "um";
			final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pixelSizeUm, pixelSizeUm, zStepSize[i] );
			final FinalDimensions size = new FinalDimensions( new int[] { width, height, depth } );

			final BasicViewSetup setup = new BasicViewSetup( i, "" + i, size, voxelSize );
			setup.setAttribute( new Angle( i ) );
			setups.put( i, setup );

			final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );
			perSetupExportMipmapInfo.put( i, autoMipmapSettings );

			// create SourceTransform from the images calibration
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			sourceTransform.set( pixelSizeUm, 0, 0, 0, 0, pixelSizeUm, 0, 0, 0, 0, zStepSize[i], 0 );

			for ( int t = 0; t < numTimepoints; ++t )
				registrations.add( new ViewRegistration( t, i, sourceTransform ) );
		}

		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );

		final OMETiffImageLoader imgLoader = new OMETiffImageLoader( path );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		imgLoader.setSetups( setups );
		imgLoader.setSequenceDescription( seq );

		final boolean isVirtual = false;
		final long planeSizeInBytes = planeSize;
		final long ijMaxMemory = IJ.maxMemory();
		final int numCellCreatorThreads = Math.max( 1, Prefs.getThreads() );
		final WriteSequenceToHdf5.LoopbackHeuristic loopbackHeuristic = new WriteSequenceToHdf5.LoopbackHeuristic()
		{
			@Override
			public boolean decide( final RandomAccessibleInterval< ? > originalImg, final int[] factorsToOriginalImg, final int previousLevel, final int[] factorsToPreviousLevel, final int[] chunkSize )
			{
				if ( previousLevel < 0 )
					return false;

				if ( WriteSequenceToHdf5.numElements( factorsToOriginalImg ) / WriteSequenceToHdf5.numElements( factorsToPreviousLevel ) >= 8 )
					return true;

				if ( isVirtual )
				{
					final long requiredCacheSize = planeSizeInBytes * factorsToOriginalImg[ 2 ] * chunkSize[ 2 ];
					if ( requiredCacheSize > ijMaxMemory / 4 )
						return true;
				}

				return false;
			}
		};

		final WriteSequenceToHdf5.AfterEachPlane afterEachPlane = new WriteSequenceToHdf5.AfterEachPlane()
		{
			@Override
			public void afterEachPlane( final boolean usedLoopBack )
			{
				if ( !usedLoopBack && isVirtual )
				{
					final long free = Runtime.getRuntime().freeMemory();
					final long total = Runtime.getRuntime().totalMemory();
					final long max = Runtime.getRuntime().maxMemory();
					final long actuallyFree = max - total + free;
				}
			}

		};

		ProgressWriterIJ progressWriter = new ProgressWriterIJ();
		final ArrayList< Partition > partitions = null;
		WriteSequenceToHdf5.writeHdf5File( seq, perSetupExportMipmapInfo, false, new File(outDir + "/dataset.h5"), loopbackHeuristic, afterEachPlane, numCellCreatorThreads, new SubTaskProgressWriter( progressWriter, 0, 0.95 ) );

		// write xml sequence description
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( new File(outDir + "/dataset.h5"), partitions, null, false );
		final SequenceDescriptionMinimal seqh5 = new SequenceDescriptionMinimal( seq, hdf5Loader );

		final File basePath = outputDirectory;
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seqh5, new ViewRegistrations( registrations ) );

		try
		{
			new XmlIoSpimDataMinimal().save( spimData, outDir + "/dataset.xml" );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}

		progressWriter.out().println( "done" );
	}

	private class ProgressWriterIJ implements ProgressWriter
	{
		protected final PrintStream out;

		protected final PrintStream err;

		public ProgressWriterIJ()
		{
			out = new LogStream();
			err = new LogStream();
		}

		@Override
		public PrintStream out()
		{
			return out;
		}

		@Override
		public PrintStream err()
		{
			return err;
		}

		@Override
		public void setProgress( final double completionRatio )
		{
			IJ.showProgress( completionRatio );
		}
	}
}
