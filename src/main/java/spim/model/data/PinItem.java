package spim.model.data;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2019
 */
public class PinItem
{
	private final String pin;
	private final IntegerProperty state = new SimpleIntegerProperty();
	private final StringProperty pinName = new SimpleStringProperty();

	public PinItem( String pin, int state )
	{
		this(pin, state, "Pin-" + pin);
	}

	public PinItem( String pin, int state, String pinName )
	{
		this.pin = pin;
		this.state.set(state);
		this.pinName.set( pinName );
	}

	public String getPin()
	{
		return pin;
	}

	public int getState()
	{
		return state.get();
	}

	public IntegerProperty stateProperty()
	{
		return state;
	}

	public void setState( int state )
	{
		this.state.set( state );
	}

	public String getPinName()
	{
		return pinName.get();
	}

	public StringProperty pinNameProperty()
	{
		return pinName;
	}

	public void setPinName( String pinName )
	{
		this.pinName.set( pinName );
	}
}
