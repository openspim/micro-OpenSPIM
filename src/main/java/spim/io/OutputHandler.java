package spim.io;

import ij.ImagePlus;
import ij.process.ImageProcessor;

public interface OutputHandler
{
	/**
	 * Gets the current state of the acquisition sequence as represented by an
	 * ImagePlus. This can be called in the middle of acquisition, prior to
	 * finalize (specifically if acquisition is interrupted).
	 *
	 * @return an ImagePlus representing the acquisition
	 * @throws Exception
	 */
	public abstract ImagePlus getImagePlus() throws Exception;

	/**
	 * Called by the acquisition code when about to begin snapping a new stack.
	 * 
	 * @param axis The dimension along which the stack is beginning.
	 * @throws Exception
	 */
	void beginStack(int axis) throws Exception;

	/**
	 * Handle the next slice as output by the acquisition code. What this means
	 * obviously depends largely on implementation.
	 *
	 * @param ip an ImageProcessor holding the pixels
	 * @param X the X coordinate at which the image was acquired
	 * @param Y the X coordinate at which the image was acquired
	 * @param Z the X coordinate at which the image was acquired
	 * @param theta the theta at which the image was acquired
	 * @param deltaT the time since beginning, in seconds, at which the image was acquired
	 * @throws Exception
	 */
	public abstract void processSlice(ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT) throws Exception;

	/**
	 * A stack has finished being acquired; react accordingly.
	 *
	 * @param depth The dimension along which the stack has been finished.
	 * @throws Exception
	 */
	public abstract void finalizeStack(int depth) throws Exception;

	/**
	 * The acquisition has ended; do any clean-up and finishing steps (such as
	 * saving the collected data to a file). IMPORTANT: After a call to finalize
	 * the handler should be in a state where it can accept new slices as an
	 * entirely different acquisition.
	 *
	 * @param b true if the process is finalized successfully. Otherwise, false for abortion.
	 * @throws Exception
	 */
	public abstract void finalizeAcquisition(boolean b) throws Exception;

}
