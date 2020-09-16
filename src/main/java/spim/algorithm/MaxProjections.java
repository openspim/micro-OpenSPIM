package spim.algorithm;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2020
 */
public class MaxProjections
{
	private FloatProcessor xy;
	int d = 0;

	public void addXYSlice( ImageProcessor ip )
	{
		if ( !( ip instanceof FloatProcessor ) )
		{
			ip = ( FloatProcessor ) ip.convertToFloat();
		}

		final int w = ip.getWidth();
		final int h = ip.getHeight();
		final float[] pixels = ( float[] ) ip.getPixels();

		if ( xy == null )
		{
			d = 1;
			xy = ( FloatProcessor ) ip.duplicate();
		}
		else
		{
			if ( w != xy.getWidth() || h != xy.getHeight() )
			{
				new IllegalArgumentException( "" + w + "x" + h
						+ " is incompatible with previously recorded "
						+ xy.getWidth() + "x" + xy.getHeight() );
			}

			d = d + 1;

			final float[] xyPixels = ( float[] ) xy.getPixels();
			for ( int i = 0; i < w * h; i++ )
			{
				xyPixels[ i ] = ( xyPixels[ i ] * ( d - 1 ) + pixels[ i ] ) / d;
			}
		}
	}

	public void reset()
	{
		xy = null;
		d = 0;
	}

	public ImageProcessor getProcessor()
	{
		return xy;
	}

	private static int normalize( float value, double min, double max )
	{
		if ( value < min )
			return 0;
		if ( value >= max )
			return 255;
		return ( int ) ( ( value - min ) * 256 / ( max - min ) );
	}

	public void write( File file )
	{
		ij.IJ.save( new ImagePlus( "MaxProject", xy ), file.getAbsolutePath() );
	}
}
