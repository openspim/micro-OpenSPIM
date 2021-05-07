package spim.algorithm;

import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * The interface Anti-drift.
 */
public interface AntiDrift
{
	enum Type {
		CenterOfMass,
		PhaseCorrelation
	}
	/**
	 * Start new stack.
	 */
	void startNewStack();

	/**
	 * add a XY slice.
	 *
	 * @param ip the ip
	 */
	void addXYSlice(ImageProcessor ip);

	/**
	 * Finish stack.
	 */
	Vector3D finishStack();

	/**
	 * Update offset.
	 *
	 * @param offset the offset
	 */
	Vector3D updateOffset(Vector3D offset);

	/**
	 * The interface Callback.
	 */
	public interface Callback {
		/**
		 * Apply offset.
		 *
		 * @param offset the offset
		 */
		void applyOffset(Vector3D offset);
	}

	/**
	 * Sets callback.
	 *
	 * @param cb the cb
	 */
	void setCallback(Callback cb);
}
