package spim.hardware;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.internal.utils.ReportingUtils;

public class Laser extends Device {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new Laser(core, label);
			}
		}, "*", SPIMSetup.SPIMDevice.LASER1, SPIMSetup.SPIMDevice.LASER2);
	}

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
			ReportingUtils.logError(e, "Couldn't open/close shutter " + label);
		}
	}

	/**
	 * Get the laser's power status.
	 * 
	 * @return True of the laser is on, false if off.
	 */
	public boolean getPoweredOn() {
		try {
			pushShutter();
			boolean open = core.getShutterOpen();
			popShutter();
			return open;
		} catch (Exception e) {
			ReportingUtils.logError(e, "Couldn't get shutter status for " + label);
			return false;
		}
	}

	/**
	 * Set the laser's power in Watts.
	 * 
	 * @param power Laser power in Watts.
	 */
	public void setPower(double power) throws UnsupportedOperationException, IllegalArgumentException {
		if(hasProperty("Power"))
			setProperty("Power", power);
		else
			throw new UnsupportedOperationException();
	}

	/**
	 * Get the laser's power in Watts.
	 * 
	 * @return Current laser power in Watts.
	 */
	public double getPower() {
		if(hasProperty("Power"))
			return getPropertyDouble("Power");
		else
			return 0.0;
	}

	/**
	 * Return the minimum laser power in Watts. May be 0.
	 * 
	 * @return Minimum active laser power in Watts.
	 */
	public double getMinPower() {
		if(hasProperty("Minimum Laser Power"))
			return getPropertyDouble("Minimum Laser Power");
		else
			return 0.0;
	}

	/**
	 * Return the maximum laser power in Watts.
	 * 
	 * @return Maximum active laser power in Watts.
	 */
	public double getMaxPower() {
		if(hasProperty("Maximum Laser Power"))
			return getPropertyDouble("Maximum Laser Power");
		else
			return 0.05;
	}

	/**
	 * Return the laser wavelength.
	 *
	 * @return Wavelength.
	 */
	public String getWavelength() {
		if(hasProperty("Wavelength"))
			return getProperty("Wavelength");
		else
			return "594";
	}

	public DeviceType getMMType() {
		return DeviceType.ShutterDevice;
	}
}