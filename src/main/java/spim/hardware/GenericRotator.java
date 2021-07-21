package spim.hardware;

import java.util.Arrays;
import java.util.Collection;

import mmcorej.CMMCore;

/**
 * Description: Generic Rotator device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class GenericRotator extends Stage {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new GenericRotator(core, label);
			}
		}, "*", SPIMSetup.SPIMDevice.STAGE_THETA);
	}

	public GenericRotator(CMMCore core, String label) {
		super(core, label);
	}

	@Override
	public double getVelocity() {
		return 1.0D;
	}

	@Override
	public void setVelocity(double velocity) throws IllegalArgumentException {
		if (velocity != 1.0D)
			throw new IllegalArgumentException("Invalid rotator velocity " + velocity);

		// nop
	}

	@Override
	public Collection<Double> getAllowedVelocities() {
		return Arrays.asList(1.0D);
	}

	@Override
	public double getMinPosition() {
		return 0.0D;
	}

	@Override
	public double getMaxPosition() {
		return 360.0D;
	}
}
