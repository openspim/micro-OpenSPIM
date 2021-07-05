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

	public static void applyMMPatches() {
		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final ClassPatcher hacker = new ClassPatcher(classLoader, PATCH_PKG, PATCH_SUFFIX);

//		hacker.insertBeforeMethod("org.micromanager.internal.MMStudio", "public boolean loadSystemConfiguration()", "private static String configFile;");
//
//		String newCode = "";
		hacker.addField("org.micromanager.internal.MMStudio", "public static java.lang.String configFile = \"\";");
		hacker.insertMethod("org.micromanager.internal.MMStudio", "public static java.lang.String getSysConfFile()", "return configFile;");
		hacker.insertMethod("org.micromanager.internal.MMStudio", "public static void setSysConfFile(java.lang.String file)", "configFile = file;");

		hacker.replaceMethod("org.micromanager.internal.MMStudio", "public boolean loadSystemConfiguration()", "     { " +
				"boolean result = true;\n" +
				"\n" +
				"      final org.micromanager.internal.utils.WaitDialog waitDlg = new org.micromanager.internal.utils.WaitDialog(\n" +
				"              \"Loading system configuration, please wait...\");\n" +
				"\n" +
				"      waitDlg.setAlwaysOnTop(true);\n" +
				"      waitDlg.showDialog();\n" +
				"      if (ui_.frame() != null) {\n" +
				"         ui_.frame().setEnabled(false);\n" +
				"      }\n" +
				"\n" +
				"      try {\n" +
				"		  if ( !configFile.isEmpty() ) sysConfigFile_ = configFile;\n" +
				"         if (sysConfigFile_ != null && sysConfigFile_.length() > 0) {\n" +
				"            org.micromanager.internal.utils.GUIUtils.preventDisplayAdapterChangeExceptions();\n" +
				"            core_.waitForSystem();\n" +
				"            coreCallback_.setIgnoring(true);\n" +
				"            org.micromanager.profile.internal.gui.HardwareConfigurationManager.\n" +
				"                  create(profile(), core_).\n" +
				"                  loadHardwareConfiguration(sysConfigFile_);\n" +
				"            coreCallback_.setIgnoring(false);\n" +
				"            org.micromanager.internal.utils.GUIUtils.preventDisplayAdapterChangeExceptions();\n" +
				"            afMgr_.initialize();\n" +
				"            events().post(new org.micromanager.events.AutofocusPluginShouldInitializeEvent());\n" +
				"            org.micromanager.internal.utils.FileDialogs.storePath(org.micromanager.internal.utils.FileDialogs.MM_CONFIG_FILE, new java.io.File(sysConfigFile_));\n" +
				"         }\n" +
				"      } catch (Exception err) {\n" +
				"         org.micromanager.internal.utils.GUIUtils.preventDisplayAdapterChangeExceptions();\n" +
				"\n" +
				"         waitDlg.closeDialog(); // Prevent from obscuring error alert\n" +
				"         org.micromanager.internal.utils.ReportingUtils.showError(err,\n" +
				"               \"Failed to load hardware configuration\",\n" +
				"               null);\n" +
				"         result = false;\n" +
				"      } finally {\n" +
				"         waitDlg.closeDialog();\n" +
				"         if (ui_.frame() != null) {\n" +
				"            ui_.frame().setEnabled(true);\n" +
				"         }\n" +
				"\n" +
				"      }\n" +
				"\n" +
				"      ui_.initializeGUI();\n" +
				"\n" +
				"      return result;" +
				"}\n");

		hacker.loadClass("org.micromanager.internal.MMStudio", classLoader, null);
	}
}
