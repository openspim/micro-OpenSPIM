package spim.ui.view.component.viewer;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class HelpResourceUtil {
	// Load the bundles
	private static final ResourceBundle strings =
			ResourceBundle.getBundle("spim.help.Strings");

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
