package spim.model.data;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class ChannelItem
{
	public enum Type { Laser, Arduino }

	private final BooleanProperty selected = new SimpleBooleanProperty();
	private final ObjectProperty<Type> type = new SimpleObjectProperty<>();
	String name;
	String laser;
	Number value;

	public ChannelItem()
	{
	}

	public ChannelItem( String camera, String laser, double exposure )
	{
		selected.set( true );
		type.set( Type.Laser );
		this.name = camera;
		this.laser = laser;
		this.value = exposure;
	}

	public ChannelItem( String name, int pin )
	{
		selected.set( true );
		type.set( Type.Arduino );
		this.name = name;
		this.value = pin;
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

	public String getName()
	{
		return name;
	}

	public void setName( String name )
	{
		this.name = name;
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
