package spim.setup;

import ij.IJ;

import java.util.HashMap;
import java.util.Map;

import org.micromanager.utils.ReportingUtils;

import mmcorej.CMMCore;
import spim.setup.Device.Factory;
import spim.setup.SPIMSetup.SPIMDevice;

public class PicardXYStage extends GenericXYStage {
	static {
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

		Device.installFactory(factX, "Picard XY Stage", SPIMDevice.STAGE_X);
		Device.installFactory(factY, "Picard XY Stage", SPIMDevice.STAGE_Y);
	}

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
			} catch (Exception e) {
				ReportingUtils.logError(e, "Could not home X/Y stage.");
			}
		}

	}

	private static Map<String, PicardXYStage> labelToInstanceMap = new HashMap<String, PicardXYStage>();

	public static Device getStage(CMMCore core, String label, boolean X) {
		PicardXYStage instance = labelToInstanceMap.get(label);

		if (instance == null) {
			instance = new PicardXYStage();
			labelToInstanceMap.put(label, instance);
		}

		if (X && instance.stageX == null)
			instance.stageX = instance.new PicardSubStage(core, label, true);
		else if (!X && instance.stageY == null)
			instance.stageY = instance.new PicardSubStage(core, label, false);

		return X ? instance.stageX : instance.stageY;
	}
}
