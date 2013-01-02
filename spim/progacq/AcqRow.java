package spim.progacq;

import java.util.EnumMap;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

import spim.DeviceManager.SPIMDevice;

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

	private EnumMap<SPIMDevice, DeviceValueSet[]> posMap;

	public AcqRow(SPIMDevice[] devs, String[] infos) {
		posMap = new EnumMap<SPIMDevice, DeviceValueSet[]>(SPIMDevice.class);

		for(int i = 0; i < devs.length; ++i)
			setValueSet(devs[i], infos[i]);
	}

	public void setValueSet(SPIMDevice dev, String totalInfo) {
		if(totalInfo == null) {
			posMap.remove(dev);
			return;
		}

		String[] subdevs = totalInfo.split(",");

		DeviceValueSet[] points = new DeviceValueSet[subdevs.length];

		for(int i = 0; i < subdevs.length; ++i)
			points[i] = new DeviceValueSet(subdevs[i].trim());

		posMap.put(dev, points);
	}
	
	public DeviceValueSet[] getValueSets(SPIMDevice dev) {
		return posMap.get(dev);
	}
	
	public SPIMDevice[] getDevices() {
		return posMap.keySet().toArray(new SPIMDevice[posMap.size()]);
	}

	public String describeValueSet(SPIMDevice dev) {
		DeviceValueSet[] sets = posMap.get(dev);

		if(sets == null)
			return "(null)";

		String ret = "";

		for(DeviceValueSet set : sets)
			ret += set.toString() + (set == sets[sets.length - 1] ? "" : ", ");

		return ret;
	}

	public double getZStartPosition() {
		return posMap.get(SPIMDevice.STAGE_Z)[0].getStartPosition();
	}

	public double getZEndPosition() {
		return posMap.get(SPIMDevice.STAGE_Z)[0].getEndPosition();
	}

	public double getZVelocity() {
		return posMap.get(SPIMDevice.STAGE_Z)[0].getSpeed();
	}

	public double getZStepSize() {
		return posMap.get(SPIMDevice.STAGE_Z)[0].getStepSize();
	}

	public boolean getZContinuous() {
		return posMap.get(SPIMDevice.STAGE_Z)[0].getSpeed() != 0;
	}

	public int getDepth() {
		int out = 1;

		for(DeviceValueSet[] sets : posMap.values())
			for(DeviceValueSet set : sets)
				out *= set.getSteps();

		return out;
	}

	public double getX() {
		return posMap.get(SPIMDevice.STAGE_XY)[0].getStartPosition();
	}

	public double getY() {
		return posMap.get(SPIMDevice.STAGE_XY)[1].getStartPosition();
	}
	
	public double getTheta() {
		return posMap.get(SPIMDevice.STAGE_THETA)[0].getStartPosition();
	}

	public void translate(Vector3D v) {
		if(posMap.get(SPIMDevice.STAGE_X) != null)
			posMap.get(SPIMDevice.STAGE_X)[0].translate(v.getX());

		if(posMap.get(SPIMDevice.STAGE_Y) != null)
			posMap.get(SPIMDevice.STAGE_Y)[0].translate(v.getY());

		if(posMap.get(SPIMDevice.STAGE_XY) != null) {
			posMap.get(SPIMDevice.STAGE_XY)[0].translate(v.getX());
			posMap.get(SPIMDevice.STAGE_XY)[1].translate(v.getY());
		}

		if(posMap.get(SPIMDevice.STAGE_Z) != null)
			posMap.get(SPIMDevice.STAGE_Z)[0].translate(v.getZ());
	}
}
