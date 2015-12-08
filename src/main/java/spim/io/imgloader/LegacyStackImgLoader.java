package spim.io.imgloader;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;

import java.io.File;

public abstract class LegacyStackImgLoader extends AbstractImgFactoryImgLoader
{
	protected File path = null;

	protected AbstractSequenceDescription< ?, ?, ? > sequenceDescription;

	protected < T extends NativeType< T > > Img< T > instantiateImg( final long[] dim, final T type )
	{
		Img< T > img;

		try
		{
			img = getImgFactory().imgFactory( type ).create( dim, type );
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

	/**
	 * For a local initialization without the XML
	 *
	 * @param path
	 * @param imgFactory
	 */
	public LegacyStackImgLoader(
			final File path, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super();
		this.path = path;
		this.sequenceDescription = sequenceDescription;

		this.init( imgFactory );
	}

	protected void init( final ImgFactory< ? extends NativeType< ? > > imgFactory )
	{
		setImgFactory( imgFactory );
	}
}
