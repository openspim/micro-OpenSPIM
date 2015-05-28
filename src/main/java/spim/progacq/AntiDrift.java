package spim.progacq;

import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * The interface Anti-drift.
 */
public interface AntiDrift
{
	/**
	 * Start new stack.
	 */
	void startNewStack();

	/**
	 * Tally slice.
	 *
	 * @param ip the ip
	 */
	void tallySlice(ImageProcessor ip);

	/**
	 * Finish stack.
	 */
	void finishStack();

	/**
	 * Finish stack.
	 *
	 * @param initial the initial
	 */
	void finishStack(boolean initial);

	/**
	 * Update offset.
	 *
	 * @param offset the offset
	 */
	void updateOffset(Vector3D offset);

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
