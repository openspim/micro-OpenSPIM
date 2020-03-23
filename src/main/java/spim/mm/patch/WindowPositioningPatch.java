package spim.mm.patch;

import spim.mm.ClassPatcher;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class WindowPositioningPatch
{
	private static final String PATCH_PKG = "spim.mm.patch";
	private static final String PATCH_SUFFIX = "Methods";

	/** Overrides class behavior of Micro-Manager classes by injecting method hooks. */
	public static void applyPatches()
	{
		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final ClassPatcher hacker = new ClassPatcher(classLoader, PATCH_PKG, PATCH_SUFFIX);

		// override behavior of org.micromanager.MMStudio
		hacker.replaceMethod("org.micromanager.internal.utils.WindowPositioning", "public static void setUpBoundsMemory(java.awt.Window window, java.lang.Class positioningClass, java.lang.String positioningKey)");

		// this directly load the new patched MMStudio class in the Plugin class loader
		hacker.loadClass("org.micromanager.internal.utils.WindowPositioning", classLoader, null);
	}
}
