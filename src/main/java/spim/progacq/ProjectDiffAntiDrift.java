package spim.progacq;

import ij.process.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * AntiDrift handler for checking the difference among projections
 */
public class ProjectDiffAntiDrift extends AntiDrift
{
	private Projections latest;
	private Projections first;
	private Vector3D lastCorrection;

	/**
	 * Instantiates a new Project diff anti drift.
	 */
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

		Vector3D init = latest.correlateAndAverage(first);

		System.out.println( init );
		setLastCorrection( init );

		final Vector3D correctedCenter = lastCorrection.add(latest.getCenter());
		System.out.println( correctedCenter );

		first = latest;
	}

	/**
	 * Gets last correction.
	 *
	 * @return the last correction
	 */
	public Vector3D getLastCorrection()
	{
		return lastCorrection;
	}

	/**
	 * Sets last correction.
	 *
	 * @param lastCorrection the last correction
	 */
	public void setLastCorrection( Vector3D lastCorrection )
	{
		this.lastCorrection = lastCorrection;
	}
}
