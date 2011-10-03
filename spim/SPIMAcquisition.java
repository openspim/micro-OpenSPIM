package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class SPIMAcquisition implements MMPlugin {
	// TODO: read these from the properties
	protected int motorMin = 1, motorMax = 8000,
		twisterMin = -32767, twisterMax = 32767;

	protected ScriptInterface app;
	protected CMMCore mmc;
	protected MMStudioMainFrame gui;

	protected String xyStageLabel, zStageLabel, twisterLabel,
		laserLabel, cameraLabel;

	protected JFrame frame;
	protected IntegerField xPosition, yPosition, zPosition, rotation,
		zFrom, zTo, stepsPerRotation, degreesPerStep,
		laserPower, exposure;
	protected MotorSlider xSlider, ySlider, zSlider, rotationSlider,
		laserSlider, exposureSlider;
	protected JCheckBox liveCheckbox, registrationCheckbox,
		multipleAngleCheckbox;
	protected JButton ohSnap;

	protected boolean updateLiveImage;

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
		frame.dispose();
		frame = null;
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
		frame = new JFrame("SPIM in a Briefcase");

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.PAGE_AXIS));
		left.setBorder(BorderFactory.createTitledBorder("Position/Angle"));

		xSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				try {
					runToXPosition(value);
					maybeUpdateImage();
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};
		ySlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				try {
					runToYPosition(value);
					maybeUpdateImage();
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};
		zSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				try {
					runToZPosition(value);
					maybeUpdateImage();
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};
		rotationSlider = new MotorSlider(twisterMin, twisterMax, 0) {
			@Override
			public void valueChanged(int value) {
				try {
					runToAngle(value);
					maybeUpdateImage();
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};

		xPosition = new IntegerSliderField(xSlider);
		yPosition = new IntegerSliderField(ySlider);
		zPosition = new IntegerSliderField(zSlider);
		rotation = new IntegerSliderField(rotationSlider);

		zFrom = new IntegerField(1) {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		zTo = new IntegerField(motorMax) {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		stepsPerRotation = new IntegerField(4) {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};
		degreesPerStep = new IntegerField(90) {
			@Override
			public void valueChanged(int value) {
				stepsPerRotation.setText("" + (360 / value));
			}
		};

		addLine(left, Justification.LEFT, "x:", xPosition, "y:", yPosition, "z:", zPosition, "angle:", rotation);
		addLine(left, Justification.STRETCH, "x:", xSlider);
		addLine(left, Justification.STRETCH, "y:", ySlider);
		addLine(left, Justification.STRETCH, "z:", zSlider);
		addLine(left, Justification.RIGHT, "from z:", zFrom, "to z:", zTo);
		addLine(left, Justification.STRETCH, "rotation:", rotationSlider);
		addLine(left, Justification.RIGHT, "steps/rotation:", stepsPerRotation, "degrees/step:", degreesPerStep);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.PAGE_AXIS));
		right.setBorder(BorderFactory.createTitledBorder("Acquisition"));

		// TODO: find out correct values
		laserSlider = new MotorSlider(0, 1000, 1000) {
			@Override
			public void valueChanged(int value) {
				// TODO
			}
		};
		// TODO: find out correct values
		exposureSlider = new MotorSlider(10, 1000, 10) {
			@Override
			public void valueChanged(int value) {
				// TODO
			}
		};

		laserPower = new IntegerSliderField(laserSlider);
		exposure = new IntegerSliderField(exposureSlider);

		liveCheckbox = new JCheckBox("Update Live View");
		liveCheckbox.setSelected(true);
		updateLiveImage = true;
		liveCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				updateLiveImage = e.getStateChange() == ItemEvent.SELECTED;
			}
		});
		registrationCheckbox = new JCheckBox("Perform SPIM registration");
		registrationCheckbox.setSelected(false);
		multipleAngleCheckbox = new JCheckBox("Multiple Rotation Angles");
		multipleAngleCheckbox.setSelected(false);

		ohSnap = new JButton("Oh snap!");
		ohSnap.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new Thread() {
					@Override
					public void run() {
						try {
							snapStack(zFrom.getValue(), zTo.getValue()).show();
						} catch (Exception e) {
							IJ.handleException(e);
						}
					}
				}.start();
			}
		});

		addLine(right, Justification.RIGHT, "laser power:", laserPower, "exposure:", exposure);
		addLine(right, Justification.STRETCH, "laser:", laserSlider);
		addLine(right, Justification.STRETCH, "exposure:", exposureSlider);
		addLine(right, Justification.RIGHT, liveCheckbox);
		addLine(right, Justification.RIGHT, registrationCheckbox);
		addLine(right, Justification.RIGHT, multipleAngleCheckbox);
		addLine(right, Justification.RIGHT, ohSnap);

		Container panel = frame.getContentPane();
		panel.setLayout(new GridLayout(1, 2));
		panel.add(left);
		panel.add(right);

		frame.pack();
	}

	protected void updateUI() {
		xPosition.setEnabled(xyStageLabel != null);
		yPosition.setEnabled(xyStageLabel != null);
		zPosition.setEnabled(zStageLabel != null);
		rotation.setEnabled(twisterLabel != null);

		xSlider.setEnabled(xyStageLabel != null);
		ySlider.setEnabled(xyStageLabel != null);
		zSlider.setEnabled(zStageLabel != null);
		zFrom.setEnabled(zStageLabel != null);
		zTo.setEnabled(zStageLabel != null);
		rotationSlider.setEnabled(twisterLabel != null);
		stepsPerRotation.setEnabled(twisterLabel != null);
		degreesPerStep.setEnabled(twisterLabel != null);

		laserPower.setEnabled(laserLabel != null);
		exposure.setEnabled(cameraLabel != null);
		laserSlider.setEnabled(laserLabel != null);
		exposureSlider.setEnabled(cameraLabel != null);
		liveCheckbox.setEnabled(cameraLabel != null);
		ohSnap.setEnabled(zStageLabel != null && cameraLabel != null);

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

	protected static abstract class MotorSlider extends JSlider implements ChangeListener {
		public MotorSlider(int min, int max, int current) {
			super(JSlider.HORIZONTAL, min, max, Math.min(max, Math.max(min, current)));

			setMinorTickSpacing((int)((max - min) / 40));
			setMajorTickSpacing((int)((max - min) / 5));
			setPaintTrack(true);
			setPaintTicks(true);
			setPaintLabels(true);

			addChangeListener(this);
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			if (getValueIsAdjusting())
				return;
			final int value = getValue();
			new Thread() {
				@Override
				public void run() {
					valueChanged(value);
				}
			}.start();
		}

		public abstract void valueChanged(int value);
	}

	protected static abstract class IntegerField extends JTextField {
		public IntegerField(int value) {
			this(value, 4);
		}

		public IntegerField(int value, int columns) {
			super(columns);
			setText("" + value);
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					if (e.getKeyCode() != KeyEvent.VK_ENTER)
						return;
					valueChanged(getValue());
				}
			});
		}

		public int getValue() {
			String typed = getText();
			if(!typed.matches("\\d+"))
				return 0;
			return Integer.parseInt(typed);
		}

		public abstract void valueChanged(int value);
	}

	protected static class IntegerSliderField extends IntegerField {
		protected JSlider slider;

		public IntegerSliderField(JSlider slider) {
			super(slider.getValue());
			this.slider = slider;
		}

		@Override
		public void valueChanged(int value) {
			if (slider != null)
				slider.setValue(value);
		}
	}

	// Accessing the devices

	protected void maybeUpdateImage() {
		if (cameraLabel != null && updateLiveImage)
			gui.updateImage();
	}

	protected void runToXPosition(int x) throws Exception {
		if (mmc.getXPosition(xyStageLabel) == x)
			return;
		int y = (int)mmc.getYPosition(xyStageLabel);
		mmc.setXYPosition(xyStageLabel, x, y);
		do {
			IJ.wait(50);
			xPosition.setText("" + (int)mmc.getXPosition(xyStageLabel));
		} while (mmc.getXPosition(xyStageLabel) != x);
	}

	protected void runToYPosition(int y) throws Exception {
		if (mmc.getYPosition(xyStageLabel) == y)
			return;
		int x = (int)mmc.getXPosition(xyStageLabel);
		mmc.setXYPosition(xyStageLabel, x, y);
		do {
			IJ.wait(50);
			yPosition.setText("" + (int)mmc.getYPosition(xyStageLabel));
		} while (mmc.getYPosition(xyStageLabel) != y);
	}

	protected void runToZPosition(int position) throws Exception {
		if (mmc.getPosition(zStageLabel) == position)
			return;
		mmc.setPosition(zStageLabel, position);
		do {
			IJ.wait(50);
			zPosition.setText("" + (int)mmc.getPosition(zStageLabel));
		} while (mmc.getPosition(zStageLabel) != position);
	}

	protected void runToAngle(int step) throws Exception {
		if (mmc.getPosition(twisterLabel) == step)
			return;
		mmc.setPosition(twisterLabel, step);
		do {
			IJ.wait(50);
			rotation.setText("" + (int)mmc.getPosition(twisterLabel));
		} while (mmc.getPosition(twisterLabel) != step);
	}

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

	protected ImagePlus snapStack(int zStart, int zEnd) throws Exception {
		ImageStack stack = null;
		int zStep = (zStart < zEnd ? +1 : -1);
		for (int z = zStart; z <= zEnd; z = z + zStep) {
			runToZPosition(z);
			if (zSlider != null)
				zSlider.setValue(z);
			ImageProcessor ip = snapSlice();
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("z: " + z, ip);
		}
		return new ImagePlus("SPIM!", stack);
	}

}

