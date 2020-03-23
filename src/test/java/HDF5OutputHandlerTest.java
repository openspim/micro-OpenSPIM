import ij.ImagePlus;
import spim.io.HDF5OutputHandler;
import utils.ImageGenerator;

import java.io.File;

/**
 * HDF5OutputHandler Test
 */
public class HDF5OutputHandlerTest
{
	private File outputDirectory = new File( "/Users/moon/Desktop/ome2" );

	private int angleSize;
	private int channelSize;
	private int tSize, zSize, xSize, ySize;

	public HDF5OutputHandlerTest()
	{
		angleSize = 2;
		channelSize = 1;

		tSize = 9;
		zSize = 128;
		xSize = 128;
		ySize = 128;

	}

	public void process() throws Exception
	{
		// Setup handler
		final HDF5OutputHandler handler;
		handler = new HDF5OutputHandler( outputDirectory, xSize, ySize, tSize, angleSize, 1 );
		handler.setPixelDepth( 8 );
		handler.setPixelSizeUm( 0.043 );

		handler.setzSizes( new int[]{128, 128} );
		handler.setzStepSize( new double[] {10.0, 10.0} );

		handler.init( "niceDataset" );

		// Setup input images
		int width = xSize, height = ySize, depth = zSize, time = tSize;

		for (int t=1; t <= time; t++)
		{
			// angle 0
			ImagePlus firstChannel0 = ImageGenerator.generateByteBlob( width, height, depth, 32 + 3 * t, 32 + 3 * t, 64, 12.5f, 12.5f, 10.0f );
			handler.beginStack( t - 1, 0 );
			for (int z=1; z <= depth; z++) {
				handler.processSlice( t - 1, 0, 0, 0, firstChannel0.getStack().getProcessor( z ), 0, 0, z - 1, 0, t - 1 );
			}
			handler.finalizeStack( t - 1, 0 );

			// angle 1
			ImagePlus firstChannel1 = ImageGenerator.generateByteBlob(width, height, depth, 96 - 3*t, 96 - 3*t, 64, 12.5f , 12.5f, 10.0f);
			handler.beginStack( t - 1, 1 );
			for (int z=1; z <= depth; z++) {
				handler.processSlice( t - 1, 1, 0, 0, firstChannel1.getStack().getProcessor( z ), 1, 0, z - 1, 1, t - 1 );
			}
			handler.finalizeStack( t - 1, 1 );
		}

		handler.finalizeAcquisition( true );
	}

	public static void main( final String[] args )
	{
		HDF5OutputHandlerTest test = new HDF5OutputHandlerTest();
		try
		{
			test.process();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}
}
