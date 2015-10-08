package utils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;

/**
 * Generates test images
 * @author Johannes Schindelin
 * @author HongKee Moon
 */
public class ImageGenerator
{
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
	public static ImagePlus generateFloatBlob( final int width, final int height, final int depth,
			final float centerX, final float centerY, final float centerZ,
			final float sigmaX, final float sigmaY, final float sigmaZ ) {

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
	public static ImagePlus generateByteBlob( final int width, final int height, final int depth,
			final float centerX, final float centerY, final float centerZ,
			final float sigmaX, final float sigmaY, final float sigmaZ ) {

		final float[] gaussX = gauss(width, centerX, sigmaX);
		final float[] gaussY = gauss(height, centerY, sigmaY);
		final float[] gaussZ = gauss(depth, centerZ, sigmaZ);

		final ImageStack stack = new ImageStack(width, height);
		for (int k = 0; k < depth; k++) {
			final byte[] pixels = new byte[width * height];
			for (int j = 0; j < height; j++)
				for (int i = 0; i < width; i++)
					pixels[i + width * j] = (byte) ((gaussX[i] * gaussY[j] * gaussZ[k]) * 255);
			stack.addSlice(null, new ByteProcessor(width, height, pixels, null));
		}
		return new ImagePlus("Blob", stack);
	}

}
