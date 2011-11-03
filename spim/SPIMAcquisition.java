package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.gui.GenericDialog;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.util.Dictionary;
import java.util.Hashtable;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

import org.micromanager.utils.ReportingUtils;

public class SPIMAcquisition implements MMPlugin {
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

	protected Preferences prefs;

	// MMPlugin stuff

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the plugin
	 */
	public static String menuName = "Acquire SPIM image";
	
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
		initUI();
		configurationChanged();
		frame.setVisible(true);
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
			String driver = mmc.getDeviceNameInLibrary(label);
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
				runToAngle.run(value * 200 / 360);
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
				ohSnap.setLabel("Abort acquisition");
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
		ohSnap.setLabel(acquiring == null ? "Oh snap!" : "Abort!");

		if (xyStageLabel != null) try {
			int x = (int)mmc.getXPosition(xyStageLabel);
			int y = (int)mmc.getYPosition(xyStageLabel);
			xPosition.setText("" + x);
			yPosition.setText("" + y);
			xSlider.setValue(x);
			ySlider.setValue(y);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (zStageLabel != null) try {
			int z = (int)mmc.getPosition(zStageLabel);
			zPosition.setText("" + z);
			zSlider.setValue(z);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (twisterLabel != null) try {
			// TODO: how to handle 200 steps per 360 degrees?
			int angle = (int)mmc.getPosition(twisterLabel);
			rotation.setText("" + angle);
			rotationSlider.setValue(angle);
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

	protected abstract class MotorSlider extends JSlider implements ChangeListener {
		protected JTextField updating;
		protected Color background;
		protected String prefsKey;

		public MotorSlider(int min, int max, int current) {
			this(min, max, current, null);
		}

		public MotorSlider(int min, int max, int current, String prefsKey) {
			super(JSlider.HORIZONTAL, min, max, Math.min(max, Math.max(min, prefsGet(prefsKey, current))));

			this.prefsKey = prefsKey;

			setMinorTickSpacing((int)((max - min) / 40));
			setMajorTickSpacing((int)((max - min) / 5));
			setPaintTrack(true);
			setPaintTicks(true);

			if (min == 1)
				setLabelTable(makeLabelTable(min, max, 5));
			setPaintLabels(true);

			addChangeListener(this);
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			final int value = getValue();
			if (getValueIsAdjusting()) {
				if (updating != null) {
					if (background == null)
						background = updating.getBackground();
					updating.setBackground(Color.YELLOW);
					updating.setText("" + value);
				}
			}
			else
				new Thread() {
					@Override
					public void run() {
						if (updating != null)
							updating.setBackground(background);
						synchronized (MotorSlider.this) {
							valueChanged(value);
						}
					}
				}.start();
		}

		protected void handleChange(int value) {
			valueChanged(value);
			prefsSet(prefsKey, value);
		}

		public abstract void valueChanged(int value);
	}

	protected class LimitedRangeCheckbox extends JPanel implements ItemListener {
		protected IntegerField min, max;
		protected JCheckBox checkbox;
		protected MotorSlider slider;
		protected Dictionary originalLabels, limitedLabels;
		protected int originalMin, originalMax;
		protected int limitedMin, limitedMax;
		protected String prefsKey;

		public LimitedRangeCheckbox(String label, MotorSlider slider, int min, int max, String prefsKey) {
			String prefsKeyMin = null, prefsKeyMax = null;
			if (prefsKey != null) {
				this.prefsKey = prefsKey;
				prefsKeyMin = prefsKey + ".x";
				prefsKeyMax = prefsKey + ".y";
			}

			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
			checkbox = new JCheckBox(label);
			add(checkbox);
			this.min = new IntegerField(min, prefsKeyMin);
			add(this.min);
			add(new JLabel(" to "));
			this.max = new IntegerField(max, prefsKeyMax);
			add(this.max);

			this.slider = slider;
			originalLabels = slider.getLabelTable();
			originalMin = slider.getMinimum();
			originalMax = slider.getMaximum();
			limitedMin = this.min.getValue();
			limitedMax = this.max.getValue();
			checkbox.setSelected(false);
			checkbox.addItemListener(this);
		}

		@Override
		public void setEnabled(boolean enabled) {
			checkbox.setEnabled(enabled);
			min.setEnabled(enabled);
			max.setEnabled(enabled);
		}

		@Override
		public void itemStateChanged(ItemEvent e) {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				limitedMin = min.getValue();
				limitedMax = max.getValue();
				limitedLabels = makeLabelTable(limitedMin, limitedMax, 5);
				int current = slider.getValue();
				if (current < limitedMin)
					slider.setValue(limitedMin);
				else if (current > limitedMax)
					slider.setValue(limitedMax);
				slider.setMinimum(limitedMin);
				slider.setMaximum(limitedMax);
				slider.setLabelTable(limitedLabels);
			}
			else {
				slider.setMinimum(originalMin);
				slider.setMaximum(originalMax);
				slider.setLabelTable(originalLabels);
			}
		}
	}

	protected static Dictionary makeLabelTable(int min, int max, int count) {
		int spacing = (int)((max - min) / count);
		spacing = 100 * ((spacing + 50) / 100); // round to nearest 100
		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();
		table.put(min, new JLabel("" + min));
		for (int i = max; i > min; i -= spacing)
			table.put(i, new JLabel("" + i));
		return table;
	}

	protected class IntegerField extends JTextField {
		protected String prefsKey;

		public IntegerField(int value) {
			this(value, 4, null);
		}

		public IntegerField(int value, String prefsKey) {
			this(value, 4, prefsKey);
		}

		public IntegerField(int value, int columns) {
			this(value, columns, null);
		}

		public IntegerField(int value, int columns, String prefsKey) {
			super(columns);
			this.prefsKey = prefsKey;
			setText("" + prefsGet(prefsKey, value));
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					if (e.getKeyCode() != KeyEvent.VK_ENTER)
						return;
					handleChange();
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					handleChange();
				}
			});
		}

		protected void handleChange() {
			int value = getValue();
			valueChanged(value);
			prefsSet(prefsKey, value);
		}

		public int getValue() {
			String typed = getText();
			if(!typed.matches("\\d+"))
				return 0;
			return Integer.parseInt(typed);
		}

		public void valueChanged(int value) {}
	}

	protected class IntegerSliderField extends IntegerField {
		protected JSlider slider;

		public IntegerSliderField(JSlider slider) {
			super(slider.getValue());
			this.slider = slider;
			if (slider instanceof MotorSlider)
				((MotorSlider)slider).updating = this;
		}

		@Override
		public void valueChanged(int value) {
			if (slider != null)
				slider.setValue(value);
		}
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

	protected int prefsGet(String key, int defaultValue) {
		if (key == null)
			return defaultValue;
		return prefs.getInt(prefsPrefix + key, defaultValue);
	}

	protected void prefsSet(String key, int value) {
		if (key != null)
			prefs.putInt(prefsPrefix + key, value);
	}

	// Accessing the devices

	protected void maybeUpdateImage() {
		if (cameraLabel != null && updateLiveImage)
			gui.updateImage();
	}

	protected abstract static class RunTo extends Thread {
		protected int goal, current = Integer.MAX_VALUE;

		@Override
		public void run() {
			for (;;) try {
				if (goal != current) synchronized (this) {
					if (get() == goal) {
						ReportingUtils.logMessage("Reached goal: " + goal);
						current = goal;
						done();
						notifyAll();
					}
				}
				Thread.currentThread().sleep(50);
			} catch (Exception e) {
				return;
			}
		}

		public void run(int value) {
			synchronized (this) {
				if (goal == value) {
					done();
					return;
				}
				goal = value;
				ReportingUtils.logMessage("Setting goal: " + goal);
				try {
					set(goal);
				} catch (Exception e) {
					return;
				}
				synchronized (this) {
					if (!isAlive())
						start();
					try {
						wait();
					} catch (InterruptedException e) {
						return;
					}
					ReportingUtils.logMessage("Reached goal & returning: " + goal);
				}
			}
		}

		public abstract int get() throws Exception ;

		public abstract void set(int value) throws Exception;

		public abstract void done();
	}

	protected RunTo runToX = new RunTo() {
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

	protected RunTo runToY = new RunTo() {
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

	protected RunTo runToZ = new RunTo() {
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

	protected RunTo runToAngle = new RunTo() {
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
			rotation.setText("" + (goal * 360 / 200));
		}
	};

	protected ImageProcessor snapSlice() throws Exception {
		mmc.snapImage();

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
				+ "twister angle: " + (360.0 / 200.0 * mmc.getPosition(twisterLabel)) + "\n";
		return meta;
	}
}

