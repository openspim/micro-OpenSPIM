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
import java.awt.Point;
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
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
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

import spim.setup.DeviceManager;
import spim.setup.SPIMSetup;
import spim.setup.SPIMSetup.SPIMDevice;
import spim.setup.Stage;

import spim.progacq.AcqOutputHandler;
import spim.progacq.AcqParams;
import spim.progacq.AcqRow;
import spim.progacq.AntiDrift;
import spim.progacq.AsyncOutputWrapper;
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
	protected double motorMin = 0, motorMax = 9000, motorStep = 1.5,
		twisterMin = -180, twisterMax = 180, twisterStep = 2;
	protected static int STAGE_OPTIONS = SteppedSlider.INCREMENT_BUTTONS | SteppedSlider.CLAMP_VALUE | SteppedSlider.RANGE_LIMITS;

	protected ScriptInterface app;
	protected CMMCore mmc;
	protected MMStudioMainFrame gui;

	protected SPIMSetup setup;
	protected DeviceManager devMgr;

	protected JFrame frame;
	protected JSpinner settleTime;
	protected JTextField xPosition, yPosition, zPosition, rotation, laserPower, exposure;
	protected SteppedSlider xSlider, ySlider, zSlider, rotationSlider, laserSlider, exposureSlider;
	protected JCheckBox liveCheckbox, registrationCheckbox, continuousCheckbox;
	protected JButton speedControl, ohSnap;
	protected JFrame acqOptionsFrame;
	protected JCheckBox asyncMonitorCheckbox, acqProfileCheckbox;

	protected boolean updateLiveImage;

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

	private JCheckBox asyncCheckbox;

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

		setup = SPIMSetup.createDefaultSetup(mmc);

		if(!setup.isMinimalSPIM())
			JOptionPane.showMessageDialog(frame, "Your setup appears to be invalid. Please make sure you have a camera, laser, and 4D stage set up.\nYou may need to restart Micro-Manager for the OpenSPIM plugin to detect a correct setup.");

		ensurePixelResolution();
		initUI();
		configurationChanged();

		if(!gui.isLiveModeOn() && setup.isConnected(SPIMDevice.CAMERA1));
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
/*		zStageLabel = null;
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
		cameraLabel = mmc.getCameraDevice();*/

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

		xSlider = makeStageSlider(setup, SPIMDevice.STAGE_X, motorMin, motorMax, motorStep, STAGE_OPTIONS);
		xPosition = xSlider.getValueBox();

		ySlider = makeStageSlider(setup, SPIMDevice.STAGE_Y, motorMin, motorMax, motorStep, STAGE_OPTIONS);
		yPosition = ySlider.getValueBox();

		zSlider = makeStageSlider(setup, SPIMDevice.STAGE_Z, motorMin, motorMax, motorStep, STAGE_OPTIONS);
		zPosition = zSlider.getValueBox();

		rotationSlider = makeStageSlider(setup, SPIMDevice.STAGE_THETA, twisterMin, twisterMax, twisterStep, SteppedSlider.INCREMENT_BUTTONS);
		rotation = rotationSlider.getValueBox();

		JButton calibrateButton = new JButton("Calibrate...");
		calibrateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(calibration == null) {
					if((ae.getModifiers() & ActionEvent.ALT_MASK) != 0) {
						calibration = new SPIMManualCalibrator(mmc, gui, setup);
					} else {
						calibration = new SPIMAutoCalibrator(mmc, gui, setup);
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

		JButton devMgrBtn = new JButton("Manage Devices");
		devMgrBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(devMgr == null)
					devMgr = new DeviceManager(setup);

				devMgr.setVisible(true);
			}
		});

		addLine(left, Justification.LEFT, "x:", xPosition, "y:", yPosition, "z:", zPosition, "angle:", rotation);
		addLine(left, Justification.STRETCH, xSlider);
		left.add(Box.createVerticalStrut(8));
		addLine(left, Justification.STRETCH, ySlider);
		left.add(Box.createVerticalStrut(8));
		addLine(left, Justification.STRETCH, zSlider);
		left.add(Box.createVerticalStrut(8));
		addLine(left, Justification.STRETCH, rotationSlider);

		JPanel stageControls = new JPanel();
		stageControls.setName("Stage Controls");
		stageControls.setLayout(new BoxLayout(stageControls, BoxLayout.PAGE_AXIS));
		stageControls.add(left);
		stageControls.add(Box.createVerticalStrut(200));
		addLine(stageControls, Justification.RIGHT, devMgrBtn, pixCalibBtn, calibrateButton);

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

		acqRangeX = new RangeSlider(xSlider.getMinimum(), xSlider.getMaximum());

		xy_x.add(acqRangeX);
		xy_x.setMaximumSize(xy_x.getPreferredSize());

		xyXY.add(xy_x);

		JPanel xy_y = new JPanel();
		xy_y.setBorder(BorderFactory.createTitledBorder("Stage Y"));

		acqRangeY = new RangeSlider(ySlider.getMinimum(), ySlider.getMaximum());

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

		acqRangeZ = new RangeSlider(zSlider.getMinimum(), zSlider.getMaximum());

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

		acqRangeTheta = new RangeSlider(rotationSlider.getMinimum(), rotationSlider.getMaximum());

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

					Vector3D pos = setup.getPosition();

					model.insertRow(idx,
						new Object[] {
							"" + pos.getX(),
							"" + pos.getY(),
							"" + setup.getAngle(),
							"" + pos.getZ()
						}
					);
				} catch(Throwable t) {
					JOptionPane.showMessageDialog(acqPositionsTable,
							"Couldn't mark: " + t.getMessage());

					ReportingUtils.logError(t);
				}
			}
		});

		JButton acqMakeSlices = new JButton("Stack at this Z plus:");

		// TODO: Decide good ranges for these...
		double zstep = (setup.getZStage() != null ? setup.getZStage().getStepSize() : 1);

		final JSpinner acqSliceRange = new JSpinner(new SpinnerNumberModel(zstep*50, zstep*-1000, zstep*1000, zstep));
		acqSliceRange.setMaximumSize(acqSliceRange.getPreferredSize());
		final JSpinner acqSliceStep = new JSpinner(new SpinnerNumberModel(zstep, zstep, zstep*100, zstep));
		acqSliceStep.setMaximumSize(acqSliceStep.getPreferredSize());

		acqMakeSlices.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				StepTableModel model = (StepTableModel)acqPositionsTable.getModel();

				Vector3D xyz = setup.getPosition();
				double theta = setup.getAngle();

				double range = (Double)acqSliceRange.getValue();
				double step = (Double)acqSliceStep.getValue();

				model.insertRow(xyz.getX(), xyz.getY(), theta, String.format("%.3f:%.3f:%.3f", xyz.getZ(), step, (xyz.getZ() + range)));
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
		tblScroller.setPreferredSize(new Dimension(tblScroller.getSize().width, 256));

		StepTableModel model = new StepTableModel(SPIMDevice.STAGE_X, SPIMDevice.STAGE_Y, SPIMDevice.STAGE_THETA, SPIMDevice.STAGE_Z);

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
							((StepTableModel)acqPositionsTable.getModel()).insertRow((Object[]) line.split("\t"));
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

					String x = (String) mdl.getValueAt(row, 0);
					String y = (String) mdl.getValueAt(row, 1);
					String t = (String) mdl.getValueAt(row, 2);
					String zr = (String) mdl.getValueAt(row, 3);

					double xd = Double.parseDouble(x);
					double yd = Double.parseDouble(y);
					double td = Double.parseDouble(t);

					double z = 0;
					if(zr.contains(":"))
						z = Double.parseDouble(zr.substring(0,zr.indexOf(':')));
					else if(zr.contains("@"))
						z = Double.parseDouble(zr.substring(0,zr.indexOf('-')));
					else
						z = Double.parseDouble(zr);

					setup.setPosition(xd, yd, z, td);
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

		int minlp = (int) ((setup.getLaser() != null ? setup.getLaser().getMinPower() : 0.001) * 1000);
		int deflp = (int) ((setup.getLaser() != null ? setup.getLaser().getPower() : 1) * 1000);
		int maxlp = (int) ((setup.getLaser() != null ? setup.getLaser().getMaxPower() : 1) * 1000);

		laserSlider = new SteppedSlider("Laser Power:", minlp, maxlp, 1, deflp, SteppedSlider.LABEL_LEFT | SteppedSlider.INCREMENT_BUTTONS) {
			@Override
			public void valueChanged() {
				try {
					setup.getLaser().setPower(getValue() / 1000D); // laser api is in W
				} catch (Exception e) {
					ReportingUtils.logError(e);
				}
			}
		};

		double defExposure = 100;
		try {
			defExposure = Math.min(1000, Math.max(10, mmc.getExposure()));
		} catch(Exception e) {
			ReportingUtils.logError(e);
		};

		// TODO: find out correct values
		exposureSlider = new SteppedSlider("Exposure:", 10, 1000, 1, defExposure, SteppedSlider.LABEL_LEFT | SteppedSlider.INCREMENT_BUTTONS) {
			@Override
			public void valueChanged() {
				try {
					mmc.setExposure(getValue());
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};

		laserPower = laserSlider.getValueBox();
		exposure = exposureSlider.getValueBox();

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

		registrationCheckbox = new JCheckBox(/*"Perform SPIM registration"*/);
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

		continuousCheckbox = new JCheckBox(/*"Snap Continously"*/);
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

		settleTime = new JSpinner(new SpinnerNumberModel(10, 0, 1000, 1));

		laseStackCheckbox = new JCheckBox("Lase Full Stack");
		laseStackCheckbox.setSelected(false);
		laseStackCheckbox.setEnabled(true);

		acqSaveDir = new JTextField(48);
		acqSaveDir.setEnabled(true);

		asyncCheckbox = new JCheckBox("Asynchronous Output");
		asyncCheckbox.setSelected(true);
		asyncCheckbox.setEnabled(true);
		asyncCheckbox.setToolTipText("If checked, captured images will be buffered and written as time permits. This speeds up acquisition. Currently only applies if an output directory is specified.");

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

		addLine(right, Justification.RIGHT, "Laser power (mW):", laserPower, "Exposure (ms):", exposure);
		addLine(right, Justification.STRETCH, laserSlider);
		addLine(right, Justification.STRETCH, exposureSlider);
		addLine(right, Justification.RIGHT, speedControl, antiDriftCheckbox, liveCheckbox, laseStackCheckbox);
		addLine(right, Justification.RIGHT, "Output directory:", acqSaveDir, pickDirBtn, asyncCheckbox);

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

		asyncMonitorCheckbox = new JCheckBox(/*"Monitor Async Output"*/);

		acqOptionsFrame = new JFrame("Acquisition Options");
		JPanel optsPanel = LayoutUtils.form(
				"Z settle time (ms):", settleTime,
				"Continuous Mode:", continuousCheckbox,
				"SPIM Registration:", registrationCheckbox,
				"Monitor Async Output:", asyncMonitorCheckbox,
				"Profile Acquisition:", acqProfileCheckbox
		);
		optsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		acqOptionsFrame.add(optsPanel);
		acqOptionsFrame.pack();

		JButton showMoreOptions = new JButton("Show Dialog");
		showMoreOptions.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				Point screen = ((Component) ae.getSource()).getLocationOnScreen();
				int w = ((Component) ae.getSource()).getWidth();
				int h = ((Component) ae.getSource()).getHeight();
				acqOptionsFrame.setVisible(true);
				acqOptionsFrame.setLocation(screen.x + w/2 - acqOptionsFrame.getWidth()/2, screen.y + h);
			}
		});

		bottom.add(LayoutUtils.horizPanel("More Options", showMoreOptions));

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

	@SuppressWarnings("serial")
	private static SteppedSlider makeStageSlider(final SPIMSetup setup, final SPIMDevice dev, double min, double max, double step, int options) {
		if(setup.getDevice(dev) != null && !(setup.getDevice(dev) instanceof Stage))
			throw new IllegalArgumentException("makeStageSliderSafe given a non-Stage device");

		Stage stage = (Stage) setup.getDevice(dev);

		min = stage != null ? stage.getMinPosition() : min;
		max = stage != null ? stage.getMaxPosition() : max;
		step = stage != null ? stage.getStepSize() : step;
		double def = stage != null ? stage.getPosition() : 0;

		return new SteppedSlider(dev.getText(), min, max, step, def, options) {
			@Override
			public void valueChanged() {
				((Stage)setup.getDevice(dev)).setPosition(getValue());
			}
		};
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
		long count = 0, bytesperimg;
		if(SPIM_RANGES.equals(acqPosTabs.getSelectedComponent().getName())) {
			try {
				count = estimateRowCount(getRanges());
			} catch (Exception e) {
				estimatesText.setText("An exception occurred: " + e.getMessage());
				return;
			};
		} else if(POSITION_LIST.equals(acqPosTabs.getSelectedComponent().getName())) {
			for(AcqRow row : ((StepTableModel)acqPositionsTable.getModel()))
				count += row.getDepth();
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
					value = setup.getXStage().getPosition();
				} else if(ae.getSource().equals(acqFetchY)) {
					target = acqRangeY;
					value = setup.getYStage().getPosition();
				} else if(ae.getSource().equals(acqFetchZ)) {
					target = acqRangeZ;
					value = setup.getZStage().getPosition();
				} else if(ae.getSource().equals(acqFetchT)) {
					target = acqRangeTheta;
					value = setup.getAngle();
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
				min = Math.max(min, twisterMin);
				max = Math.min(max, twisterMax);
			} else {
				min = Math.max(min, motorMin);
				max = Math.min(max, motorMax);
			};

			target.setMinMax(min, max);
		};
	};

	protected void updateUI() {
		String xStageLabel = setup.getXStage() != null ? setup.getXStage().getLabel() : null;
		String yStageLabel = setup.getYStage() != null ? setup.getYStage().getLabel() : null;
		String zStageLabel = setup.getZStage() != null ? setup.getZStage().getLabel() : null;
		String twisterLabel = setup.getThetaStage() != null ? setup.getThetaStage().getLabel() : null;
		String laserLabel = setup.getLaser() != null ? setup.getLaser().getLabel() : null;
		String cameraLabel = setup.getCamera() != null ? setup.getCamera().getLabel() : null;

		xPosition.setEnabled(acqThread == null && xStageLabel != null);
		yPosition.setEnabled(acqThread == null && yStageLabel != null);
		zPosition.setEnabled(acqThread == null && zStageLabel != null);
		rotation.setEnabled(acqThread == null && twisterLabel != null);

		xSlider.setEnabled(acqThread == null && xStageLabel != null);
		ySlider.setEnabled(acqThread == null && yStageLabel != null);
		zSlider.setEnabled(acqThread == null && zStageLabel != null);
		rotationSlider.setEnabled(acqThread == null && twisterLabel != null);

		laserPower.setEnabled(acqThread == null && laserLabel != null);
		exposure.setEnabled(acqThread == null && cameraLabel != null);
		laserSlider.setEnabled(acqThread == null && laserLabel != null);
		exposureSlider.setEnabled(acqThread == null && cameraLabel != null);
		liveCheckbox.setEnabled(acqThread == null && cameraLabel != null);
		speedControl.setEnabled(acqThread == null && zStageLabel != null && setup.getZStage().getAllowedVelocities().size() > 1);
		continuousCheckbox.setEnabled(acqThread == null && zStageLabel != null && cameraLabel != null);
		settleTime.setEnabled(acqThread == null && zStageLabel != null);

		acqXYDevCB.setSelected(xStageLabel != null && yStageLabel != null);
		acqZDevCB.setSelected(zStageLabel != null);
		acqTDevCB.setSelected(twisterLabel != null);

		acqXYDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.XYStageDevice).toArray()));
		acqZDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.StageDevice).toArray()));
		acqTDevCmbo.setModel(new DefaultComboBoxModel(
				mmc.getLoadedDevicesOfType(DeviceType.StageDevice).toArray()));

		if(mmc.getXYStageDevice() != null)	// TODO: fixme or delete this tab outright...
			acqXYDevCmbo.setSelectedItem(mmc.getXYStageDevice());
		if(zStageLabel != null)
			acqZDevCmbo.setSelectedItem(zStageLabel);
		if(twisterLabel != null)
			acqTDevCmbo.setSelectedItem(twisterLabel);

		acqXYDevCmbo.setEnabled(xStageLabel != null && yStageLabel != null);
		acqZDevCmbo.setEnabled(zStageLabel != null);
		acqTDevCmbo.setEnabled(twisterLabel != null);

		acqRangeX.setEnabled(xStageLabel != null);
		acqRangeY.setEnabled(yStageLabel != null);
		acqRangeZ.setEnabled(zStageLabel != null);
		acqRangeTheta.setEnabled(twisterLabel != null);

		acqFetchX.setEnabled(xStageLabel != null);
		acqFetchY.setEnabled(yStageLabel != null);
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
		xSlider.setEnabled(setup.isConnected(SPIMDevice.STAGE_X));
		ySlider.setEnabled(setup.isConnected(SPIMDevice.STAGE_Y));
		zSlider.setEnabled(setup.isConnected(SPIMDevice.STAGE_Z));
		rotationSlider.setEnabled(setup.isConnected(SPIMDevice.STAGE_THETA));
		laserSlider.setEnabled(setup.isConnected(SPIMDevice.LASER1));
		exposureSlider.setEnabled(setup.isConnected(SPIMDevice.CAMERA1));

		if (xSlider.isEnabled())
			xSlider.trySetValue(setup.getXStage().getPosition(), false);

		if (ySlider.isEnabled())
			ySlider.trySetValue(setup.getYStage().getPosition(), false);

		if (zSlider.isEnabled())
			zSlider.trySetValue(setup.getZStage().getPosition(), false);

		if (rotationSlider.isEnabled())
			rotationSlider.trySetValue(setup.getAngle(), false);

		if (laserSlider.isEnabled())
			laserSlider.trySetValue(setup.getLaser().getPower() * 1000D, false);

		if (exposureSlider.isEnabled())
			exposureSlider.trySetValue(mmc.getExposure(), false);
	}

	private int mouseStartX = -1;
	private double[] stageStart = new double[4];

	private Vector3D applyCalibratedRotation(Vector3D pos, double dtheta) {
		if(calibration == null || !calibration.getIsCalibrated())
			return pos;

		Vector3D rotOrigin = calibration.getRotationOrigin();
		Vector3D rotAxis = calibration.getRotationAxis();

		// Reverse dtheta; for our twister motor, negative dtheta is CCW, the
		// direction of rotation for commons math (about +j).
		Rotation rot = new Rotation(rotAxis, -dtheta * Math.PI / 180D);

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
					Vector3D xyz = setup.getPosition();
					stageStart[0] = xyz.getX();
					stageStart[1] = xyz.getY();
					stageStart[2] = xyz.getZ();
					stageStart[3] = setup.getAngle();
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
						delta * 0.2);

				setup.setPosition(xyz, stageStart[3] + delta * 0.2);
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
			Double[] allowedDoubles = (Double[]) setup.getZStage().getAllowedVelocities().toArray(new Double[0]);
			String[] allowedValues = new String[allowedDoubles.length];
			for(int i=0; i < allowedDoubles.length; ++i)
				allowedValues[i] = allowedDoubles[i].toString();
			String currentValue = Double.toString(setup.getZStage().getVelocity());
			GenericDialog gd = new GenericDialog("z-stage velocity");
			gd.addChoice("velocity", allowedValues, currentValue);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			setup.getZStage().setVelocity(Double.parseDouble(gd.getNextChoice()));
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
		if (setup.isConnected(SPIMDevice.CAMERA1) && updateLiveImage && !gui.isLiveModeOn()) {
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
			ranges[0][2] = ranges[0][0] = setup.getXStage().getPosition();
			ranges[1][2] = ranges[1][0] = setup.getYStage().getPosition();
		};

		if(!acqTDevCB.isSelected())
			ranges[2][2] = ranges[2][0] = setup.getAngle();

		if(!acqZDevCB.isSelected())
			ranges[3][2] = ranges[3][0] = setup.getZStage().getPosition();

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

		SPIMDevice[] canonicalDevices = new SPIMDevice[] {SPIMDevice.STAGE_X, SPIMDevice.STAGE_Y, SPIMDevice.STAGE_THETA, SPIMDevice.STAGE_Z};

		if(SPIM_RANGES.equals(acqPosTabs.getSelectedComponent().getName())) {
			double currentRot = setup.getAngle();

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

						rows.add(new AcqRow(canonicalDevices, new String[] {"" + basev.getX(), "" + basev.getY(), "" + t, z}));
					}
				}
			}
		} else if(POSITION_LIST.equals(acqPosTabs.getSelectedComponent().getName())) {
			rows = ((StepTableModel)acqPositionsTable.getModel()).getRows();
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
		}

		return out;
	}

	private static void appendNext(CMMCore mmc, File saveFile, double bt) throws Exception {
		mmcorej.TaggedImage TI = mmc.popNextTaggedImage();

		ImageProcessor IP = ImageUtils.makeProcessor(TI);
		
		double t = System.nanoTime() / 1e9 - bt;

		ImagePlus img = null;
		ImageStack stck = new ImageStack(IP.getWidth(), IP.getHeight());
		stck.addSlice(String.format("t=%.3fs", t), IP);
		img = new ImagePlus("Video", stck);

		img.setProperty("Info", img.getProperty("Info") + "\n" + TI.tags.toString());
		IJ.save(img, new File(saveFile, String.format("%.3f.tiff",t)).getAbsolutePath());
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
							LabelledVirtualStack stck = new LabelledVirtualStack((int) mmc.getImageWidth(), (int) mmc.getImageHeight(), null, saveDir.getAbsolutePath());

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
								String t = String.format("t=%s", f.getName().substring(0, f.getName().indexOf('.') + 4)) + "s";
								stck.addSlice(t, f.getName());
								infoStr += t + "\n";
							}

							fij.setProperty("Info", infoStr);
							fij.setStack(stck);
							fij.setFileInfo(fij.getFileInfo());
							fij.getOriginalFileInfo().directory = saveFile.getParent();
							fij.getOriginalFileInfo().fileName = saveFile.getName();
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

				final AcqParams params = new AcqParams(mmc, setup, acqRows);
				params.setTimeSeqCount(timeSeqs);
				params.setTimeStepSeconds(timeStep);
				params.setContinuous(continuousCheckbox.isSelected());

				final File output;
				if(!continuousCheckbox.isSelected() && !"".equals(acqSaveDir.getText())) {
					output = new File(acqSaveDir.getText());

					if(!output.isDirectory()) {
						JOptionPane.showMessageDialog(null, "You must specify a directory.");
						return;
					}

					if(output.list().length != 0) {
						int res = JOptionPane.showConfirmDialog(null, "The destination directory is not empty. Save here anyway?\nWarning: Any OME-TIFF files in the directory will be deleted!", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
						if(res == JOptionPane.NO_OPTION)
							return;

						File[] list = output.listFiles(new FilenameFilter() {
							@Override
							public boolean accept(File dir, String name) {
								return (name.endsWith(".ome.tiff"));
							}
						});

						for(File f : list)
							if(!f.delete())
								if(JOptionPane.showConfirmDialog(null, "Couldn't clean destination directory (" + f.getName() + "). Continue anyway?", "Confirm Append", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
									return;
					}

					AcqOutputHandler handler = new OMETIFFHandler(
						mmc, output, null, null, null, "t",
						acqRows, timeSeqs, timeStep
					);
					if(asyncCheckbox.isSelected())
						handler = new AsyncOutputWrapper(handler, (ij.IJ.maxMemory() - ij.IJ.currentMemory())/(mmc.getImageWidth()*mmc.getImageHeight()*mmc.getBytesPerPixel()*2), asyncMonitorCheckbox.isSelected());

					params.setOutputHandler(handler);
				} else {
					output = null;
				}

				if(antiDriftCheckbox.isSelected())
					params.setAntiDrift(new AntiDrift.Factory() {
						@Override
						public AntiDrift manufacture(AcqParams p, AcqRow r) {
							return new ProjDiffAntiDrift(output, p, r);
						}
					});

				params.setUpdateLive(liveCheckbox.isSelected());
				params.setIllumFullStack(laseStackCheckbox.isSelected());
				params.setSettleDelay(((Number) settleTime.getValue()).intValue());

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
