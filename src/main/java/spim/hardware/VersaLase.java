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
							map.put( laser, new VersaLaseLaser( laser ) );
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

	public VersaLaseLaser getCameraA() {
		return map.getOrDefault( "A", null );
	}

	public VersaLaseLaser getCameraB() {
		return map.getOrDefault( "B", null );
	}

	public VersaLaseLaser getCameraC() {
		return map.getOrDefault( "C", null );
	}

	public VersaLaseLaser getCameraD() {
		return map.getOrDefault( "D", null );
	}

	public class VersaLaseLaser {
		private final String laserLabel;

		VersaLaseLaser (String label) {
			// "-LASER_A_"
			laserLabel = String.format( "-LASER_%s_", label );
		}

		public void setPoweredOn(boolean open) {
			if(open) {
				setProperty(laserLabel + "LaserEmission", "ON");
			} else {
				setProperty(laserLabel + "LaserEmission", "OFF");
			}
		}

		public String getLaserLabel() {
			return getProperty( laserLabel + "LaserID" );
		}
	}
}