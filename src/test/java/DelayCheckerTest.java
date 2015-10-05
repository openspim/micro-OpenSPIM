import org.junit.Test;
import org.junit.Assert;
import spim.util.DelayChecker;

/**
 * Unit test class for delay checker
 */
public class DelayCheckerTest
{
	@Test
	public void moreThan5seconds()
	{
		DelayChecker checker = new DelayChecker( 5000 );

		checker.reset();

		try
		{
			Thread.sleep( 11000 );
		}
		catch ( InterruptedException e )
		{
			e.printStackTrace();
		}

		checker.stop();

		Assert.assertTrue( checker.isDelayed() );
	}

	@Test
	public void moreThan5secondsWithThread()
	{
		Thread thread = new Thread(){
			@Override
			public void run()
			{
				DelayChecker checker = new DelayChecker( Thread.currentThread(), 5000 );

				checker.reset();

				try
				{
					Thread.sleep( 11000 );
				}
				catch ( InterruptedException e )
				{
					e.printStackTrace();
				}

				checker.stop();
			}
		};

		thread.start();

		try
		{
			thread.join();
		}
		catch ( InterruptedException e )
		{
			e.printStackTrace();
		}

		//Assert.assertTrue( checker.isDelayed() );
	}

}
