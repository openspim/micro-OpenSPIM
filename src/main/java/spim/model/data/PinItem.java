package spim.model.data;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import spim.ui.view.component.ArduinoPanel;

import java.util.Properties;

/**
 * Description: Holding the device name and value for property table.
 *
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

	public static PinItem createPinItem( String pin, Properties prop, ArduinoPanel.PinList pinList ) {
		String[] tokens = prop.getProperty( pin ).split( "," );
		PinItem item;
		if(tokens.length == 2) {
			item = new PinItem( pin, Integer.parseInt( tokens[0].trim() ), tokens[1].trim());
		}
		else
		{
			item = new PinItem( pin, Integer.parseInt( tokens[0].trim() ));
		}

		item.pinNameProperty().addListener( generatePinNameChangeListener(prop, item, pinList) );
		item.stateProperty().addListener( generateStateChangeListener(prop, item, pinList) );
		return item;
	}

	private static ChangeListener< String > generatePinNameChangeListener( Properties prop, PinItem item, ArduinoPanel.PinList pinList ) {
		return ( observable, oldValue, newValue ) -> {
			if(newValue.isEmpty() || newValue.equals( "Pin-" + item.pin ))
				prop.setProperty( item.pin, item.getState() + "" );
			else
				prop.setProperty( item.pin, item.getState() + ", " + newValue );
			pinList.store();
		};
	}

	private static ChangeListener< Number > generateStateChangeListener( Properties prop, PinItem item, ArduinoPanel.PinList pinList ) {
		return ( observable, oldValue, newValue ) -> {
			if(item.getPinName().equals( "Pin-" + item.pin ))
				prop.setProperty( item.pin, newValue + "" );
			else
				prop.setProperty( item.pin, newValue + ", " + item.getPinName() );
			pinList.store();
		};
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
