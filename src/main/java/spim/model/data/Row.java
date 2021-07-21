package spim.model.data;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.EnumMap;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import spim.hardware.SPIMSetup.SPIMDevice;

/**
 * Description: Row data structure in Position table
 *
 * Author: Johannes Schindelin
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class Row
{
	public class DeviceValueSet {
		private boolean continuous;
		private double start, end, stepOrSpeed;

		public DeviceValueSet(double single) {
			start = end = single;
			stepOrSpeed = 0;
			continuous = false;
		}

		public DeviceValueSet(String range) {
			parse(range);
		}

		public DeviceValueSet(Object obj) {
			if(obj instanceof Double) {
				start = end = (Double)obj;
				stepOrSpeed = 0;
				continuous = false;
			} else if(obj instanceof String) {
				parse((String)obj);
			} else {
				throw new IllegalArgumentException("unknown device value set specifier type: " + obj.toString());
			}
		}

		public DeviceValueSet(double start, double stepspeed, double end, boolean continuous) {
			this.start = start;
			this.stepOrSpeed = stepspeed;
			this.end = end;
			this.continuous = continuous;
		}

		private void parse(String range) {
			NumberFormat parser = NumberFormat.getInstance();

			try {
				if(range.indexOf('@') != -1) {
					start = parser.parse(range.substring(0,range.indexOf('-'))).doubleValue();
					end = parser.parse(range.substring(range.indexOf('-')+1, range.indexOf('@'))).doubleValue();
					stepOrSpeed = parser.parse(range.substring(range.indexOf('@')+1)).doubleValue();
					continuous = true;
				} else if(range.indexOf(':') != -1) {
					start = parser.parse(range.substring(0,range.indexOf(':'))).doubleValue();
					stepOrSpeed = parser.parse(range.substring(range.indexOf(':')+1,range.lastIndexOf(':'))).doubleValue();
					end = parser.parse(range.substring(range.lastIndexOf(':')+1)).doubleValue();
					continuous = false;
				} else {
					start = end = parser.parse(range).doubleValue();
					stepOrSpeed = 0;
					continuous = false;
				}
			} catch(ParseException nfe) {
				throw new NumberFormatException("Unknown device value description \"" + range + "\"");
			}
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
				return (int)((start < end ? end - start : start - end) / stepOrSpeed) + 1;
		}

		@Override
		public String toString() {
			if(stepOrSpeed == 0)
				return String.format("%.3f", start);

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

	public Row( SPIMDevice[] devs, String[] infos ) {
		posMap = new EnumMap<SPIMDevice, DeviceValueSet>(SPIMDevice.class);

		for(int i = 0; i < devs.length; ++i)
			setValueSet(devs[i], infos[i]);
	}

	public Row( SPIMDevice[] devs, Object... infos ) {
		posMap = new EnumMap<SPIMDevice, DeviceValueSet>(SPIMDevice.class);

		for(int i = 0; i < devs.length; ++i)
			setValueSet(devs[i], infos[i]);
	}

	public void setValueSet(SPIMDevice dev, String totalInfo) {
		setValueSet(dev, (Object) totalInfo.trim());
	}

	public void setValueSet(SPIMDevice dev, Object totalInfo) {
		if(totalInfo == null) {
			posMap.remove(dev);
			return;
		}

		posMap.put(dev, new DeviceValueSet(totalInfo));
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
		return posMap.get(SPIMDevice.STAGE_Y).getStartPosition();
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
