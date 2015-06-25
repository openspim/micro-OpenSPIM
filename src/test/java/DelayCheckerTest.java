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
}
