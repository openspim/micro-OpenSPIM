package spim.io.imgloader;

import bdv.export.ProposeMipmaps;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Partition;

import ij.Prefs;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import org.apache.commons.io.FileUtils;
import spim.io.ObjectIO;


/**
 * StackImgLoader for slice based ImageProcessor
 * The purpose of this class is to save the image as HDF5 partition.
 * This class is used in HDF5OutputHandler
 */
public class ImageProcessorStackImgLoader
{
	final BasicViewSetup setup;
	final long[] dim;
	final FloatType type;
	final File outputFolder;
	final HashMap< ViewId, Partition > viewIdToPartition;
	final ViewId viewId;

	final int time;
	int sliceCount;
	int angle;
	static ArrayImgFactory imgFactory = new ArrayImgFactory< FloatType >();

	public ImageProcessorStackImgLoader( File output, HashMap< ViewId, Partition > viewIdToPartition, BasicViewSetup setup, int time, int width, int height, int depth )
	{
		this.setup = setup;
		this.dim = new long[]{ width, height, depth };
		this.type = new FloatType();
		this.outputFolder = output;
		this.viewIdToPartition = viewIdToPartition;

		this.time = time;
		this.angle = setup.getId();
		this.viewId = new ViewId( time, angle );
	}

	protected < T extends NativeType< T > > Img< T > instantiateImg( final long[] dim, final T type )
	{
		Img< T > img;

		try
		{
			img = imgFactory.imgFactory( type ).create( dim, type );
		}
		catch ( Exception e1 )
		{
			try
			{
				img = new CellImgFactory< T >( 256 ).create( dim, type );
			}
			catch ( Exception e2 )
			{
				img = null;
			}
		}

		return img;
	}

	private String makeTempFolderName()
	{
		return String.format( "%1s/TL%02d/%03d/", outputFolder.getAbsolutePath(), time, angle );
	}

	private String makeTempParentFolderName()
	{
		return String.format( "%1s/TL%02d", outputFolder.getAbsolutePath(), time );
	}

	/**
	 * Creates temporary folders
	 */
	public void start()
	{
		sliceCount = 0;
		File file = new File( makeTempFolderName() );
		if( !file.exists() )
		{
			// Creates appropriate folder
			file.mkdirs();
		}
	}

	/**
	 * Process ImageProcessor save it to the temporary folder
	 *
	 * @param ip the ImageProcessor instance
	 */
	public void process( ImageProcessor ip )
	{
		float[][] arrays = ip.getFloatArray();
		File file = new File( makeTempFolderName() + sliceCount++ + ".ip"  );
		ObjectIO.write( file, arrays );
	}

	/**
	 * Convert files to HDF5 partition file and clean up the temporary folder
	 */
	public void finalize()
	{
		if(sliceCount != dim[2])
		{
			System.err.println( "Check the stack acquisition. The initial stack depth is " + dim[2] + " and the actual stack count is " + sliceCount );
		}

		final Img< UnsignedShortType > img = instantiateImg( new long[] { dim[0], dim[1], dim[2] }, new UnsignedShortType() );

		double min = Double.MAX_VALUE, max = 0d;
		for(int i = 0; i < dim[2]; i++)
		{
			File file = new File( makeTempFolderName() + i + ".ip"  );
			float[][] arrays = ObjectIO.read( file );
			FloatProcessor ip = new FloatProcessor( arrays );
			min = Math.min( ip.getMin(), min );
			max = Math.max( ip.getMax(), max );
			imageProcessor2ImgLib2Img( i, ip, img );
		}

		final int numCellCreatorThreads = Math.max( 1, Prefs.getThreads() );

		final RandomAccessibleInterval< UnsignedShortType > ushortimg;
		if ( ! UnsignedShortType.class.isInstance( Util.getTypeFromInterval( img ) ) )
			ushortimg = convert( img, new double[] {min, max});
		else
			ushortimg = ( RandomAccessibleInterval ) img;

		WriteSequenceToHdf5.writeViewToHdf5PartitionFile( ushortimg, viewIdToPartition.get( viewId ), time, angle, ProposeMipmaps.proposeMipmaps( setup ),
				true, false, null, null, numCellCreatorThreads, null);

		cleanFiles();
	}

	void cleanFiles()
	{
		File file = new File( makeTempFolderName() );

		try
		{
			FileUtils.deleteDirectory(file);
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		// Clean up the TL based folder if there is not a child folder.
		file = new File( makeTempParentFolderName() );

		if(file.listFiles().length == 0)
		{
			try
			{
				FileUtils.deleteDirectory(file);
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< UnsignedShortType > convert( final RandomAccessibleInterval< T > img, final double[] minmax )
	{
		final RealUnsignedShortConverter< T > converter = new RealUnsignedShortConverter< T >( minmax[ 0 ], minmax[ 1 ] );

		return new ConvertedRandomAccessibleInterval<T, UnsignedShortType>( img, converter, new UnsignedShortType() );
	}

	public static void imageProcessor2ImgLib2Img( int z, final ImageProcessor ip, final Img< UnsignedShortType > img )
	{
		if ( img instanceof ArrayImg || img instanceof PlanarImg )
		{
			final Cursor< UnsignedShortType > cursor = img.cursor();
			final int sizeXY = ip.getWidth() * ip.getHeight();
			cursor.jumpFwd( sizeXY * z );
			{
				for ( int i = 0; i < sizeXY; ++i )
				{
					cursor.next().setReal( ip.getf( i ) );
				}
			}
		}
		else
		{
			throw new RuntimeException( "Only ArrayImg and PlanarImg are supported." );
		}
	}
}
