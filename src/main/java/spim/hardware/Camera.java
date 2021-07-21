package spim.hardware;

import mmcorej.*;
import org.micromanager.internal.utils.ReportingUtils;
import spim.mm.StringUtil;

/**
 * Description: Camera device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
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

	/**
	 * @return Return a list of available binning values
	 * @throws Exception
	 */
	public StrVector getAvailableBinningValues() throws Exception {

		return core.getAllowedPropertyValues(getLabel(), MMCoreJ.getG_Keyword_Binning());
	}

	/**
	 * Get the binning in integer format
	 */
	private static int getBinningAsInt(String value, int def) throws Exception
	{
		// binning can be in "1;2;4" or "1x1;2x2;4x4" format...
		if (!StringUtil.isEmpty(value))
			// only use the first digit to get the binning as int
			return StringUtil.parseInt(value.substring(0, 1), 1);

		// default
		return def;
	}

	/**
	 * Get the current camera binning mode
	 */
	public int getBinning()
	{
		try {
			return getBinningAsInt(getBinningAsString(), 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 1;
	}


	/**
	 * Get the current camera binning mode (String format)
	 */
	public String getBinningAsString() {
		return getProperty(MMCoreJ.getG_Keyword_Binning());
	}

	public void setBinning(String val) {
		try {
			setProperty( MMCoreJ.getG_Keyword_Binning(), val);
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}
	}

	@Override
	public DeviceType getMMType() {
		return DeviceType.CameraDevice;
	}

}
