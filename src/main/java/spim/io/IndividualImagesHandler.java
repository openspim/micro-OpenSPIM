package spim.io;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

public class IndividualImagesHandler implements OutputHandler
{
	private File outputDirectory;
	private String namingScheme;
	
	/**
	 * Creates a nameScheme string from a list of 'short' names. Only applies to
	 * saveIndividually.
	 * 
	 * @param header Prefixed onto the string.
	 * @param xyztt	which axes to include in the filename.
	 * @param nameMap map of short names for devices to be in the filename.
	 * @return the generated scheme (is also saved to this object!)
	 */
	public static String shortNamesToScheme(String header, boolean xyztt[], String[] nameMap) {
		String nameScheme = header;

		if(xyztt[4])
			nameScheme += "-t=$(dt)";
		if(xyztt[0])
			nameScheme += "-" + (nameMap == null ? "X" : nameMap[0]) + "=$(X)";
		if(xyztt[1])
			nameScheme += "-" + (nameMap == null ? "Y" : nameMap[1]) + "=$(Y)";

		if(xyztt[2])
			nameScheme += "-" + (nameMap == null ? "Z" : nameMap[2]) + "=$(Z)";
		
		if(xyztt[3])
			nameScheme += "-" + (nameMap == null ? "Theta" : nameMap[3]) + "=$(T)";

		nameScheme += ".tif";

		return nameScheme;
	}


	public IndividualImagesHandler(File directory, String scheme) {
		outputDirectory = directory;
		if(!outputDirectory.exists() || !outputDirectory.isDirectory())
			throw new IllegalArgumentException("Invalid path or not a directory: " + directory.getAbsolutePath());
	
		namingScheme = scheme;
	}

	@Override
	public void processSlice(int expT, int C, int time, int angle, ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT)
			throws Exception {
		String name = nameImage(X, Y, C, Z, theta, deltaT);
		ImagePlus imp = new ImagePlus(name, ip);
		
		imp.setProperty("Info", X + "/" + Y + "/" + C + "/" + Z + ", " + theta + " @ " + deltaT + "s");
		
		IJ.save(imp, new File(outputDirectory, name).getAbsolutePath());
	}

	private String nameImage(double X, double Y, double C, double Z, double T, double dT) {
		String result = new String(namingScheme);

		result = result.replace("$(X)", Double.toString(X));
		result = result.replace("$(Y)", Double.toString(Y));
		result = result.replace("$(C)", Double.toString(C));
		result = result.replace("$(Z)", Double.toString(Z));
		result = result.replace("$(T)", Double.toString(T));
		result = result.replace("$(dt)", Double.toString(dT));

		return result;
	}

	@Override
	public void finalizeAcquisition(boolean bSuccess) throws Exception {
		// Nothing to do.
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		IJ.run("QuickPALM.Run_MyMacro", "Fast_VirtualStack_Opener.txt"); // TODO: Invoke the Open Virtual Stack process.

		return IJ.getImage();
	}


	@Override
	public void finalizeStack(int time, int angle) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void beginStack(int time, int angle) throws Exception {
		// TODO Auto-generated method stub
		
	}
}
