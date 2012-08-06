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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.apache.commons.math.geometry.euclidean.threed.Rotation;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

import progacq.AcqParams;
import progacq.ProgrammaticAcquisitor;
import progacq.RangeSlider;

public class SPIMAcquisition implements MMPlugin, MouseMotionListener, KeyListener, ItemListener, ActionListener {
	private static final String BTN_STOP = "Abort!";
	private static final String BTN_START = "Oh Snap!";

	private JButton acq_fetchX;
	private JButton acq_fetchY;
	private JButton acq_fetchZ;
	private JSpinner acq_fetchDelta;
	private JButton acq_fetchT;
	private JCheckBox acq_xyDevCB;
	private JComboBox acq_xyDevCmbo;
	private RangeSlider acq_rangeX;
	private RangeSlider acq_rangeY;
	private JCheckBox acq_zDevCB;
	private JComboBox acq_zDevCmbo;
	private RangeSlider acq_rangeZ;
	private JCheckBox acq_tDevCB;
	private JComboBox acq_tDevCmbo;
	private RangeSlider acq_rangeTheta;
	private JCheckBox acq_timeCB;
	private JTextField acq_stepBox;
	private JTextField acq_countBox;
	private JCheckBox acq_timeoutCB;
	private JTextField acq_timeoutValBox;
	private JButton acq_goBtn;
	private Thread acqThread;
	
	private SPIMManualCalibrator calibration;

	// TODO: read these from the properties
	protected int motorMin = 1, motorMax = 8000,
		twisterMin = -100, twisterMax = 100;

	protected ScriptInterface app;
	protected CMMCore mmc;
	protected MMStudioMainFrame gui;

	protected String xyStageLabel, zStageLabel, twisterLabel,
		laserLabel, cameraLabel;

	protected JFrame frame;
	protected IntegerField xPosition, yPosition, zPosition, rotation,
		stepsPerRotation, degreesPerStep, laserPower, exposure, settleTime;
	protected MotorSlider xSlider, ySlider, zSlider, rotationSlider,
		laserSlider, exposureSlider;
	protected LimitedRangeCheckbox limitedXRange, limitedYRange,
		limitedZRange;
	protected JCheckBox liveCheckbox, registrationCheckbox, continuousCheckbox;
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
		return "See http://openspim.org/";
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
		rotationSlider = new MappedMotorSlider(twisterMin, twisterMax, 0) {
			@Override
			public void valueChanged(int value) {
				runToAngle.run(value);
				maybeUpdateImage();
			}

			@Override
			public double displayForValue(double value) {
				return twisterPosition2Angle(new Double(value).intValue());
			}

			@Override
			public double valueForDisplay(double value) {
				return angle2TwisterPosition(new Double(value).intValue());
			}
		};

		xPosition = new IntegerSliderField(xSlider);
		yPosition = new IntegerSliderField(ySlider);
		zPosition = new IntegerSliderField(zSlider);
		rotation = new IntegerSliderField(rotationSlider);

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

		JButton calibrateButton = new JButton("Calibrate...");
		calibrateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(calibration == null)
					calibration = new SPIMManualCalibrator(mmc, gui, twisterLabel);
				
				calibration.setVisible(true);
			};
		});

		addLine(left, Justification.LEFT, "x:", xPosition, "y:", yPosition, "z:", zPosition, "angle:", rotation);
		addLine(left, Justification.STRETCH, "x:", xSlider);
		addLine(left, Justification.RIGHT, limitedXRange);
		addLine(left, Justification.STRETCH, "y:", ySlider);
		addLine(left, Justification.RIGHT, limitedYRange);
		addLine(left, Justification.STRETCH, "z:", zSlider);
		addLine(left, Justification.RIGHT, limitedZRange);
		addLine(left, Justification.STRETCH, "rotation:", rotationSlider);
		addLine(left, Justification.RIGHT, "steps/rotation:", stepsPerRotation, "degrees/step:", degreesPerStep);
		addLine(left, Justification.RIGHT, calibrateButton);

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
		liveCheckbox.setEnabled(false);
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

		continuousCheckbox = new JCheckBox("Snap Continously");
		continuousCheckbox.setSelected(false);
		continuousCheckbox.setEnabled(true);

		settleTime = new IntegerField(0, "settle.delay") {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};

		addLine(right, Justification.RIGHT, "Laser power:", laserPower, "exposure:", exposure);
		addLine(right, Justification.STRETCH, "Laser:", laserSlider);
		addLine(right, Justification.STRETCH, "Exposure:", exposureSlider);
		addLine(right, Justification.RIGHT, continuousCheckbox, liveCheckbox, registrationCheckbox, speedControl);
		addLine(right, Justification.RIGHT, speedControl, "Delay to let z-stage settle (ms):", settleTime);

		JPanel stageControls = new JPanel();
		stageControls.setName("Stage Controls");
		stageControls.setLayout(new GridLayout(1, 2));
		stageControls.add(left);

		/**
		 * 4D Sliders
		 */
		JPanel importer = new JPanel();
		importer.setLayout(new BoxLayout(importer, BoxLayout.LINE_AXIS));

		acq_fetchX = new JButton("X");
		acq_fetchX.addActionListener(importStagePosition);
		acq_fetchY = new JButton("Y");
		acq_fetchY.addActionListener(importStagePosition);
		acq_fetchZ = new JButton("Z");
		acq_fetchZ.addActionListener(importStagePosition);
		acq_fetchT = new JButton("\u03B8");
		acq_fetchT.addActionListener(importStagePosition);

		acq_fetchDelta = new JSpinner(new SpinnerNumberModel(motorMax / 8, motorMin, motorMax, 10));

		addLine(importer, Justification.RIGHT, "Use current value of ", acq_fetchX, acq_fetchY, acq_fetchZ, acq_fetchT, " \u00B1 ", acq_fetchDelta);

		JPanel xy = new JPanel();
		xy.setLayout(new BoxLayout(xy, BoxLayout.PAGE_AXIS));
		xy.setBorder(BorderFactory.createTitledBorder("X/Y Stage"));

		JPanel xyDev = new JPanel();
		xyDev.setLayout(new BoxLayout(xyDev, BoxLayout.LINE_AXIS));

		acq_xyDevCB = new JCheckBox("");
		acq_xyDevCB.addItemListener(this);

		JLabel xyDevLbl = new JLabel("X/Y Stage Device:");
		acq_xyDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.XYStageDevice).toArray());
		acq_xyDevCmbo.setMaximumSize(acq_xyDevCmbo.getPreferredSize());

		xyDev.add(acq_xyDevCB);
		xyDev.add(xyDevLbl);
		xyDev.add(acq_xyDevCmbo);
		xyDev.add(Box.createHorizontalGlue());

		xy.add(xyDev);

		// These names keep getting more and more convoluted.
		JPanel xyXY = new JPanel();
		xyXY.setLayout(new BoxLayout(xyXY, BoxLayout.PAGE_AXIS));

		JPanel xy_x = new JPanel();
		xy_x.setBorder(BorderFactory.createTitledBorder("Stage X"));

		acq_rangeX = new RangeSlider(1D, 8000D);

		xy_x.add(acq_rangeX);
		xy_x.setMaximumSize(xy_x.getPreferredSize());

		xyXY.add(xy_x);

		JPanel xy_y = new JPanel();
		xy_y.setBorder(BorderFactory.createTitledBorder("Stage Y"));

		acq_rangeY = new RangeSlider(1D, 8000D);

		xy_y.add(acq_rangeY);
		xy_y.setMaximumSize(xy_y.getPreferredSize());

		xyXY.add(xy_y);

		xy.add(xyXY);
		xy.setMaximumSize(xy.getPreferredSize());

		JPanel z = new JPanel();
		z.setBorder(BorderFactory.createTitledBorder("Stage Z"));
		z.setLayout(new BoxLayout(z, BoxLayout.PAGE_AXIS));

		JPanel zDev = new JPanel();
		zDev.setLayout(new BoxLayout(zDev, BoxLayout.LINE_AXIS));

		acq_zDevCB = new JCheckBox("");
		acq_zDevCB.addItemListener(this);
		JLabel zDevLbl = new JLabel("Z Stage Device:");
		acq_zDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.StageDevice).toArray());
		acq_zDevCmbo.setSelectedItem(mmc.getFocusDevice());
		acq_zDevCmbo.setMaximumSize(acq_zDevCmbo.getPreferredSize());

		zDev.add(acq_zDevCB);
		zDev.add(zDevLbl);
		zDev.add(acq_zDevCmbo);
		zDev.add(Box.createHorizontalGlue());

		z.add(zDev);

		z.add(Box.createRigidArea(new Dimension(10, 4)));

		acq_rangeZ = new RangeSlider(1D, 8000D);

		z.add(acq_rangeZ);
		z.setMaximumSize(z.getPreferredSize());

		JPanel t = new JPanel();
		t.setBorder(BorderFactory.createTitledBorder("Theta"));
		t.setLayout(new BoxLayout(t, BoxLayout.PAGE_AXIS));

		JPanel tDev = new JPanel();
		tDev.setLayout(new BoxLayout(tDev, BoxLayout.LINE_AXIS));

		acq_tDevCB = new JCheckBox("");
		acq_tDevCB.addItemListener(this);
		JLabel tDevLbl = new JLabel("Theta Device:");
		tDevLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		acq_tDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.StageDevice).toArray());
		acq_tDevCmbo.setMaximumSize(acq_tDevCmbo.getPreferredSize());
		acq_tDevCmbo.setSelectedIndex(acq_tDevCmbo.getItemCount() - 1);
		acq_tDevCmbo.setAlignmentX(Component.LEFT_ALIGNMENT);

		tDev.add(acq_tDevCB);
		tDev.add(tDevLbl);
		tDev.add(acq_tDevCmbo);
		tDev.add(Box.createHorizontalGlue());

		t.add(tDev);

		t.add(Box.createRigidArea(new Dimension(10, 4)));

		acq_rangeTheta = new RangeSlider(-100D, 100D);

		t.add(acq_rangeTheta);
		t.setMaximumSize(t.getPreferredSize());

		JPanel acquisition = new JPanel();
		acquisition.setName("Acquisition");
		acquisition.setLayout(new BoxLayout(acquisition, BoxLayout.PAGE_AXIS));

		JPanel acqTop = new JPanel();
		acqTop.setLayout(new BoxLayout(acqTop, BoxLayout.LINE_AXIS));
		acqTop.add(xy);

		JPanel acqTopRight = new JPanel();
		acqTopRight.setLayout(new BoxLayout(acqTopRight, BoxLayout.PAGE_AXIS));
		acqTopRight.add(z);
		acqTopRight.add(t);

		acqTop.add(acqTopRight);

		JPanel bottom = new JPanel();
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.LINE_AXIS));

		JPanel timeBox = new JPanel();
		timeBox.setLayout(new BoxLayout(timeBox, BoxLayout.LINE_AXIS));
		timeBox.setBorder(BorderFactory.createTitledBorder("Time"));

		acq_timeCB = new JCheckBox("");
		acq_timeCB.setSelected(false);

		JLabel step = new JLabel("Interval (ms):");
		step.setToolTipText("Delay between acquisition sequences in milliseconds.");
		acq_stepBox = new JTextField(8);
		acq_stepBox.setMaximumSize(acq_stepBox.getPreferredSize());

		JLabel count = new JLabel("Count:");
		count.setToolTipText("Number of acquisition sequences to perform.");
		acq_countBox = new JTextField(8);
		acq_countBox.setMaximumSize(acq_countBox.getPreferredSize());

		acq_timeCB.addItemListener(this);

		timeBox.add(acq_timeCB);
		timeBox.add(step);
		timeBox.add(acq_stepBox);
		timeBox.add(Box.createRigidArea(new Dimension(4, 10)));
		timeBox.add(count);
		timeBox.add(acq_countBox);

		bottom.add(timeBox);

		JPanel timeoutBox = new JPanel();
		timeoutBox.setLayout(new BoxLayout(timeoutBox, BoxLayout.LINE_AXIS));
		timeoutBox
				.setBorder(BorderFactory.createTitledBorder("Device Timeout"));

		acq_timeoutCB = new JCheckBox("Override Timeout:");
		acq_timeoutCB.setHorizontalTextPosition(JCheckBox.RIGHT);
		acq_timeoutCB.addItemListener(this);

		acq_timeoutValBox = new JTextField(8);
		acq_timeoutValBox.setMaximumSize(acq_timeoutValBox.getPreferredSize());

		timeoutBox.add(acq_timeoutCB);
		timeoutBox.add(acq_timeoutValBox);

		bottom.add(timeoutBox);

		JPanel goBtnPnl = new JPanel();
		goBtnPnl.setLayout(new GridLayout(2,1));

		acq_goBtn = new JButton(BTN_START);
		acq_goBtn.addActionListener(this);
		goBtnPnl.add(acq_goBtn);

		acq_Progress = new JProgressBar(0, 100);
		acq_Progress.setEnabled(false);
		goBtnPnl.add(acq_Progress);

		bottom.add(Box.createHorizontalGlue());
		bottom.add(goBtnPnl);
		bottom.add(Box.createHorizontalGlue());

		acq_timeCB.setSelected(false);
		acq_countBox.setEnabled(false);
		acq_stepBox.setEnabled(false);

		acq_timeoutCB.setSelected(false);
		acq_timeoutValBox.setEnabled(false);

		acquisition.add(importer);
		acquisition.add(acqTop);
		acquisition.add(right);
		acquisition.add(bottom);

		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Stage Controls", stageControls);
		tabs.add("Acquisition", acquisition);

		frame.add(tabs);

		frame.pack();

		frame.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				tryUpdateSliderPositions();
			}
		});

		timer = new java.util.Timer(true);

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					updateMotorPositions();
				} catch (Exception e) {
					ReportingUtils.logError(e);
				}
			}
		}, 0, MOTORS_UPDATE_PERIOD);
	}

	protected ActionListener importStagePosition = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent ae) {
			double range = ((Integer)acq_fetchDelta.getValue()).doubleValue();
			double value = 0;

			RangeSlider target = null;

			try {
				if(ae.getSource().equals(acq_fetchX)) {
					target = acq_rangeX;
					value = mmc.getXPosition(xyStageLabel);
				} else if(ae.getSource().equals(acq_fetchY)) {
					target = acq_rangeY;
					value = mmc.getYPosition(xyStageLabel);
				} else if(ae.getSource().equals(acq_fetchZ)) {
					target = acq_rangeZ;
					value = mmc.getPosition(zStageLabel);
				} else if(ae.getSource().equals(acq_fetchT)) {
					target = acq_rangeTheta;
					value = mmc.getPosition(twisterLabel);
				} else {
					throw new Exception("Import from where now?");
				}
			} catch(Exception e) {
				JOptionPane.showMessageDialog(SPIMAcquisition.this.frame, e.getMessage());
				return;
			}

			double min = value - range;
			double max = value + range;
			if(ae.getSource().equals(acq_fetchT)) {
				min = Math.max(value - angle2TwisterPosition((int)range), twisterMin);
				max = Math.min(value + angle2TwisterPosition((int)range), twisterMax);
			} else {
				min = Math.max(min, motorMin);
				max = Math.min(max, motorMax);
			};

			target.setMinMax(min, max);
		};
	};

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

		acq_xyDevCB.setSelected(xyStageLabel != null);
		acq_zDevCB.setSelected(zStageLabel != null);
		acq_tDevCB.setSelected(twisterLabel != null);

		if(xyStageLabel != null)
			acq_xyDevCmbo.setSelectedItem(xyStageLabel);
		if(zStageLabel != null)
			acq_zDevCmbo.setSelectedItem(zStageLabel);
		if(twisterLabel != null)
			acq_tDevCmbo.setSelectedItem(twisterLabel);

		acq_xyDevCmbo.setEnabled(xyStageLabel != null);
		acq_zDevCmbo.setEnabled(zStageLabel != null);
		acq_tDevCmbo.setEnabled(twisterLabel != null);

		acq_rangeX.setEnabled(xyStageLabel != null);
		acq_rangeY.setEnabled(xyStageLabel != null);
		acq_rangeZ.setEnabled(zStageLabel != null);
		acq_rangeTheta.setEnabled(twisterLabel != null);

		acq_fetchX.setEnabled(xyStageLabel != null);
		acq_fetchY.setEnabled(xyStageLabel != null);
		acq_fetchZ.setEnabled(zStageLabel != null);
		acq_fetchT.setEnabled(twisterLabel != null);

		tryUpdateSliderPositions();
	}

	protected void tryUpdateSliderPositions() {
		try {
			updateMotorPositions();
		} catch(Exception e) {
			IJ.handleException(e);
		}
	}

	private void updateMotorPositions() throws Exception {
		if (xyStageLabel != null) {
			int x = (int)mmc.getXPosition(xyStageLabel);
			int y = (int)mmc.getYPosition(xyStageLabel);
			xSlider.updateValueQuietly(x);
			ySlider.updateValueQuietly(y);
		}

		if (zStageLabel != null) {
			int z = (int)mmc.getPosition(zStageLabel);
			zSlider.updateValueQuietly(z);
		}

		if (twisterLabel != null) {
			int position = (int)mmc.getPosition(twisterLabel);
			rotationSlider.updateValueQuietly(twisterPosition2Angle(position));
		}

		if (laserLabel != null) {
			// TODO: get current laser power
		}

		if (cameraLabel != null) {
			// TODO: get current exposure
		}
	}

	private int mouseStartX = -1;
	private double[] stageStart = new double[4];

	private Vector3D applyCalibratedRotation(Vector3D pos, double dtheta) {
		if(calibration == null || !calibration.getIsCalibrated())
			return pos;

		Vector3D rotOrigin = calibration.getRotationOrigin();
		Vector3D rotAxis = calibration.getRotationAxis();

		Rotation rot = new Rotation(rotAxis, dtheta * Math.PI / 100D);

		return rotOrigin.add(rot.applyTo(pos.subtract(rotOrigin)));
	}

	public void mouseDragged(MouseEvent me) {}

	public void mouseMoved(MouseEvent me) {
		// Don't rotate unless they're actively looking at the window.
		if(!gui.getImageWin().hasFocus())
			return;

		if(me.isAltDown()) {
			// TODO: Rotate, then translate to keep axis fixed.
			if(mouseStartX < 0)
			{
				mouseStartX = me.getX();
				try {
					stageStart[0] = mmc.getXPosition(xyStageLabel);
					stageStart[1] = mmc.getYPosition(xyStageLabel);
					stageStart[2] = mmc.getPosition(zStageLabel);
					stageStart[3] = mmc.getPosition(twisterLabel);
				} catch(Exception e) {
					ReportingUtils.logError(e);
				}
				return;
			}

			int delta = me.getX() - mouseStartX;

			// For now, at least, one pixel is going to be about
			// .1 steps.

			try {
				// Note: If the system isn't calibrated, the method below
				// returns what we pass it -- for XYZ, the current position
				// of the motors. That is, for an uncalibrated system, the
				// setXYPosition and first setPosition lines below are noops.
				Vector3D xyz = applyCalibratedRotation(new Vector3D(
						stageStart[0], stageStart[1], stageStart[2]),
						delta * 0.1);

				mmc.setXYPosition(xyStageLabel, xyz.getX(), xyz.getY());

				mmc.setPosition(zStageLabel, xyz.getZ());

				mmc.setPosition(
					twisterLabel,
					stageStart[3] + delta * 0.1);
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
		return makeLabelTable(min, max, (int)((max - min) / count), 100, -1);
	}

	public static Dictionary<Integer, JLabel> makeLabelTable(int min, int max, int step, int round, int align) {
		int count = (max - min) / step;

		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();

		table.put(min, new JLabel("" + min));
		table.put(max, new JLabel("" + max));

		float start = min;

		if(align == 0) {
			float offset = ((max - min) % step) / 2;

			start = min + (int)offset;
		} else if(align > 0) {
			start = max;
			step = -step;
		}

		for(int lbl = 1; lbl < count; ++lbl) {
			float nearPos = start + step*lbl;

			if(round > 0)
				nearPos = Math.round(nearPos / round) * round;

			ReportingUtils.logMessage("" + lbl + ": Putting " + (int)nearPos + " (" + nearPos + ")");
			table.put((int)nearPos, new JLabel("" + (int)nearPos));
		}

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
	
	public static double prefsGet(String key, double defaultValue) {
		if (key == null)
			return defaultValue;
		return prefs.getDouble(key, defaultValue);
	}
	
	public static void prefsSet(String key, double value) {
		if (key != null)
			prefs.putDouble(key, value);
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
	private JProgressBar acq_Progress;

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

	@Override
	public void itemStateChanged(ItemEvent ie) {
		if(ie.getSource().equals(acq_xyDevCB)) {
			acq_xyDevCmbo.setEnabled(acq_xyDevCB.isSelected());
			acq_rangeX.setEnabled(acq_xyDevCB.isSelected());
			acq_rangeY.setEnabled(acq_xyDevCB.isSelected());
		} else if(ie.getSource().equals(acq_zDevCB)) {
			acq_rangeZ.setEnabled(acq_zDevCB.isSelected());
			acq_zDevCmbo.setEnabled(acq_zDevCB.isSelected());
		} else if(ie.getSource().equals(acq_tDevCB)) {
			acq_rangeTheta.setEnabled(acq_tDevCB.isSelected());
			acq_tDevCmbo.setEnabled(acq_tDevCB.isSelected());
		} else if(ie.getSource().equals(acq_timeCB)) {
			acq_countBox.setEnabled(acq_timeCB.isSelected());
			acq_stepBox.setEnabled(acq_timeCB.isSelected());
		} else if(ie.getSource().equals(acq_timeoutCB)) {
			acq_timeoutValBox.setEnabled(acq_timeoutCB.isSelected());
			acq_timeoutValBox.setText("" + mmc.getTimeoutMs());
		}
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(BTN_START.equals(ae.getActionCommand())) {
			if(acqThread != null)
				acqThread.interrupt();

			final String devs[];
			final List<String[]> rows;

			List<double[]> ranges = new Vector<double[]>();
			List<String> devsL = new Vector<String>();

			if (acq_xyDevCB.isSelected()) {
				ranges.add(acq_rangeX.getRange());
				ranges.add(acq_rangeY.getRange());
				devsL.add(acq_xyDevCmbo.getSelectedItem().toString());
			}

			if (acq_zDevCB.isSelected()) {
				ranges.add(acq_rangeZ.getRange());
				devsL.add(acq_zDevCmbo.getSelectedItem().toString());
			}

			if (acq_tDevCB.isSelected()) {
				ranges.add(acq_rangeTheta.getRange());
				devsL.add(acq_tDevCmbo.getSelectedItem().toString());
			}

			devs = devsL.toArray(new String[devsL.size()]);

			rows = ProgrammaticAcquisitor.generateRowsFromRanges(mmc, ranges, devs);

			if (acq_timeoutCB.isSelected())
				mmc.setTimeoutMs(Integer.parseInt(acq_timeoutValBox.getText()));

			final int timeSeqs;
			final double timeStep;

			if (acq_timeCB.isSelected()) {
				if (acq_countBox.getText().isEmpty()) {
					JOptionPane.showMessageDialog(frame,
							"Please enter a count or disable timing.");
					acq_countBox.requestFocusInWindow();
					return;
				} else if (acq_stepBox.getText().isEmpty()) {
					JOptionPane.showMessageDialog(frame,
							"Please enter a time step or disable timing.");
					acq_stepBox.requestFocusInWindow();
					return;
				}

				timeSeqs = Integer.parseInt(acq_countBox.getText());
				timeStep = Double.parseDouble(acq_stepBox.getText());
			} else {
				timeSeqs = 1;
				timeStep = 0;
			}

			final AcqParams params = new AcqParams(mmc, devs, rows);
			params.setTimeSeqCount(timeSeqs);
			params.setTimeStepSeconds(timeStep);
			params.setContinuous(continuousCheckbox.isSelected());

			acq_Progress.setEnabled(true);

			params.setProgressListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent ce) {
					ReportingUtils.logMessage("Acq: " + (Double)ce.getSource() * 100D);
					acq_Progress.setValue((int)((Double)ce.getSource() * 100));
				};
			});

			acqThread = new Thread() {
				@Override
				public void run() {
					try {
						ProgrammaticAcquisitor.performAcquisition(params)
								.show();
					} catch (Exception e) {
						JOptionPane.showMessageDialog(frame, "Error acquiring: "
								+ e.getMessage());
						throw new Error("Error acquiring!", e);
					} finally {
						acq_goBtn.setText(BTN_START);
						acq_Progress.setValue(0);
						acq_Progress.setEnabled(false);
					}
				}
			};

			acqThread.start();
			acq_goBtn.setText(BTN_STOP);
		} else if(BTN_STOP.equals(ae.getActionCommand())) {
			try {
				acqThread.interrupt();
				acqThread.join(10000);
			} catch (NullPointerException npe) {
				// Don't care.
			} catch (InterruptedException e1) {
				JOptionPane.showMessageDialog(frame,
						"Couldn't stop the thread gracefully.");
			} finally {
				acqThread = null;

				acq_goBtn.setText(BTN_START);
			}
		}
	}
}
