package spim.mm;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class StringUtil
{
	/**
	 * Return true if the specified String are exactly the same.
	 *
	 * @param trim
	 *        if true then string are trimmed before comparison
	 */
	public static boolean equals(String s1, String s2, boolean trim)
	{
		if (isEmpty(s1, trim))
			return isEmpty(s2, trim);
		else if (isEmpty(s2, trim))
			return false;

		if (trim)
			return s1.trim().equals(s2.trim());

		return s1.equals(s2);
	}

	/**
	 * Return true if the specified String are exactly the same
	 */
	public static boolean equals(String s1, String s2)
	{
		return equals(s1, s2, false);
	}

	/**
	 * Return true if the specified String is empty.
	 *
	 * @param trim
	 *        trim the String before doing the empty test
	 */
	public static boolean isEmpty(String value, boolean trim)
	{
		if (value != null)
		{
			if (trim)
				return value.trim().length() == 0;

			return value.length() == 0;
		}

		return true;
	}

	/**
	 * Return true if the specified String is empty.
	 * The String is trimed by default before doing the test
	 */
	public static boolean isEmpty(String value)
	{
		return isEmpty(value, true);
	}

	/**
	 * Try to parse a integer from the specified String and return it.
	 * Return 'def' is we can't parse any integer from the string.
	 */
	public static int parseInt(String s, int def)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException E)
		{
			return def;
		}
	}
}
