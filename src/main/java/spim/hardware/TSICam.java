package spim.hardware;

import mmcorej.CMMCore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2023
 */
public class TSICam extends Camera {
	static {
		Device.installFactory(new Device.Factory() {
			@Override
			public Device manufacture(CMMCore core, String label ) {
				return new TSICam(core, label);
			}
		}, "TSICam", SPIMSetup.SPIMDevice.CAMERA1, SPIMSetup.SPIMDevice.CAMERA2);
	}

	public TSICam(CMMCore core, String label) {
		super(core, label);
	}
}
