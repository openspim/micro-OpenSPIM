package spim.hardware;

import mmcorej.CMMCore;

/**
 * Description: PicardTwister device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class PicardTwister extends GenericRotator {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new PicardTwister(core, label);
			}
		}, "Picard Twister", SPIMSetup.SPIMDevice.STAGE_THETA);
	}

	public PicardTwister(CMMCore core, String label) {
		super(core, label);
	}
}
