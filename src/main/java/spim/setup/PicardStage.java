package spim.setup;

import java.util.Arrays;
import java.util.Collection;

import spim.setup.SPIMSetup.SPIMDevice;
import mmcorej.CMMCore;

public class PicardStage extends Stage {
	/*
	 * Every new device implementation needs to define a Factory to create it.
	 * This lets you implement new devices with only the addition of a single
	 * file. This is done using Device.installFactory, and should be inside a
	 * static initializer block like below.
	 * 
	 * The 'manufacture' method is where you make a new instance of your device.
	 * If the class needs any special setup beyond the constructor, be sure to
	 * do that before returning. Here, we only need to make a new PicardStage.
	 * 
	 * The string following the Factory is the device name (*not* label!) that
	 * this factory can create. After that comes the SPIMDevices that this class
	 * can control. You can list as many as you want; as seen here, the X, Y,
	 * and Z stages could each be a linear stage.
	 */
	static {
		Device.installFactory(new Factory() {
			public Device manufacture(CMMCore core, String label) {
				return new PicardStage(core, label);
			}
		}, "Picard Z Stage", SPIMDevice.STAGE_X, SPIMDevice.STAGE_Y, SPIMDevice.STAGE_Z);
	}

	public PicardStage(CMMCore core, String label) {
		super(core, label);
	}

	@Override
	public double getVelocity() {
		return getPropertyDouble("Velocity");
	}

	@Override
	public void setVelocity(double velocity) throws IllegalArgumentException {
		if(velocity < 1 || velocity > 10 || Math.round(velocity) != velocity)
			throw new IllegalArgumentException("Velocity is not in 1..10 or is not an integer.");

		setProperty("Velocity", velocity);
	}
	
	@Override
	public Collection<Double> getAllowedVelocities() {
		return Arrays.asList(1D, 2D, 3D, 4D, 5D, 6D, 7D, 8D, 9D, 10D);
	}

	@Override
	public double getStepSize() {
		return 1.5;
	}

	@Override
	public double getMinPosition() {
		return 0;
	}

	@Override
	public double getMaxPosition() {
		return 9000;
	}

}
