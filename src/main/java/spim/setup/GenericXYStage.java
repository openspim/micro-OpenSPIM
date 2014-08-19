package spim.setup;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.micromanager.utils.ReportingUtils;

import spim.setup.Device.Factory;
import spim.setup.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

public class GenericXYStage {
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

		Device.installFactory(factX, "*", SPIMDevice.STAGE_X);
		Device.installFactory(factY, "*", SPIMDevice.STAGE_Y);
	}

	protected double destX, destY;
	protected SubStage stageX, stageY;

	public GenericXYStage() {
		stageX = stageY = null;
		destX = destY = 0;
	}

	public class SubStage extends Stage {
		protected boolean iAmX;

		public SubStage(CMMCore core, String label, boolean isX) {
			super(core, label);

			iAmX = isX;

			if (isX)
				GenericXYStage.this.destX = getPosition();
			else
				GenericXYStage.this.destY = getPosition();
		}

		@Override
		public void setPosition(double pos) {
			try {
				if (iAmX) {
					core.setXYPosition(label, pos, GenericXYStage.this.destY);
					GenericXYStage.this.destX = pos;
				} else {
					core.setXYPosition(label, GenericXYStage.this.destX, pos);
					GenericXYStage.this.destY = pos;
				}
			} catch (Exception e) {
				ReportingUtils.logError(e, "Couldn't set " + (iAmX ? "X" : "Y") + " position on " + label);
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
				ReportingUtils.logError(e, "Couldn't get " + (iAmX ? "X" : "Y") + " position on " + label);
				return 0;
			}
		}

		@Override
		public boolean hasProperty(String name) {
			return super.hasProperty((iAmX ? "X-" : "Y-") + name);
		}

		@Override
		public String getProperty(String name) {
			return super.getProperty((iAmX ? "X-" : "Y-") + name);
		}

		@Override
		public void setProperty(String name, String value) {
			super.setProperty((iAmX ? "X-" : "Y-") + name, value);
		}

		@Override
		public Collection<String> getPropertyAllowedValues(String name) {
			return super.getPropertyAllowedValues((iAmX ? "X-" : "Y-") + name);
		}

		@Override
		public DeviceType getMMType() {
			return DeviceType.XYStageDevice;
		}
	}

	private static Map<String, GenericXYStage> labelToInstanceMap = new HashMap<String, GenericXYStage>();

	public static Device getStage(CMMCore core, String label, boolean X) {
		GenericXYStage instance = labelToInstanceMap.get(label);

		if (instance == null) {
			instance = new GenericXYStage();
			labelToInstanceMap.put(label, instance);
		}

		if (X && instance.stageX == null)
			instance.stageX = instance.new SubStage(core, label, true);
		else if (!X && instance.stageY == null)
			instance.stageY = instance.new SubStage(core, label, false);

		return X ? instance.stageX : instance.stageY;
	}
}
