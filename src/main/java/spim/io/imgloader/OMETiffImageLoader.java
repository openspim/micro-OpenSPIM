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
import mpicbg.spim.data.legacy.LegacyImgLoader;
import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by moon on 10/9/15.
 */
public class OMETiffImageLoader extends LegacyImgLoaderWrapper
{
	final LegacyOMETiffImageLoader legacyOMETiffImageLoader;

	public OMETiffImageLoader( final File file )
	{
		super( new LegacyOMETiffImageLoader( file ) );

		legacyOMETiffImageLoader = ( LegacyOMETiffImageLoader ) super.legacyImgLoader;
	}

	public static boolean createOMEXMLMetadata( final IFormatReader r )
	{
		try
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance( OMEXMLService.class );
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore( omexmlMeta );
		}
		catch ( final ServiceException e )
		{
			e.printStackTrace();
			return false;
		}
		catch ( final DependencyException e )
		{
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void setSequenceDescription( SequenceDescriptionMinimal sequenceDescriptionMinimal )
	{
		legacyOMETiffImageLoader.setSequenceDescription( sequenceDescriptionMinimal );
	}
}
