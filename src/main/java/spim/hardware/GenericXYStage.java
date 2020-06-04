package spim.hardware;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.micromanager.internal.utils.ReportingUtils;

import spim.hardware.Device.Factory;

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

		Device.installFactory(factX, "*", SPIMSetup.SPIMDevice.STAGE_X);
		Device.installFactory(factY, "*", SPIMSetup.SPIMDevice.STAGE_Y);
	}

	protected double destX, destY;
	protected SubStage stageX, stageY;

	public GenericXYStage() {
		stageX = stageY = null;
		destX = destY = -1;
	}

	public class SubStage extends Stage {
		protected boolean iAmX;

		public SubStage(CMMCore core, String label, boolean isX) {
			super(core, label);

			iAmX = isX;
		}

		@Override
		public void setPosition(double pos) {
			try {
				if (iAmX) {
					if (destY < 0) destY = core.getYPosition(label);
					core.setXYPosition( label, pos, GenericXYStage.this.destY );
					GenericXYStage.this.destX = pos;
				} else {
					if (destX < 0) destX = core.getXPosition(label);
					core.setXYPosition( label, GenericXYStage.this.destX, pos );
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
		public double getStepSize() {
			if(hasProperty("StepSize")) {
				return getPropertyDouble("StepSize");
			}
			else {
				return 1.0;
			}
		}

		public double getMinPosition() {
			if(hasProperty("Min"))
				return getPropertyDouble("Min")*getStepSize();
			else
				return 0.0;
		}

		/**
		 * Get the maximum possible position in um.
		 * 
		 * @return max position in um
		 */
		public double getMaxPosition()
		{
			if(hasProperty("Max")) {
				return getPropertyDouble("Max")*getStepSize();
			}	
			else
				return 9000.0; // *** this is why you should implement your own stages.
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
}
