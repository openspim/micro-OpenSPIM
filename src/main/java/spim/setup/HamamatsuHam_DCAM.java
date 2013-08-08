package spim.setup;

import spim.setup.SPIMSetup.SPIMDevice;
import mmcorej.CMMCore;

public class HamamatsuHam_DCAM extends Camera {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new HamamatsuHam_DCAM(core, label);
			}
		}, "HamamatsuHam_DCAM", SPIMDevice.CAMERA1, SPIMDevice.CAMERA2);
	}

	public HamamatsuHam_DCAM(CMMCore core, String label) {
		super(core, label);
	}

}
