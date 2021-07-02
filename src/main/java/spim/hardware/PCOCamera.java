package spim.hardware;

import mmcorej.CMMCore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: July 2021
 */
public class PCOCamera extends Camera {
	static {
		Device.installFactory(new Device.Factory() {
			@Override
			public Device manufacture(CMMCore core, String label ) {
				return new PCOCamera(core, label);
			}
		}, "PCO Camera", SPIMSetup.SPIMDevice.CAMERA1, SPIMSetup.SPIMDevice.CAMERA2);
	}

	@Override
	public void setBinning(String val) {
		if (val.contains("x")) {
			super.setBinning(val);
		} else {
			String str = "1";
			switch (val) {
				case "1" : str = "1x1"; break;
				case "2" : str = "2x2"; break;
				case "3" : str = "3x3"; break;
				case "4" : str = "4x4"; break;
				case "8" : str = "8x8"; break;
			}

			super.setBinning(str);
		}
	}

	public PCOCamera(CMMCore core, String label) {
		super(core, label);
	}
}
