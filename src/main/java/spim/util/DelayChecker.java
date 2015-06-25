package spim.util;

import ij.IJ;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Delay checker class
 * When the specific process delays more than 5 seconds, it will informed to IJ.log
 */
public class DelayChecker extends TimerTask
{
	final long delay;
	long previousTime;
	final Timer timer;

	public DelayChecker( long millis )
	{
		previousTime = System.currentTimeMillis();
		delay = millis;
		timer = new Timer( true );
		timer.scheduleAtFixedRate(this, 0, delay);
	}

	public void reset()
	{
		previousTime = System.currentTimeMillis();
	}

	public boolean isDelayed()
	{
		final long currentTime = System.currentTimeMillis();

		return previousTime + delay < currentTime;
	}

	public void stop()
	{
		timer.cancel();
	}

	@Override
	public void run() {
		if ( isDelayed() )
		{
			IJ.log( "[WARNING] The current process takes longer than " + delay / 1000 + " secs delay." );
		}
	}
}
