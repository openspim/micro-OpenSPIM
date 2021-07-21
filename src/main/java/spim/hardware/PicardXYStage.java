package spim.hardware;

import org.micromanager.internal.utils.ReportingUtils;

import mmcorej.CMMCore;
import spim.hardware.Device.Factory;

/**
 * Description: PicardXYStage device for µOpenSPIM
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class PicardXYStage extends GenericXYStage {
	static {
		instance = new PicardXYStage();

		Factory factX = new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return PicardXYStage.getStage(core, label, true);
			}
		};

		Factory factY = new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return PicardXYStage.getStage(core, label, false);
			}
		};

		Device.installFactory(factX, "Picard XY Stage", SPIMSetup.SPIMDevice.STAGE_X);
		Device.installFactory(factY, "Picard XY Stage", SPIMSetup.SPIMDevice.STAGE_Y);
	}

	// Singleton pattern
	static PicardXYStage instance;

	public PicardXYStage() {
		super();
	}

	public class PicardSubStage extends GenericXYStage.SubStage {
		public PicardSubStage(CMMCore core, String label, boolean isX) {
			super(core, label, isX);
		}

		@Override
		public void setVelocity(double velocity) throws IllegalArgumentException {
			if(velocity < 1 || velocity > 10 || Math.round(velocity) != velocity)
				throw new IllegalArgumentException("Velocity is not in 1..10 or is not an integer.");

			super.setVelocity(velocity);
		}

		@Override
		public void home() {
			try {
				core.home(label);
				if (iAmX)
					destX = -1;
				else
					destY = -1;
			} catch (Exception e) {
				ReportingUtils.logError(e, "Could not home X/Y stage.");
			}
		}
	}

	public static Device getStage(CMMCore core, String label, boolean X) {
		if (X && instance.stageX == null)
			instance.stageX = instance.new PicardSubStage(core, label, true);
		else if (!X && instance.stageY == null)
			instance.stageY = instance.new PicardSubStage(core, label, false);

		return X ? instance.stageX : instance.stageY;
	}
}
