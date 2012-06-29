package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

public class SPIMAcquisition implements MMPlugin, MouseMotionListener, KeyListener {
	// TODO: read these from the properties
	protected int motorMin = 1, motorMax = 8000,
		twisterMin = -180, twisterMax = 180;

	protected ScriptInterface app;
	protected CMMCore mmc;
	protected MMStudioMainFrame gui;

	protected String xyStageLabel, zStageLabel, twisterLabel,
		laserLabel, cameraLabel;

	protected JFrame frame;
	protected IntegerField xPosition, yPosition, zPosition, rotation,
		zFrom, zTo, stepsPerRotation, degreesPerStep,
		laserPower, exposure, settleTime;
	protected MotorSlider xSlider, ySlider, zSlider, rotationSlider,
		laserSlider, exposureSlider;
	protected LimitedRangeCheckbox limitedXRange, limitedYRange,
		limitedZRange;
	protected JCheckBox liveCheckbox, registrationCheckbox,
		multipleAngleCheckbox, continuousCheckbox;
	protected JButton speedControl, ohSnap;

	protected boolean updateLiveImage, zStageHasVelocity;
	protected Thread acquiring;

	private static Preferences prefs;

	private Timer timer;

	private final String UNCALIBRATED = "Uncalibrated";
	private final long MOTORS_UPDATE_PERIOD = 1000;

	// MMPlugin stuff

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the plugin
	 */
	public static String menuName = "Acquire SPIM image";
	public static String tooltipDescription = "The OpenSPIM GUI";
	
	/**
	 * The main app calls this method to remove the module window
	 */
	@Override
	public void dispose() {
		if (frame == null)
			return;
		if (prefs != null) try {
			prefs.sync();
		} catch (BackingStoreException e) {
			ReportingUtils.logException("Could not write preferences: ", e);
		}
		frame.dispose();
		frame = null;
		runToX.interrupt();
		runToY.interrupt();
		runToZ.interrupt();
		runToAngle.interrupt();
		timer.cancel();
		hookLiveControls(false);
	}
   
	/**
	 * The main app passes its ScriptInterface to the module. This
	 * method is typically called after the module is instantiated.
	 * @param app - ScriptInterface implementation
	 */
	@Override
	public void setApp(ScriptInterface app) {
		this.app = app;
		mmc = app.getMMCore();
		gui = MMStudioMainFrame.getInstance();
	}
   
	/**
	 * Open the module window
	 */
	@Override
	public void show() {
		prefs = Preferences.userNodeForPackage(getClass());
		ensurePixelResolution();
		initUI();
		configurationChanged();

		if(!gui.isLiveModeOn())
			gui.enableLiveMode(true);

		frame.setVisible(true);

		hookLiveControls(true);
	}

	private boolean liveControlsHooked;

	/**
	 * Embed our listeners in the live window's canvas space.
	 */
	public void hookLiveControls(boolean hook) {
		if(!gui.isLiveModeOn() || hook == liveControlsHooked)
			return;

		ImageWindow win = gui.getImageWin();
		if(win != null && win.isVisible()) {
			if(!hook) {
				win.getCanvas().removeMouseMotionListener(this);
				win.getCanvas().removeKeyListener(this);
			} else {
				win.getCanvas().addMouseMotionListener(this);
				win.getCanvas().addKeyListener(this);
			}
			liveControlsHooked = hook;
		} else {
			ReportingUtils.logException("Couldn't set hooked=" + hook, new NullPointerException("win=" + win + ", val?" + win.isValid() + ", vis?" + win.isVisible()));
		}
	}

	/**
	 * Makes sure we have at least a default 1:1 pixel size config.
	 */
	public void ensurePixelResolution() {
		try {
			if(mmc.getPixelSizeUm() <= 0) {
				mmc.definePixelSizeConfig(UNCALIBRATED, "Core", "Initialize", "1");
				mmc.setPixelSizeUm(UNCALIBRATED, 1);
				mmc.setPixelSizeConfig(UNCALIBRATED);
				ReportingUtils.logMessage("Defined uncalibrated pixel size (1:1).");
			}
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't define uncalibrated pixel size: ", e);
		}
	}
   
	/**
	 * The main app calls this method when hardware settings change.
	 * This call signals to the module that it needs to update whatever
	 * information it needs from the MMCore.
	 */
	@Override
	public void configurationChanged() {
		zStageLabel = null;
		xyStageLabel = null;
		twisterLabel = null;

		for (String label : mmc.getLoadedDevices().toArray()) try {
			String driver = mmc.getDeviceName(label);
			if (driver.equals("Picard Twister"))
				twisterLabel = label;
			else if (driver.equals("Picard Z Stage")) { // TODO: read this from the to-be-added property
				zStageLabel = label;
				zStageHasVelocity = mmc.hasProperty(zStageLabel, "Velocity");
			}
			else if (driver.equals("Picard XY Stage"))
				xyStageLabel = label;
			// testing
			else if (driver.equals("DStage")) {
				if (label.equals("DStage"))
					zStageLabel = label;
				else
					twisterLabel = label;
			}
			else if (driver.equals("DXYStage"))
				xyStageLabel = label;
		} catch (Exception e) {
			IJ.handleException(e);
		}
		cameraLabel = mmc.getCameraDevice();

		updateUI();
	}
   
	/**
	 * Returns a very short (few words) description of the module.
	 */
	@Override
	public String getDescription() {
		return "Open Source SPIM acquisition";
	}
   
	/**
	 * Returns verbose information about the module.
	 * This may even include a short help instructions.
	 */
	@Override
	public String getInfo() {
		// TODO: be more verbose
		return "See https://wiki.mpi-cbg.de/wiki/spiminabriefcase/";
	}
   
	/**
	 * Returns version string for the module.
	 * There is no specific required format for the version
	 */
	@Override
	public String getVersion() {
		return "0.01";
	}
   
	/**
	 * Returns copyright information
	 */
	@Override
	public String getCopyright() {
		return "Copyright Johannes Schindelin (2011)\n"
			+ "GPLv2 or later";
	}

	// UI stuff

	@SuppressWarnings("serial")
	protected void initUI() {
		if (frame != null)
			return;
		frame = new JFrame("OpenSPIM");

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.PAGE_AXIS));
		left.setBorder(BorderFactory.createTitledBorder("Position/Angle"));

		xSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToX.run(value);
				maybeUpdateImage();
			}
		};
		limitedXRange = new LimitedRangeCheckbox("Limit range", xSlider, 500, 2500, "range.x");
		ySlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToY.run(value);
				maybeUpdateImage();
			}
		};
		limitedYRange = new LimitedRangeCheckbox("Limit range", ySlider, 500, 2500, "range.y");
		zSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToZ.run(value);
				maybeUpdateImage();
			}
		};
		limitedZRange = new LimitedRangeCheckbox("Limit range", zSlider, 500, 2500, "range.z");
		rotationSlider = new MotorSlider(twisterMin, twisterMax, 0) {
			@Override
			public void valueChanged(int value) {
				runToAngle.run(angle2TwisterPosition(value));
				maybeUpdateImage();
			}
		};

		xPosition = new IntegerSliderField(xSlider);
		yPosition = new IntegerSliderField(ySlider);
		zPosition = new IntegerSliderField(zSlider);
		rotation = new IntegerSliderField(rotationSlider);

		zFrom = new IntegerField(1, "z.from") {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		zTo = new IntegerField(motorMax, "z.to") {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		degreesPerStep = new IntegerField(90, "degrees.per.rotation") {
			@Override
			public void valueChanged(int value) {
				stepsPerRotation.setText("" + (360 / value));
			}
		};
		stepsPerRotation = new IntegerField(360 / degreesPerStep.getValue()) {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};

		addLine(left, Justification.LEFT, "x:", xPosition, "y:", yPosition, "z:", zPosition, "angle:", rotation);
		addLine(left, Justification.STRETCH, "x:", xSlider);
		addLine(left, Justification.RIGHT, limitedXRange);
		addLine(left, Justification.STRETCH, "y:", ySlider);
		addLine(left, Justification.RIGHT, limitedYRange);
		addLine(left, Justification.STRETCH, "z:", zSlider);
		addLine(left, Justification.RIGHT, limitedZRange);
		addLine(left, Justification.RIGHT, "from z:", zFrom, "to z:", zTo);
		addLine(left, Justification.STRETCH, "rotation:", rotationSlider);
		addLine(left, Justification.RIGHT, "steps/rotation:", stepsPerRotation, "degrees/step:", degreesPerStep);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.PAGE_AXIS));
		right.setBorder(BorderFactory.createTitledBorder("Acquisition"));

		// TODO: find out correct values
		laserSlider = new MotorSlider(0, 1000, 1000, "laser.power") {
			@Override
			public void valueChanged(int value) {
				// TODO
			}
		};
		// TODO: find out correct values
		exposureSlider = new MotorSlider(10, 1000, 10, "exposure") {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setExposure(value);
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};

		laserPower = new IntegerSliderField(laserSlider);
		exposure = new IntegerSliderField(exposureSlider);

		liveCheckbox = new JCheckBox("Update Live View");
		updateLiveImage = gui.isLiveModeOn();
		liveCheckbox.setSelected(updateLiveImage);
		liveCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				updateLiveImage = e.getStateChange() == ItemEvent.SELECTED;
				if (updateLiveImage && !gui.isLiveModeOn())
					gui.enableLiveMode(true);
			}
		});
		registrationCheckbox = new JCheckBox("Perform SPIM registration");
		registrationCheckbox.setSelected(false);
		registrationCheckbox.setEnabled(false);
		multipleAngleCheckbox = new JCheckBox("Multiple Rotation Angles");
		multipleAngleCheckbox.setSelected(false);
		multipleAngleCheckbox.setEnabled(false);

		speedControl = new JButton("Set z-stage velocity");
		speedControl.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new Thread() {
					public void run() {
						setZStageVelocity();
					}
				}.start();
			}
		});

		continuousCheckbox = new JCheckBox("Continuous z motion");
		continuousCheckbox.setSelected(false);
		continuousCheckbox.setEnabled(true);

		settleTime = new IntegerField(0, "settle.delay") {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};

		ohSnap = new JButton("Oh snap!");
		ohSnap.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (acquiring != null) {
					acquiring.interrupt();
					try {
						acquiring.join();
					} catch (InterruptedException e2) {
						// orderly shutdown
					}
					return;
				}
				final int zStart = zFrom.getValue();
				final int zEnd = zTo.getValue();
				final boolean isContinuous = continuousCheckbox.isSelected();
				acquiring = new Thread() {
					@Override
					public void run() {
						updateUI();
						try {
							ImagePlus image = isContinuous ?
								snapContinuousStack(zStart, zEnd) : snapStack(zStart, zEnd, settleTime.getValue());
							image.show();
						} catch (InterruptedException e) {
							// orderly shutdown of this thread
						} catch (Exception e) {
							IJ.handleException(e);
						}
						acquiring = null;
						updateUI();
					}
				};
				ohSnap.setText("Abort acquisition");
				acquiring.start();
			}
		});

		addLine(right, Justification.RIGHT, "laser power:", laserPower, "exposure:", exposure);
		addLine(right, Justification.STRETCH, "laser:", laserSlider);
		addLine(right, Justification.STRETCH, "exposure:", exposureSlider);
		addLine(right, Justification.RIGHT, liveCheckbox);
		addLine(right, Justification.RIGHT, registrationCheckbox);
		addLine(right, Justification.RIGHT, multipleAngleCheckbox);
		addLine(right, Justification.RIGHT, speedControl);
		//addLine(right, Justification.RIGHT, continuousCheckbox);
		addLine(right, Justification.RIGHT, "Delay to let z-stage settle (ms)", settleTime);
		addLine(right, Justification.RIGHT, ohSnap);

		Container panel = frame.getContentPane();
		panel.setLayout(new GridLayout(1, 2));
		panel.add(left);
		panel.add(right);

		frame.pack();

		frame.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				updateMotorPositions();
			}
		});

		timer = new java.util.Timer(true);

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateMotorPositions();
			}
		}, 0, MOTORS_UPDATE_PERIOD);
	}

	protected void updateUI() {
		xPosition.setEnabled(acquiring == null && xyStageLabel != null);
		yPosition.setEnabled(acquiring == null && xyStageLabel != null);
		zPosition.setEnabled(acquiring == null && zStageLabel != null);
		rotation.setEnabled(acquiring == null && twisterLabel != null);

		xSlider.setEnabled(acquiring == null && xyStageLabel != null);
		limitedXRange.setEnabled(acquiring == null && xyStageLabel != null);
		ySlider.setEnabled(acquiring == null && xyStageLabel != null);
		limitedYRange.setEnabled(acquiring == null && xyStageLabel != null);
		zSlider.setEnabled(acquiring == null && zStageLabel != null);
		limitedZRange.setEnabled(acquiring == null && zStageLabel != null);
		zFrom.setEnabled(acquiring == null && zStageLabel != null);
		zTo.setEnabled(acquiring == null && zStageLabel != null);
		rotationSlider.setEnabled(acquiring == null && twisterLabel != null);
		stepsPerRotation.setEnabled(acquiring == null && twisterLabel != null);
		degreesPerStep.setEnabled(acquiring == null && twisterLabel != null);

		laserPower.setEnabled(acquiring == null && laserLabel != null);
		exposure.setEnabled(acquiring == null && cameraLabel != null);
		laserSlider.setEnabled(acquiring == null && laserLabel != null);
		exposureSlider.setEnabled(acquiring == null && cameraLabel != null);
		liveCheckbox.setEnabled(acquiring == null && cameraLabel != null);
		speedControl.setEnabled(acquiring == null && zStageHasVelocity);
		continuousCheckbox.setEnabled(acquiring == null && zStageLabel != null && cameraLabel != null);
		settleTime.setEnabled(acquiring == null && zStageLabel != null);
		ohSnap.setEnabled(zStageLabel != null && cameraLabel != null);
		ohSnap.setText(acquiring == null ? "Oh snap!" : "Abort!");

		updateMotorPositions();
	}

	protected void updateMotorPositions() {
		if (xyStageLabel != null) try {
			int x = (int)mmc.getXPosition(xyStageLabel);
			int y = (int)mmc.getYPosition(xyStageLabel);
			xSlider.setValue(x, true);
			ySlider.setValue(y, true);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (zStageLabel != null) try {
			int z = (int)mmc.getPosition(zStageLabel);
			zSlider.setValue(z, true);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (twisterLabel != null) try {
			int position = (int)mmc.getPosition(twisterLabel);
			rotationSlider.setValue(twisterPosition2Angle(position), true);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (laserLabel != null) try {
			// TODO: get current laser power
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (cameraLabel != null) try {
			// TODO: get current exposure
		} catch (Exception e) {
			IJ.handleException(e);
		}
	}

	private int mouseStartX = -1;
	private double stageStartT = -1;

	public void mouseDragged(MouseEvent me) {}

	public void mouseMoved(MouseEvent me) {
		if(me.isAltDown()) {
			// TODO: Rotate, then translate to keep axis fixed.
			if(mouseStartX < 0)
			{
				mouseStartX = me.getX();
				try {
					stageStartT = mmc.getPosition(twisterLabel);
				} catch(Exception e) {
					ReportingUtils.logError(e);
				}
				ReportingUtils.logMessage("Reset X=" + mouseStartX + ", T=" + stageStartT);
				return;
			}

			int delta = me.getX() - mouseStartX;

			// For now, at least, one pixel is going to be about
			// .1 degrees.

			try {
				mmc.setPosition(
					twisterLabel,
					stageStartT + delta * 0.1);
			} catch (Exception e) {
				ReportingUtils.logException(
					"Couldn't move stage: ", e);
			}
		} else if(mouseStartX >= 0) {
			mouseStartX = -1;
		};
	}

	public void keyPressed(KeyEvent ke) {
		// TODO: Using calibrated(?) axis of rotation, set up our
		// translated axis information.
	}

	public void keyReleased(KeyEvent ke) {}

	public void keyTyped(KeyEvent ke) {}

	protected int angle2TwisterPosition(int angle) {
		// always round towards zero
		return angle * 200 / 360;
	}

	protected int twisterPosition2Angle(int position) {
		// we need to guarantee that angle2Twister(twister2Angle(pos)) == pos,
		// so always round away from zero
		return (position * 360 + 199 * (position < 0 ? -1 : +1)) / 200;
	}

	protected boolean testTwisterPosition2Angle() {
		boolean error = false;
		for (int pos = -20000; pos <= 20000; pos++)
			if (angle2TwisterPosition(twisterPosition2Angle(pos)) != pos) {
				System.err.println("pos (" + pos + ") -> "
						+ twisterPosition2Angle(pos) + " -> "
						+ angle2TwisterPosition(twisterPosition2Angle(pos)));
				error = true;
			}
		return error;
	}

	// UI helpers

	protected enum Justification {
		LEFT, STRETCH, RIGHT
	};

	protected static void addLine(Container container, Justification justification, Object... objects) {
		JPanel panel = new JPanel();
		if (justification == Justification.STRETCH)
			panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		else
			panel.setLayout(new FlowLayout(justification == Justification.LEFT ? FlowLayout.LEADING : FlowLayout.TRAILING));
		for (Object object : objects) {
			Component component = (object instanceof String) ?
				new JLabel((String)object) :
				(Component)object;
			panel.add(component);
		}
		container.add(panel);
	}

	public static Dictionary<Integer, JLabel> makeLabelTable(int min, int max, int count) {
		int spacing = (int)((max - min) / count);
		spacing = 100 * ((spacing + 50) / 100); // round to nearest 100
		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();
		table.put(min, new JLabel("" + min));
		if (spacing <= 0)
			spacing = 100;
		for (int i = max; i > min; i -= spacing)
			table.put(i, new JLabel("" + i));
		return table;
	}

	protected void setZStageVelocity() {
		try {
			String[] allowedValues = mmc.getAllowedPropertyValues(zStageLabel, "Velocity").toArray();
			String currentValue = mmc.getProperty(zStageLabel, "Velocity");
			GenericDialog gd = new GenericDialog("z-stage velocity");
			gd.addChoice("velocity", allowedValues, currentValue);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			int newValue = (int)Integer.parseInt(gd.getNextChoice());
			mmc.setProperty(zStageLabel, "Velocity", newValue);
		} catch (Exception e) {
			IJ.handleException(e);
		}
	}

	// Persistence

	protected final static String prefsPrefix = "org.tomancak.spim.";

	public static int prefsGet(String key, int defaultValue) {
		if (key == null)
			return defaultValue;
		return prefs.getInt(prefsPrefix + key, defaultValue);
	}

	public static void prefsSet(String key, int value) {
		if (key != null)
			prefs.putInt(prefsPrefix + key, value);
	}

	// Accessing the devices

	protected void maybeUpdateImage() {
		if (cameraLabel != null && updateLiveImage) {
			synchronized(frame) {
				gui.updateImage();
			}
		}
	}

	protected RunTo runToX = new RunTo("x") {
		@Override
		public int get() throws Exception {
			return (int)mmc.getXPosition(xyStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setXYPosition(xyStageLabel, value, mmc.getYPosition(xyStageLabel));
		}

		@Override
		public void done() {
			xPosition.setText("" + goal);
		}
	};

	protected RunTo runToY = new RunTo("y") {
		@Override
		public int get() throws Exception {
			return (int)mmc.getYPosition(xyStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setXYPosition(xyStageLabel, mmc.getXPosition(xyStageLabel), value);
		}

		@Override
		public void done() {
			yPosition.setText("" + goal);
		}
	};

	protected RunTo runToZ = new RunTo("z") {
		@Override
		public int get() throws Exception {
			return (int)mmc.getPosition(zStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setPosition(zStageLabel, value);
		}

		@Override
		public void done() {
			zPosition.setText("" + goal);
		}
	};

	protected RunTo runToAngle = new RunTo("angle") {
		@Override
		public int get() throws Exception {
			return (int)mmc.getPosition(twisterLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setPosition(twisterLabel, value);
		}

		@Override
		public void done() {
			rotation.setText("" + twisterPosition2Angle(goal));
		}
	};

	protected ImageProcessor snapSlice() throws Exception {
		synchronized(frame) {
			mmc.snapImage();
		}

		int width = (int)mmc.getImageWidth();
		int height = (int)mmc.getImageHeight();
		if (mmc.getBytesPerPixel() == 1) {
			byte[] pixels = (byte[])mmc.getImage();
			return new ByteProcessor(width, height, pixels, null);
		} else if (mmc.getBytesPerPixel() == 2){
			short[] pixels = (short[])mmc.getImage();
			return new ShortProcessor(width, height, pixels, null);
		} else
			return null;
	}

	protected void snapAndShowContinuousStack(final int zStart, final int zEnd) throws Exception {
		// Cannot run this on the EDT
		if (SwingUtilities.isEventDispatchThread()) {
			new Thread() {
				public void run() {
					try {
						snapAndShowContinuousStack(zStart, zEnd);
					} catch (Exception e) {
						IJ.handleException(e);
					}
				}
			}.start();
			return;
		}

		snapContinuousStack(zStart, zEnd).show();
	}

	protected ImagePlus snapContinuousStack(int zStart, int zEnd) throws Exception {
		String meta = getMetaData();
		ImageStack stack = null;
		zSlider.setValue(zStart);
		runToZ.run(zStart);
		Thread.sleep(50); // wait 50 milliseconds for the state to settle
		zSlider.setValue(zEnd);
		int zStep = (zStart < zEnd ? +1 : -1);
		ReportingUtils.logMessage("from " + zStart + " to " + zEnd + ", step: " + zStep);
		for (int z = zStart; z  * zStep <= zEnd * zStep; z = z + zStep) {
			ReportingUtils.logMessage("Waiting for " + z + " (" + (z * zStep) + " < " + ((int)mmc.getPosition(zStageLabel) * zStep) + ")");
			while (z * zStep > (int)mmc.getPosition(zStageLabel) * zStep)
				Thread.sleep(0);
			ReportingUtils.logMessage("Got " + mmc.getPosition(zStageLabel));
			ImageProcessor ip = snapSlice();
			z = (int)mmc.getPosition(zStageLabel);
			ReportingUtils.logMessage("Updated z to " + z);
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("z: " + z, ip);
		}
		ReportingUtils.logMessage("Finished taking " + (zStep * (zEnd - zStart)) + " slices (really got " + (stack == null ? "0" : stack.getSize() + ")"));
		ImagePlus result = new ImagePlus("SPIM!", stack);
		result.setProperty("Info", meta);
		return result;
	}

	protected ImagePlus snapStack(int zStart, int zEnd, int delayMs) throws Exception {
		boolean isLive = gui.isLiveModeOn();
		if (isLive) {
			gui.enableLiveMode(false);
			Thread.sleep(100);
		}
		if (delayMs < 0)
			delayMs = 0;
		String meta = getMetaData();
		ImageStack stack = null;
		int zStep = (zStart < zEnd ? +1 : -1);
		for (int z = zStart; z * zStep <= zEnd * zStep; z = z + zStep) {
			zSlider.setValue(z);
			runToZ.run(z);
			Thread.sleep(delayMs);
			ImageProcessor ip = snapSlice();
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("z: " + z, ip);
		}
		ImagePlus result = new ImagePlus("SPIM!", stack);
		result.setProperty("Info", meta);
		if (isLive)
			gui.enableLiveMode(true);
		return result;
	}

	protected String getMetaData() throws Exception {
		String meta = "";
		if (xyStageLabel != "")
			meta += "x motor position: " + mmc.getXPosition(xyStageLabel) + "\n"
				+ "y motor position: " + mmc.getYPosition(xyStageLabel) + "\n";
		if (zStageLabel != "")
			meta +=  "z motor position: " + mmc.getPosition(zStageLabel) + "\n";
		if (twisterLabel != "")
			meta +=  "twister position: " + mmc.getPosition(twisterLabel) + "\n"
				+ "twister angle: " + twisterPosition2Angle((int)mmc.getPosition(twisterLabel)) + "\n";
		return meta;
	}

	/**
	 * This main() method is for use with Fiji's Script Editor
	 */
	public static void main(String[] args) {
		MMStudioMainFrame app = MMStudioMainFrame.getInstance();
		if (app == null) {
			app = new MMStudioMainFrame(true);
			app.setVisible(true);
		}
		SPIMAcquisition plugin = new SPIMAcquisition();
		plugin.setApp(app);
		plugin.show();
	}
}
