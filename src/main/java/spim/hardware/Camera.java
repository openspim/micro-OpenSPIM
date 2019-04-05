package spim.hardware;

import org.micromanager.internal.utils.ReportingUtils;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.TaggedImage;

public class Camera extends Device {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new Camera(core, label);
			}
		}, "*", SPIMSetup.SPIMDevice.CAMERA1, SPIMSetup.SPIMDevice.CAMERA2);
	}

	public Camera(CMMCore core, String label) {
		super(core, label);
//		try
//		{
//			for(String s : core.getDevicePropertyNames(label)){
//				System.out.println(s);
//			}
//		}
//		catch ( Exception e )
//		{
//			e.printStackTrace();
//		}
	}

	public TaggedImage snapImage() {
		try {
			core.snapImage();
			return core.getTaggedImage();
		} catch (Exception e) {
			ReportingUtils.logError(e);
			return null;
		}
	}

	/**
	 * Sets the exposure time in milliseconds.
	 * 
	 * @param exposureTime Time to open camera's physical shutter in milliseconds.
	 */
	public void setExposure(double exposureTime) {
		try {
			setProperty( "Exposure", exposureTime );
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}
	}

	/**
	 * Gets the exposure time in milliseconds.
	 * 
	 * @return Camera's current exposure time, in ms.
	 */
	public double getExposure() {
		try {
			return getPropertyDouble( "Exposure" );
		} catch (Exception e) {
			ReportingUtils.logError(e);
			return -1;
		}
	}

	public int getBinning() {
		try {
			return Integer.parseInt( getProperty( "Binning" ) );
		} catch (Exception e) {
			ReportingUtils.logError(e);
			return 1;
		}
	}

	@Override
	public DeviceType getMMType() {
		return DeviceType.CameraDevice;
	}

}
