package spim.hardware;

import mmcorej.CMMCore;

/**
 * Description: CoherentObis device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class CoherentObis extends Laser {
    static {
        Device.installFactory(new Factory() {
            @Override
            public Device manufacture(CMMCore core, String label) {
                return new CoherentObis(core, label);
            }
        }, "CoherentObis", SPIMSetup.SPIMDevice.LASER1, SPIMSetup.SPIMDevice.LASER2);
    }

    public CoherentObis(CMMCore core, String label) {
        super(core, label);
    }

	/**
	 * Power the laser off or on.
	 *
	 * @param open True to turn on, false to turn off.
	 */
	@Override
	public void setPoweredOn(boolean open) {
		if(open) {
			setProperty("State", 1);
		} else {
			setProperty("State", 0);
		}
	}

	/**
	 * Get the laser's power status.
	 *
	 * @return True of the laser is on, false if off.
	 */
	@Override
	public boolean getPoweredOn() {
		return getProperty( "State" ).equals( "1" );
	}

    @Override
    public void setPower(double power) { setProperty("PowerSetpoint", power); }

    @Override
    public double getPower() { return getPropertyDouble("PowerReadback"); }

    @Override
    public double getMinPower() {
        return getPropertyDouble("Minimum Laser Power") * 1000;
    }

    @Override
    public double getMaxPower() {
        return getPropertyDouble("Maximum Laser Power") * 1000;
    }

}
