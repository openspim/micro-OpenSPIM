import ij.ImagePlus;
import ij.process.ImageProcessor;
import loci.common.services.ServiceFactory;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.services.OMEXMLService;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import utils.ImageGenerator;

import java.io.File;

/**
 * Create OmeTiff with multi-angle, multi-timeseries, multi-stack, multi-channel
 *
 * @author HongKee Moon
 */
public class OmeTiffTestCreator
{
	private File outputDirectory = new File( "/Users/moon/Desktop/ome" );
	private IMetadata meta;
	private IFormatWriter writer;
	private int imageCounter, sliceCounter;

	private int angleSize;
	private int channelSize;
	private int tSize, zSize, xSize, ySize;

	private static String makeFilename( int angleIndex, int timepoint )
	{
		return String.format( "Test_TL%02d_Angle%01d.ome.tiff", timepoint, angleIndex );
	}

	public OmeTiffTestCreator()
	{
		imageCounter = -1;
		sliceCounter = 0;

		angleSize = 4;
		channelSize = 2;

		tSize = 9;
		zSize = 128;
		xSize = 128;
		ySize = 128;

		try
		{
			meta = new ServiceFactory().getInstance( OMEXMLService.class ).createOMEXMLMetadata();

			meta.createRoot();

			meta.setDatasetID( MetadataTools.createLSID( "Dataset", 0 ), 0 );

			for ( int image = 0; image < angleSize; ++image )
			{
				meta.setImageID( MetadataTools.createLSID( "Image", image ), image );

				meta.setPixelsID( MetadataTools.createLSID( "Pixels", image ), image );
				meta.setPixelsDimensionOrder( DimensionOrder.XYCZT, image );
				meta.setPixelsBinDataBigEndian( Boolean.FALSE, image, 0 );
				meta.setPixelsType( PixelType.UINT8, image );

				for ( int c = 0; c < channelSize; ++c )
				{
					meta.setChannelID( MetadataTools.createLSID( "Channel", image ), image, c );
					meta.setChannelSamplesPerPixel( new PositiveInteger(1), image, c );
				}

				for ( int t = 0; t < tSize; ++t )
				{
					String fileName = makeFilename( image % angleSize, t );
					for ( int z = 0; z < zSize; ++z )
					{

						int td = zSize * t + z ;
						meta.setUUIDFileName( fileName, image, td );

						meta.setTiffDataPlaneCount( new NonNegativeInteger( channelSize ), image, td );
						meta.setTiffDataFirstT( new NonNegativeInteger( t ), image, td );
						meta.setTiffDataFirstZ( new NonNegativeInteger( z ), image, td );
						meta.setTiffDataFirstC( new NonNegativeInteger( 0 ), image, td );
					}
				}

				meta.setPixelsSizeX( new PositiveInteger( xSize ), image );
				meta.setPixelsSizeY( new PositiveInteger( ySize ), image );
				meta.setPixelsSizeC( new PositiveInteger( channelSize ), image );
				meta.setPixelsSizeZ( new PositiveInteger( zSize ), image );
				meta.setPixelsSizeT( new PositiveInteger( tSize ), image );

				meta.setPixelsPhysicalSizeX( FormatTools.getPhysicalSizeX( 0.035 ), image );
				meta.setPixelsPhysicalSizeY( FormatTools.getPhysicalSizeX( 0.035 ), image );
				meta.setPixelsPhysicalSizeZ( FormatTools.getPhysicalSizeX( Math.max( 0.01, 1.0D ) ), image );
				meta.setPixelsTimeIncrement( new Time( new Double( 1 ), UNITS.S ), image );
			}

			writer = new ImageWriter().getWriter( makeFilename( 0, 0 ) );
			if ( writer instanceof OMETiffWriter )
			{
				ij.IJ.log( "calling setBigTiff(true)" );
				( ( OMETiffWriter ) writer ).setBigTiff( true );
			}

			writer.setWriteSequentially( true );
			writer.setMetadataRetrieve( meta );
			writer.setInterleaved( false );
			writer.setValidBitsPerPixel( 8 );
			writer.setCompression( "Uncompressed" );
		}
		catch ( Throwable t )
		{
			t.printStackTrace();
			throw new IllegalArgumentException( t );
		}
	}

	private void openWriter( int angleIndex, int timepoint) throws Exception
	{
		writer.changeOutputFile( new File( outputDirectory, meta.getUUIDFileName( angleIndex, zSize * timepoint ) ).getAbsolutePath() );
		writer.setSeries( angleIndex );
		meta.setUUID( meta.getUUIDValue( angleIndex, zSize * timepoint ) );

		sliceCounter = 0;
	}

	public void beginStack() throws Exception
	{
		if ( ++imageCounter < angleSize * zSize )
			openWriter( imageCounter % angleSize, imageCounter / angleSize );
	}

	private int doubleAnnotations = 0;

	private int storeDouble( int image, int plane, int n, String name, double val )
	{
		String key = String.format( "%d/%d/%d: %s", image, plane, n, name );

		meta.setDoubleAnnotationID( key, doubleAnnotations );
		meta.setDoubleAnnotationValue( val, doubleAnnotations );
		meta.setPlaneAnnotationRef( key, image, plane, n );

		return doubleAnnotations++;
	}

	// ImageProcessor should have multiple channels
	public void processSlice( ImageProcessor ip, int channel, double X, double Y, double Z, double theta, double deltaT )
			throws Exception
	{
		byte[] data = ( byte[] ) ip.getPixels();

		int image = imageCounter % angleSize;
		int timePoint = imageCounter / angleSize;
		int plane = timePoint * zSize + sliceCounter;

		meta.setPlanePositionX( new Length( X, UNITS.REFERENCEFRAME ), image, plane );
		meta.setPlanePositionY( new Length( Y, UNITS.REFERENCEFRAME ), image, plane );
		meta.setPlanePositionZ( new Length( Z, UNITS.REFERENCEFRAME ), image, plane );
		meta.setPlaneTheC( new NonNegativeInteger( channel ), image, plane );
		meta.setPlaneTheZ( new NonNegativeInteger( sliceCounter ), image, plane );
		meta.setPlaneTheT( new NonNegativeInteger( timePoint ), image, plane );
		meta.setPlaneDeltaT( new Time( deltaT, UNITS.S ), image, plane );

		storeDouble( image, plane, 0, "Theta", theta );

		try
		{
			writer.saveBytes( plane, data );
		}
		catch ( java.io.IOException ioe )
		{
			finalizeStack();
			if ( writer != null )
				writer.close();
			throw new Exception( "Error writing OME-TIFF.", ioe );
		}

		++sliceCounter;
	}

	public void finalizeStack() throws Exception
	{
	}

	public void finalizeAcquisition() throws Exception
	{
		if ( writer != null )
			writer.close();

		imageCounter = 0;

		writer = null;
	}

	public void produceOmeTiff() throws Exception
	{
		int width = xSize, height = ySize, depth = zSize, time = tSize;

		for (int t=1; t <= time; t++)
		{
			// angle 0
			ImagePlus firstChannel0 = ImageGenerator.generateByteBlob( width, height, depth, 32 + 3 * t, 32 + 3 * t, 64, 12.5f, 12.5f, 10.0f );
			ImagePlus secondChannel0 = ImageGenerator.generateByteBlob(width, height, depth, 32 + 3*t, 32 + 3*t, 64, 5.0f, 5.0f, 7.5f);
			beginStack();
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel0.getStack().getProcessor( z ), 0, 0, 0, z - 1, 0, t - 1 );
				processSlice( secondChannel0.getStack().getProcessor( z ), 1, 0, 0, z - 1, 0, t - 1);
			}
			finalizeStack();

			// angle 1
			ImagePlus firstChannel1 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 96 - 3*t, 64, 12.5f , 12.5f, 10.0f);
			ImagePlus secondChannel1 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 96 - 3*t, 64, 5.0f, 5.0f, 7.5f);
			beginStack();
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel1.getStack().getProcessor( z ), 0, 1, 0, z - 1, 1, t - 1 );
				processSlice( secondChannel1.getStack().getProcessor( z ), 1, 1, 0, z - 1, 1, t - 1);
			}
			finalizeStack();

			// angle 2
			ImagePlus firstChannel2 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 32 + 3*t, 64, 12.5f , 12.5f, 10.0f);
			ImagePlus secondChannel2 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 32 + 3*t, 64, 5.0f, 5.0f, 7.5f);
			beginStack();
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel2.getStack().getProcessor( z ), 0, 0, 1, z - 1, 2, t - 1 );
				processSlice( secondChannel2.getStack().getProcessor( z ), 1, 0, 1, z - 1, 2, t - 1);
			}

			// angle 3
			ImagePlus firstChannel3 = ImageGenerator.generateByteBlob(width, height, depth, 32 + 3*t, 96 - 3*t, 64, 12.5f , 12.5f, 10.0f);
			ImagePlus secondChannel3 = ImageGenerator.generateByteBlob(width, height, depth, 32 + 3*t, 96 - 3*t, 64, 5.0f, 5.0f, 7.5f);
			beginStack();
			for (int z=1; z <= depth; z++) {
				processSlice( firstChannel3.getStack().getProcessor( z ), 0, 0, 1, z - 1, 2, t - 1 );
				processSlice( secondChannel3.getStack().getProcessor( z ), 1, 0, 1, z - 1, 2, t - 1);
			}
			finalizeStack();
		}

		finalizeAcquisition();
	}

	public static void main( String[] args )
	{
		OmeTiffTestCreator creator = new OmeTiffTestCreator();
		try
		{
			creator.produceOmeTiff();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}
}
