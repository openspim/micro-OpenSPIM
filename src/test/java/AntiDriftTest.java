/**
 * Created by moon on 5/11/15.
 */

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import spim.progacq.ProjectDiffAntiDrift;

public class AntiDriftTest
{
	private ImagePlus impFirst;
	private ImagePlus impSecond;


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

	@Before
	public void setup()
	{
		impFirst = generateBlob(128, 128, 128, 64, 32, 48, 50, 30, 40);
		impSecond = generateBlob(128, 128, 128, 64 + 16, 32 + 24, 48 + 32, 50 - 10, 30 - 10, 40 - 10);
	}

	@Test
	public void testAntiDrift()
	{
		final ProjectDiffAntiDrift proj = new ProjectDiffAntiDrift();
		proj.startNewStack();

		for(int k = 1; k <= impFirst.getSlice(); k++)
		{
			impFirst.setSlice( k );
			proj.tallySlice( impFirst.getProcessor() );
		}
		proj.finishStack( true );

		for(int k = 1; k <= impSecond.getSlice(); k++)
		{
			impFirst.setSlice( k );
			proj.tallySlice( impSecond.getProcessor() );
		}
		proj.finishStack( false );

		final double DELTA = 1e-5;
		final Vector3D correction = proj.getLastCorrection();
		Assert.assertEquals(16, correction.getX(), DELTA);
		Assert.assertEquals(24, correction.getY(), DELTA);
		Assert.assertEquals(32, correction.getZ(), DELTA);
	}
}
