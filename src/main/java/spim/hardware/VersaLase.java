package spim.hardware;

import mmcorej.CMMCore;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class VersaLase extends Laser {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture( CMMCore core, String label) {
				return new VersaLase(core, label);
			}
		}, "VLT_VersaLase", SPIMSetup.SPIMDevice.LASER1, SPIMSetup.SPIMDevice.LASER2);
	}

	final HashMap<String, VersaLaseLaser> map = new HashMap<>(  );

	public VersaLase(CMMCore core, String label)
	{
		super(core, label);

		// "VLT_VersaLase"
		// LASER_B_AnalogModulation
		//
		// LASER_B_Current
		//
		// LASER_B_DigitalModulation
		//
		// LASER_B_DigitalPeakPowerSetting
		//
		// LASER_B_FaultCode
		//
		// LASER_B_Hours
		//
		// LASER_B_LaserEmission
		//
		// LASER_B_LaserEmissionDelay
		//
		// LASER_B_LaserID
		//
		// LASER_B_OperatingCondition
		//
		// LASER_B_Power
		//
		// LASER_B_PowerMaximum
		//
		// LASER_B_PowerSetting
		//
		// LASER_B_Shutter
		Pattern laserPattern= Pattern.compile("LASER_(.)_.+");

		try
		{
			String lastLaser = " ";
			for (String s : core.getDevicePropertyNames("VLT_VersaLase")) {
				if( s.startsWith( "LASER_" ) && !s.startsWith( "LASER_" + lastLaser )) {
					Matcher m = laserPattern.matcher( s );
					if(m.find()) {
						String laser = m.group( 1 );
						if( !map.containsKey( laser ) ) {
							map.put( laser, new VersaLaseLaser(core, label, laser) );
							lastLaser = laser;
						}
					}
				}
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	public VersaLaseLaser getLaser(String laser) {
		return map.getOrDefault( laser, null );
	}

	public VersaLaseLaser getLaserA() {
		return map.getOrDefault( "A", null );
	}

	public VersaLaseLaser getLaserB() {
		return map.getOrDefault( "B", null );
	}

	public VersaLaseLaser getLaserC() {
		return map.getOrDefault( "C", null );
	}

	public VersaLaseLaser getLaserD() {
		return map.getOrDefault( "D", null );
	}

	public class VersaLaseLaser extends Laser{
		private final String laserLabel;
		private final double maxPower;
		private final String waveLength;
		private final String id;

		VersaLaseLaser (CMMCore core, String parentLabel, String label) {
			super(core, parentLabel);
			this.id = label;

			// "-LASER_A_"
			laserLabel = String.format( "LASER_%s_", label );

			switch ( label ) {
				case "A": maxPower = 50.0;
					waveLength = "637";
					break;
				case "B": maxPower = 50.0;
					waveLength = "561";
					break;
				case "C": maxPower = 50.0;
					waveLength = "488";
					break;
				case "D": maxPower = 50.0;
					waveLength = "395";
					break;
				default: maxPower = 50.0;
					waveLength = "488";
			}
		}

		@Override
		public void setPoweredOn(boolean open) {
			if(open) {
				setProperty(laserLabel + "LaserEmission", "ON");
			} else {
				setProperty(laserLabel + "LaserEmission", "OFF");
			}
		}

		public String getLaserEmissionProperty() {
			return laserLabel + "LaserEmission";
		}

		public String getShutter() {
			return laserLabel + "Shutter";
		}

		public String getDigitalModulationProperty() {
			return laserLabel + "DigitalModulation";
		}

		@Override
		public boolean getPoweredOn() {
			return getProperty( laserLabel + "LaserEmission" ).equals( "ON" );
		}

		@Override
		public void setPower(double power) throws UnsupportedOperationException, IllegalArgumentException {
			if(isDigitalModulationOn()) {
				if(hasProperty(laserLabel + "DigitalPeakPowerSetting"))
					setProperty(laserLabel + "DigitalPeakPowerSetting", power);
				else
					throw new UnsupportedOperationException();
			} else {
				if(hasProperty(laserLabel + "PowerSetting"))
					setProperty(laserLabel + "PowerSetting", power);
				else
					throw new UnsupportedOperationException();
			}
		}

		@Override
		public double getPower() {
			if(isDigitalModulationOn()) {
				if(hasProperty(laserLabel + "DigitalPeakPowerSetting"))
					return getPropertyDouble(laserLabel + "DigitalPeakPowerSetting");
				else
					return 0.0;
			} else {
				if(hasProperty(laserLabel + "PowerSetting"))
					return getPropertyDouble(laserLabel + "PowerSetting");
				else
					return 0.0;
			}
		}

		boolean isDigitalModulationOn() throws UnsupportedOperationException {
			String val = getDigitalModulation();
			return val.equals("ON");
		}

		@Override
		public double getMaxPower() {
			return maxPower;
		}

		@Override
		public String getWavelength() {
			return waveLength;
		}

		@Override
		public String getDeviceName() {
			return id;
		}

		public String getLaserLabel() {
			return getProperty( laserLabel + "LaserID" );
		}

		public String getLaserEmission() {
			if(hasProperty(laserLabel + "LaserEmission"))
				return getProperty(laserLabel + "LaserEmission");
			else
				throw new UnsupportedOperationException();
		}

		public String getDigitalModulation() {
			if(hasProperty(laserLabel + "DigitalModulation"))
				return getProperty(laserLabel + "DigitalModulation");
			else
				throw new UnsupportedOperationException();
		}

		public String getAnalogModulation() {
			if(hasProperty(laserLabel + "AnalogModulation"))
				return getProperty(laserLabel + "AnalogModulation");
			else
				throw new UnsupportedOperationException();
		}
	}
}