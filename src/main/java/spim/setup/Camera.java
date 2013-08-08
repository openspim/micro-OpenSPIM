package spim.setup;

import ij.process.ImageProcessor;

import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public class Camera extends Device {

	public Camera(CMMCore core, String label) {
		super(core, label);
	}

	public ImageProcessor snapImage() {
		try {
			core.snapImage();
			return ImageUtils.makeProcessor(core.getLastTaggedImage());
		} catch(Exception e) {
			ReportingUtils.logError(e);
			return null;
		}
	}

	@Override
	public DeviceType getMMType() {
		return DeviceType.CameraDevice;
	}

}
