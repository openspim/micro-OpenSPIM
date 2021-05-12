package spim.algorithm;

import ij.ImageStack;
import ij.process.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.awt.image.ColorModel;


/**
 * The DefaultAntiDrift class provides PhaseCorrelation method for AntiDrift.
 */
public class DefaultAntiDrift extends AbstractAntiDrift
{
//	Logger log = Logger.getLogger(DefaultAntiDrift.class.getName());
	/**
	 * Instantiates a new DefaultAntiDrift class using PhaseCorrelation.
	 */
	private final double sigma;
	private final AntiDrift.Type type;

	public DefaultAntiDrift(double sigmaValue)
	{
		sigma = sigmaValue;
		this.type = Type.PhaseCorrelation;
		setLastCorrection( Vector3D.ZERO );
	}

	public DefaultAntiDrift() {
		sigma = 0;
		type = Type.CenterOfMass;
	}

	public void reset()
	{
		first = null;
	}

	@Override public void startNewStack()
	{
		latest = new Projections();

		if(first == null)
			first = latest;

		stack = null;
	}

	@Override public void addXYSlice( ImageProcessor ip )
	{
		ImageProcessor copy = ip.duplicate();

		switch (type) {
			case CenterOfMass:
				if(stack == null) stack = new ImageStack( ip.getWidth(), ip.getHeight() );
				stack.addSlice( copy );
				break;
			case PhaseCorrelation:
//				copy.blurGaussian( sigma );
				latest.addXYSlice( copy );
				break;
		}
	}

	@Override public Vector3D finishStack()
	{
		Vector3D suggested = null;

		switch (type) {
			case CenterOfMass:
				lastMIP = generateMIP(stack);
				addMIPImage(lastMIP);
				// TODO: Check the inverse X, Y for XY Stage
				suggested = new Vector3D(lastXY.getX() - refXY.getX(), lastXY.getY() - refXY.getY(), 0f);
				break;
			case PhaseCorrelation:
				suggested = latest.correlateAndAverage(first);
				break;
		}

//		ij.IJ.log( "Suggested offset: " + suggested.toString() );
		System.out.println( type + "-Suggested offset: " + suggested.toString() );

		return suggested;
	}

	private Vector3D updatedOffset = Vector3D.ZERO;

	@Override public Vector3D updateOffset( Vector3D correction )
	{
		updatedOffset = new Vector3D(
				correction.getX() * -1,
				correction.getY() * -1,
				correction.getZ() * -1);

//		ij.IJ.log( "Updated offset: " + updatedOffset.toString() );
		System.out.println( type + "-Updated offset: " + updatedOffset.toString() );

		return updatedOffset;
	}

	public Vector3D getUpdatedOffset() {
		return updatedOffset;
	}

	public Type getType() {
		return this.type;
	}

	public ImageProcessor getLastMIP() {
		return lastMIP;
	}

	private void addMIPImage(ImageProcessor ip) {

		ImageStatistics stat = ip.getStatistics();
//		ImageStatistics stat = ImageStatistics.getStatistics(ip, 1043199, (Calibration)null);
		lastXY = new Vector3D(stat.xCenterOfMass, stat.yCenterOfMass, 0f);

		if(refImage == null) {
			refImage = ip.duplicate();
			refXY = lastXY;
		}
	}

	private ImageProcessor generateMIP(ImageStack stack) {
		if (null != stack) {
			ImageProcessor img = stack.getProcessor(1);

			int bitDepth = img.getBitDepth();
			int width = img.getWidth();
			int height = img.getHeight();
			int bytesPerPixel = img.getBitDepth() / 8;
			int numComponents = img.getNChannels();

			if (bytesPerPixel == 1) {
				// Create new array
				float[] newPixels = new float[width * height];
				byte[] newPixelsFinal = new byte[width * height];

				float currentValue;
				float actualValue;

				// Init the new array
				for (int i = 0; i < newPixels.length; i++) {
					newPixels[i] = 0;
				}

				// Iterate over all frames
				for (int i = 1; i <= stack.size(); i++) {

					// Get current frame pixels
					img = stack.getProcessor(i);
					byte[] imgPixels = (byte[]) img.getPixels();

					// Iterate over all pixels
					for (int index = 0; index < newPixels.length; index++) {
						currentValue = (float) (int) (imgPixels[index] & 0xffff);
						actualValue = (float) newPixels[index];
						newPixels[index] = (float) Math.max(currentValue, actualValue);
					}
				}

				// Convert to short
				for (int index = 0; index < newPixels.length; index++) {
					newPixelsFinal[index] = (byte) newPixels[index];
				}

				return new ByteProcessor(width, height, newPixelsFinal);

			} else if (bytesPerPixel == 2) {

				// Create new array
				float[] newPixels = new float[width * height];
				short[] newPixelsFinal = new short[width * height];

				float currentValue;
				float actualValue;

				// Init the new array
				for (int i = 0; i < newPixels.length; i++) {
					newPixels[i] = 0;
				}


				// Iterate over all frames
				for (int i = 1; i <= stack.size(); i++) {

					// Get current frame pixels
					img = stack.getProcessor(i);
					short[] imgPixels = (short[]) img.getPixels();

					// Iterate over all pixels
					for (int index = 0; index < newPixels.length; index++) {
						currentValue = (float) (int) (imgPixels[index] & 0xffff);
						actualValue = (float) newPixels[index];
						newPixels[index] = (float) Math.max(currentValue, actualValue);
					}
				}

				// Convert to short
				for (int index = 0; index < newPixels.length; index++) {
					newPixelsFinal[index] = (short) newPixels[index];
				}

				return new ShortProcessor(width, height, newPixelsFinal, ColorModel.getRGBdefault());
			} else if (bytesPerPixel == 4) {

				// Create new array
				float[] newPixels = new float[width * height];
				int[] newPixelsFinal = new int[width * height];

				float currentValue;
				float actualValue;

				// Init the new array
				for (int i = 0; i < newPixels.length; i++) {
					newPixels[i] = 0;
				}


				// Iterate over all frames
				for (int i = 1; i <= stack.size(); i++) {

					// Get current frame pixels
					img = stack.getProcessor(i);
					short[] imgPixels = (short[]) img.getPixels();

					// Iterate over all pixels
					for (int index = 0; index < newPixels.length; index++) {
						currentValue = (float) (int) (imgPixels[index] & 0xffff);
						actualValue = (float) newPixels[index];
						newPixels[index] = (float) Math.max(currentValue, actualValue);
					}
				}

				// Convert to short
				for (int index = 0; index < newPixels.length; index++) {
					newPixelsFinal[index] = (short) newPixels[index];
				}

				return new ColorProcessor(width, height, newPixelsFinal);
			}
		}
		return null;
	}
}
