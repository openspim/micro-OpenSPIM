package spim.hardware;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class Arduino extends Device {
	static {
		Device.installFactory(new Device.Factory() {
			@Override
			public Device manufacture( CMMCore core, String label) {
				return new Arduino(core, label);
			}
		}, "Arduino-Switch", SPIMSetup.SPIMDevice.ARDUINO1, SPIMSetup.SPIMDevice.ARDUINO2);
	}

	public Arduino(CMMCore core, String label) {
		super(core, label);
	}

	public String getBlankOn() {
		if(hasProperty("Blank On"))
			return getProperty("Blank On");
		else
			return "Low";
	}

	public void setBlankOn(String onOff) {
		setProperty( "Blank On", onOff );
	}

	public String getBlankingMode() {
		if(hasProperty("Blanking Mode"))
			return getProperty("Blanking Mode");
		else
			return "Off";
	}

	public void setBlankingMode(String onOff) {
		setProperty( "Blanking Mode", onOff );
	}

	public String getSequence() {
		if(hasProperty( "Sequence" ))
			return getProperty( "Sequence" );
		else
			return "Off";
	}

	public void setSequence(String onOff) {
		setProperty( "Sequence", onOff );
	}

	public Double getState() {
		if(hasProperty( "State" ))
			return getPropertyDouble( "State" );
		else
			return 0.0;
	}

	public String getSwitchLabel() {
		if(hasProperty( "Label" ))
			return getProperty( "Label" );
		else
			return "0";
	}

	public void setSwitchLabel(String label) {
		setProperty( "Label", label );
	}

	public String getSwitchState() {
		if(hasProperty( "State" ))
			return getProperty( "State" );
		else
			return getProperty( "0" );
	}

	public void setSwitchState(String state) {
		setProperty( "State", state );
	}

	public double getSwitchStateLowerLimit() {
		return getLowerLimit( "State" );
	}

	public double getSwitchStateUpperLimit() {
		return getUpperLimit( "State" );
	}


	public DeviceType getMMType() {
		return DeviceType.StateDevice;
	}
}