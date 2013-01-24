package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.TableModel;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.apache.commons.math.geometry.euclidean.threed.Rotation;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

import spim.progacq.AcqParams;
import spim.progacq.AcqRow;
import spim.progacq.AntiDrift;
import spim.progacq.IntensityMeanAntiDrift;
import spim.progacq.ManualAntiDrift;
import spim.progacq.OMETIFFHandler;
import spim.progacq.ProgrammaticAcquisitor;
import spim.progacq.ProjDiffAntiDrift;
import spim.progacq.RangeSlider;
import spim.progacq.StepTableModel;

public class SPIMAcquisition implements MMPlugin, MouseMotionListener, KeyListener, ItemListener, ActionListener {
	private static final String SPIM_RANGES = "SPIM Ranges";
	private static final String POSITION_LIST = "Position List";
	private static final String VIDEO_RECORDER = "Video";
	private static final String BTN_STOP = "Abort!";
	private static final String BTN_START = "Oh Snap!";

	private JButton acqFetchX;
	private JButton acqFetchY;
	private JButton acqFetchZ;
	private JSpinner acqFetchDelta;
	private JButton acqFetchT;
	private JCheckBox acqXYDevCB;
	private JComboBox acqXYDevCmbo;
	private RangeSlider acqRangeX;
	private RangeSlider acqRangeY;
	private JCheckBox acqZDevCB;
	private JComboBox acqZDevCmbo;
	private RangeSlider acqRangeZ;
	private JCheckBox acqTDevCB;
	private JComboBox acqTDevCmbo;
	private RangeSlider acqRangeTheta;
	private JCheckBox acqTimeCB;
	private JTextField acqStepBox;
	private JTextField acqCountBox;
	private JCheckBox acqTimeoutCB;
	private JTextField acqTimeoutValBox;
	private JTextField acqSaveDir;
	private JButton acqGoBtn;
	private Thread acqThread;
	
	private SPIMCalibrator calibration;

	// TODO: read these from the properties
	protected int motorMin = 0, motorMax = 8000,
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
	private final long MOTORS_UPDATE_PERIOD = 500;

	private boolean liveControlsHooked;
	private JTable acqPositionsTable;
	private JCheckBox antiDriftCheckbox;
	private JCheckBox laseStackCheckbox;

	private JProgressBar acqProgress;
	private JTabbedPane acqPosTabs;
	private JLabel estimatesText;

	private final String AD_MODE_MANUAL = "Manual Select";
	private final String AD_MODE_PROJECTIONS = "Projections";
	private final String AD_MODE_INTCENT = "Center of Intensity";

	private final String[] AntiDriftModeNames = {
		AD_MODE_MANUAL, AD_MODE_PROJECTIONS, AD_MODE_INTCENT
	};

	protected JFrame adPane;
	protected JComboBox adModeCmbo;
	protected JCheckBox adAutoThresh;
	protected JTextField adThreshMin;
	protected JTextField adThreshMax;
	protected JTextField adOldWeight;
	protected JTextField adResetMagn;
	protected JCheckBox adAbsolute;
	protected JCheckBox adVisualize;
	protected JComponent adAutoControls;

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
			else if (driver.equals("CoherentCube"))
				laserLabel = label;
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
				try {
					mmc.setXYPosition(xyStageLabel, value, mmc.getYPosition(xyStageLabel));
				} catch (Exception e) {
					IJ.handleException(e);
				}
				maybeUpdateImage();
			}
		};
		limitedXRange = new LimitedRangeCheckbox("Limit range", xSlider, 500, 2500, "range.x");
		ySlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setXYPosition(xyStageLabel, mmc.getXPosition(xyStageLabel), value);
				} catch (Exception e) {
					IJ.handleException(e);
				}
				maybeUpdateImage();
			}
		};
		limitedYRange = new LimitedRangeCheckbox("Limit range", ySlider, 500, 2500, "range.y");
		zSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setPosition(zStageLabel, value);
				} catch (Exception e) {
					IJ.handleException(e);
				}
				maybeUpdateImage();
			}
		};
		limitedZRange = new LimitedRangeCheckbox("Limit range", zSlider, 500, 2500, "range.z");
		rotationSlider = new MappedMotorSlider(twisterMin, twisterMax, 0) {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setPosition(twisterLabel, value);
				} catch (Exception e) {
					IJ.handleException(e);
				}
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
				if(calibration == null) {
					if((ae.getModifiers() & ActionEvent.ALT_MASK) != 0) {
						calibration = new SPIMManualCalibrator(mmc, gui, twisterLabel);
					} else {
						calibration = new SPIMAutoCalibrator(mmc, gui, twisterLabel);
					}
				}
				
				((JFrame)calibration).setVisible(true);
			};
		});

		JButton pixCalibBtn = new JButton("Cal. Pix. Size");
		pixCalibBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				(new PixelSizeCalibrator(mmc, gui)).setVisible(true);
			}
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
		addLine(left, Justification.RIGHT, pixCalibBtn, calibrateButton);

		JPanel stageControls = new JPanel();
		stageControls.setName("Stage Controls");
		stageControls.setLayout(new GridLayout(1, 2));
		stageControls.add(left);

		acqPosTabs = new JTabbedPane();
		
		JPanel importer = new JPanel();
		importer.setLayout(new BoxLayout(importer, BoxLayout.LINE_AXIS));

		acqFetchX = new JButton("X");
		acqFetchX.addActionListener(importStagePosition);
		acqFetchY = new JButton("Y");
		acqFetchY.addActionListener(importStagePosition);
		acqFetchZ = new JButton("Z");
		acqFetchZ.addActionListener(importStagePosition);
		acqFetchT = new JButton("\u03B8");
		acqFetchT.addActionListener(importStagePosition);

		acqFetchDelta = new JSpinner(new SpinnerNumberModel(motorMax / 8, motorMin, motorMax, 10));

		addLine(importer, Justification.RIGHT, "Use current value of ", acqFetchX, acqFetchY, acqFetchZ, acqFetchT, " \u00B1 ", acqFetchDelta);

		JPanel xy = new JPanel();
		xy.setLayout(new BoxLayout(xy, BoxLayout.PAGE_AXIS));
		xy.setBorder(BorderFactory.createTitledBorder("X/Y Stage"));

		JPanel xyDev = new JPanel();
		xyDev.setLayout(new BoxLayout(xyDev, BoxLayout.LINE_AXIS));

		acqXYDevCB = new JCheckBox("");
		acqXYDevCB.addItemListener(this);

		JLabel xyDevLbl = new JLabel("X/Y Stage Device:");
		acqXYDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.XYStageDevice).toArray());
		acqXYDevCmbo.setMaximumSize(acqXYDevCmbo.getPreferredSize());

		xyDev.add(acqXYDevCB);
		xyDev.add(xyDevLbl);
		xyDev.add(acqXYDevCmbo);
		xyDev.add(Box.createHorizontalGlue());

		xy.add(xyDev);

		// These names keep getting more and more convoluted.
		JPanel xyXY = new JPanel();
		xyXY.setLayout(new BoxLayout(xyXY, BoxLayout.PAGE_AXIS));

		JPanel xy_x = new JPanel();
		xy_x.setBorder(BorderFactory.createTitledBorder("Stage X"));

		acqRangeX = new RangeSlider((double)motorMin, (double)motorMax);

		xy_x.add(acqRangeX);
		xy_x.setMaximumSize(xy_x.getPreferredSize());

		xyXY.add(xy_x);

		JPanel xy_y = new JPanel();
		xy_y.setBorder(BorderFactory.createTitledBorder("Stage Y"));

		acqRangeY = new RangeSlider((double)motorMin, (double)motorMax);

		xy_y.add(acqRangeY);
		xy_y.setMaximumSize(xy_y.getPreferredSize());

		xyXY.add(xy_y);

		xy.add(xyXY);
		xy.setMaximumSize(xy.getPreferredSize());

		JPanel z = new JPanel();
		z.setBorder(BorderFactory.createTitledBorder("Stage Z"));
		z.setLayout(new BoxLayout(z, BoxLayout.PAGE_AXIS));

		JPanel zDev = new JPanel();
		zDev.setLayout(new BoxLayout(zDev, BoxLayout.LINE_AXIS));

		acqZDevCB = new JCheckBox("");
		acqZDevCB.addItemListener(this);
		JLabel zDevLbl = new JLabel("Z Stage Device:");
		acqZDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.StageDevice).toArray());
		acqZDevCmbo.setSelectedItem(mmc.getFocusDevice());
		acqZDevCmbo.setMaximumSize(acqZDevCmbo.getPreferredSize());

		zDev.add(acqZDevCB);
		zDev.add(zDevLbl);
		zDev.add(acqZDevCmbo);
		zDev.add(Box.createHorizontalGlue());

		z.add(zDev);

		z.add(Box.createRigidArea(new Dimension(10, 4)));

		acqRangeZ = new RangeSlider((double)motorMin, (double)motorMax);

		z.add(acqRangeZ);
		z.setMaximumSize(z.getPreferredSize());

		JPanel t = new JPanel();
		t.setBorder(BorderFactory.createTitledBorder("Theta"));
		t.setLayout(new BoxLayout(t, BoxLayout.PAGE_AXIS));

		JPanel tDev = new JPanel();
		tDev.setLayout(new BoxLayout(tDev, BoxLayout.LINE_AXIS));

		acqTDevCB = new JCheckBox("");
		acqTDevCB.addItemListener(this);
		JLabel tDevLbl = new JLabel("Theta Device:");
		tDevLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		acqTDevCmbo = new JComboBox(mmc.getLoadedDevicesOfType(
				DeviceType.StageDevice).toArray());
		acqTDevCmbo.setMaximumSize(acqTDevCmbo.getPreferredSize());
		acqTDevCmbo.setSelectedIndex(acqTDevCmbo.getItemCount() - 1);
		acqTDevCmbo.setAlignmentX(Component.LEFT_ALIGNMENT);

		tDev.add(acqTDevCB);
		tDev.add(tDevLbl);
		tDev.add(acqTDevCmbo);
		tDev.add(Box.createHorizontalGlue());

		t.add(tDev);

		t.add(Box.createRigidArea(new Dimension(10, 4)));

		acqRangeTheta = new RangeSlider((double)twisterMin, (double)twisterMax);

		t.add(acqRangeTheta);
		t.setMaximumSize(t.getPreferredSize());

		JPanel acquisition = new JPanel();
		acquisition.setName("Acquisition");
		acquisition.setLayout(new BoxLayout(acquisition, BoxLayout.PAGE_AXIS));

		JPanel acqSPIMTab = (JPanel)LayoutUtils.vertPanel(
			Box.createVerticalGlue(),
			LayoutUtils.horizPanel(
				LayoutUtils.vertPanel(
					Box.createVerticalGlue(),
					xy
				),
				LayoutUtils.vertPanel(
					importer,
					t,
					z
				)
			)
		);
		acqSPIMTab.setName(SPIM_RANGES);

		acqPosTabs.add(SPIM_RANGES, acqSPIMTab);

		JButton acqMarkPos = new JButton("Insert Current Position");
		acqMarkPos.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				StepTableModel model = (StepTableModel)acqPositionsTable.getModel();
				try {
					int idx = model.getRowCount();

					int[] selectedRows = acqPositionsTable.getSelectedRows();
					if(selectedRows.length > 0)
						idx = selectedRows[selectedRows.length - 1];

					model.insertRow(idx,
							new String[] {
							mmc.getXPosition(xyStageLabel) + ", " + 
									mmc.getYPosition(xyStageLabel),
							"" + mmc.getPosition(twisterLabel),
							"" + mmc.getPosition(zStageLabel)
					});
				} catch(Throwable t) {
					JOptionPane.showMessageDialog(acqPositionsTable,
							"Couldn't mark: " + t.getMessage());

					ReportingUtils.logError(t);
				}
			}
		});

		JButton acqMakeSlices = new JButton("Stack at this Z plus:");

		// TODO: Decide good ranges for these...
		final JSpinner acqSliceRange = new JSpinner(new SpinnerNumberModel(50, -1000, 1000, 5));
		acqSliceRange.setMaximumSize(acqSliceRange.getPreferredSize());
		final JSpinner acqSliceStep = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
		acqSliceStep.setMaximumSize(acqSliceStep.getPreferredSize());

		acqMakeSlices.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				StepTableModel model = (StepTableModel)acqPositionsTable.getModel();

				String xy, theta;
				double curz;
				try {
					xy = mmc.getXPosition(xyStageLabel) + ", " + mmc.getYPosition(xyStageLabel);
					curz = mmc.getPosition(zStageLabel);
					theta = "" + mmc.getPosition(twisterLabel);
				} catch(Throwable t) {
					JOptionPane.showMessageDialog((Component)ae.getSource(),
							"Couldn't get current position: " + t.getMessage());

					return;
				}

				int range = (Integer)acqSliceRange.getValue();
				int step = (Integer)acqSliceStep.getValue();

				model.insertRow(new String[] {xy, theta, curz + ":" + step + ":" + (curz + range)});
			}
		});

		JPanel sliceOpts = (JPanel)LayoutUtils.horizPanel(
			acqSliceRange,
			new JLabel(" @ "),
			acqSliceStep,
			new JLabel(" \u03BCm")
		);
		sliceOpts.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton acqRemovePos = new JButton("Delete Selected Rows");
		acqRemovePos.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				StepTableModel model = (StepTableModel)acqPositionsTable.getModel();

				model.removeRows(acqPositionsTable.getSelectedRows());
			}
		});

		JScrollPane tblScroller = new JScrollPane(acqPositionsTable = new JTable());
		tblScroller.setPreferredSize(new Dimension(tblScroller.getSize().width, 128));

		StepTableModel model = new StepTableModel();
		model.setColumns(Arrays.asList(new String[] {"X/Y Stage", "Theta", "Z Stage"}));

		acqPositionsTable.setFillsViewportHeight(true);
		acqPositionsTable.setModel(model);
		acqPositionsTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if(ke.isControlDown() && ke.getKeyCode() == KeyEvent.VK_V) {
					try {
						String data = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null).getTransferData(DataFlavor.stringFlavor);

						String[] lines = data.split("\n");
						for(String line : lines)
							((StepTableModel)acqPositionsTable.getModel()).insertRow(line.split("\t"));
					} catch(Exception e) {
						IJ.handleException(e);
					}
				}
			}
		});
		acqPositionsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent me) {
				if(acqPositionsTable.getSelectedRowCount() == 1 &&
						me.getClickCount() == 2) {
					TableModel mdl = ((JTable)me.getComponent()).getModel();
					int row = ((JTable)me.getComponent()).getSelectedRow();

					String xy = (String) mdl.getValueAt(row, 0);
					String t = (String) mdl.getValueAt(row, 1);
					String zr = (String) mdl.getValueAt(row, 2);

					double x = Double.parseDouble(xy.substring(0,xy.indexOf(',')));
					double y = Double.parseDouble(xy.substring(xy.indexOf(',')+2));
					double td = Double.parseDouble(t);

					double z = 0;
					if(zr.contains(":"))
						z = Double.parseDouble(zr.substring(0,zr.indexOf(':')));
					else if(zr.contains("@"))
						z = Double.parseDouble(zr.substring(0,zr.indexOf('-')));
					else
						z = Double.parseDouble(zr);

					try {
						mmc.setXYPosition(xyStageLabel, x, y);
						mmc.setPosition(zStageLabel, z);
						mmc.setPosition(twisterLabel, td);
					} catch(Throwable thrown) {
						IJ.handleException(thrown);
					}
				}
			}
		});

		JPanel acqTableTab = new JPanel();
		acqTableTab.setLayout(new BoxLayout(acqTableTab, BoxLayout.LINE_AXIS));

		acqTableTab.add(tblScroller);

		JPanel outer = new JPanel();
		outer.setLayout(new BoxLayout(outer, BoxLayout.PAGE_AXIS));

		JPanel controls = new JPanel();
		controls.setLayout(new GridLayout(4,1));

		controls.add(acqMarkPos);
		controls.add(acqRemovePos);
		controls.add(acqMakeSlices);
		controls.add(sliceOpts);

		controls.setMaximumSize(controls.getPreferredSize());

		outer.add(controls);
		outer.add(Box.createVerticalGlue());

		acqTableTab.add(outer);
		acqTableTab.setName(POSITION_LIST);

		acqPosTabs.add(POSITION_LIST, acqTableTab);

		JPanel acqVideoTab = (JPanel) LayoutUtils.vertPanel(
			Box.createVerticalGlue(),
			LayoutUtils.horizPanel(
					Box.createHorizontalGlue(),
					new JLabel("The current position will be used for video capture."),
					Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
					Box.createHorizontalGlue(),
					new JLabel(" "),
					Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
					Box.createHorizontalGlue(),
					new JLabel("You may specify a time limit in the 'Interval' box."),
					Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
					Box.createHorizontalGlue(),
					new JLabel("If you do not, press 'Abort!' to stop recording."),
					Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
					Box.createHorizontalGlue(),
					new JLabel("(The 'Count' box has no effect in video mode.)"),
					Box.createHorizontalGlue()
			),
			Box.createVerticalGlue()
		);
		acqVideoTab.setName(VIDEO_RECORDER);

		acqPosTabs.add(VIDEO_RECORDER, acqVideoTab);

		JPanel estimates = (JPanel)LayoutUtils.horizPanel(
			estimatesText = new JLabel(" Estimates:"),
			Box.createHorizontalGlue()
		);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.PAGE_AXIS));
		right.setBorder(BorderFactory.createTitledBorder("Acquisition"));

		int minlp = 5;
		int deflp = 500;
		int maxlp = 2000;
		String lbl = laserLabel;
		if(lbl == null)
			lbl = mmc.getShutterDevice();

		if(lbl != null) {
			try {
				// Coherent Cube specific properties!
				minlp = (int)(Float.parseFloat(mmc.getProperty(lbl, "Minimum Laser Power")) * 100.0f);
				deflp = (int)(Float.parseFloat(mmc.getProperty(lbl, "PowerSetpoint")) * 100.0f);
				maxlp = (int)(Float.parseFloat(mmc.getProperty(lbl, "Maximum Laser Power")) * 100.0f);
			} catch (Throwable e) {
				ReportingUtils.logError(e);
			}
		}

		laserSlider = new MotorSlider(minlp, maxlp, deflp, "laser.power") {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setProperty(laserLabel, "PowerSetpoint", (float)value / 100.0f);
				} catch (Exception e) {
					ReportingUtils.logError(e);
				}
			}
		};

		int defExposure = 100;
		try {
			defExposure = (int)mmc.getExposure();
		} catch(Exception e) {
			ReportingUtils.logError(e);
		};
		
		// TODO: find out correct values
		exposureSlider = new MotorSlider(10, 1000, defExposure, "exposure") {
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

		final JButton pickDirBtn = new JButton("Browse");

		continuousCheckbox = new JCheckBox("Snap Continously");
		continuousCheckbox.setSelected(false);
		continuousCheckbox.setEnabled(false);
		continuousCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				acqSaveDir.setEnabled(!continuousCheckbox.isSelected());
				pickDirBtn.setEnabled(!continuousCheckbox.isSelected());
			}
		});

		antiDriftCheckbox = new JCheckBox("Use Anti-Drift");
		antiDriftCheckbox.setSelected(false);
		antiDriftCheckbox.setEnabled(true);
		antiDriftCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent ie) {
				if(antiDriftCheckbox.isSelected()) {
					if(SPIMAcquisition.this.adPane == null) {
						JFrame fr = new JFrame("AD Settings");
						fr.setLayout(new BoxLayout(fr.getContentPane(), BoxLayout.PAGE_AXIS));
						
						fr.add(SPIMAcquisition.this.adModeCmbo = new JComboBox(AntiDriftModeNames));
						
						fr.add(SPIMAcquisition.this.adAutoControls = LayoutUtils.titled("Automatic", (JComponent) LayoutUtils.vertPanel(
							SPIMAcquisition.this.adAbsolute = new JCheckBox("Absolute"),
							LayoutUtils.titled("Thresholding", (JComponent) LayoutUtils.vertPanel(
								SPIMAcquisition.this.adAutoThresh = new JCheckBox("Auto"),
								LayoutUtils.labelMe(SPIMAcquisition.this.adThreshMin = new JTextField("350"), "Min:"),
								LayoutUtils.labelMe(SPIMAcquisition.this.adThreshMax = new JTextField("4096"), "Max:")
							)),
							LayoutUtils.labelMe(SPIMAcquisition.this.adOldWeight = new JTextField("0.0"), "Old Weight (0..2):"),
							LayoutUtils.labelMe(SPIMAcquisition.this.adResetMagn = new JTextField("-1"), "Reset Magn.:"),
							SPIMAcquisition.this.adVisualize = new JCheckBox("Visualize")
						)));
						fr.pack();

						adPane = fr;

						adAutoThresh.addItemListener(new ItemListener() {
							@Override
							public void itemStateChanged(ItemEvent ie) {
								adThreshMin.setText("0.1");
								adThreshMax.setText("0.4");
							}
						});

						adModeCmbo.setSelectedItem(AD_MODE_PROJECTIONS);
						adModeCmbo.addItemListener(new ItemListener() {
							@Override
							public void itemStateChanged(ItemEvent ie) {
								adAutoControls.setEnabled(AD_MODE_INTCENT.equals(adModeCmbo.getSelectedItem()));
							}
						});
					}

					SPIMAcquisition.this.adPane.setVisible(true);
				}
			}
		});

		settleTime = new IntegerField(0, "settle.delay") {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};

		laseStackCheckbox = new JCheckBox("Lase Full Stack");
		laseStackCheckbox.setSelected(false);
		laseStackCheckbox.setEnabled(true);

		acqSaveDir = new JTextField(48);
		acqSaveDir.setEnabled(true);

		pickDirBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				JFileChooser fc = new JFileChooser(acqSaveDir.getText());

				fc.setFileSelectionMode(VIDEO_RECORDER.equals(acqPosTabs.getSelectedComponent().getName()) ?
						JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);

				if(fc.showDialog(frame, "Select") == JFileChooser.APPROVE_OPTION)
					acqSaveDir.setText(fc.getSelectedFile().getAbsolutePath());
			};
		});

		addLine(right, Justification.RIGHT, "Laser power (0.01 mW):", laserPower, "Exposure (ms):", exposure);
		addLine(right, Justification.STRETCH, "Laser:", laserSlider);
		addLine(right, Justification.STRETCH, "Exposure:", exposureSlider);
		addLine(right, Justification.RIGHT, speedControl, "Z settle time (ms):", settleTime, continuousCheckbox, antiDriftCheckbox, liveCheckbox, laseStackCheckbox /*registrationCheckbox*/);
		addLine(right, Justification.RIGHT, "Output directory:", acqSaveDir, pickDirBtn);

		JPanel bottom = new JPanel();
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.LINE_AXIS));

		JPanel timeBox = new JPanel();
		timeBox.setLayout(new BoxLayout(timeBox, BoxLayout.LINE_AXIS));
		timeBox.setBorder(BorderFactory.createTitledBorder("Time"));

		acqTimeCB = new JCheckBox("");
		acqTimeCB.setSelected(false);

		JLabel step = new JLabel("Interval (s):");
		step.setToolTipText("Delay between acquisition sequences in milliseconds.");
		acqStepBox = new JTextField(8);
		acqStepBox.setMaximumSize(acqStepBox.getPreferredSize());

		JLabel count = new JLabel("Count:");
		count.setToolTipText("Number of acquisition sequences to perform.");
		acqCountBox = new JTextField(8);
		acqCountBox.setMaximumSize(acqCountBox.getPreferredSize());

		acqTimeCB.addItemListener(this);

		timeBox.add(acqTimeCB);
		timeBox.add(step);
		timeBox.add(acqStepBox);
		timeBox.add(Box.createRigidArea(new Dimension(4, 10)));
		timeBox.add(count);
		timeBox.add(acqCountBox);

		bottom.add(timeBox);

		JPanel timeoutBox = new JPanel();
		timeoutBox.setLayout(new BoxLayout(timeoutBox, BoxLayout.LINE_AXIS));
		timeoutBox
				.setBorder(BorderFactory.createTitledBorder("Device Timeout"));

		acqTimeoutCB = new JCheckBox("Override Timeout:");
		acqTimeoutCB.setHorizontalTextPosition(JCheckBox.RIGHT);
		acqTimeoutCB.addItemListener(this);

		acqTimeoutValBox = new JTextField(8);
		acqTimeoutValBox.setMaximumSize(acqTimeoutValBox.getPreferredSize());

		timeoutBox.add(acqTimeoutCB);
		timeoutBox.add(acqTimeoutValBox);

		bottom.add(timeoutBox);

		JPanel goBtnPnl = new JPanel();
		goBtnPnl.setLayout(new GridLayout(2,1));

		acqGoBtn = new JButton(BTN_START);
		acqGoBtn.addActionListener(this);
		goBtnPnl.add(acqGoBtn);

		acqProgress = new JProgressBar(0, 100);
		acqProgress.setEnabled(false);
		acqProgress.setStringPainted(true);
		acqProgress.setString("Not Acquiring");
		goBtnPnl.add(acqProgress);

		bottom.add(Box.createHorizontalGlue());
		bottom.add(goBtnPnl);

		acqTimeCB.setSelected(false);
		acqCountBox.setEnabled(false);
		acqStepBox.setEnabled(false);

		acqTimeoutCB.setSelected(false);
		acqTimeoutValBox.setEnabled(false);

		acquisition.add(acqPosTabs);
		acquisition.add(estimates);
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
					updateSizeEstimate();
				} catch (Exception e) {
					ReportingUtils.logError(e);
				}
			}
		}, 0, MOTORS_UPDATE_PERIOD);
	}

	private static String[] units = {
		"B", "kB", "MB", "GB", "TB", "PB" // If we get any further than this...
	};

	private static String describeSize(long size) {
		int factor = 0;
		while(size > 1024 && factor < units.length - 1) {
			size /= 1024;
			++factor;
		};

		return size + " " + units[factor];
	}

	private void updateSizeEstimate() {
		// First, determine the number of rows, and estimate the amount of
		// storage required.
		long count, bytesperimg;
		if(SPIM_RANGES.equals(acqPosTabs.getSelectedComponent().getName())) {
			try {
				count = estimateRowCount(getRanges());
			} catch (Exception e) {
				estimatesText.setText("An exception occurred: " + e.getMessage());
				return;
			};
		} else if(POSITION_LIST.equals(acqPosTabs.getSelectedComponent().getName())) {
			count = buildRowsProper(((StepTableModel)acqPositionsTable.getModel()).getRows()).size();
		} else if(VIDEO_RECORDER.equals(acqPosTabs.getSelectedComponent().getName())) {
			estimatesText.setText(" Dataset size depends on how long you record for.");
			return;
		} else {
			estimatesText.setText("What tab are you on? (Please report this.)");
			return;
		}

		if(acqTimeCB.isSelected()) {
			try {
				count *= Long.parseLong(acqCountBox.getText());
			} catch(Exception e) {
			}
		}

		bytesperimg = mmc.getImageHeight()*mmc.getImageWidth()*mmc.getBytesPerPixel() + 2560;

		String s = " Estimates: " + count + " images; " + describeSize(bytesperimg*count);

		if(!"".equals(acqSaveDir.getText())) {
			File f = new File(acqSaveDir.getText());
			if(f.exists()) {
				while(f.getFreeSpace() == 0 && f != null)
					f = f.getParentFile();

				if(f != null && f.exists()) {
					s += " (" + describeSize(f.getFreeSpace()) + " available)";
				} else {
					s += " (error traversing filesystem)";
				}
			} else {
				s += " (unknown available)";
			};
		} else {
			s += " (" + describeSize(ij.IJ.maxMemory() - ij.IJ.currentMemory()) + " available)";
		};

		estimatesText.setText(s);
	};

	protected ActionListener importStagePosition = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent ae) {
			double range = ((Integer)acqFetchDelta.getValue()).doubleValue();
			double value = 0;

			RangeSlider target = null;

			try {
				if(ae.getSource().equals(acqFetchX)) {
					target = acqRangeX;
					value = mmc.getXPosition(xyStageLabel);
				} else if(ae.getSource().equals(acqFetchY)) {
					target = acqRangeY;
					value = mmc.getYPosition(xyStageLabel);
				} else if(ae.getSource().equals(acqFetchZ)) {
					target = acqRangeZ;
					value = mmc.getPosition(zStageLabel);
				} else if(ae.getSource().equals(acqFetchT)) {
					target = acqRangeTheta;
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
			if(ae.getSource().equals(acqFetchT)) {
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

		acqXYDevCB.setSelected(xyStageLabel != null);
		acqZDevCB.setSelected(zStageLabel != null);
		acqTDevCB.setSelected(twisterLabel != null);

		acqXYDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.XYStageDevice).toArray()));
		acqZDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.StageDevice).toArray()));
		acqTDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.StageDevice).toArray()));

		if(xyStageLabel != null)
			acqXYDevCmbo.setSelectedItem(xyStageLabel);
		if(zStageLabel != null)
			acqZDevCmbo.setSelectedItem(zStageLabel);
		if(twisterLabel != null)
			acqTDevCmbo.setSelectedItem(twisterLabel);

		acqXYDevCmbo.setEnabled(xyStageLabel != null);
		acqZDevCmbo.setEnabled(zStageLabel != null);
		acqTDevCmbo.setEnabled(twisterLabel != null);

		acqRangeX.setEnabled(xyStageLabel != null);
		acqRangeY.setEnabled(xyStageLabel != null);
		acqRangeZ.setEnabled(zStageLabel != null);
		acqRangeTheta.setEnabled(twisterLabel != null);

		acqFetchX.setEnabled(xyStageLabel != null);
		acqFetchY.setEnabled(xyStageLabel != null);
		acqFetchZ.setEnabled(zStageLabel != null);
		acqFetchT.setEnabled(twisterLabel != null);

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
			// Make sure we have the property before trying to use it. This
			// prevents an annoying sort-of-loop from happening.
			if(mmc.hasProperty(laserLabel, "PowerSetpoint") && acqThread == null) {
				// I know I'm supposed to use the readback property for power, but
				// this updates the control's displayed value, which should match
				// the desired power setting...
				double psp = Double.parseDouble(mmc.getProperty(laserLabel, "PowerSetpoint"));
				laserSlider.updateValueQuietly((int)(psp * 100.0f));
			}
		}

		if (cameraLabel != null) {
			int exposure = (int)mmc.getExposure();
			exposureSlider.updateValueQuietly(exposure);
		}
	}

	private int mouseStartX = -1;
	private double[] stageStart = new double[4];

	private Vector3D applyCalibratedRotation(Vector3D pos, double dtheta) {
		if(calibration == null || !calibration.getIsCalibrated())
			return pos;

		Vector3D rotOrigin = calibration.getRotationOrigin();
		Vector3D rotAxis = calibration.getRotationAxis();

		ReportingUtils.logMessage("Rotating about axis " + rotAxis.toString() + " at " + rotOrigin.toString());

		// Reverse dtheta; for our twister motor, negative dtheta is CCW, the
		// direction of rotation for commons math (about +k).
		Rotation rot = new Rotation(rotAxis, -dtheta * Math.PI / 100D);

		return rotOrigin.add(rot.applyTo(pos.subtract(rotOrigin)));
	}

	public void mouseDragged(MouseEvent me) {}

	public void mouseMoved(MouseEvent me) {
		// Don't rotate unless they're actively looking at the window.
		if(!gui.getImageWin().isFocused())
			return;

		if(me.isAltDown()) {
			if(mouseStartX < 0) {
				try {
					stageStart[0] = mmc.getXPosition(xyStageLabel);
					stageStart[1] = mmc.getYPosition(xyStageLabel);
					stageStart[2] = mmc.getPosition(zStageLabel);
					stageStart[3] = mmc.getPosition(twisterLabel);
				} catch(Exception e) {
					ReportingUtils.logError(e);
				}
				mouseStartX = me.getX();
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

				mmc.setPosition(twisterLabel, stageStart[3] + delta * 0.1);
			} catch (Exception e) {
				ReportingUtils.logException("Couldn't move stage: ", e);
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
		if (cameraLabel != null && updateLiveImage && !gui.isLiveModeOn()) {
			synchronized(frame) {
				gui.updateImage();
			}
		}
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
		if(ie.getSource().equals(acqXYDevCB)) {
			acqXYDevCmbo.setEnabled(acqXYDevCB.isSelected());
			acqRangeX.setEnabled(acqXYDevCB.isSelected());
			acqRangeY.setEnabled(acqXYDevCB.isSelected());
		} else if(ie.getSource().equals(acqZDevCB)) {
			acqRangeZ.setEnabled(acqZDevCB.isSelected());
			acqZDevCmbo.setEnabled(acqZDevCB.isSelected());
		} else if(ie.getSource().equals(acqTDevCB)) {
			acqRangeTheta.setEnabled(acqTDevCB.isSelected());
			acqTDevCmbo.setEnabled(acqTDevCB.isSelected());
		} else if(ie.getSource().equals(acqTimeCB)) {
			acqCountBox.setEnabled(acqTimeCB.isSelected());
			acqStepBox.setEnabled(acqTimeCB.isSelected());
		} else if(ie.getSource().equals(acqTimeoutCB)) {
			acqTimeoutValBox.setEnabled(acqTimeoutCB.isSelected());
			acqTimeoutValBox.setText("" + mmc.getTimeoutMs());
		}
	}

	private double[][] getRanges() throws Exception {
		double[][] ranges = new double[][] {
				acqRangeX.getRange(),
				acqRangeY.getRange(),
				acqRangeTheta.getRange(),
				acqRangeZ.getRange()
		};

		if(!acqXYDevCB.isSelected()) {
			ranges[0][2] = ranges[0][0] = mmc.getXPosition(xyStageLabel);
			ranges[1][2] = ranges[1][0] = mmc.getYPosition(xyStageLabel);
		};

		if(!acqTDevCB.isSelected())
			ranges[2][2] = ranges[2][0] = mmc.getPosition(twisterLabel);

		if(!acqZDevCB.isSelected())
			ranges[3][2] = ranges[3][0] = mmc.getPosition(zStageLabel);

		return ranges;
	};

	private int estimateRowCount(double[][] ranges) {
		return (int)(((ranges[0][2] - ranges[0][0])/ranges[0][1] + 1) *
				 ((ranges[1][2] - ranges[1][0])/ranges[1][1] + 1) *
				 ((ranges[2][2] - ranges[2][0])/ranges[2][1] + 1) *
				 ((ranges[3][2] - ranges[3][0])/ranges[3][1] + 1));
	}

	private AcqRow[] getBuiltRows() throws Exception {
		List<AcqRow> rows = new ArrayList<AcqRow>();

		if(SPIM_RANGES.equals(acqPosTabs.getSelectedComponent().getName())) {
			double currentRot = mmc.getPosition(twisterLabel);

			double[][] ranges = getRanges();

			for(double x = ranges[0][0]; x <= ranges[0][2]; x += ranges[0][1]) {
				for(double y = ranges[1][0]; y <= ranges[1][2]; y += ranges[1][1]) {
					for(double t = ranges[2][0]; t <= ranges[2][2]; t += ranges[2][1]) {
						Vector3D basev = new Vector3D(x, y, (ranges[3][0] + ranges[3][2]) / 2);

						// Apply the transformation required to rotate to the
						// target angle from the angle at the start of
						// acquisition.
						basev = applyCalibratedRotation(basev, t - currentRot);

						String z;

//						if(continuousCheckbox.isSelected())
//							z = ranges[3][0] + "-" + ranges[3][2] + "@10";
//						else
							z = ranges[3][0] + ":" + ranges[3][1] + ":" + ranges[3][2];

						rows.add(new AcqRow(new String[] {basev.getX() + ", " + basev.getY(), "" + t}, zStageLabel, z));
					}
				}
			}
		} else if(POSITION_LIST.equals(acqPosTabs.getSelectedComponent().getName())) {
			for(String[] row : ((StepTableModel)acqPositionsTable.getModel()).getRows()) {
				if(row[0].equals(ProgrammaticAcquisitor.STACK_DIVIDER))
					continue;

				rows.add(new AcqRow(Arrays.copyOfRange(row, 0, row.length - 1), zStageLabel, row[row.length - 1]));
			}
		}

		return rows.toArray(new AcqRow[rows.size()]);
	}

	public List<String[]> buildRowsProper(List<String[]> model) {
		List<String[]> out = new LinkedList<String[]>();

		for(String[] row : model) {
			if(row[2].contains(":")) {
				double start = Double.parseDouble(row[2].substring(0,row[2].indexOf(":")));
				double step = Double.parseDouble(row[2].substring(row[2].indexOf(":")+1,row[2].lastIndexOf(":")));
				double end = Double.parseDouble(row[2].substring(row[2].lastIndexOf(":")+1));

				if(start < end)
					for(double z = start; z < end; z += step)
						out.add(new String[] {row[0], row[1], "" + z});
				else if(end < start)
					for(double z = start; z > end; z -= step)
						out.add(new String[] {row[0], row[1], "" + z});
				else
					out.add(new String[] {row[0], row[1], "" + start});

			} else {
				out.add(row);
			}

			out.add(new String[] {ProgrammaticAcquisitor.STACK_DIVIDER,
					ProgrammaticAcquisitor.STACK_DIVIDER,
					ProgrammaticAcquisitor.STACK_DIVIDER});

		}

		return out;
	}

	private static void appendNext(CMMCore mmc, File saveFile, double bt) throws Exception {
		mmcorej.TaggedImage TI = mmc.popNextTaggedImage();

		ImageProcessor IP = ImageUtils.makeProcessor(TI);
		
		double t = System.nanoTime() / 1e9 - bt;

		ImagePlus img = null;
		ImageStack stck = new ImageStack(IP.getWidth(), IP.getHeight());
		IP.drawString(String.format("t = %.3fs", t), 8, 20);
		stck.addSlice("" + t, IP);
		img = new ImagePlus("Video", stck);

		img.setProperty("Info", img.getProperty("Info") + "\n" + TI.tags.toString());
		IJ.save(img, new File(saveFile, t + ".tiff").getAbsolutePath());
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(BTN_START.equals(ae.getActionCommand())) {
			if(acqThread != null)
				acqThread.interrupt();

			if(VIDEO_RECORDER.equals(acqPosTabs.getSelectedComponent().getName())) {
				if("".equals(acqSaveDir.getText())) {
					JOptionPane.showMessageDialog(null, "Please specify an output file.");
					return;
				}

				File tmpSaveFile = new File(acqSaveDir.getText());

				if(tmpSaveFile.exists() && tmpSaveFile.isDirectory())  {
					Date now = Calendar.getInstance().getTime();
					SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy HH.mm");
					
					tmpSaveFile = new File(tmpSaveFile, format.format(now) + ".tiff");
				}

				if(tmpSaveFile.exists()) {
					if(!tmpSaveFile.canWrite()) {
						JOptionPane.showMessageDialog(null, "Can't overwrite selected file. Please choose a new output file.");
						return;
					}

					int res = JOptionPane.showConfirmDialog(null, "Overwrite \"" + tmpSaveFile.getName() + "\"?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
					if(res != JOptionPane.YES_OPTION)
						return;
				}

				final File saveFile = tmpSaveFile;

				final File saveDir;
				try {
					saveDir = File.createTempFile("vid", "dir");
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, "Couldn't create temporary directory.");
					return;
				}

				// Weird: mkdir once returned false though it was successful...
				if(!saveDir.delete() || !saveDir.mkdir()) {
					JOptionPane.showMessageDialog(null, "Couldn't create temporary directory.");
					return;
				}

				final double recordFor = acqTimeCB.isSelected() ? Double.parseDouble(acqStepBox.getText()) : -1;

				acqThread = new Thread() {
					@Override
					public void run() {
						try {
							boolean live;
							if(live = gui.isLiveModeOn())
								gui.enableLiveMode(false);

							double beginTime = System.nanoTime() / 1e9;
							double endTime = recordFor > 0 ? beginTime + recordFor : -1;

							mmc.clearCircularBuffer();
							mmc.startContinuousSequenceAcquisition(0);

							while(!Thread.interrupted() && (endTime < 0 || (System.nanoTime() / 1e9) < endTime)) {
								if (mmc.getRemainingImageCount() == 0) {
									Thread.yield();
									continue;
								};

								appendNext(mmc, saveDir, beginTime);
							}

							mmc.stopSequenceAcquisition();

							if(live)
								gui.enableLiveMode(true);

							ReportingUtils.logMessage("Video stopped; finishing individual file saving...");

							while(mmc.getRemainingImageCount() != 0)
								appendNext(mmc, saveDir, beginTime);

							ReportingUtils.logMessage("Condensing individual files...");

							ImagePlus fij = new ImagePlus();
							VirtualStack stck = new VirtualStack((int) mmc.getImageWidth(), (int) mmc.getImageHeight(), null, saveDir.getAbsolutePath());

							File[] files = saveDir.listFiles();
							Arrays.sort(files, new Comparator<File>() {
								@Override
								public int compare(File f1, File f2) {
									String n1 = f1.getName();
									String n2 = f2.getName();
									
									double d1 = Double.parseDouble(n1.substring(0, n1.length() - 5));
									double d2 = Double.parseDouble(n2.substring(0, n2.length() - 5));

									return Double.compare(d1, d2);
								}
							});

							String infoStr = "Timepoints:\n";

							for(File f : files) {
								ReportingUtils.logMessage(" Adding file " + f.getName() + "...");
								stck.addSlice(f.getName());
								infoStr += f.getName() + "\n";
							}

							fij.setProperty("Info", infoStr);
							fij.setStack(stck);
							fij.setFileInfo(new ij.io.FileInfo());
							IJ.save(fij, saveFile.getAbsolutePath());

							for(File f : files)
								if(!f.delete())
									throw new Exception("Couldn't delete temporary image " + f.getName());

							if(!saveDir.delete())
								throw new Exception("Couldn't delete temporary directory " + saveDir.getAbsolutePath());
						} catch(Throwable t) {
							JOptionPane.showMessageDialog(null, "Error during acquisition: " + t.getMessage());
							t.printStackTrace();
						} finally {
							acqGoBtn.setText(BTN_START);
							acqProgress.setValue(0);
							acqProgress.setEnabled(false);
						};
					}
				};
			} else {
				final String devs[] = {xyStageLabel, twisterLabel, zStageLabel};
				final AcqRow[] acqRows;

				try {
					 acqRows = getBuiltRows();
				} catch(Exception e) {
					JOptionPane.showMessageDialog(frame, "Error: " + e.toString());
					return;
				}

				if (acqTimeoutCB.isSelected())
					mmc.setTimeoutMs(Integer.parseInt(acqTimeoutValBox.getText()));

				final int timeSeqs;
				final double timeStep;

				if (acqTimeCB.isSelected()) {
					if (acqCountBox.getText().isEmpty()) {
						JOptionPane.showMessageDialog(frame,
								"Please enter a count or disable timing.");
						acqCountBox.requestFocusInWindow();
						return;
					} else if (acqStepBox.getText().isEmpty()) {
						JOptionPane.showMessageDialog(frame,
								"Please enter a time step or disable timing.");
						acqStepBox.requestFocusInWindow();
						return;
					}

					timeSeqs = Integer.parseInt(acqCountBox.getText());
					timeStep = Double.parseDouble(acqStepBox.getText());
				} else {
					timeSeqs = 1;
					timeStep = 0;
				}

				final AcqParams params = new AcqParams(mmc, devs, acqRows);
				params.setTimeSeqCount(timeSeqs);
				params.setTimeStepSeconds(timeStep);
				params.setContinuous(continuousCheckbox.isSelected());

				HashMap<String, String> nameMap = new HashMap<String, String>(3);
				nameMap.put(xyStageLabel, "XY");
				nameMap.put(twisterLabel, "Ang");
				nameMap.put(zStageLabel, "Z");

				final File output;
				if(!continuousCheckbox.isSelected() && !"".equals(acqSaveDir.getText())) {
					output = new File(acqSaveDir.getText());

					if(!output.isDirectory()) {
						JOptionPane.showMessageDialog(null, "You must specify a directory.");
						return;
					}

					if(output.list().length != 0) {
						int res = JOptionPane.showConfirmDialog(null, "The destination directory is not empty. Save here anyway?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
						if(res == JOptionPane.NO_OPTION)
							return;

						for(File f : output.listFiles())
							if(!f.delete())
								if(JOptionPane.showConfirmDialog(null, "Couldn't clean destination directory (" + f.getName() + "). Continue anyway?", "Confirm Append", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
									return;
					}

					params.setOutputHandler(new OMETIFFHandler(
						mmc, output, xyStageLabel, twisterLabel, zStageLabel, "t",
						acqRows, timeSeqs, timeStep
					));
				} else {
					output = null;
				}

				if(antiDriftCheckbox.isSelected()) {
					AntiDrift.Factory f = null;

					if(AD_MODE_MANUAL.equals(adModeCmbo.getSelectedItem())) {
						f = new AntiDrift.Factory() {
							@Override
							public AntiDrift manufacture(AcqParams p, AcqRow r) {
								return new ManualAntiDrift(p, r);
							}
						};
					} else if(AD_MODE_PROJECTIONS.equals(adModeCmbo.getSelectedItem())) {
						f = new AntiDrift.Factory() {
							@Override
							public AntiDrift manufacture(AcqParams p, AcqRow r) {
								return new ProjDiffAntiDrift(output, r);
							}
						};
					} else if(AD_MODE_INTCENT.equals(adModeCmbo.getSelectedItem())) {
						final double[] adparams = new double[7];

						adparams[0] = (adAutoThresh.isSelected() ? 1 : -1);
						adparams[1] = Double.parseDouble(adThreshMin.getText());
						adparams[2] = Double.parseDouble(adThreshMax.getText());
						adparams[3] = Double.parseDouble(adOldWeight.getText());
						adparams[4] = Double.parseDouble(adResetMagn.getText());
						adparams[5] = (adAbsolute.isSelected() ? 1 : -1);
						adparams[6] = (adVisualize.isSelected() ? 1 : -1);

						f = new AntiDrift.Factory() {
							@Override
							public AntiDrift manufacture(AcqParams p, AcqRow r) {
								return new IntensityMeanAntiDrift(adparams, r.getEndPosition() - r.getStartPosition());
							}
						};
					}
					
					params.setAntiDrift(f);
				}

				params.setUpdateLive(liveCheckbox.isSelected());
				params.setIllumFullStack(laseStackCheckbox.isSelected());
				params.setSettleDelay(settleTime.getValue());

				acqProgress.setEnabled(true);

				params.setProgressListener(new ProgrammaticAcquisitor.AcqProgressCallback() {
					@Override
					public void reportProgress(int tp, int row, double overall) {
						acqProgress.setString(String.format("%.02f%%: T %d \u03B8 %d", overall*100, tp+1, row+1));
						acqProgress.setValue((int)(overall * 100));
					}
				});

				if(output != null) {
					String log = new File(output, "log.txt").getAbsolutePath();
					System.setProperty("ij.log.file", log);
					ij.IJ.log("Opened log file " + log);
				}

				acqThread = new Thread() {
					@Override
					public void run() {
						try {
							ImagePlus img = ProgrammaticAcquisitor.performAcquisition(params);

							if(img != null)
								img.show();
						} catch (Exception e) {
							e.printStackTrace();
							JOptionPane.showMessageDialog(frame, "Error acquiring: "
									+ e.getMessage());
							throw new Error("Error acquiring!", e);
						}

						acqThread = null;
						acqGoBtn.setText(BTN_START);

						acqProgress.setString("Not Acquiring");
						acqProgress.setValue(0);
						acqProgress.repaint();
					}
				};
			}

			acqThread.start();
			acqGoBtn.setText(BTN_STOP);
		} else if(BTN_STOP.equals(ae.getActionCommand())) {
			try {
				acqThread.interrupt();
				acqThread.join(30000);
			} catch (NullPointerException npe) {
				// Don't care.
			} catch (InterruptedException e1) {
				JOptionPane.showMessageDialog(frame,
						"Couldn't stop the thread gracefully.");
			}

			acqThread = null;

			acqGoBtn.setText(BTN_START);

			acqProgress.setString("Not Acquiring");
			acqProgress.setValue(0);
			acqProgress.repaint();
		}
	}
}
