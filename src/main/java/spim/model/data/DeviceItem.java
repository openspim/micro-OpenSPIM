package spim.model.data;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2020
 */
public class DeviceItem
{
	String name;
	String value;

	public DeviceItem()
	{

	}

	public DeviceItem(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getName()
	{
		return name;
	}

	public void setName( String name )
	{
		this.name = name;
	}

	public String getValue()
	{
		return value;
	}

	public void setValue( String value )
	{
		this.value = value;
	}
}
