package spim.model.data;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Description: PositionItem is used for position table.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class PositionItem
{
	private double x;
	private double y;
	private double r;
	private double zStart;
	private double zEnd;
	private double zStep;
	private final BooleanProperty selected = new SimpleBooleanProperty();
	private final StringProperty name = new SimpleStringProperty();

	public PositionItem()
	{
	}

	public PositionItem( double x, double y, double r, double zStart, double zEnd, double zStep, InvalidationListener invalidationListener )
	{
		this.x = x;
		this.y = y;
		this.r = r;
		this.zStart = zStart;
		this.zEnd = zEnd;
		this.zStep = zStep;
		this.name.set("New Position");
		setSelected( true );
		selectedProperty().addListener( observable -> invalidationListener.invalidated( observable ) );
	}

	public double getX()
	{
		return x;
	}

	public double getY()
	{
		return y;
	}

	public double getR()
	{
		return r;
	}

	public double getZStart()
	{
		return zStart;
	}

	public double getZEnd()
	{
		return zEnd;
	}

	public double getZStep()
	{
		if( zStep == 0 ) zStep = 1.524;
		return zStep;
	}

	public String getZString() {
		if(zStart < zEnd)
			return String.format( "%.0f-%.0f", zStart, zEnd );
		else
			return String.format( "%.0f", zStart );
	}

	public String getPosZString() {
		if(zStart < zEnd)
			return String.format( "%.0f:%.1f:%.0f", zStart, zStep, zEnd );
		else
			return String.format( "%.0f", zStart );
	}

	public String getName() {
		return name.getValue();
	}

	public StringProperty getNameProperty() { return name; }

	public void setX( double x )
	{
		this.x = x;
	}

	public void setY( double y )
	{
		this.y = y;
	}

	public void setR( double r )
	{
		this.r = r;
	}

	public void setZStart( double zStart )
	{
		this.zStart = zStart;
	}

	public void setZEnd( double zEnd )
	{
		this.zEnd = zEnd;
	}

	public void setZStep( double zStep )
	{
		if (zStep != 0)
			this.zStep = zStep;
	}

	public int getNumberOfSlices() {
		return (int) ((int) (getZEnd() - getZStart() + getZStep()) / getZStep() + 1);
	}

	public BooleanProperty selectedProperty() { return selected; }

	public boolean getSelected() {
		return selected.get();
	}

	public void setSelected(boolean selected) {
		this.selected.set( selected );
	}

	public void setName(String name) {
		this.name.set(name);
	}

	public PositionItem clone(InvalidationListener invalidationListener) {
		PositionItem clone = new PositionItem(x, y, r, zStart, zEnd, zStep, invalidationListener );
		clone.setName( getName() + " Copy" );
		return clone;
	}

	@Override public String toString()
	{
		StringBuilder sb = new StringBuilder(  );
		sb.append( x );
		sb.append( '\t' );
		sb.append( y );
		sb.append( '\t' );
		sb.append( r );
		sb.append( '\t' );
		sb.append( zStart );
		sb.append( ':' );
		sb.append( zStep );
		sb.append( ':' );
		sb.append( zEnd );
		return sb.toString();
	}
}
