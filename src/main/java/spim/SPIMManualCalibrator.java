package spim;

import spim.LayoutUtils;
import spim.setup.SPIMSetup;

import java.awt.Color;
import java.awt.Rectangle;

import java.lang.Math;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import mmcorej.CMMCore;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

import org.apache.commons.math.geometry.euclidean.threed.Rotation;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Plane;
import org.apache.commons.math.geometry.euclidean.threed.Line;

import edu.valelab.GaussianFit.GaussianFit;

public class SPIMManualCalibrator extends JFrame implements ActionListener, SPIMCalibrator {
	private static final long serialVersionUID = -4228128887292057193L;

	private CMMCore core;
	private MMStudioMainFrame gui;
	private SPIMSetup setup;

	private Roi pixelSizeRoi;
	private JButton psrRoiPickerBtn;
	private JTextField psrX, psrY;

	private Vector3D rotVecInit, rotVecMid, rotVecFinal;
	private JButton rotPickInit, rotPickMid, rotPickFinal;
	private JTextField thetaInit, dTheta;

	private JLabel umPerPixLbl, rotAxisLbl, rotPosLbl;

	private final String PICK_ROI = "Pick ROI from Live Window";
	private final String PICK_BEAD = "Pick Bead";

	public SPIMManualCalibrator(CMMCore icore, MMStudioMainFrame igui, SPIMSetup isetup) {
		super("SPIM Calibration");

		core = icore;
		gui = igui;
		setup = isetup;

		setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));

		add(LayoutUtils.vertPanel("Pixel Size",
			psrRoiPickerBtn = new JButton(PICK_ROI),
			LayoutUtils.horizPanel(
				LayoutUtils.labelMe(psrX = new JTextField(4),
						    "Width (\u03BCm):"),
				(JComponent) Box.createHorizontalStrut(4),
				LayoutUtils.labelMe(psrY = new JTextField(4),
						    "Height (\u03BCm):")
			)
		));

		psrRoiPickerBtn.setActionCommand(PICK_ROI);
		psrRoiPickerBtn.addActionListener(this);

		JButton goto0 = new JButton("Goto \u03B80");
		JButton goto1 = new JButton("Goto \u03B81");
		JButton goto2 = new JButton("Goto \u03B82");

		goto0.addActionListener(this);
		goto1.addActionListener(this);
		goto2.addActionListener(this);

		JComponent rotAxisBox = (JComponent)LayoutUtils.vertPanel(
			LayoutUtils.horizPanel(
				LayoutUtils.vertPanel(
					new JLabel("Click any of the 'Goto' buttons below, then pick it using a ROI."),
					new JLabel("Ctrl+Click a 'goto' to reposition/focus the window on that bead."),
					new JLabel("Ctrl+Click a 'pick' to clear that bead position.")
				),
				Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
				Box.createHorizontalGlue(),
				rotPickInit = new JButton(PICK_BEAD),
				rotPickMid = new JButton(PICK_BEAD),
				rotPickFinal = new JButton(PICK_BEAD),
				Box.createHorizontalGlue()
			),
			LayoutUtils.horizPanel(
				LayoutUtils.labelMe(thetaInit = new JTextField(6), "Initial \u03B8:"),
				(JComponent) Box.createHorizontalStrut(4),
				LayoutUtils.labelMe(dTheta = new JTextField(6), "\u0394\u03B8:")
			),
			LayoutUtils.horizPanel(
				Box.createHorizontalGlue(),
				goto0,
				goto1,
				goto2,
				Box.createHorizontalGlue()
			)
		);

		rotAxisBox.setBorder(BorderFactory.createTitledBorder("Rotational Axis"));

		add(rotAxisBox);

		rotPickInit.setEnabled(false);
		rotPickMid.setEnabled(false);
		rotPickFinal.setEnabled(false);

		rotPickInit.setActionCommand(PICK_BEAD);
		rotPickMid.setActionCommand(PICK_BEAD);
		rotPickFinal.setActionCommand(PICK_BEAD);

		rotPickInit.addActionListener(this);
		rotPickMid.addActionListener(this);
		rotPickFinal.addActionListener(this);

		JButton recalc = new JButton("Recalculate");
		recalc.addActionListener(this);

		JButton save = new JButton("Save & Apply");
		save.addActionListener(this);

		JButton revert = new JButton("Reverse Axis");
		revert.addActionListener(this);

		JButton guess = new JButton("Guess #3");
		guess.addActionListener(this);

		add(LayoutUtils.horizPanel(
			LayoutUtils.vertPanel("Calculated Results",
				umPerPixLbl = new JLabel("\u03BCm per pixel: Unknown"),
				rotAxisLbl = new JLabel("Rotational axis: Unknown"),
				rotPosLbl = new JLabel("Rot. axis origin: Unknown")
			),
			(JComponent) Box.createHorizontalGlue(),
			LayoutUtils.vertPanel(
				recalc,
				save,
				revert,
				guess
			)
		));

		pack();

		dTheta.setText("25");

		try {
			thetaInit.setText("" + setup.getAngle());
		} catch(Exception e) {
			thetaInit.setText("0");
			ReportingUtils.logError(e);
		}

		load();
	}

	public void load() {
		// Pixel Size Roi
		int psrroiw = SPIMAcquisition.prefsGet("calibration.psr.roiw", 0);
		int psrroih = SPIMAcquisition.prefsGet("calibration.psr.roih", 0);
		double psrw = SPIMAcquisition.prefsGet("calibration.psr.w", 0D);
		double psrh = SPIMAcquisition.prefsGet("calibration.psr.h", 0D);

		if(psrroiw != 0 && psrroih != 0 && psrw != 0 && psrh != 0) {
			pixelSizeRoi = new Roi(0, 0, psrroiw, psrroih);
			psrRoiPickerBtn.setText("" + psrroiw + " x " + psrroih);
			psrX.setText("" + psrw);
			psrY.setText("" + psrh);
		};

		// Rotational axis. Unfortunately, this info is probably stale.
		// Any time a new sample is loaded, the rotational axis must be
		// recalibrated...

		rotVecInit = getvec("rvi");
		rotVecMid = getvec("rvm");
		rotVecFinal = getvec("rvf");

		rotPickInit.setText(vToString(rotVecInit));
		rotPickMid.setText(vToString(rotVecMid));
		rotPickFinal.setText(vToString(rotVecFinal));

		double tinit = SPIMAcquisition.prefsGet("calibration.rxs.theta", Double.NaN);
		double dt = SPIMAcquisition.prefsGet("calibration.rxs.dtheta", Double.NaN);

		if(tinit != Double.NaN  && dt != Double.NaN) {
			thetaInit.setText("" + tinit);
			dTheta.setText("" + dt);
		}

		calculateRotationAxis();
		redisplayRotData();
		redisplayUmPerPix();
	}

	private static void putvec(String n, Vector3D v) {
		SPIMAcquisition.prefsSet("calibration.rxs." + n + ".x", v.getX());
		SPIMAcquisition.prefsSet("calibration.rxs." + n + ".y", v.getY());
		SPIMAcquisition.prefsSet("calibration.rxs." + n + ".z", v.getZ());
	}

	private static Vector3D getvec(String n) {
		double x = SPIMAcquisition.prefsGet("calibration.rxs." + n + ".x", Double.NaN);
		double y = SPIMAcquisition.prefsGet("calibration.rxs." + n + ".y", Double.NaN);
		double z = SPIMAcquisition.prefsGet("calibration.rxs." + n + ".z", Double.NaN);

		if(x != Double.NaN && y != Double.NaN && z != Double.NaN)
			return new Vector3D(x,y,z);
		else
			return null;
	}

	public void save() {
		if(pixelSizeRoi != null) {
			SPIMAcquisition.prefsSet("calibration.psr.roiw", pixelSizeRoi.getBounds().width);
			SPIMAcquisition.prefsSet("calibration.psr.roih", pixelSizeRoi.getBounds().height);
		}
		try {
			SPIMAcquisition.prefsSet("calibration.psr.w", Double.parseDouble(psrX.getText()));
			SPIMAcquisition.prefsSet("calibration.psr.h", Double.parseDouble(psrY.getText()));
		} catch(Throwable t) {
			ReportingUtils.logError(t);
		}
		putvec("rvi", rotVecInit);
		putvec("rvm", rotVecMid);
		putvec("rvf", rotVecFinal);
		try {
			SPIMAcquisition.prefsSet("calibration.rxs.theta", Double.parseDouble(thetaInit.getText()));
			SPIMAcquisition.prefsSet("calibration.rxs.dtheta", Double.parseDouble(dTheta.getText()));
		} catch(Throwable t) {
			ReportingUtils.logError(t);
		}
	}

	public double getUmPerPixel() {
		if(pixelSizeRoi == null)
			return 0;

		double w, h;
		try {
			w = Double.parseDouble(psrX.getText());
			h = Double.parseDouble(psrY.getText());
		} catch(Exception e) {
			return 0;
		}

		double hres = pixelSizeRoi.getBounds().getWidth() / w;
		double vres = pixelSizeRoi.getBounds().getHeight() / h;

		double mean = 0.5*(hres + vres);

		if(Math.abs((hres - mean)/mean) > 0.05 || Math.abs((vres - mean)/mean) > 0.05) {
			ReportingUtils.logMessage("Horizontal and vertical resolutions differ (> 5%): " + hres + " (horizontal) vs " + vres + " (vertical)");
			JOptionPane.showMessageDialog(null, "A likely story! (Check specified width and height.)");
			return 0;
		}

		return 1/mean;
	}

	private double DTheta() {
		return Double.parseDouble(dTheta.getText()) * Math.PI / 180.0D;
	}

	private Vector3D yaxis = Vector3D.PLUS_J;

	private Vector3D TwoVecAxis(boolean backwards) {
		Vector3D a = rotVecInit;
		Vector3D b = rotVecMid;

		double avgy = (a.getY() + b.getY()) / 2;

		a = new Vector3D(a.getX(), avgy, a.getZ());
		b = new Vector3D(b.getX(), avgy, b.getZ());

		Vector3D dir = b.subtract(a);

		Vector3D halfway = a.add(dir.scalarMultiply(0.5D));

		double l = (dir.getNorm() * 0.5D) / Math.sin(DTheta() * 0.5D);

		Vector3D ortho = dir.crossProduct(yaxis).normalize();

		return halfway.add(ortho.scalarMultiply(l*(backwards?1D:-1D)));
	};

	private void GuessNextAndGo(boolean backwards, boolean tryagain) {
		Vector3D axispos = TwoVecAxis(backwards);

		Rotation r = new Rotation(yaxis,DTheta());

		Vector3D endpos = axispos.add(r.applyTo(rotVecMid.subtract(axispos)));

		try {
			setup.setPosition(endpos, Double.parseDouble(thetaInit.getText()) + DTheta()*180.0D/Math.PI*2);

			// Move our ROI to around the center of the image (where the point
			// should be) and run detect().
			ImagePlus img = gui.getImageWin().getImagePlus();
			Roi imgRoi = img.getRoi();

			Rectangle bounds = imgRoi.getBounds();

			imgRoi.setLocation((img.getWidth() - bounds.width) / 2,
					(img.getHeight() - bounds.height) / 2);

			Vector3D det = detect();

			if(tryagain && det.equals(Vector3D.NaN)) {
				System.out.println("Couldn't find; trying reversed ortho.");
				GuessNextAndGo(!backwards, false);
			} else {
				System.out.println("Guessed/detected: " + vToString(det));
			};
		} catch(Exception e) {
			ReportingUtils.logError(e);

			JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
		};
	};

	private static int detect_delta = 10;
	private Vector3D detect() throws Exception {
		// Peek the current ROI. Pan up and down through several frames, apply
		// the gaussian fitter to each. We'll need to throw out some points.

		double basez = setup.getZStage().getPosition();

		GaussianFit fitter = new GaussianFit(3, 1);

		if(gui.getImageWin() == null || gui.getImageWin().getImagePlus().getRoi() == null)
			return null;

		Vector3D c = Vector3D.ZERO;
		double intsum = 0;

		for(double z = basez - detect_delta; z < basez + detect_delta; ++z) {
			setup.getZStage().setPosition(z);
			setup.getZStage().waitFor();

			ImageProcessor ip = gui.getImageWin().getImagePlus().getProcessor();

			double[] params = fitter.doGaussianFit(ip.crop(), (int)1e12);

//			System.out.println("bgr=" + params[GaussianFit.BGR] + ", int=" + params[GaussianFit.INT] + ", xc=" + params[GaussianFit.XC]+ ", yc=" + params[GaussianFit.YC] + ", sigma_x=" + params[GaussianFit.S1] + ", sigma_y=" + params[GaussianFit.S2] + ", theta=" + params[GaussianFit.S3]);

			double INT = params[GaussianFit.INT];

			if(INT > 10) {
				intsum += INT;

				double x = (ip.getRoi().getMinX() + params[GaussianFit.XC] - ip.getWidth()/2)*getUmPerPixel();
				double y = (ip.getRoi().getMinY() + params[GaussianFit.YC] - ip.getHeight()/2)*getUmPerPixel();

				c = c.add(INT, setup.getPosition().add(new Vector3D(x, y, 0)));
			}
		}

		setup.getZStage().setPosition(basez);

		c = c.scalarMultiply(1.0/intsum);

//		System.out.println("~Pos: " + vToString(new Vector3D(cx,cy,cz)));
		return c;
	}

	private Line rotAxis;

	private void calculateRotationAxis() {
		if(rotVecInit == null || rotVecMid == null || rotVecFinal == null) {
			rotAxis = null;
			return;
		}

		// Calculate the norm and position of the two planes straddling the
		// vectors between each position. The line intersecting these planes
		// is the rotational axis.
		Vector3D firstVec = rotVecMid.subtract(rotVecInit);
		Vector3D plane1Pos = rotVecInit.add(firstVec.scalarMultiply(0.5));

		Vector3D secondVec = rotVecFinal.subtract(rotVecMid);
		Vector3D plane2Pos = rotVecMid.add(secondVec.scalarMultiply(0.5));

		Plane firstPlane = new Plane(plane1Pos, firstVec.normalize());
		Plane secondPlane = new Plane(plane2Pos, secondVec.normalize());

		rotAxis = firstPlane.intersection(secondPlane);
	};

	/* (non-Javadoc)
	 * @see spim.SPIMCalibrator#getRotationOrigin()
	 */
	@Override
	public Vector3D getRotationOrigin() {
		return rotAxis != null ? rotAxis.getOrigin() : null;
	}

	/* (non-Javadoc)
	 * @see spim.SPIMCalibrator#getRotationAxis()
	 */
	@Override
	public Vector3D getRotationAxis() {
		return rotAxis != null ? rotAxis.getDirection() : null;
	}

	/* (non-Javadoc)
	 * @see spim.SPIMCalibrator#getIsCalibrated()
	 */
	@Override
	public boolean getIsCalibrated() {
		return rotAxis != null && getUmPerPixel() != 0;
	}

	private void redisplayUmPerPix() {
		rotPickInit.setEnabled(getUmPerPixel() > 0);
		rotPickMid.setEnabled(getUmPerPixel() > 0);
		rotPickFinal.setEnabled(getUmPerPixel() > 0);

		umPerPixLbl.setText("\u03BCm per pixel: " + (getUmPerPixel() > 0 ? Double.toString(getUmPerPixel()) : "Unknown"));
	}

	private String vToString(Vector3D in) {
		return String.format("<%.3f, %.3f, %.3f>", in.getX(), in.getY(), in.getZ());
	}

	private void redisplayRotData() {
		rotAxisLbl.setText("Rotational axis: " + (rotAxis != null ? vToString(rotAxis.getDirection()) : "Unknown"));
		rotPosLbl.setText("Rot. axis origin: " + (rotAxis != null ? vToString(rotAxis.getOrigin()) : "Unknown"));
	}

	private Vector3D pickBead(ImagePlus img, boolean detect) {
		try {
			if(detect)
				return detect();

			GaussianFit xyFitter = new GaussianFit(3, 1);
			ImageProcessor ip = img.getProcessor();

			double[] params = xyFitter.doGaussianFit(ip.crop(), (int)1e12);

			img.setOverlay(new ij.gui.OvalRoi(ip.getRoi().getMinX() + params[GaussianFit.XC],
					ip.getRoi().getMinY() + params[GaussianFit.YC], 2, 2), Color.RED, 0, Color.RED);

			double x = (ip.getRoi().getMinX() + params[GaussianFit.XC] - ip.getWidth()/2)*getUmPerPixel();
			double y = (ip.getRoi().getMinY() + params[GaussianFit.YC] - ip.getHeight()/2)*getUmPerPixel();

			return setup.getPosition().add(new Vector3D(x, y, 0));
		} catch(Throwable e) {
			ReportingUtils.logError(e);
			return null;
		}
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(PICK_ROI.equals(ae.getActionCommand())) {
			if(gui.getImageWin() == null || gui.getImageWin().getImagePlus().getRoi() == null)
				pixelSizeRoi = null;
			else
				pixelSizeRoi = (Roi) gui.getImageWin().getImagePlus().getRoi().clone();

			redisplayUmPerPix();
			psrRoiPickerBtn.setText(pixelSizeRoi.getBounds().getWidth() +
					" x " + pixelSizeRoi.getBounds().getHeight());
		} else if(PICK_BEAD.equals(ae.getActionCommand())) {
			String newText = null;

			if((ae.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
				if(rotPickInit.equals(ae.getSource())) {
					rotVecInit = null;
				} else if(rotPickMid.equals(ae.getSource())) {
					rotVecMid = null;
				} else if(rotPickFinal.equals(ae.getSource())) {
					rotVecFinal = null;
				} else {
					throw new Error("PICK_BEAD from unknown component!");
				}

				newText = PICK_BEAD;
			} else {
				if(gui.getImageWin() == null || gui.getImageWin().getImagePlus().getRoi() == null)
					return;

				Vector3D vec = pickBead(gui.getImageWin().getImagePlus(), (ae.getModifiers() & ActionEvent.ALT_MASK) != 0);
				if(rotPickInit.equals(ae.getSource())) {
					rotVecInit = vec;
				} else if(rotPickMid.equals(ae.getSource())) {
					rotVecMid = vec;
				} else if(rotPickFinal.equals(ae.getSource())) {
					rotVecFinal = vec;
				} else {
					throw new Error("PICK_BEAD from unknown component!");
				}

				newText = vToString(vec);
			}

			calculateRotationAxis();
			redisplayRotData();

			((JButton)ae.getSource()).setText(newText);
		} else if(ae.getActionCommand().startsWith("Goto")) {
			int dest = ae.getActionCommand().charAt(ae.getActionCommand().length() - 1) - '0';

			if(dest < 0 || dest > 3)
				throw new Error("Goto unknown theta!");

			double thetaDest = Double.parseDouble(thetaInit.getText()) + 
					Double.parseDouble(dTheta.getText()) * dest;

			Vector3D destVec = null;
			switch(dest) {
			case 0:
				destVec = rotVecInit;
				break;
			case 1:
				destVec = rotVecMid;
				break;
			case 2:
				destVec = rotVecFinal;
				break;
			};

			try {
				setup.getThetaStage().setPosition(thetaDest);

				if((ae.getModifiers() & ActionEvent.CTRL_MASK) != 0 && destVec != null)
					setup.setPosition(destVec);
			} catch(Exception e) {
				ReportingUtils.logError(e);

				JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
			}
		} else if(ae.getActionCommand().equals("Recalculate")) {
			calculateRotationAxis();

			redisplayRotData();
			redisplayUmPerPix();
		} else if(ae.getActionCommand().equals("Save & Apply")) {
			save();

			// Apply our calculated um/pix.
			try {
				core.definePixelSizeConfig("SPIM-Calibrated");
				core.setPixelSizeUm("SPIM-Calibrated", getUmPerPixel());
				core.setPixelSizeConfig("SPIM-Calibrated");
			} catch (Throwable t) {
				ReportingUtils.logError(t);
				JOptionPane.showMessageDialog(this, "Couldn't apply pixel size configuration: " + t.getMessage());
			}
		} else if(ae.getActionCommand().equals("Reverse Axis")) {
			if(rotAxis != null) {
				rotAxis = rotAxis.revert();
				redisplayRotData();
			}
		} else if(ae.getActionCommand().equals("Guess #3")) {
			if(rotVecInit != null && rotVecMid != null) {
				GuessNextAndGo((ae.getModifiers() & ActionEvent.CTRL_MASK) != 0, true);
			}
		}
	}
}
