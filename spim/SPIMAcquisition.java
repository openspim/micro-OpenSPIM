package spim;

import ij.IJ;

import javax.swing.JFrame;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class SPIMAcquisition implements MMPlugin {
	protected ScriptInterface app;
	protected JFrame frame;

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the plugin
	 */
	public static String menuName = "Acquire SPIM image";
	
	/**
	 * The main app calls this method to remove the module window
	 */
	public void dispose() {
		if (frame == null)
			return;
		frame.dispose();
		frame = null;
	}
   
	/**
	 * The main app passes its ScriptInterface to the module. This
	 * method is typically called after the module is instantiated.
	 * @param app - ScriptInterface implementation
	 */
	public void setApp(ScriptInterface app) {
		this.app = app;
	}
   
	/**
	 * Open the module window
	 */
	public void show() {
		// TODO
		IJ.showMessage("This will show the SPIM GUI!");
	}
   
	/**
	 * The main app calls this method when hardware settings change.
	 * This call signals to the module that it needs to update whatever
	 * information it needs from the MMCore.
	 */
	public void configurationChanged() {
		// TODO: (re-)discover stages, camera, laser (Arduino)
	}
   
	/**
	 * Returns a very short (few words) description of the module.
	 */
	public String getDescription() {
		return "Open Source SPIM acquisition";
	}
   
	/**
	 * Returns verbose information about the module.
	 * This may even include a short help instructions.
	 */
	public String getInfo() {
		// TODO: be more verbose
		return "See https://wiki.mpi-cbg.de/wiki/spiminabriefcase/";
	}
   
	/**
	 * Returns version string for the module.
	 * There is no specific required format for the version
	 */
	public String getVersion() {
		return "0.01";
	}
   
	/**
	 * Returns copyright information
	 */
	public String getCopyright() {
		return "Copyright Johannes Schindelin (2011)\n"
			+ "GPLv2 or later";
	}

}

