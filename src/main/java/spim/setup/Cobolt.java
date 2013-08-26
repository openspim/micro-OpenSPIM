package spim.setup;

import mmcorej.CMMCore;
import spim.setup.SPIMSetup.SPIMDevice;

public class Cobolt extends Laser {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new Cobolt(core, label);
			}
		}, "Cobolt", SPIMDevice.LASER1, SPIMDevice.LASER2);
	}

	public Cobolt(CMMCore core, String label) {
		super(core, label);
	}

	/**
	 * Power the laser off or on.
	 * 
	 * @param open True to turn on, false to turn off.
	 */
	public void setPoweredOn(boolean open) {
		setProperty("Laser", open ? "On" : "Off");
	}

	/**
	 * Get the laser's power status.
	 * 
	 * @return True of the laser is on, false if off.
	 */
	public boolean getPoweredOn() {
		return getProperty("Laser") == "On";
	}

	@Override
	public void setPower(double power) {
		setProperty("Power", power);
	}

	@Override
	public double getPower() {
		return getPropertyDouble("Power");
	}

	@Override
	public double getMinPower() {
		return 0;
	}

	@Override
	public double getMaxPower() {
		return 0.05;  // 50 mW. I don't know if this truly the maximum power, but I certainly wouldn't suggest more on a live sample.
	}

}
