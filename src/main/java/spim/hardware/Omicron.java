package spim.hardware;

import mmcorej.CMMCore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2022
 */
public class Omicron extends Laser {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new Omicron(core, label);
			}
		}, "Omicron", SPIMSetup.SPIMDevice.LASER1, SPIMSetup.SPIMDevice.LASER2);
	}

	public Omicron(CMMCore core, String label) {
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
			setProperty("Laser Operation Select", "On");
		} else {
			setProperty("Laser Operation Select", "Off");
		}
	}

	@Override
	public void setPower(double power) {
		setProperty("Laser Power Set-point Select [mW]", power);
	}

	@Override
	public double getPower() {
		return Double.parseDouble( getProperty("Laser Power Status [mW]").replace("mW", "").trim() );
	}

	@Override
	public double getMinPower() {
		return 0.0;
	}

	@Override
	public double getMaxPower() {
		return Double.parseDouble( getProperty("Specified Power [mW]").replace("mW", "").trim() );
	}

	/**
	 * Return the laser wavelength.
	 *
	 * @return Wavelength.
	 */
	@Override
	public String getWavelength() {
		if(hasProperty("Wavelength [nm]"))
			return getProperty("Wavelength [nm]").replace("nm", "").trim();
		else
			return "488";
	}
}
