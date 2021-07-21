package spim.model.data;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Description: Channel item for channel table.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class ChannelItem
{
	public enum Type { Laser, Arduino }

	private final BooleanProperty selected = new SimpleBooleanProperty();
	private final ObjectProperty<Type> type = new SimpleObjectProperty<>();
	private final StringProperty name = new SimpleStringProperty();
	String laser;
	Number value;

	public ChannelItem()
	{
	}

	public ChannelItem( String camera, String laser, double exposure, InvalidationListener invalidationListener )
	{
		selected.set( true );
		type.set( Type.Laser );
		this.name.set( camera );
		this.laser = laser;
		this.value = exposure;
		selectedProperty().addListener( observable -> invalidationListener.invalidated( observable ) );
	}

	public ChannelItem( String name, double exposure )
	{
		type.set( Type.Arduino );
		this.name.set( name );
		this.value = exposure;
	}

	public ChannelItem( PinItem item, int exposure, InvalidationListener invalidationListener )
	{
		type.set( Type.Arduino );
		this.name.bind( item.pinNameProperty() );
		this.value = exposure;
		this.laser = item.getState() + "";
		selectedProperty().addListener( observable -> invalidationListener.invalidated( observable ) );
	}

	public BooleanProperty selectedProperty() { return selected; }

	public boolean getSelected() {
		return selected.get();
	}

	public void setSelected(boolean selected) {
		this.selected.set( selected );
	}

	public ObjectProperty<Type> getTypeProperty()
	{
		return type;
	}

	public Type getType() {
		return getTypeProperty().get();
	}

	public void setType( Type type )
	{
		this.type.set( type );
	}

	public StringProperty nameProperty() {
		return this.name;
	}

	public String getName()
	{
		return name.get();
	}

	public void setName( String name )
	{
		this.name.set( name );
	}

	public String getLaser()
	{
		return laser;
	}

	public void setLaser( String laser )
	{
		this.laser = laser;
	}

	public Number getValue()
	{
		return value;
	}

	public void setValue( Number value )
	{
		this.value = value;
	}
}
