package spim.hardware;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class Arduino extends Device {
	static {
		installFactory(new Device.Factory() {
			@Override
			public Device manufacture( CMMCore core, String label) {
				return new Arduino(core, label);
			}
		}, "Arduino-Shutter", SPIMSetup.SPIMDevice.ARDUINO1, SPIMSetup.SPIMDevice.ARDUINO2);
	}

	public Arduino(CMMCore core, String label) {
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
	 * Power the arduino off or on.
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
	 * Get the power status.
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

	public DeviceType getMMType() {
		return DeviceType.ShutterDevice;
	}
}