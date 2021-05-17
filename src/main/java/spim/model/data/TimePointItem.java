package spim.model.data;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2020
 */
public class TimePointItem
{
	static public StringProperty updateTimePointItem;
	static {
		updateTimePointItem = new SimpleStringProperty();
	}

	public enum Type {
		Acq,
		Wait
	}

	public enum IntervalUnit {
		Ms(0.001),
		Sec(1),
		Min(60),
		Hour(3600);

		private final double value;
		IntervalUnit( double value ) {
			this.value = value;
		}

		public double getValue() {
			return value;
		}
	}

	private Type type;

	final private IntegerProperty noTimePoints;
	final private IntegerProperty interval;
	final private ObjectProperty<IntervalUnit> intervalUnit;
	final StringBinding objectBinding;

	public TimePointItem()
	{
		this.noTimePoints = new SimpleIntegerProperty();
		this.interval = new SimpleIntegerProperty();
		this.intervalUnit = new SimpleObjectProperty<>();

		objectBinding = new StringBinding()
		{
			{
				super.bind( noTimePoints, interval, intervalUnit );
			}

			@Override protected String computeValue()
			{
				String total = "";

				if(type != null && intervalUnit.get() != null)
					total = this.hashCode() + "-" + TimePointItem.this.toString();
				return total;
			}
		};

		objectBinding.addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				updateTimePointItem.set(newValue);
			}
		} );
	}

	public TimePointItem( int timePoints, int interval, IntervalUnit unit )
	{
		this();
		this.type = Type.Acq;
		setNoTimePoints( timePoints );
		setInterval( interval );
		setIntervalUnit( unit );
	}

	public TimePointItem( int interval, IntervalUnit unit )
	{
		this();
		this.type = Type.Wait;
		setNoTimePoints( 0 );
		setInterval( interval );
		setIntervalUnit( unit );
	}

	public Type getType()
	{
		return type;
	}

	public void setType( Type type )
	{
		this.type = type;
	}

	public IntegerProperty getNoTimePointsProperty()
	{
		return noTimePoints;
	}

	public int getNoTimePoints()
	{
		return noTimePoints.get();
	}

	public void setNoTimePoints( int noTimePoints )
	{
		this.noTimePoints.setValue( noTimePoints );
	}

	public IntegerProperty getIntervalProperty()
	{
		return interval;
	}

	public void setInterval( int interval )
	{
		this.interval.setValue( interval );
	}

	public int getInterval()
	{
		return interval.get();
	}

	public ObjectProperty<IntervalUnit> getIntervalUnitProperty()
	{
		return intervalUnit;
	}

	public IntervalUnit getIntervalUnit()
	{
		return intervalUnit.get();
	}

	public void setIntervalUnit( IntervalUnit intervalUnit )
	{
		this.intervalUnit.setValue( intervalUnit );
	}

	public double getTotalSeconds() {
		if(type.equals( Type.Acq ) )
			return intervalUnit.get().getValue() * (noTimePoints.get() - 1) * interval.get();
		else if(type.equals( Type.Wait ))
			return intervalUnit.get().getValue() * interval.get();
		else return 0;
	}

	public double getIntervalSeconds() {
		return intervalUnit.get().getValue() * interval.get();
	}

	public static String toString(double total) {
		int h = (int) total / 3600;

		int m = (int) (total - (h * 3600)) / 60;

		double s = total - (h * 3600) - (m * 60);

		return String.format( "%dh %dm %.02fs", h, m, s );
	}

	public String toIntervalString() {
		return String.format( "%d %s", getInterval(), getIntervalUnit() );
	}

	@Override public String toString()
	{
		if(getType().equals( Type.Acq ))
			return String.format( "%d TP (%d %s/TP)", getNoTimePoints(), getInterval(), getIntervalUnit() );
		else if(getType().equals( Type.Wait ))
			return String.format( "Wait (%d %s)", getInterval(), getIntervalUnit() );

		return TimePointItem.toString( getTotalSeconds() );
	}
}
