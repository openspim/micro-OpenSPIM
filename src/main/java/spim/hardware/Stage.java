package spim.hardware;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.micromanager.internal.utils.ReportingUtils;

import spim.hardware.SPIMSetup.SPIMDevice;

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

	final SimpleDoubleProperty stepSizeProperty;
	final SimpleBooleanProperty inversedProperty;

	public Stage(CMMCore core, String label) {
		super(core, label);
		stepSizeProperty = new SimpleDoubleProperty(1);
		inversedProperty = new SimpleBooleanProperty(false);
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
			ReportingUtils.logError(e, "Couldn't get stage position for " + label);
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
			ReportingUtils.logError(e, "Couldn't set stage position for " + label + " to " + position);
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
		if(!hasProperty("Velocity")) {
			// if the stage hasn't got the Velocity property, we are probably on the demo stage,
			// so let's return a fake value
			return Arrays.asList(1.0);
		}
		
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
		double stepSize;
		if(hasProperty("StepSize"))
			stepSize = getPropertyDouble("StepSize");
		else
			stepSize = 1.0;

		if(stepSize == 1.0) {
			stepSize = stepSizeProperty.get();
		}

		return stepSize;
	}

	/**
	 * Get the minimum possible position in um.
	 * 
	 * @return min position in um
	 */
	public double getMinPosition() {
		if(hasProperty("Min"))
			return getPropertyDouble("Min")*getStepSize();
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
			return getPropertyDouble("Max")*getStepSize();
		else
			return 9000.0; // *** this is why you should implement your own stages.
	}

	/**
	 * Get the real minimum possible position in um.
	 *
	 * @return real min position in um
	 */
	public double getRealMinPosition() {
		if(hasProperty("Min"))
			return getPropertyDouble("Min");
		else
			return 0.0;
	}

	/**
	 * Get the real maximum possible position in um.
	 *
	 * @return real max position in um
	 */
	public double getRealMaxPosition()
	{
		if(hasProperty("Max"))
			return getPropertyDouble("Max");
		else
			return 9000.0; // *** this is why you should implement your own stages.
	}

	/**
	 * Run the motor to its home location.
	 */
	public void home()
	{
		throw new UnsupportedOperationException("Stage does not appear to support homing.");
	}

	public DeviceType getMMType() {
		return DeviceType.StageDevice;
	}

	/**
	 * Sets step size.
	 * @param stepSize the step size
	 * @throws UnsupportedOperationException the unsupported operation exception
	 */
	public void setStepSize(double stepSize) throws UnsupportedOperationException {
		if(hasProperty("StepSize"))
		{
			stepSizeProperty.set(stepSize);
			setProperty( "StepSize", stepSize );
		} else
			throw new UnsupportedOperationException();
	}

	public SimpleDoubleProperty stepSizeProperty() {
		return stepSizeProperty;
	}

	public SimpleBooleanProperty inversedProperty() {
		return inversedProperty;
	}
}
