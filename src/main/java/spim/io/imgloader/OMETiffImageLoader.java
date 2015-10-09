package spim.io.imgloader;

import bdv.spimdata.SequenceDescriptionMinimal;
import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.micromanager.utils.ReportingUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by moon on 10/9/15.
 */
public class OMETiffImageLoader implements BasicImgLoader< UnsignedShortType >
{
	private final File file;

	private HashMap< Integer, BasicViewSetup > setups;

	private SequenceDescriptionMinimal sequenceDescriptionMinimal;

	public OMETiffImageLoader( final File file )
	{
		this.file = file;
	}

	public void setSetups( HashMap< Integer, BasicViewSetup > setups )
	{
		this.setups = setups;
	}

	public void setSequenceDescription( SequenceDescriptionMinimal sequenceDescriptionMinimal )
	{
		this.sequenceDescriptionMinimal = sequenceDescriptionMinimal;
	}

	@Override public RandomAccessibleInterval< UnsignedShortType > getImage( ViewId view )
	{
		Img< UnsignedShortType > img = null;
		try
		{
			img = openLOCI( file, new UnsignedShortType(), view );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		return img;
	}

	public static boolean createOMEXMLMetadata( final IFormatReader r )
	{
		try
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance( OMEXMLService.class );
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore(omexmlMeta);
		}
		catch (final ServiceException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (final DependencyException e)
		{
			e.printStackTrace();
			return false;
		}

		return true;
	}


	protected < T extends RealType< T > & NativeType< T > > Img< T > openLOCI( final File path, final T type, final ViewId view ) throws Exception
	{
		BasicViewDescription< ? > viewDescription = sequenceDescriptionMinimal.getViewDescriptions().get( view );

		final IFormatReader r = new ChannelSeparator();

		if ( !createOMEXMLMetadata( r ) )
		{
			try
			{
				r.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return null;
		}

		final String id = path.getAbsolutePath();

		r.setId( id );
		final Angle angle = getAngle( viewDescription );
		r.setSeries( angle.getId() );

		final boolean isLittleEndian = r.isLittleEndian();
		final int width = r.getSizeX();
		final int height = r.getSizeY();
		final int depth = r.getSizeZ();
		final int timepoints = r.getSizeT();
		final int channels = r.getSizeC();
		final int pixelType = r.getPixelType();
		final int bytesPerPixel = FormatTools.getBytesPerPixel( pixelType );
		final String pixelTypeString = FormatTools.getPixelTypeString( pixelType );

		final TimePoint timePoint = viewDescription.getTimePoint();
		int t = timePoint.getId();

//		final Channel channel = getChannel( viewDescription );
//		int c = channel.getId();
		int c = 0;

		final MetadataRetrieve retrieve = (MetadataRetrieve)r.getMetadataStore();

		double calX, calY, calZ;

		try
		{
			float cal = retrieve.getPixelsPhysicalSizeX( 0 ).value().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
			}
			calX = cal;

			cal = retrieve.getPixelsPhysicalSizeY( 0 ).value().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
			}
			calY = cal;

			cal = retrieve.getPixelsPhysicalSizeZ( 0 ).value().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
			}
			calZ = cal;
		}
		catch ( Exception e )
		{
			calX = calY = calZ = 1;
		}

		final Img< T > img;

		ArrayImgFactory factory = new ArrayImgFactory< UnsignedShortType >();
		img = factory.create( new long[] { width, height, depth }, type );

		if ( img == null )
		{
			r.close();
			throw new RuntimeException( "Could not instantiate " + factory.getClass().getSimpleName() + " for '" + path + "', most likely out of memory." );
		}
		else
			ReportingUtils.logMessage( new Date( System.currentTimeMillis() ) + ": Opening '" + path + "' [" + width + "x" + height + "x" + depth + " ch=" + c + " tp=" + t + " type=" + pixelTypeString + " image=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

		final byte[] b = new byte[width * height * bytesPerPixel];

		final int planeX = 0;
		final int planeY = 1;

		for ( int z = 0; z < depth; ++z )
		{
			IJ.showProgress( ( double ) z / ( double ) depth );

			final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();

			r.openBytes( r.getIndex( z, c, t ), b );

			if ( pixelType == FormatTools.UINT8 )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( b[ cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ] & 0xff );
				}
			}
			else if ( pixelType == FormatTools.UINT16 )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getShortValueInt( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
				}
			}
			else if ( pixelType == FormatTools.INT16 )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getShortValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
				}
			}
			else if ( pixelType == FormatTools.UINT32 )
			{
				//TODO: Untested
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getIntValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
				}
			}
			else if ( pixelType == FormatTools.FLOAT )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getFloatValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
				}
			}
		}

		r.close();

		IJ.showProgress( 1 );

		return img;
	}

	protected static final float getFloatValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return Float.intBitsToFloat( ((b[i+3] & 0xff) << 24)  + ((b[i+2] & 0xff) << 16)  +  ((b[i+1] & 0xff) << 8)  + (b[i] & 0xff) );
		else
			return Float.intBitsToFloat( ((b[i] & 0xff) << 24)  + ((b[i+1] & 0xff) << 16)  +  ((b[i+2] & 0xff) << 8)  + (b[i+3] & 0xff) );
	}

	protected static final int getIntValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return ( ((b[i+3] & 0xff) << 24)  + ((b[i+2] & 0xff) << 16)  +  ((b[i+1] & 0xff) << 8)  + (b[i] & 0xff) );
		else
			return ( ((b[i] & 0xff) << 24)  + ((b[i+1] & 0xff) << 16)  +  ((b[i+2] & 0xff) << 8)  + (b[i+3] & 0xff) );
	}

	protected static final short getShortValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		return (short)getShortValueInt( b, i, isLittleEndian );
	}

	protected static final int getShortValueInt( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return ((((b[i+1] & 0xff) << 8)) + (b[i] & 0xff));
		else
			return ((((b[i] & 0xff) << 8)) + (b[i+1] & 0xff));
	}

	protected static Angle getAngle( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Angle angle = vs.getAttribute( Angle.class );

		if ( angle == null )
			throw new RuntimeException( "This XML does not have the 'Angle' attribute for their ViewSetup. Cannot continue." );

		return angle;
	}

	protected static Channel getChannel( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Channel channel = vs.getAttribute( Channel.class );

		if ( channel == null )
			throw new RuntimeException( "This XML does not have the 'Channel' attribute for their ViewSetup. Cannot continue." );

		return channel;
	}

	@Override public UnsignedShortType getImageType()
	{
		return new UnsignedShortType();
	}
}
