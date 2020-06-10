package spim.mm;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.internal.ConfigGroupPad;
import spim.SlimOpenSPIM;

import java.awt.Desktop;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class MMUtils
{
	public static final String separator = "/";
	private final static String MM_PATH_ID = "libray_path";

	static String prefFile = getUserDataDirectory()
			+ "setting.pref";

	private static Preferences prefs = (Preferences) loadCollection(prefFile);
	private static String microManagerFolder = null;
	static File demoConfigFile = null;
	private static boolean loaded = false;

	public static boolean isSystemLibrairiesLoaded()
	{
		return loaded;
	}

	public static boolean fixSystemLibrairies( Stage stage )
	{
		if (loaded)
			return loaded;

		microManagerFolder = prefs.get(MM_PATH_ID, "");

		final File microManagerFolderFile = new File( microManagerFolder );

		// empty or not existing ?
		if ( microManagerFolder.isEmpty() || !microManagerFolderFile.exists() || !microManagerFolderFile.isDirectory())
		{
			Alert alert = new Alert( Alert.AlertType.CONFIRMATION );
			alert.setTitle("Micro-Manager installation check");
			alert.setHeaderText("Check your Micro-Manager installation");
			alert.setContentText("Have you already installed Micro-Manager?");

			ButtonType buttonTypeChooseMMFolder = new ButtonType("Select Micro-Manager directory");
			ButtonType buttonTypeDownloadMM = new ButtonType("Download Micro-Manager");
			ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

			alert.getButtonTypes().setAll(buttonTypeChooseMMFolder, buttonTypeDownloadMM, buttonTypeCancel);

			// https://stackoverflow.com/questions/45866249/javafx-8-alert-different-button-sizes
			DialogPane pane = alert.getDialogPane();
			pane.getButtonTypes().stream()
					.map(pane::lookupButton)
					.forEach(btn-> ButtonBar.setButtonUniformSize( btn, false));

			Optional<ButtonType> result = alert.showAndWait();
			if (result.get() == buttonTypeChooseMMFolder){
				DirectoryChooser directoryChooser = new DirectoryChooser();
				directoryChooser.setTitle( "Select Micro-Manager folder" );
				File folder = directoryChooser.showDialog( stage );
				if(folder != null) {
					microManagerFolder = folder.getPath();
				}
			} else if (result.get() == buttonTypeDownloadMM) {
				try
				{
					openBrowser(
							"http://www.micro-manager.org/wiki/Download%20Micro-Manager_Latest%20Release");
				}
				catch ( URISyntaxException e )
				{
					e.printStackTrace();
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}

				Alert info = new Alert( Alert.AlertType.INFORMATION );
				info.setHeaderText( "Restart this plugin after Micro-Manager installation complete." );
				info.show();
				microManagerFolder = null;
			} else {
				microManagerFolder = null;
			}
		}

		// operation canceled or directory set
		if ( microManagerFolder == null)
			return false;

		// Set working directory with the give uManager folder
		System.setProperty("user.dir", microManagerFolder );

		if (!microManagerFolder.endsWith(separator))
			microManagerFolder += separator;

		loaded = loadJarFrom(new File( microManagerFolder + separator + "plugins" + separator
				+ "Micro-Manager" + separator));

		if (!loaded)
		{
			Alert alert = new Alert( Alert.AlertType.WARNING );
			alert.setHeaderText( "Error while loading libraries, have you chosen the correct directory ? Please try again." );
			alert.show();
		}
		else
		{
			// load DLL - no need
			loadDllFrom(new File( microManagerFolder ));
			// find configuration file
			File[] cfg = new File( microManagerFolder ).listFiles( new FilenameFilter()
			{

				@Override
				public boolean accept(File file, String s)
				{
					return s.equalsIgnoreCase("MMConfig_demo.cfg");
				}

			});
			if (cfg != null && cfg.length > 0)
				demoConfigFile = cfg[0];

			prefs.put(MM_PATH_ID, microManagerFolder );
			System.setProperty("mmcorej.library.path", microManagerFolder );
		}

		return loaded;
	}

	public static void resetLibrayPath()
	{
		loaded = false;
		prefs.put(MM_PATH_ID, "");
		System.setProperty("mmcorej.library.path", "");
	}

	/**
	 * Returns the selected group name from specified ConfigGroupPad
	 */
	public static String getSelectedGroupName( ConfigGroupPad group )
	{
		try
		{
			return (String) invokeMethod(group, "getGroup", true, new Object[] {});
		}
		catch (Exception e1)
		{
			try
			{
				return (String) invokeMethod(group, "getSelectedGroup", true, new Object[] {});
			}
			catch (Exception e2)
			{
				return "";
			}
		}
	}

	/**
	 * Returns the selected preset name from specified ConfigGroupPad
	 */
	public static String getSelectedPresetName( ConfigGroupPad group )
	{
		try
		{
			return (String) invokeMethod(group, "getPreset", true, new Object[] {});
		}
		catch (Exception e1)
		{
			try
			{
				return (String) invokeMethod(group, "getPresetForSelectedGroup", true, new Object[] {});
			}
			catch (Exception e2)
			{
				return "";
			}
		}
	}

	public static Object invokeMethod(Object object, String methodName, boolean forceAccess, Object... args)
			throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException,
			InvocationTargetException
	{
		final Class<?>[] parameterTypes = new Class<?>[args.length];

		// build parameter types
		for (int i = 0; i < args.length; i++)
			parameterTypes[i] = args[i].getClass();

		// get method
		final Method method = getMethod(object.getClass(), methodName, forceAccess, parameterTypes);
		// invoke method
		return method.invoke(object, args);
	}

	public static Method getMethod(Class<?> objectClass, String methodName, boolean forceAccess,
			Class<?>... parameterTypes) throws SecurityException, NoSuchMethodException
	{
		Class<?> clazz = objectClass;
		Method result = null;

		while ((clazz != null) && (result == null))
		{
			try
			{
				result = clazz.getDeclaredMethod(methodName, parameterTypes);
			}
			catch (NoSuchMethodException e)
			{
				// ignore
			}

			clazz = clazz.getSuperclass();
		}

		if (result == null)
			throw new NoSuchMethodException("Method " + methodName + "(..) not found in class " + objectClass.getName());

		if (forceAccess)
			result.setAccessible(true);

		return result;
	}

	private static boolean loadDllFrom(File microManagerDirectory)
	{
		final List<File> dll = Arrays.asList(getFiles(microManagerDirectory, new FileFilter()
		{
			@Override
			public boolean accept(File pathname)
			{
				String extension = getFileExtension(pathname.getAbsolutePath(), false);
				return (extension.equalsIgnoreCase("dll") || extension.equalsIgnoreCase("jnilib"))
						&& !pathname.getName().startsWith("OmicronxXDevices64");
			}
		}, false, false, true));

		if (dll.isEmpty())
			return false;

		int numberOfTry = 0;
		while (!dll.isEmpty())
		{
			numberOfTry++;
			for (File f : dll)
			{
				try
				{
					loadLibrary(f.getAbsolutePath());
				}
				catch (UnsatisfiedLinkError e)
				{
					// ignore
				}
			}

			if (numberOfTry > 1)
				break;
		}

		return true;
	}

	static void loadLibrary(String pathname)
	{
		final File file = new File(pathname);

		if (file.exists())
			System.load(file.getAbsolutePath());
		else
			System.loadLibrary(pathname);
	}

	private static boolean loadJarFrom(File microManagerDirectoryPath)
	{
		File[] files = getFiles(microManagerDirectoryPath, new FileFilter()
		{
			@Override
			public boolean accept(File pathname)
			{
				return getFileExtension(pathname.getAbsolutePath(), false).equalsIgnoreCase("jar");
			}
		}, true, false, true);

		if (files == null || files.length == 0)
			return false;

		URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class clazz= URLClassLoader.class;
		Method method= null;
		try
		{
			method = clazz.getDeclaredMethod("addURL", new Class[] { URL.class });
		} catch (NoSuchMethodException e)
		{
			e.printStackTrace();
		}
		method.setAccessible(true);

		for (File f : files)
		{
			final String path = f.getAbsolutePath();
			final String ext = getFileExtension(path, false).toLowerCase();

			if (ext.equals("jar") || ext.equals("class")) {
				try
				{
					method.invoke(classLoader, new Object[] { f.toURL() });
				} catch (IllegalAccessException e)
				{
					e.printStackTrace();
				} catch (InvocationTargetException e)
				{
					e.printStackTrace();
				} catch (MalformedURLException e)
				{
					e.printStackTrace();
				}
			}
		}

		return true;
	}

	public static String getFileExtension(String path, boolean withDot)
	{
		final String finalPath = getGenericPath(path);

		if (StringUtil.isEmpty(finalPath))
			return "";

		final int indexSep = finalPath.lastIndexOf('/');
		final int indexDot = finalPath.lastIndexOf('.');

		if ((indexDot == -1) || (indexDot < indexSep))
			return "";

		if (withDot)
			return finalPath.substring(indexDot);

		return finalPath.substring(indexDot + 1);
	}

	public static String[] getFiles(String directory, FileFilter filter, boolean recursive, boolean wantDirectory,
			boolean wantHidden)
	{
		final File[] files = getFiles(new File(getGenericPath(directory)), filter, recursive, wantDirectory,
				wantHidden);
		final String[] result = new String[files.length];

		for (int i = 0; i < files.length; i++)
			result[i] = files[i].getPath();

		return result;
	}

	public static File[] getFiles(File directory, FileFilter filter, boolean recursive, boolean wantDirectory,
			boolean wantHidden)
	{
		final List<File> result = new ArrayList<File>();

		getFiles(directory, filter, recursive, true, wantDirectory, wantHidden, result);

		return result.toArray(new File[result.size()]);
	}

	private static void getFiles(File f, FileFilter filter, boolean recursive, boolean wantFile, boolean wantDirectory,
			boolean wantHidden, List<File> list)
	{
		final File[] files = f.listFiles(filter);

		if (files != null)
		{
			for (File file : files)
			{
				if ((!file.isHidden()) || wantHidden)
				{
					if (file.isDirectory())
					{
						if (wantDirectory)
							list.add(file);
						if (recursive)
							getFiles(file, filter, recursive, wantFile, wantDirectory, wantHidden, list);
					}
					else if (wantFile)
						list.add(file);
				}
			}
		}
	}

	public static String getGenericPath(String path)
	{
		if (path != null)
			return path.replace('\\', '/');

		return null;
	}

	public static String getUserDataDirectory()
	{
		return System.getProperty("user.home") + File.separator
				+ ".slimOpenSPIM"
				+ File.separator
				+ getApplicationVersionString()
				+ File.separator;
	}

	public static String getApplicationVersionString()
	{
		return "1.0";
	}

	public static boolean dirExist(String dir)
	{
		return new File(dir).exists() || new File(dir).mkdirs();
	}

	static public void storePreference(String filePath) {
		storeCollection(filePath, prefs);
	}

	static private Object loadCollection(String fileName) {
		dirExist( getUserDataDirectory() );

		if(!new File(fileName).exists()) {
			return Preferences.userRoot().node( SlimOpenSPIM.class.getName() );
		}

		XMLDecoder e = null;

		try {
			e = new XMLDecoder(new BufferedInputStream(new FileInputStream(fileName)));
		} catch ( FileNotFoundException except) {
			except.printStackTrace();
		}

		Object collection = e.readObject();
		e.close();
		return collection;
	}

	static private void storeCollection(String fileName, Object collection) {
		XMLEncoder e = null;

		try {
			e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(fileName)));
		} catch (FileNotFoundException except) {
			except.printStackTrace();
		}

		e.writeObject(collection);
		e.close();
	}

	/**
	 * Open an URL in the default system browser
	 */
	public static boolean openBrowser(String uri) throws URISyntaxException, IOException
	{
		if (uri == null)
			return false;

		Desktop.getDesktop().browse( new URI( uri ) );

		return true;
	}

	/**
	 * Return true if the specified list of TaggedImage contains a <code>null</code> or poison image.
	 */
	public static boolean hasNullOrPoison(List< TaggedImage > images)
	{
		for (TaggedImage image : images)
			if ((image == null) || TaggedImageQueue.isPoison(image))
				return true;

		return false;
	}
}
