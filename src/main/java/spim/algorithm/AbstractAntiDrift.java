package spim.algorithm;

import ij.process.ImageProcessor;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import spim.algorithm.AntiDrift;
import spim.algorithm.Projections;

import java.io.File;

/**
 * The Abstract class of anti-drift.
 * It holds the first projection and the last projection
 */
public abstract class AbstractAntiDrift implements AntiDrift
{
	/**
	 * Gets latest projection.
	 *
	 * @return the latest
	 */
	public Projections getLatest()
	{
		return latest;
	}

	/**
	 * Sets first projection.
	 *
	 * @param first the first
	 */
	public void setFirst( Projections first )
	{
		this.first = first;
	}

	/**
	 * Gets first projection.
	 *
	 * @return the first
	 */
	public Projections getFirst()
	{
		return first;
	}

	/**
	 * The Latest projection.
	 */
	protected Projections latest;
	/**
	 * The First projection.
	 */
	protected Projections first;
	/**
	 * The Last correction.
	 */
	protected Vector3D lastCorrection;

	private Callback callback;

	public abstract void startNewStack();

	public abstract void addXYSlice(ImageProcessor ip);

	public abstract Vector3D finishStack();

	public abstract Vector3D updateOffset(Vector3D offset);

	public void setCallback(Callback cb) {
		callback = cb;
	}

	/**
	 * Invoke callback.
	 *
	 * @param offset the offset
	 */
	public void invokeCallback(Vector3D offset) {
		if(callback != null)
			callback.applyOffset(offset);
		else
			ij.IJ.log("Anti-drift with no callback tried to invoke!");
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

	/**
	 * Write diff.
	 *
	 * @param outputDir the output dir
	 * @param offset the offset
	 * @param zratio the zratio
	 * @param center the center
	 */
	public void writeDiff(File outputDir, Vector3D offset, double zratio, Vector3D center)
	{
		latest.writeDiff(first, zratio, offset, center, outputDir);
	}
}
