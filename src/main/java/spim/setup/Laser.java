package spim.setup;

import org.micromanager.utils.ReportingUtils;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public abstract class Laser extends Device {
	public Laser(CMMCore core, String label) {
		super(core, label);
	}

	protected String oldShutter;

	/**
	 * Store the current MM shutter, for operations that require replacing the
	 * default shutter (i.e. get/setShutterOpen).
	 * 
	 * @throws Exception
	 */
	protected void pushShutter() throws Exception {
		oldShutter = core.getShutterDevice();
	}

	/**
	 * Restore the stored MM shutter.
	 * 
	 * @throws Exception
	 */
	protected void popShutter() throws Exception {
		if (oldShutter == null)
			throw new Exception("No shutter stored!");

		core.setShutterDevice(oldShutter);
		oldShutter = null;
	}

	/**
	 * Power the laser off or on.
	 * 
	 * @param open True to turn on, false to turn off.
	 */
	public void setPoweredOn(boolean open) {
		try {
			pushShutter();
			core.setShutterOpen(open);
			popShutter();
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't open/close shutter " + label, e);
		}
	}

	/**
	 * Get the laser's power status.
	 * 
	 * @return True of the laser is on, false if off.
	 */
	public boolean getPoweredOn() {
		try {
			// This device is not necessarily the active shutter. Work around
			// that possibility.
			String oldShutter = core.getShutterDevice();
			boolean open = core.getShutterOpen();
			core.setShutterDevice(oldShutter);
			return open;
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't get shutter status for " + label, e);
			return false;
		}
	}

	/**
	 * Set the laser's power in Watts.
	 * 
	 * @param power Laser power in Watts.
	 */
	public abstract void setPower(double power);

	/**
	 * Get the laser's power in Watts.
	 * 
	 * @return Current laser power in Watts.
	 */
	public abstract double getPower();

	/**
	 * Return the minimum laser power in Watts. May be 0.
	 * 
	 * @return Minimum active laser power in Watts.
	 */
	public abstract double getMinPower();

	/**
	 * Return the maximum laser power in Watts.
	 * 
	 * @return Maximum active laser power in Watts.
	 */
	public abstract double getMaxPower();

	public DeviceType getMMType() {
		return DeviceType.ShutterDevice;
	}
}