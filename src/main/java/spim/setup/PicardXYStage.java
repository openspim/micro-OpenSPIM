package spim.setup;

import java.util.HashMap;
import java.util.Map;

import org.micromanager.utils.ReportingUtils;

import spim.setup.Device.Factory;
import spim.setup.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public class PicardXYStage {
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

	private double destX, destY;
	private SubStage stageX, stageY;

	public PicardXYStage() {
		stageX = stageY = null;
		destX = destY = 0;
	}

	public class SubStage extends PicardStage {
		private boolean iAmX;

		public SubStage(CMMCore core, String label, boolean isX) {
			super(core, label);

			iAmX = isX;

			if (isX)
				PicardXYStage.this.destX = getPosition();
			else
				PicardXYStage.this.destY = getPosition();
		}

		@Override
		public void setPosition(double pos) {
			try {
				if (iAmX) {
					core.setXYPosition(label, pos, PicardXYStage.this.destY);
					PicardXYStage.this.destX = pos;
				} else {
					core.setXYPosition(label, PicardXYStage.this.destX, pos);
					PicardXYStage.this.destY = pos;
				}
			} catch (Exception e) {
				ReportingUtils.logException("Couldn't set " + (iAmX ? "X" : "Y") + " position on " + label, e);
			}
		}

		@Override
		public double getPosition() {
			try {
				if (iAmX)
					return core.getXPosition(label);
				else
					return core.getYPosition(label);
			} catch (Exception e) {
				ReportingUtils.logException("Couldn't get " + (iAmX ? "X" : "Y") + " position on " + label, e);
				return 0;
			}
		}

		@Override
		public DeviceType getMMType() {
			return DeviceType.XYStageDevice;
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
			instance.stageX = instance.new SubStage(core, label, true);
		else if (!X && instance.stageY == null)
			instance.stageY = instance.new SubStage(core, label, false);

		return X ? instance.stageX : instance.stageY;
	}
}
