package spim.setup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.micromanager.utils.ReportingUtils;

import spim.setup.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public class Stage extends Device {
	static {
		Device.installFactory(new Factory() {
			public Device manufacture(CMMCore core, String label) {
				return new Stage(core, label);
			}
		}, "*", SPIMDevice.STAGE_Z);
	}

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
	public double getVelocity() {
		if(hasProperty("Velocity"))
			return getPropertyDouble("Velocity");
		else
			return 1.0;
	}

	/**
	 * Set the stage's velocity in um/s.
	 * 
	 * @param velocity new velocity
	 * @throws UnsupportedOperationException If the stage does not support
	 *             variable velocity.
	 * @throws IllegalArgumentException Mostly if the value passed is not in
	 * 			   getAllowedVelocities.
	 */
	public void setVelocity(double velocity) throws UnsupportedOperationException, IllegalArgumentException {
		if(hasProperty("Velocity"))
			if(getAllowedVelocities().contains(velocity))
				setProperty("Velocity", velocity);
			else
				throw new IllegalArgumentException();
		else
			throw new UnsupportedOperationException();
	}

	/**
	 * Get the possible velocities of the motor in um/s (maybe).
	 * 
	 * @return A collection of the allowed velocities.
	 */
	public Collection<Double> getAllowedVelocities() {
		if(!hasProperty("Velocity"))
			return null;
		
		Collection<String> vals = getPropertyAllowedValues("Velocity");
		List<Double> list = new ArrayList<Double>(vals.size());
		
		for(String val : vals)
			list.add(Double.parseDouble(val));
		
		return list;
	}

	/**
	 * Get the length of a motor step in um.
	 * 
	 * @return um/step of the motor
	 */
	public double getStepSize() {
		if(hasProperty("StepSize"))
			return getPropertyDouble("StepSize");
		else
			return 1.0;
	}

	/**
	 * Get the minimum possible position in um.
	 * 
	 * @return min position in um
	 */
	public double getMinPosition() {
		if(hasProperty("Min"))
			return getPropertyDouble("Min");
		else
			return 0.0;
	}

	/**
	 * Get the maximum possible position in um.
	 * 
	 * @return max position in um
	 */
	public double getMaxPosition()
	{
		if(hasProperty("Max"))
			return getPropertyDouble("Max");
		else
			return 10000.0; // *** this is why you should implement your own stages.
	}

	public DeviceType getMMType() {
		return DeviceType.StageDevice;
	}
}