package spim.ui.view.component;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ResourceUtil
{
	// Load the bundles
	private static final ResourceBundle strings =
			ResourceBundle.getBundle("spim.ui.view.component.images.Strings");

	private static final String VERSION = getString("build.date");

	/**
	 * Return string property value.
	 *
	 * @param key
	 *          to find the String value
	 * @return a String corresponding to the given key.
	 */
	public static String getString(String key)
	{
		try
		{
			return strings.getString(key);
		}
		catch ( MissingResourceException e)
		{
			return key;
		}
	}
}
