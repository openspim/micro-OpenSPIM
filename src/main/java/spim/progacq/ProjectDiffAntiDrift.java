package spim.progacq;

import ij.process.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * Created by moon on 5/11/15.
 */
public class ProjectDiffAntiDrift extends AntiDrift
{
	Projections latest;
	Projections first;
	private Vector3D lastCorrection;

	public ProjectDiffAntiDrift()
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

		final Vector3D center = getLastCorrection().add( latest.getCenter() );

		Vector3D init = latest.correlateAndAverage(first);

		first = latest;
	}

	public Vector3D getLastCorrection()
	{
		return lastCorrection;
	}

	public void setLastCorrection( Vector3D lastCorrection )
	{
		this.lastCorrection = lastCorrection;
	}
}
