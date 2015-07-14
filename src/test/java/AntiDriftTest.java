import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import spim.algorithm.AntiDrift;
import spim.controller.AntiDriftController;
import spim.algorithm.DefaultAntiDrift;

import java.awt.*;
import java.io.File;

import static org.junit.Assume.assumeTrue;

/**
 * The type Anti drift handler test.
 */
public class AntiDriftTest
{
	private ImagePlus impFirst;
	private ImagePlus impSecond;
	private ImagePlus impThird;


	/**
	 * Calculates the Gauss function (AKA kernel) of the given parameters.
	 *
	 * @param size the size of the kernel
	 * @param center the center of the Gaussian function
	 * @param sigma the standard deviation of the Gaussian function
	 * @return the kernel
	 */
	private static float[] gauss(final int size, final float center, final float sigma) {
		final float[] result = new float[size];
		for (int i = 0; i < size; i++) {
			final float x = (i - center) / sigma;
			result[i] = (float) Math.exp(-x * x / 2);
		}
		return result;
	}

	/**
	 * Generates a Gaussian 3D blob.
	 *
	 * @param width the width of the output image
	 * @param height the height of the output image
	 * @param depth the depth of the output image
	 * @param centerX the x-coordinate of the center of the blob
	 * @param centerY the y-coordinate of the center of the blob
	 * @param centerZ the z-coordinate of the center of the blob
	 * @param sigmaX the fuzzy radius in x direction
	 * @param sigmaY the fuzzy radius in y direction
	 * @param sigmaZ the fuzzy radius in z direction
	 * @return the image with the blob
	 */
	private static ImagePlus generateBlob(final int width, final int height, final int depth,
			final float centerX, final float centerY, final float centerZ,
			final float sigmaX, final float sigmaY, final float sigmaZ) {

		final float[] gaussX = gauss(width, centerX, sigmaX);
		final float[] gaussY = gauss(height, centerY, sigmaY);
		final float[] gaussZ = gauss(depth, centerZ, sigmaZ);

		final ImageStack stack = new ImageStack(width, height);
		for (int k = 0; k < depth; k++) {
			final float[] pixels = new float[width * height];
			for (int j = 0; j < height; j++)
				for (int i = 0; i < width; i++)
					pixels[i + width * j] = gaussX[i] * gaussY[j] * gaussZ[k];
			stack.addSlice(null, new FloatProcessor(width, height, pixels, null));
		}
		return new ImagePlus("Blob", stack);
	}

	/**
	 * setup the images
	 */
	@Before
	public void setup()
	{
		// impFirst = generateBlob(128, 128, 128, 64, 32, 48, 50 / 8.0f , 30 / 4.0f, 40 / 4.0f);
		// impSecond = generateBlob(128, 128, 128, 64 + 16, 32 + 24, 48 + 32, 40 /8.0f, 20 / 4.0f, 30 / 4.0f);
		// The below parameters are simplified version with above ratio
		impFirst = generateBlob(128, 128, 128, 64, 32, 48, 12.5f , 7.5f, 10.0f);
		impSecond = generateBlob(128, 128, 128, 64 + 16, 32 + 24, 48 + 32, 5.0f, 5.0f, 7.5f);
		impThird = generateBlob(128, 128, 128, 64 + 8, 32 + 32, 48 + 24, 4.0f, 4.0f, 6.5f);
	}

	/**
	 * Test anti drift handler.
	 */
	@Test
	public void testAntiDrift()
	{
		final DefaultAntiDrift proj = new DefaultAntiDrift();
		proj.startNewStack();

		final ImageStack stackFirst = impFirst.getImageStack();
		for(int k = 1; k <= stackFirst.getSize(); k++)
		{
			proj.addXYSlice( stackFirst.getProcessor( k ) );
		}
		proj.finishStack( );

		proj.startNewStack();
		final ImageStack stackSecond = impSecond.getImageStack();
		for(int k = 1; k <= stackSecond.getSize(); k++)
		{
			proj.addXYSlice( stackSecond.getProcessor( k ) );
		}
		proj.finishStack( );

		final double DELTA = 1e-5;
		final Vector3D correction = proj.getLastCorrection();
		Assert.assertEquals(16, correction.getX(), DELTA);
		Assert.assertEquals(24, correction.getY(), DELTA);
		Assert.assertEquals(32, correction.getZ(), DELTA);
	}

	/**
	 * GUI involved test which is run in main function
	 */
	public void testAntiDriftController()
	{
		assumeTrue(!GraphicsEnvironment.isHeadless());

		final AntiDriftController ct = new AntiDriftController( new File("/Users/moon/temp/"), 2, 3, 4, 0, 1, 0);

		ct.setCallback(new AntiDrift.Callback() {
			public void applyOffset(Vector3D offs) {
				offs = new Vector3D(offs.getX()*-1, offs.getY()*-1, -offs.getZ());
				System.out.println(String.format("Offset: %s", offs.toString()));
			}
		});

		ct.startNewStack();

		final ImageStack stackFirst = impFirst.getImageStack();
		for(int k = 1; k <= stackFirst.getSize(); k++)
		{
			ct.addXYSlice( stackFirst.getProcessor( k ) );
		}
		ct.finishStack();

		ct.startNewStack();
		final ImageStack stackSecond = impSecond.getImageStack();
		for(int k = 1; k <= stackSecond.getSize(); k++)
		{
			ct.addXYSlice( stackSecond.getProcessor( k ) );
		}
		ct.finishStack();

		try
		{
			Thread.sleep( 10000 );
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		ct.startNewStack();
		final ImageStack stackThird = impThird.getImageStack();
		for(int k = 1; k <= stackThird.getSize(); k++)
		{
			ct.addXYSlice( stackThird.getProcessor( k ) );
		}
		ct.finishStack();
	}

	public static void main(String[] argv)
	{
		AntiDriftTest test = new AntiDriftTest();
		test.setup();
		test.testAntiDriftController();
	}
}
