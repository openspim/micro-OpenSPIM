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

	public PCOCamera(CMMCore core, String label) {
		super(core, label);
	}
}
