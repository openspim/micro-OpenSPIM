package spim.hardware;

import ij.IJ;

import java.util.ArrayList;
import java.util.Map;
import java.util.EnumMap;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.internal.utils.ReportingUtils;

public class SPIMSetup {
	public static enum SPIMDevice {
		STAGE_X ("X Stage"),
		STAGE_Y ("Y Stage"),
		STAGE_Z ("Z Stage"),
		STAGE_THETA ("Rotator"),
		LASER1 ("Laser"),
		LASER2 ("Laser (2)"),
		ARDUINO1 ("Arduino"),
		ARDUINO2 ("Arduino (2)"),
		CAMERA1 ("Camera"),
		CAMERA2 ("Camera (2)"),
		SYNCHRONIZER ("Synchronizer");

		private String text;
		private SPIMDevice(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}

	private Map<SPIMDevice, Device> deviceMap;
	private CMMCore core;

	public SPIMSetup(CMMCore core) {
		this.core = core;

		deviceMap = new EnumMap<SPIMDevice, Device>(SPIMDevice.class);
	}

	public void debugLog() {
		IJ.log("SPIM Setup " + this.toString() + ":");
		for(Map.Entry<SPIMDevice, Device> entr : deviceMap.entrySet())
			IJ.log(" " + entr.getKey() + " => " + (entr.getValue() != null ? "\"" + entr.getValue().getLabel() + "\" (" + entr.getValue().getDeviceName() + " / " + entr.getValue().toString() + ")" : "None"));
	}

	/*
	 * Some methods which analyze the setup.
	 */
	public boolean isConnected(SPIMDevice type) {
		if (deviceMap.get(type) != null) {
			try {
				return strVecContains(core.getLoadedDevicesOfType(deviceMap.get(type).getMMType()), deviceMap.get(type).getLabel());
			} catch (Throwable t) {
				ReportingUtils.logError(t, "SPIMAcquisition checking connection");
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean hasZStage() {
		return isConnected(SPIMDevice.STAGE_Z);
	}

	public boolean hasXYStage() {
		return isConnected(SPIMDevice.STAGE_X) && isConnected(SPIMDevice.STAGE_Y);
	}

	public boolean has3DStage() {
		return hasZStage() && hasXYStage();
	}

	public boolean hasAngle() {
		return isConnected(SPIMDevice.STAGE_THETA);
	}

	public boolean has4DStage() {
		return has3DStage() && hasAngle();
	}

	public boolean isMinimalMicroscope() {
		return hasZStage() && isConnected(SPIMDevice.CAMERA1);
	}

	public boolean is3DMicroscope() {
		return has3DStage() && isConnected(SPIMDevice.CAMERA1);
	}

	public boolean isMinimalSPIM() {
		return is3DMicroscope() && hasAngle() && isConnected(SPIMDevice.LASER1);
	}

	/*
	 * Some generic getters. The class might not stay backed by an EnumMap, so
	 * these may be more important in the future.
	 */
	public Device getDevice(SPIMDevice device) {
		return deviceMap.get(device);
	}
	
	public void setDevice(SPIMDevice type, String label) {
		try {
			if(label == null || label.length() <= 0)
				deviceMap.put(type, null);
			else
				deviceMap.put(type, Device.createDevice(core, type, label));
		} catch (Exception e) {
			ReportingUtils.logError(e, "Trying to exchange " + (getDevice(type) != null ? getDevice(type).getLabel() : "(null)") + " with " + label);
		}
	}

	public Stage getXStage() {
		return (Stage) deviceMap.get(SPIMDevice.STAGE_X);
	}

	public Stage getYStage() {
		return (Stage) deviceMap.get(SPIMDevice.STAGE_Y);
	}

	public Stage getZStage() {
		return (Stage) deviceMap.get(SPIMDevice.STAGE_Z);
	}

	public Stage getThetaStage() {
		return (Stage) deviceMap.get(SPIMDevice.STAGE_THETA);
	}

	public Laser getLaser() {
		return (Laser) deviceMap.get(SPIMDevice.LASER1);
	}

	public Laser getLaser2() {
		return (Laser) deviceMap.get(SPIMDevice.LASER2);
	}

	public Camera getCamera1() {
		return (Camera) deviceMap.get(SPIMDevice.CAMERA1);
	}

	public Camera getCamera2() {
		return (Camera) deviceMap.get(SPIMDevice.CAMERA2);
	}

	public Device getSynchronizer() {
		return deviceMap.get(SPIMDevice.SYNCHRONIZER);
	}

	public Arduino getArduino1() { return (Arduino) deviceMap.get(SPIMDevice.ARDUINO1); }

	/*
	 * Some convenience methods for positioning the setup's 4D stage.
	 */

	/**
	 * Navigates the stage to the specified quadruplet. Null parameters mean no
	 * change.
	 * 
	 * @param x New position of the X stage
	 * @param y New position of the Y stage
	 * @param z New position of the Z stage
	 * @param t New position of the theta stage
	 */
	public void setPosition(Double x, Double y, Double z, Double t) {
		if (!has3DStage())
			return;

		if (x != null)
			getXStage().setPosition(x);

		if (y != null)
			getYStage().setPosition(y);

		if (z != null)
			getZStage().setPosition(z);

		if (t != null)
			getThetaStage().setPosition(t);
	}

	/**
	 * Reposition the stage to the specified coordinates and angle.
	 * 
	 * @param xyz New translational position of the stage
	 * @param t New position of the theta stage
	 */
	public void setPosition(Vector3D xyz, Double t) {
		setPosition(xyz.getX(), xyz.getY(), xyz.getZ(), t);
	}

	/**
	 * Reposition the stage to the specified coordinates.
	 * 
	 * @param xyz New translational position of the stage
	 */
	public void setPosition(Vector3D xyz) {
		setPosition(xyz, null);
	}

	/**
	 * Gets the position of the stage as a vector.
	 * 
	 * @return Current stage position
	 */
	public Vector3D getPosition() {
		if (!has3DStage())
			return Vector3D.ZERO;

		return new Vector3D(getXStage().getPosition(), getYStage().getPosition(), getZStage().getPosition());
	}

	public double getAngle() {
		return getThetaStage().getPosition();
	}

	public CMMCore getCore() {
		return core;
	}

	public static SPIMSetup createDefaultSetup(CMMCore core) {
		SPIMSetup setup = new SPIMSetup(core);

		try
		{
			collectDeviceLabels(core);
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		try {
			Class.forName( "spim.hardware.PicardStage" );
			Class.forName( "spim.hardware.PicardXYStage" );
			Class.forName( "spim.hardware.PicardTwister" );
			Class.forName( "spim.hardware.AndorsCMOS" );
			Class.forName( "spim.hardware.Cobolt" );
			Class.forName( "spim.hardware.CoherentCube" );
			Class.forName( "spim.hardware.CoherentObis" );
			Class.forName( "spim.hardware.VersaLase" );
			Class.forName( "spim.hardware.Arduino" );

			for (SPIMDevice dev : SPIMDevice.values())
			{
				System.out.println( String.format( "%s: %s", dev.text, setup.getDefaultDeviceLabel( dev ) ) );
				Device device = setup.constructIfValid( dev, setup.getDefaultDeviceLabel( dev ) );
				if(device != null) System.out.println( "Device: " + device.label );
				setup.deviceMap.put( dev, device );
			}
		} catch (Exception e) {
			ReportingUtils.logError(e, "Couldn't build default setup.");
			return null;
		}

		return setup;
	}

//	ShutterDevice --
//
//		VLT_VersaLase
//
//		Arduino-Shutter
//
//	StateDevice --
//
//		Arduino-Switch
//
//	XYStageDevice --
//
//		Picard XY Stage
//
//	StageDevice --
//
//		Picard Twister
//
//		Picard Z Stage
//
//	CameraDevice --
//
//		Andor sCMOS Camera-1
//
//		Andor sCMOS Camera-2
//
//		Multi Camera
//
	public static void collectDeviceLabels(CMMCore core) throws Exception {
		System.out.println("ShutterDevice --");
		for (String s : core.getLoadedDevicesOfType(DeviceType.ShutterDevice))
			System.out.println(s);

		System.out.println("StateDevice --");
		for (String s : core.getLoadedDevicesOfType(DeviceType.StateDevice))
			System.out.println(s);

		System.out.println("XYStageDevice --");
		for (String s : core.getLoadedDevicesOfType(DeviceType.XYStageDevice))
			System.out.println(s);

		System.out.println("StageDevice --");
		for (String s : core.getLoadedDevicesOfType(DeviceType.StageDevice))
			System.out.println(s);

		System.out.println("CameraDevice --");
		for (String s : core.getLoadedDevicesOfType(DeviceType.CameraDevice))
			System.out.println(s);

//		for (String s : core.getDevicePropertyNames("VLT_VersaLase"))
			System.out.println(core.getFocusDevice());
	}

	public String getDefaultDeviceLabel(SPIMDevice dev) throws Exception {
		switch (dev) {
		case STAGE_X:
		case STAGE_Y:
			return core.getXYStageDevice();

		case STAGE_Z:
			return core.getFocusDevice();

		case STAGE_THETA:
			// TODO: In my ideal stage setup (three unique linear stages) this
			// wouldn't work.
			// The X and Y stages would also be StageDevices. I haven't thought
			// of a workaround yet. :(
			return labelOfSecondary(DeviceType.StageDevice, core.getFocusDevice());

		case LASER1:
			return getLaserDevice( 0 );

		case LASER2:
			return getLaserDevice( 1 );

		case CAMERA1:
			return getCameraDevie( 0 );

		case CAMERA2:
			return getCameraDevie( 1 );

		case ARDUINO1:
			return getArduinoDevice( 0 );

		case SYNCHRONIZER:
			// wot
			return null;

		default:
			return null;
		}
	}

	private String getLaserDevice(int i) {
		ArrayList<String> list = new ArrayList<>(  );
		for (String s : core.getLoadedDevicesOfType(DeviceType.ShutterDevice)) {
			if(s.startsWith( "Arduino" )) continue;
			list.add( s );
		}
		if(i > (list.size() - 1)) return null;
		return list.get(i);
	}

	private String getCameraDevie(int i) {
		ArrayList<String> list = new ArrayList<>(  );
		for (String s : core.getLoadedDevicesOfType(DeviceType.CameraDevice)) {
			if(s.startsWith( "Multi Camera" )) continue;
			list.add( s );
		}
		if(i > (list.size() - 1)) return null;
		return list.get(i);
	}

	private String getArduinoDevice(int i) {
		ArrayList<String> list = new ArrayList<>(  );
		for (String s : core.getLoadedDevicesOfType(DeviceType.StateDevice)) {
			list.add( s );
		}
		if(i > (list.size() - 1)) return null;
		return list.get(i);
	}

	private Device constructIfValid(SPIMDevice type, String label) throws Exception {
		if (label != null && label.length() > 0)
			return Device.createDevice(core, type, label);
		else
			return null;
	}

	private String labelOfSecondary(DeviceType mmtype, String except) throws Exception {
		String other = null;

		for (String s : core.getLoadedDevicesOfType(mmtype)) {
			if (!s.equals(except)) {
				if (other == null)
					other = s;
				else
					return null;
			}
		}

		return other;
	}

	private static boolean strVecContains(StrVector vec, String check) {
		for (String str : vec)
			if (str.equals(check))
				return true;

		return false;
	}
}