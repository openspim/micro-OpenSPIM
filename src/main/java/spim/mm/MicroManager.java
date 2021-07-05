package spim.mm;

import com.google.common.eventbus.Subscribe;
import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.Macro;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;

import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.MMEventCallback;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.display.internal.displaywindow.imagej.MMVirtualStack;

import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.events.*;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.MMVersion;
import org.micromanager.internal.MainFrame;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

import spim.mm.patch.WindowPositioningPatch;
import spim.ui.view.component.HalcyonMain;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;

import static javax.swing.WindowConstants.HIDE_ON_CLOSE;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class MicroManager implements PlugIn, CommandListener
{
	/**
	 * Metadata associated to last retrieved image.
	 */
	private static final Map<Integer, JSONObject> metadatas = new HashMap<Integer, JSONObject>(4);

	private volatile static ObjectProperty<Studio> mmStudioProperty = null;
	private volatile static ObjectProperty<GUIRefreshEvent> mmStudioGUIRefreshEventProperty = null;
	private volatile static MMStudio mmstudio = null;
	private volatile static MicroManager instance = null;
	private static ReentrantLock rlock;
	private static String sysConfigFile = "";
	public static String orgUserDir = "";
	private static Thread mmThread;

	static {
		WindowPositioningPatch.applyPatches();
		WindowPositioningPatch.applyMMPatches();
	}

	private MicroManager(ObjectProperty<Studio> studioObjectProperty, ObjectProperty<GUIRefreshEvent> refreshEventProperty) {
		rlock = new ReentrantLock(true);
		mmStudioProperty = studioObjectProperty;
		mmStudioGUIRefreshEventProperty = refreshEventProperty;
		run(null);
	}

	@Override
	public void run(final String arg) {
		mmThread = new Thread(
			() -> {
				try
				{
					if ( mmstudio == null || !mmstudio.isProgramRunning() )
					{

						Executer.addCommandListener( MicroManager.this );

						String profileNameAutoStart = parseMacroOptions();

						Method setSysConfFile
								= MMStudio.class.getDeclaredMethod("setSysConfFile", String.class);

						setSysConfFile.invoke(null, sysConfigFile);

						mmstudio = new MMStudio( true, profileNameAutoStart );
						mmstudio.events().registerForEvents( this );

						ReportingUtils.setCore( null );

						final MainFrame frame = mmstudio.uiManager().frame();
						if ( frame != null )
						{

							ReportingUtils.SetContainingFrame( frame );
							// force some initialization stuff on micro manager
//							frame.dispatchEvent( new WindowEvent( frame, WindowEvent.WINDOW_OPENED ) );
							// hide the main frame of Micro-Manager (we don't want it)
							frame.setVisible( true );
							frame.setExitStrategy( false );
							// Hide on Exit
							frame.setDefaultCloseOperation( HIDE_ON_CLOSE );
							frame.addWindowListener( new WindowAdapter()
							{
								@Override public void windowClosing( WindowEvent e )
								{
									super.windowClosing( e );
									shutdown();
								}
							} );
						}

						// get the MM core
						final CMMCore core = getCore();

						core.registerCallback( new OpenSPIMEventCallback() );
						mmStudioProperty.set( MicroManager.getMMStudio() );

//						ReportingUtils.setCore( core );
						// set core for reporting
						core.enableDebugLog( false );
						core.enableStderrLog( false );

						setSysConfFile.invoke(null, "");

						try
						{
							// initialize circular buffer (only if a camera is present)
							if ( !StringUtil.isEmpty( core.getCameraDevice() ) )
								core.initializeCircularBuffer();
						}
						catch ( Throwable e )
						{
							throw new Exception( "Error while initializing circular buffer of Micro Manager", e );
						}
					}
				}
				catch ( Throwable t )
				{
					shutdown();
					System.err.println( String.format( "Could not initialize Micro Manager !\n%s", t ) );
				}
			});

		mmThread.setContextClassLoader( HalcyonMain.class.getClassLoader() );
		mmThread.start();
	}

	private String parseMacroOptions()
	{
		//This method parses the optional ImageJ macro options. Currently it only supports the specification of a profile to automatically load like so: run("Micro-Manager Studio", "-profile {MyProfileNameHere}");
		//This method could be expanded to support other startup arguments in the future.
		String optionalArgs = Macro.getOptions(); //If, in ImageJ you start this plugin as `run("Micro-Manager Studio", "-profile MyProfile")` then this line will return "-profile MyProfile"
		String profileNameAutoStart = "Default User"; //The name of the user profile that Micro-Manager should start up with. In the case that this is left as null then a splash screen will request that the user select a profile before startup.
		if ( optionalArgs != null )
		{
			String args[] = optionalArgs.split( " " ); //Split the arg string into space separated array. This matches the way that system arguments are passed in to the `main` method by Java.
			for ( int i = 0; i < args.length; i++ )
			{ // a library for the parsing of arguments such as apache commons - cli would make this more robust if needed.
				if ( args[ i ].equals( "-profile" ) )
				{
					if ( i < args.length - 1 )
					{
						i++;
						profileNameAutoStart = args[ i ];
					}
					else
					{
						ReportingUtils.showError( "Micro-Manager received no value for the `-profile` startup argument." );
					}
				}
				else
				{
					ReportingUtils.showError( "Micro-Manager received unknown startup argument: " + args[ i ] );
				}
			}
		}
		return profileNameAutoStart;
	}

	private boolean closed_;
	private boolean closeStudio() {
		try {
			GUIUtils.invokeAndWait( new Runnable() {
				@Override
				public void run() {
					closed_ = mmstudio.closeSequence(true);
				}
			});
		}
		catch (InterruptedException ex) {
			closed_ = false;
			Thread.currentThread().interrupt();
		}
		catch (InvocationTargetException ignore) {
			closed_ = false;
		}
		return closed_;
	}


	/**
	 * Override ImageJ commands for Micro-Manager
	 * @param command
	 * @return command, or null if command is canceled
	 */
	@Override
	public String commandExecuting(String command) {
		if (command.equalsIgnoreCase("Quit") && mmstudio != null) {
			if (closeStudio()) {
				Executer.removeCommandListener(MicroManager.this);
				return command;
			}
			return null;
		}
		else if (command.equals("Crop")) {
			// Override in-place crop (which won't work) with cropped duplication
			// TODO Support stack cropping
			if ( IJ.getImage().getStack() instanceof MMVirtualStack ) {
				new Duplicator().run(IJ.getImage()).show();
				return null;
			}
		}
		// TODO We could support some more non-modifying commands
		return command;
	}

	/**
	 * For initialization
	 */
	public static synchronized void init(Stage stage, ObjectProperty< Studio > mmStudioProperty, ObjectProperty<GUIRefreshEvent> refreshEventProperty )
	{
		// already initialized --> show the frame and return it
		if (instance != null)
		{
			return;
		}

		try
		{
			Version version = null;

			try
			{
				// try to get version
				version = getMMVersion();
				// force to load MMStudio class
				MicroManager.class.getClassLoader().loadClass(MMStudio.class.getName());
			}
			catch (Throwable t)
			{
				// an fatal error occurred, force error on version checking then...
				version = new Version("1");
			}

			// cannot get version or wrong version ?
			if ((version == null) || version.isLower(new Version("2.0.0")))
			{
				Alert alert = new Alert( Alert.AlertType.ERROR );
				alert.setHeaderText( "Error while loading Micro-Manager" );
				alert.setContentText( "Your version of Micro-Manager seems to not be compatible !\n"
						+ "This plugin is only compatible with version 2.0.0 or above.\n"
						+ "Also check that you are using the same architecture for Icy and Micro-Manager (32/64 bits)\n"
						+ "You need to restart Icy to redefine the Micro-Manager folder." );

				alert.showAndWait();
				// so user can change the defined MM folder
				MMUtils.resetLibrayPath();
				return;
			}

			// show config selection dialog and exit if user canceled operation
			if (!showConfigSelection(stage))
				return;

			// show loading message
			// final LoadingFrame loadingFrame = new LoadingFrame(
			//					"  Please wait while loading Micro-Manager, Icy interface may not respond...  ");
			//	loadingFrame.show();
			try
			{
				try
				{
					instance = new MicroManager(mmStudioProperty, refreshEventProperty);
				}
				catch (Throwable e)
				{
					System.err.println(e);
					return;
				}

			}
			catch (Throwable e)
			{
				// shutdown everything
				shutdown();
			}
			finally
			{
//				loadingFrame.close();
			}
		}
		catch (Throwable t)
		{
			System.err.println(t);
		}
	}

	private static boolean showConfigSelection(Stage stage) throws Exception
	{
		LoadConfig config = new LoadConfig();
		config.start( stage );

		if(config.getReturnResult()) {
			setDefaultConfigFileName( config.getConfigFilePath() );
			Preferences.userNodeForPackage(MMStudio.class).put("sysconfig_file", config.getConfigFilePath());
			sysConfigFile = config.getConfigFilePath();
		}

		return config.getReturnResult();
	}

	public static void setConfigChanged() {
		if(mmstudio != null)
			mmstudio.setConfigChanged( true );
	}

	/**
	 * Retrieve the MicroManager main frame instance.
	 */
	public static MicroManager getInstance()
	{
		return instance;
	}

	public void show() {
		if(instance != null && mmstudio != null) {
			mmstudio.uiManager().frame().show();
		}
	}

	/**
	 * Retrieve the MicroManager version
	 */
	public static Version getMMVersion()
	{
		return new Version(MMVersion.VERSION_STRING.substring( 0, 5 ));
	}

	/**
	 * Use this to access micro-manager main object to access low level function that
	 * are not been implemented in Icy's micro-Manager.
	 *
	 * @return The micro-manager main studio object.
	 */
	public static MMStudio getMMStudio()
	{
		final MMStudio mmstudio = getInstance().mmstudio;
		if (mmstudio == null)
			return null;

		return mmstudio;
	}

	/**
	 * Use this to access micro-manager core and to be able to use function that
	 * are not been implemented in Micro-Manger for Icy. </br>
	 * </br>
	 * Be careful, if you use core functions instead of the integrated {@link MicroManager} methods
	 * or utility class you will have to handle synchronization of the
	 * core and catch all exception. </br>
	 * In most of case you don't need to use it, but only if you want to make fancy things.
	 */
	public static CMMCore getCore()
	{
		final MMStudio mmstudio = getMMStudio();
		if (mmstudio == null)
			return null;

		return mmstudio.getCMMCore();
	}

	/**
	 * Get exclusive access to micro manager.</br>
	 *
	 * @param wait
	 *        number of milli second to wait to retrieve exclusive access if micro manager is
	 *        already locked by another thread. If set to 0 then it returns immediately if already
	 *        locked.
	 * @return <code>true</code> if you obtained exclusive access and <code>false</code> if micro
	 *         manager is already locked by another thread and wait time elapsed.
	 * @throws InterruptedException
	 * @see #lock()
	 * @see #unlock()
	 */
	public static boolean lock(long wait) throws InterruptedException
	{
		final MicroManager inst = getInstance();
		if (inst == null)
			return false;

		return rlock.tryLock(wait, TimeUnit.MILLISECONDS);
	}

	/**
	 * Get exclusive access to micro manager.</br>
	 * If another thread already has exclusive access then it will wait until it release it.
	 *
	 * @see #lock(long)
	 * @see #unlock()
	 */
	public static void lock()
	{
		final MicroManager inst = getInstance();
		if (inst == null)
			return;

		rlock.lock();
	}

	/**
	 * Release exclusive access to micro manager.
	 *
	 * @see #lock()
	 * @see #lock(long)
	 */
	public static void unlock()
	{
		final MicroManager inst = getInstance();
		if (inst == null)
			return;

		rlock.unlock();
	}

	/**
	 * Wait for a MM device to be ready.
	 *
	 * @throws Exception
	 *         if an error occurs
	 */
	public static void waitForDevice(String device) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		final long start = System.currentTimeMillis();

		if (!StringUtil.isEmpty(device))
		{
			boolean busy = true;

			// we wait for 3 seconds max
			while (busy && ((System.currentTimeMillis() - start) < 3000))
			{
				lock();
				try
				{
					busy = core.deviceBusy(device);
					// wait a bit if device is busy
					if (busy)
						Thread.yield();
				}
				finally
				{
					unlock();
				}
			}

			// just for safety we also use "waitForDevice" afterward
			lock();
			try
			{
				core.waitForDevice(device);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Get the default config file name
	 */
	public static String getDefaultConfigFileName()
	{
		return System.getProperty("org.micromanager.default.config.file", "MMConfig_demo.cfg");
	}

	/**
	 * Set the default config file name
	 */
	public static void setDefaultConfigFileName(String fileName)
	{
		if (fileName != null)
			System.setProperty("org.micromanager.default.config.file", fileName);
	}

	/**
	 * @return The acquisition engine wrapper from MicroManager.
	 */
	public static AcquisitionWrapperEngine getAcquisitionEngine()
	{
		final MMStudio mmstudio = getMMStudio();
		if (mmstudio == null)
			return null;

		return mmstudio.getAcquisitionEngine();
	}

	/**
	 * @return The internal new acquisition engine from MicroManager.
	 */
	public static IAcquisitionEngine2010 getAcquisitionEngine2010()
	{
		final MMStudio mmstudio = getMMStudio();
		if (mmstudio == null)
			return null;

		return mmstudio.getAcquisitionEngine2010();
	}

	/**
	 * @return The engine settings for the most recently started acquisition sequence, or return
	 *         null if you never started an acquisition.
	 */
	public static SequenceSettings getAcquisitionSettings()
	{
		final AcquisitionWrapperEngine acqEngine = getAcquisitionEngine();
		if (acqEngine == null)
			return null;

		return acqEngine.getSequenceSettings();
	}

	/**
	 * @return The summaryMetadata for the most recently started acquisition sequence, or return
	 *         null if you never started an acquisition.
	 */
	public static JSONObject getAcquisitionMetaData()
	{
		final AcquisitionWrapperEngine acqEngine = getAcquisitionEngine();
		if (acqEngine == null)
			return null;

		return acqEngine.getSummaryMetadata();
	}

	/**
	 * @return Returns the number of channel of the camera device (usually 3 for color camera and 1
	 *         in other case).</br>
	 *         Just a shortcut for <code>getCore().getNumberOfCameraChannels()</code>
	 */
	public static long getCameraChannelCount()
	{
		final CMMCore core = MicroManager.getCore();
		if (core == null)
			return 0L;

		return core.getNumberOfCameraChannels();
	}

	/**
	 * Returns the metadata object associated to the last image retrieved with
	 * {@link #getLastTaggedImage()} or
	 * {@link #snapTaggedImage()}.</br>
	 * Returns <code>null</code> if there is no metadata associated to the specified channel.
	 *
	 * @param channel
	 *        channel index for multi channel camera
	 * @see #getLastTaggedImage()
	 * @see #snapTaggedImage()
	 * @see #getMetadata()
	 * @see MDUtils
	 */
	public static JSONObject getMetadata(int channel)
	{
		return metadatas.get(Integer.valueOf(channel));
	}

	/**
	 * Returns the metadata object associated to the last image retrieved with
	 * {@link #getLastTaggedImage()}  or
	 * {@link #snapTaggedImage()}.</br>
	 * Returns <code>null</code> if there is no image has been retrieved yet.
	 *
	 * @see #snapTaggedImage()
	 * @see #getMetadata(int)
	 * @see MDUtils
	 */
	public static JSONObject getMetadata()
	{
		return getMetadata(0);
	}

	/**
	 * Returns a list of {@link TaggedImage} representing the last image captured from the micro
	 * manager continuous acquisition. The list contains as many image than camera channel (see
	 * {@link #getCameraChannelCount()}).</br>
	 * Returns an empty list if the continuous acquisition is not running or if image buffer is
	 * empty</br>
	 * </br>
	 * You can listen new image event from acquisition or live mode by using these methods:</br>
	 *
	 * @throws Exception
	 */
	public static List<TaggedImage> getLastTaggedImage() throws Exception
	{
		final CMMCore core = MicroManager.getCore();

		if (core == null || !core.isSequenceRunning())
			return new ArrayList<TaggedImage>();

		final int numChannel = (int) core.getNumberOfCameraChannels();
		final List<TaggedImage> result = new ArrayList<TaggedImage>(numChannel);

		lock();
		try
		{
			for (int c = 0; c < numChannel; c++)
			{
				final TaggedImage image = core.getLastTaggedImage(c);

				result.add(image);

				// set channel index & number
				if (image != null)
				{
					MDUtils.setChannelIndex(image.tags, c);
					MDUtils.setNumChannels(image.tags, numChannel);
				}
			}
		}
		finally
		{
			unlock();
		}

		// check that we don't have poison image
		if (MMUtils.hasNullOrPoison(result))
			return new ArrayList<TaggedImage>();

		// assign metadata
		for (int c = 0; c < numChannel; c++)
			metadatas.put(Integer.valueOf(c), result.get(c).tags);

		return result;
	}

	/**
	 * Capture and return an image (or an empty list if an error occurred).<br>
	 * If an acquisition is currently in process (live mode or standard acquisition) the method will
	 * will wait for and return the next image from the image acquisition buffer.<br>
	 * In other case it just snaps a new image from the camera device and returns it.<br>
	 * This function return a list of image as the camera device can have several channel (see
	 * {@link #getCameraChannelCount()}) in which case we have one image per channel.<br>
	 * You can retrieve the associated metadata by using {@link #getMetadata(int)} method.
	 */
	public static List<TaggedImage> snapTaggedImage() throws Exception
	{
		final CMMCore core = MicroManager.getCore();
		if (core == null)
			return new ArrayList<TaggedImage>();

		final int numChannel = (int) core.getNumberOfCameraChannels();
		final List<TaggedImage> result = new ArrayList<TaggedImage>();

		lock();
		try
		{
			// wait for camera to be ready
			core.waitForDevice(core.getCameraDevice());
			// manual snap
			core.snapImage();

			// get result
			for (int c = 0; c < numChannel; c++)
			{
				// this should not be poison image
				final TaggedImage image = core.getTaggedImage(c);

				result.add(image);

				if (image != null)
				{
					// set channel index & number
					MDUtils.setChannelIndex(image.tags, c);
					MDUtils.setNumChannels(image.tags, numChannel);
					// store metadata
					metadatas.put(Integer.valueOf(c), image.tags);
				}
				else
					metadatas.put(Integer.valueOf(c), null);
			}
		}
		finally
		{
			unlock();
		}

		return result;
	}

	/**
	 * Returns the current camera device name
	 */
	public static String getCamera() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return "";

		return core.getCameraDevice();
	}

	/**
	 * Get the exposure of the current camera
	 */
	public static double getExposure() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return 0d;

		return core.getExposure();
	}

	public static void setCameraDevice(String camera) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		if (!core.getCameraDevice().equals( camera )) {
			lock();
			try
			{
				core.setCameraDevice(camera);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Set the exposure of the current camera
	 */
	public static void setExposure(String camera, double exposure) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		if (mmstudio == null)
			return;

		if (!core.getCameraDevice().equals( camera )) {
			setCameraDevice( camera );
		}

		// exposure actually changed ?
		if (core.getExposure() != exposure)
		{
			lock();
			try
			{
				// stop acquisition if needed
//				stopAcquisition();

				// save continuous acquisition state
				final boolean liveRunning = isLiveRunning();
				// stop live
				if (liveRunning)
					stopLiveMode();

				// better to use mmstudio method so it handles exposure synchronization
				core.setExposure( exposure );

				// // set new exposure
				// core.setExposure(exposure);

				// restore continuous acquisition
				if (liveRunning)
					startLiveMode();
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Get the current camera binning mode (String format)
	 */
	private static String getBinningAsString(CMMCore core, String camera) throws Exception
	{
		return core.getProperty(camera, MMCoreJ.getG_Keyword_Binning());
	}

	/**
	 * Get the current camera binning mode (String format)
	 */
	public static String getBinningAsString() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return "";

		final String camera = core.getCameraDevice();
		if (!StringUtil.isEmpty(camera))
			return getBinningAsString(core, camera);

		// default
		return "";
	}

	/**
	 * Set the current camera binning mode (String format)
	 */
	public static void setBinning(String value) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		final String camera = core.getCameraDevice();
		if (!StringUtil.isEmpty(camera))
		{
			// binning changed ?
			if (getBinningAsString(core, camera) != value)
			{
				lock();
				try
				{
					// stop acquisition if needed
//					stopAcquisition();

					// save continuous acquisition state
					final boolean liveRunning = isLiveRunning();
					// stop live
					if (liveRunning)
						stopLiveMode();

					// set new binning
					core.waitForDevice(camera);
					core.setProperty(camera, MMCoreJ.getG_Keyword_Binning(), value);

					// restore continuous acquisition
					if (liveRunning)
						startLiveMode();
				}
				finally
				{
					unlock();
				}
			}
		}
	}

	/**
	 * Get the binning in integer format
	 */
	private static int getBinningAsInt(String value, int def) throws Exception
	{
		// binning can be in "1;2;4" or "1x1;2x2;4x4" format...
		if (!StringUtil.isEmpty(value))
			// only use the first digit to get the binning as int
			return StringUtil.parseInt(value.substring(0, 1), 1);

		// default
		return def;
	}

	/**
	 * Get the current camera binning mode
	 */
	public static int getBinning() throws Exception
	{
		return getBinningAsInt(getBinningAsString(), 1);
	}

	public static boolean isLiveRunning() {
		final MMStudio mmstudio = getMMStudio();
		if (mmstudio == null)
			return false;

		return mmstudio.live().getIsLiveModeOn();
	}

	public static void stopLiveMode() {
		final MMStudio mmstudio = getMMStudio();

		if (mmstudio != null)
			mmstudio.live().setLiveMode( false );

//		for (LiveListener l : getLiveListeners())
//			l.liveStopped();
	}

	public static void startLiveMode() {
		final MMStudio mmstudio = getMMStudio();

		if (mmstudio != null)
			mmstudio.live().setLiveMode( true );

//		for (LiveListener l : getLiveListeners())
//			l.liveStarted();
	}

	/**
	 * Set the current camera binning mode
	 */
	public static void setBinning(int value) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		final String camera = core.getCameraDevice();
		if (!StringUtil.isEmpty(camera))
		{
			// binning changed ?
			if (getBinningAsInt(getBinningAsString(core, camera), value) != value)
			{
				// get possible values
				final StrVector availableBinnings = core.getAllowedPropertyValues(camera,
						MMCoreJ.getG_Keyword_Binning());

				lock();
				try
				{
					// stop acquisition if needed
//					stopAcquisition();

					// save continuous acquisition state
					final boolean liveRunning = isLiveRunning();
					// stop live
					if (liveRunning)
						stopLiveMode();

					// set new binning
					core.waitForDevice(camera);
					for (String binningStr : availableBinnings)
					{
						// this is the String format for wanted int value ?
						if (getBinningAsInt(binningStr, 0) == value)
						{
							core.setProperty(camera, MMCoreJ.getG_Keyword_Binning(), binningStr);
							break;
						}
					}

					// restore continuous acquisition
					if (liveRunning)
						startLiveMode();
				}
				finally
				{
					unlock();
				}
			}
		}
	}

	/**
	 * Returns the current shutter device.
	 */
	public static String getShutter() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return "";

		return core.getShutterDevice();
	}

	/**
	 * Sets the current shutter device.
	 */
	public static void setShutter(String value) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		// value changed ?
		if (!StringUtil.equals(value, getShutter()))
		{
			lock();
			try
			{
				core.setShutterDevice(value);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Returns the current shutter device open state.
	 */
	public static boolean isShutterOpen(String shutterLabel) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return false;

		return core.getShutterOpen(shutterLabel);
	}

	/**
	 * Open / close the current shutter device.
	 */
	public static void setShutterOpen(String shutterLabel, boolean value) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		// value changed ?
		if (value != isShutterOpen(shutterLabel))
		{
			lock();
			try
			{
				core.setShutterOpen(shutterLabel, value);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Returns the current auto shutter state.
	 */
	public static boolean getAutoShutter() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return false;

		return core.getAutoShutter();
	}

	/**
	 * Wait for System
	 */
	public static void waitForSystem() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		core.waitForSystem();
	}

	public static void waitForImageSynchro() throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		core.waitForImageSynchro();
	}

	/**
	 * Sets the auto shutter state.
	 */
	public static void setAutoShutter(boolean value) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		// value changed ?
		if (value != getAutoShutter())
		{
			lock();
			try
			{
				// close shutter first then set auto shutter state
				core.setShutterOpen(false);
				core.setAutoShutter(value);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Returns current pixel configured pixel size in micro meter.<br/>
	 * Returns 0d if pixel size is not defined
	 */
	public static double getPixelSize()
	{
		final CMMCore core = getCore();
		if (core == null)
			return 0d;

		return core.getPixelSizeUm();
	}

	/**
	 * Get all available config group
	 */
	public static List<String> getConfigGroups()
	{
		final CMMCore core = getCore();
		if (core == null)
			return new ArrayList<String>();

		final StrVector list = core.getAvailableConfigGroups();
		final List<String> result = new ArrayList<String>((int) list.size());

		for (int i = 0; i < list.size(); i++)
			result.add(list.get(i));

		return result;
	}

	/**
	 * Get all available config preset for the specified group
	 */
	public static List<String> getConfigs(String group)
	{
		final CMMCore core = getCore();
		if (core == null)
			return new ArrayList<String>();

		if (StringUtil.isEmpty(group))
			return new ArrayList<String>();

		final StrVector list = core.getAvailableConfigs(group);
		final List<String> result = new ArrayList<String>((int) list.size());

		for (int i = 0; i < list.size(); i++)
			result.add(list.get(i));

		return result;
	}

	/**
	 * Get the current config preset for the specified group
	 */
	public static String getCurrentConfig(String group) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return "";

		if (StringUtil.isEmpty(group))
			return "";

		return getCore().getCurrentConfig(group);
	}

	/**
	 * Set the specified preset for the given config group
	 */
	public static void setConfigForGroup(String group, String preset, boolean wait) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;


		if (StringUtil.isEmpty(group) || StringUtil.isEmpty(preset))
			return;

		// config changed ?
		if (!getCurrentConfig(group).equals(preset))
		{
			lock();
			try
			{
				// stop acquisition if needed
//				stopAcquisition();

				// save continuous acquisition state
				final boolean liveRunning = isLiveRunning();
				// stop live
				if (liveRunning)
					stopLiveMode();

				core.setConfig(group, preset);
				if (wait)
					core.waitForConfig(group, preset);

				// restore continuous acquisition
				if (liveRunning)
					startLiveMode();
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Returns current channel group (camera / channel / objective...)
	 */
	public static String getChannelGroup()
	{
		final CMMCore core = getCore();
		if (core == null)
			return "";

		return core.getChannelGroup();
	}

	/**
	 * Set the channel group (camera / channel / objective...)
	 */
	public static void setChannelGroup(String group) throws Exception
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		if (StringUtil.isEmpty(group))
			return;

		// channel group changed ?
		if (!getChannelGroup().equals(group))
		{
			lock();
			try
			{
				// stop acquisition if needed
//				stopAcquisition();

				// save continuous acquisition state
				final boolean liveRunning = isLiveRunning();
				// stop live
				if (liveRunning)
					stopLiveMode();

				core.setChannelGroup(group);
			}
			finally
			{
				unlock();
			}
		}
	}

	/**
	 * Returns configurations for current channel group.
	 *
	 * @see #getChannelGroup()
	 * @see #getConfigs(String)
	 */
	public static List<String> getChannelConfigs()
	{
		return getConfigs(MicroManager.getChannelGroup());
	}

	/**
	 * Use this method to enable logging (disabled by default).<br>
	 * Log file are in Icy folder and are named this way : CoreLog20140515.txt ; 20140515 is the
	 * date of debugging (2014/05/15)<br/>
	 * <b>Be careful, log files can be very huge and take easily 1Gb.</b>
	 *
	 * @param enable
	 */
	public static void enableDebugLogging(boolean enable)
	{
		final CMMCore core = getCore();
		if (core == null)
			return;

		core.enableDebugLog(enable);
		core.enableStderrLog(enable);
	}

	/**
	 * Returns <code>true</code> if Micro-Manager is initialized / loaded.<br>
	 */
	public static boolean isInitialized()
	{
		return instance != null;
	}

	/**
	 * Stop all Micro-Manager activities
	 */
	public static void shutdown()
	{
		// Remove all the MMStudio references from the GUI
		mmStudioProperty.set( null );

		if (metadatas != null)
			metadatas.clear();

		instance = null;
		mmstudio = null;
	}

	@Subscribe
	public void onAcquisitionStart( AcquisitionStartedEvent ae) {
		System.out.println("Acquisition started");
//		store_ = ae.getDatastore();
	}


	@Subscribe
	public void onAcquisitionEnd( AcquisitionEndedEvent ae) {
		System.out.println("Acquisition stopped");
//		store_ = null;
	}

	@Subscribe
	public void onSystemConfigurationLoaded(SystemConfigurationLoadedEvent event) {
		System.out.println("Config loaded");
	}

	@Subscribe
	public void onStagePositionChanged(StagePositionChangedEvent event) {
		System.out.println("Stage changed");
	}

	@Subscribe
	public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
		System.out.println("XYStage changed");
	}


	@Subscribe
	public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
//		if(!event.isCanceled()) {
//			shutdown();
//		}
		System.out.println("Shutdown");
	}

	@Subscribe
	public void onStartupComplete(StartupCompleteEvent event) {
		System.out.println("Started up");
	}

	@Subscribe
	public void onGUIRefresh(GUIRefreshEvent event) {
		System.out.println("Refreshed.....");
		mmStudioGUIRefreshEventProperty.set(event);
	}

//	private static class LiveListenerThread extends Thread
//	{
//		public LiveListenerThread()
//		{
//			super("uManager - LiveListener");
//		}
//
//		@Override
//		public void run()
//		{
//			while (!isInterrupted())
//			{
//				final CMMCore core = getCore();
//
//				// running and we have a new image in the queue ?
//				while (core != null && core.isSequenceRunning() && (core.getRemainingImageCount() > 0))
//				{
//					try
//					{
//						final List< TaggedImage > taggedImages;
//
//						lock();
//						try
//						{
//							// retrieve the last image
//							taggedImages = getLastTaggedImage();
//
//							// not empty --> remove it from the queue
//							if (!taggedImages.isEmpty())
//							{
//								try
//								{
//									// acquisition may consume the image in the mean time
//									if (!isAcquisitionRunning() && core.getRemainingImageCount() > 0)
//										core.popNextImage();
//								}
//								catch (Exception e)
//								{
//									// can happen with advanced acquisition set with a lower time
//									// interval than current exposure time
//									System.err.println(e);
//								}
//							}
//						}
//						finally
//						{
//							unlock();
//						}
//
//						if (!taggedImages.isEmpty())
//						{
//							// send image received event
//							for (LiveListener l : getLiveListeners())
//								l.liveImgReceived(taggedImages);
//						}
//					}
//					catch (Exception e)
//					{
//						// should not happen
//						System.err.println(e);
//					}
//				}
//
//				// sleep a bit to free some CPU time
//				try
//				{
//					Thread.sleep(1);
//				}
//				catch ( InterruptedException e )
//				{
//					e.printStackTrace();
//				}
//			}
//		}
//	}

	class OpenSPIMEventCallback extends MMEventCallback
	{
		@Override
		public void onPropertiesChanged()
		{
			System.out.println("PropertiesChanged");
		}

		@Override
		public void onConfigGroupChanged(String groupName, String newConfig)
		{
			System.out.println("ConfigGroupChanged");
		}

		@Override
		public void onExposureChanged(String deviceName, double exposure)
		{
			System.out.println("ExposureChanged: " + deviceName + " - " + exposure);
		}

		@Override
		public void onPropertyChanged(String deviceName, String propName, String propValue)
		{
			System.out.println("PropertyChanged:" + deviceName + "-" + propName + " with " + propValue);
			if(deviceName.equals("Core") && propName.equals("Initialize") && propValue.equals("0")) {
				mmStudioProperty.set( null );
			}
//			else if(deviceName.equals("Core") && propName.equals("Camera") && propValue == null) {
//				mmStudioProperty.set( null );
//			}
		}

		@Override
		public void onStagePositionChanged(String deviceName, double pos)
		{
			System.out.println("StagePositionChanged");
		}

		@Override
		public void onXYStagePositionChanged(String deviceName, double xPos, double yPos)
		{
			System.out.println("XYStagePositionChanged");
		};

		@Override
		public void onSystemConfigurationLoaded()
		{
			// Reload the SPIMSetup from the Micro-Manager studio
			//
//			System.out.println("SystemconfigurationLoaded");
			System.err.println("SystemConfigurationLoaded");
			System.setProperty( "user.dir", MicroManager.orgUserDir );
			mmStudioProperty.set( MicroManager.getMMStudio() );
		}
	}
}
