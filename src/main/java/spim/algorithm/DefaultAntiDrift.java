package spim.algorithm;

import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;


/**
 * The DefaultAntiDrift class provides PhaseCorrelation method for AntiDrift.
 */
public class DefaultAntiDrift extends AbstractAntiDrift
{
//	Logger log = Logger.getLogger(DefaultAntiDrift.class.getName());
	/**
	 * Instantiates a new DefaultAntiDrift class using PhaseCorrelation.
	 */
	private final int windowSize;
	private int counter;
	private final double sigma;

	public DefaultAntiDrift(int keepWindowSize, double sigmaValue)
	{
		windowSize = keepWindowSize;
		sigma = sigmaValue;
		counter = 0;
		setLastCorrection( Vector3D.ZERO );
	}

	public DefaultAntiDrift() {
		this(5, 10);
	}

	public void reset() {
		first = null;
	}

	@Override public void startNewStack()
	{
		if((counter++ % windowSize) == 0)
		{
			reset();
		}

		latest = new Projections();

		if(first == null)
			first = latest;
	}

	@Override public void addXYSlice( ImageProcessor ip )
	{
		ip.blurGaussian(sigma);
		latest.addXYSlice( ip );
	}

	@Override public Vector3D finishStack()
	{
		Vector3D suggested = latest.correlateAndAverage(first);

		ij.IJ.log( "Suggested offset: " + suggested.toString() );

		return suggested;
	}

	@Override public Vector3D updateOffset( Vector3D offset )
	{
		Vector3D lastOffset = offset.add(lastCorrection);

		ij.IJ.log( "Last offset: " + lastOffset );

		return lastOffset;
	}
}
