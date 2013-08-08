package spim.setup;

import java.util.Collection;

import org.micromanager.utils.ReportingUtils;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public abstract class Stage extends Device {
	public Stage(CMMCore core, String label) {
		super(core, label);
	}

	/**
	 * Get this stage's position in um.
	 * 
	 * @return Stage's current position in um.
	 */
	public double getPosition() {
		try {
			return core.getPosition(label);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't get stage position for " + label, e);
			return 0;
		}
	}

	/**
	 * Set this stage's position in um.
	 * 
	 * @param position Position in um to go to.
	 */
	public void setPosition(double position) {
		try {
			core.setPosition(label, position);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't set stage position for " + label + " to " + position, e);
		}
	}

	/**
	 * Get the stage's velocity in um/s (preferably).
	 * 
	 * @return Stage's velocity in um/s.
	 * @throws UnsupportedOperationException If the stage does not support
	 *             variable velocity.
	 */
	public abstract double getVelocity();

	/**
	 * Set the stage's velocity in um/s.
	 * 
	 * @param velocity new velocity
	 * @throws UnsupportedOperationException If the stage does not support
	 *             variable velocity.
	 */
	public abstract void setVelocity(double velocity) throws IllegalArgumentException;

	/**
	 * Get the possible velocities of the motor in um/s (maybe).
	 * 
	 * @return A collection of the allowed velocities.
	 */
	public abstract Collection<Double> getAllowedVelocities();

	/**
	 * Get the length of a motor step in um.
	 * 
	 * @return um/step of the motor
	 */
	public abstract double getStepSize();

	/**
	 * Get the minimum possible position in um.
	 * 
	 * @return min position in um
	 */
	public abstract double getMinPosition();

	/**
	 * Get the maximum possible position in um.
	 * 
	 * @return max position in um
	 */
	public abstract double getMaxPosition();

	public DeviceType getMMType() {
		return DeviceType.StageDevice;
	}
}