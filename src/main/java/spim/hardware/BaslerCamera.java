package spim.hardware;

import mmcorej.CMMCore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2023
 */
public class BaslerCamera extends Camera {
	static {
		Device.installFactory(new Device.Factory() {
			@Override
			public Device manufacture(CMMCore core, String label ) {
				return new BaslerCamera(core, label);
			}
		}, "BaslerCamera", SPIMSetup.SPIMDevice.CAMERA1, SPIMSetup.SPIMDevice.CAMERA2);
	}

	public BaslerCamera(CMMCore core, String label) {
		super(core, label);
	}
}
