package spim.model.data;

/**
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

	public PositionItem()
	{
	}

	public PositionItem( double x, double y, double r, double zStart, double zEnd, double zStep )
	{
		this.x = x;
		this.y = y;
		this.r = r;
		this.zStart = zStart;
		this.zEnd = zEnd;
		this.zStep = zStep;
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
		this.zStep = zStep;
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
