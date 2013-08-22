package spim.progacq;

import java.util.EnumMap;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

import spim.setup.SPIMSetup.SPIMDevice;

public class AcqRow {
	public class DeviceValueSet {
		private boolean continuous;
		private double start, end, stepOrSpeed;

		public DeviceValueSet(double single) {
			start = end = single;
			stepOrSpeed = 0;
			continuous = false;
		}

		public DeviceValueSet(String range) {
			if(range.indexOf('@') != -1) {
				start = Double.parseDouble(range.substring(0,range.indexOf('-')));
				end = Double.parseDouble(range.substring(range.indexOf('-')+1, range.indexOf('@')));
				stepOrSpeed = Double.parseDouble(range.substring(range.indexOf('@')+1));
				continuous = true;
			} else if(range.indexOf(':') != -1) {
				start = Double.parseDouble(range.substring(0,range.indexOf(':')));
				stepOrSpeed = Double.parseDouble(range.substring(range.indexOf(':')+1,range.lastIndexOf(':')));
				end = Double.parseDouble(range.substring(range.lastIndexOf(':')+1));
				continuous = false;
			} else {
				try {
					start = end = Double.parseDouble(range);
					stepOrSpeed = 0;
					continuous = false;
				} catch(NumberFormatException nfe) {
					throw new Error("Unknown device value description \"" + range + "\"");
				}
			}
		}

		public DeviceValueSet(double start, double stepspeed, double end, boolean continuous) {
			this.start = start;
			this.stepOrSpeed = stepspeed;
			this.end = end;
			this.continuous = continuous;
		}

		public double getStartPosition() {
			return start;
		}

		public double getEndPosition() {
			return end;
		}

		public double getStepSize() {
			return (continuous ? -1 : stepOrSpeed);
		}

		public double getSpeed() {
			return (continuous ? stepOrSpeed : 0);
		}

		public int getSteps() {
			if(continuous)
				return -1;

			if(stepOrSpeed == 0)
				return 1;
			else
				return (int)((end - start) / stepOrSpeed) + 1;
		}

		@Override
		public String toString() {
			if(stepOrSpeed == 0)
				return Double.toString(start);

			if(continuous)
				return String.format("%.3f-%.3f@%d", start, end, (int) stepOrSpeed);
			else
				return String.format("%.3f:%.3f:%.3f", start, stepOrSpeed, end);
		}

		protected void translate(double by) {
			start += by;
			end += by;
		}
	}

	private EnumMap<SPIMDevice, DeviceValueSet> posMap;

	public AcqRow(SPIMDevice[] devs, String[] infos) {
		posMap = new EnumMap<SPIMDevice, DeviceValueSet>(SPIMDevice.class);

		for(int i = 0; i < devs.length; ++i)
			setValueSet(devs[i], infos[i]);
	}

	public void setValueSet(SPIMDevice dev, String totalInfo) {
		if(totalInfo == null) {
			posMap.remove(dev);
			return;
		}

		posMap.put(dev, new DeviceValueSet(totalInfo.trim()));
	}
	
	public DeviceValueSet getValueSet(SPIMDevice dev) {
		return posMap.get(dev);
	}
	
	public SPIMDevice[] getDevices() {
		return posMap.keySet().toArray(new SPIMDevice[posMap.size()]);
	}

	public String describeValueSet(SPIMDevice dev) {
		return "" + posMap.get(dev);
	}

	public double getZStartPosition() {
		return posMap.get(SPIMDevice.STAGE_Z).getStartPosition();
	}

	public double getZEndPosition() {
		return posMap.get(SPIMDevice.STAGE_Z).getEndPosition();
	}

	public double getZVelocity() {
		return posMap.get(SPIMDevice.STAGE_Z).getSpeed();
	}

	public double getZStepSize() {
		return posMap.get(SPIMDevice.STAGE_Z).getStepSize();
	}

	public boolean getZContinuous() {
		return posMap.get(SPIMDevice.STAGE_Z).getSpeed() != 0;
	}

	public int getDepth() {
		int out = 1;

		for(DeviceValueSet set : posMap.values())
			out *= set.getSteps();

		return out;
	}

	public double getX() {
		return posMap.get(SPIMDevice.STAGE_X).getStartPosition();
	}

	public double getY() {
		return posMap.get(SPIMDevice.STAGE_X).getStartPosition();
	}
	
	public double getTheta() {
		return posMap.get(SPIMDevice.STAGE_THETA).getStartPosition();
	}

	public void translate(Vector3D v) {
		posMap.get(SPIMDevice.STAGE_X).translate(v.getX());
		posMap.get(SPIMDevice.STAGE_Y).translate(v.getY());
		posMap.get(SPIMDevice.STAGE_Z).translate(v.getZ());
	}
}
