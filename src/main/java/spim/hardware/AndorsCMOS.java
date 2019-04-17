package spim.hardware;

import mmcorej.CMMCore;
import org.micromanager.internal.utils.ReportingUtils;

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

	@Override
	public int getBinning() {
		try {
			switch ( getProperty( "Binning" ) ) {
				case "1x1": return 1;
				case "2x2": return 2;
				case "3x3": return 3;
				case "4x4": return 4;
				case "8x8": return 8;
			}
		} catch (Exception e) {
			ReportingUtils.logError(e);
			return 1;
		}
		return Integer.parseInt( getProperty( "Binning" ) );
	}

	public AndorsCMOS(CMMCore core, String label) {
		super(core, label);
	}
}

