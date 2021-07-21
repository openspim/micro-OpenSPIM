package spim.hardware;

import mmcorej.CMMCore;

/**
 * Description: CoherentCube device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class CoherentCube extends Laser {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new CoherentCube(core, label);
			}
		}, "CoherentCube", SPIMSetup.SPIMDevice.LASER1, SPIMSetup.SPIMDevice.LASER2);
	}

	public CoherentCube(CMMCore core, String label) {
		super(core, label);
	}

	@Override
	public void setPower(double power) {
		setProperty("PowerSetpoint", power * 1000);
	}

	@Override
	public double getPower() {
		return getPropertyDouble("PowerSetpoint") / 1000;
	}

	@Override
	public double getMinPower() {
		return getPropertyDouble("Minimum Laser Power") / 1000.0;
	}

	@Override
	public double getMaxPower() {
		return getPropertyDouble("Maximum Laser Power") / 1000.0;
	}

}
