package spim.hardware;

import mmcorej.CMMCore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: December 2018
 */
public class AndorsCMOS extends Camera {
	static {
		Device.installFactory(new Device.Factory() {
			@Override
			public Device manufacture( CMMCore core, String label ) {
				return new AndorsCMOS(core, label);
			}
		}, "Andor sCMOS Camera", SPIMSetup.SPIMDevice.CAMERA1, SPIMSetup.SPIMDevice.CAMERA2);
	}

	public AndorsCMOS(CMMCore core, String label) {
		super(core, label);
	}
}

