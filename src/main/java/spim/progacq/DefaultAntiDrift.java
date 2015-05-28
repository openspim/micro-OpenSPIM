package spim.progacq;

import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * Created by moon on 5/28/15.
 */
public class DefaultAntiDrift extends AbstractAntiDrift
{
	/**
	 * Instantiates a new Project diff anti drift.
	 */
	public DefaultAntiDrift()
	{
		setLastCorrection( Vector3D.ZERO );
	}

	@Override public void startNewStack()
	{
		latest = new Projections();
	}

	@Override public void tallySlice( ImageProcessor ip )
	{
		latest.addXYSlice( ip );
	}

	@Override public void finishStack()
	{
		finishStack( first == null );
	}

	@Override public void finishStack( boolean initial )
	{
		if(initial)
			first = latest;

		Vector3D suggested = latest.correlateAndAverage(first);

		System.out.println( suggested );
		setLastCorrection( suggested );
	}

	@Override public void updateOffset( Vector3D offset )
	{
		offset = offset.add(lastCorrection);
		setLastCorrection( offset );
	}
}
