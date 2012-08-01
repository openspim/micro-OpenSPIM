package spim;

import spim.LayoutUtils;

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

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Plane;
import org.apache.commons.math.geometry.euclidean.threed.Line;

import edu.valelab.GaussianFit.GaussianFit;

public class SPIMCalibrator extends JFrame implements ActionListener {
	private static final long serialVersionUID = -4228128887292057193L;

	private CMMCore core;
	private MMStudioMainFrame gui;
	private String twisterLabel;

	private Roi pixelSizeRoi;
	private JButton psrRoiPickerBtn;
	private JTextField psrX, psrY;

	private Vector3D rotVecInit, rotVecMid, rotVecFinal;
	private JButton rotPickInit, rotPickMid, rotPickFinal;
	private JTextField thetaInit, dTheta;

	private JLabel umPerPixLbl, rotAxisLbl, rotPosLbl;

	private final String PICK_ROI = "Pick ROI";
	private final String PICK_BEAD = "Pick Bead";
	
	public SPIMCalibrator(CMMCore icore, MMStudioMainFrame igui, String itwister) {
		super("SPIM Calibration");

		core = icore;
		gui = igui;
		twisterLabel = itwister;

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

		JComponent rotAxis2 = LayoutUtils.vertPanel(
			LayoutUtils.horizPanel(
				rotPickInit = new JButton(PICK_BEAD),
				rotPickMid = new JButton(PICK_BEAD),
				rotPickFinal = new JButton(PICK_BEAD)
			),
			LayoutUtils.horizPanel(
				LayoutUtils.labelMe(thetaInit = new JTextField(6), "Initial \u03B8:"),
				(JComponent) Box.createHorizontalStrut(4),
				LayoutUtils.labelMe(dTheta = new JTextField(6), "\u0394\u03B8:")
			),
			LayoutUtils.horizPanel(
				goto0,
				goto1,
				goto2
			)
		);

		rotAxis2.setBorder(BorderFactory.createTitledBorder("Rotational Axis"));

		add(rotAxis2);

		rotPickInit.setActionCommand(PICK_BEAD);
		rotPickMid.setActionCommand(PICK_BEAD);
		rotPickFinal.setActionCommand(PICK_BEAD);

		rotPickInit.addActionListener(this);
		rotPickMid.addActionListener(this);
		rotPickFinal.addActionListener(this);

		JButton recalc = new JButton("Recalculate");
		recalc.addActionListener(this);

		JButton ok = new JButton("OK");
		ok.addActionListener(this);

		add(LayoutUtils.horizPanel(
			LayoutUtils.vertPanel("Calculated Results",
				umPerPixLbl = new JLabel("\u03BCm per pixel: Unknown"),
				rotAxisLbl = new JLabel("Rotational axis: Unknown"),
				rotPosLbl = new JLabel("Rot. axis origin: Unknown")
			),
			(JComponent) Box.createHorizontalGlue(),
			LayoutUtils.vertPanel(
				recalc,
				ok
			)
		));

		pack();

		dTheta.setText("25");

		try {
			thetaInit.setText("" + core.getPosition(twisterLabel));
		} catch(Exception e) {
			thetaInit.setText("0");
			ReportingUtils.logError(e);
		}
	}

	public double getUmPerPixel() {
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

	private Line rotAxis;

	private void calculateRotationAxis() {
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
		// HACKHACK: Make sure the axis is +k...
		if(rotAxis.getDirection().getY() < 0)
			rotAxis = rotAxis.revert();
	};

	public Vector3D getRotationOrigin() {
		return rotAxis != null ? rotAxis.getOrigin() : null;
	}

	public Vector3D getRotationAxis() {
		return rotAxis != null ? rotAxis.getDirection() : null;
	}

	public boolean getIsCalibrated() {
		return rotAxis != null && getUmPerPixel() != 0;
	}

	private void redisplayUmPerPix() {
		umPerPixLbl.setText("\u03BCm per pixel: " + (getUmPerPixel() > 0 ? Double.toString(getUmPerPixel()) : "Unknown"));
	}

	private String vToString(Vector3D in) {
		return new String("<" + in.getX() + ", " + in.getY() + ", " + in.getZ() + ">");
	}

	private void redisplayRotData() {
		rotAxisLbl.setText("Rotational axis: " + (rotAxis != null ? vToString(rotAxis.getDirection()) : "Unknown"));
		rotPosLbl.setText("Rot. axis origin: " + (rotAxis != null ? vToString(rotAxis.getOrigin()) : "Unknown"));
	}

	private Vector3D pickBead(ImagePlus img) {
		try {
			GaussianFit xyFitter = new GaussianFit(3, 1);
			ImageProcessor ip = img.getProcessor();

			double[] params = xyFitter.doGaussianFit(ip.crop(), (int)1e12);

			img.setOverlay(new ij.gui.OvalRoi(ip.getRoi().getMinX() + params[GaussianFit.XC] - 1,
					ip.getRoi().getMinY() + params[GaussianFit.YC] - 1, 2, 2), Color.RED, 0, Color.RED);

			double x = (ip.getRoi().getMinX() + params[GaussianFit.XC] - ip.getWidth()/2)*getUmPerPixel();
			double y = (ip.getRoi().getMinY() + params[GaussianFit.YC] - ip.getHeight()/2)*getUmPerPixel();

			x += core.getXPosition(core.getXYStageDevice());
			y += core.getYPosition(core.getXYStageDevice());
			double z = core.getPosition(core.getFocusDevice());

			return new Vector3D(x, y, z);
		} catch(Throwable e) {
			ReportingUtils.logError(e);
			return null;
		}
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(PICK_ROI.equals(ae.getActionCommand())) {
			Roi activeRoi = gui.getImageWin().getImagePlus().getRoi();
			String roiText = "???";

			if(psrRoiPickerBtn.equals(ae.getSource())) {
				pixelSizeRoi = (Roi) activeRoi.clone();
				roiText = activeRoi.getBounds().getWidth() + " x " + activeRoi.getBounds().getHeight();
				redisplayUmPerPix();
			}

			((JButton)ae.getSource()).setText(roiText);
		} else if(PICK_BEAD.equals(ae.getActionCommand())) {
			Vector3D vec = pickBead(gui.getImageWin().getImagePlus());
			if(rotPickInit.equals(ae.getSource())) {
				rotVecInit = vec;
			} else if(rotPickMid.equals(ae.getSource())) {
				rotVecMid = vec;
			} else if(rotPickFinal.equals(ae.getSource())) {
				rotVecFinal = vec;
			} else {
				throw new Error("PICK_BEAD from unknown component!");
			}

			redisplayRotData();

			((JButton)ae.getSource()).setText(vToString(vec));
		} else if(ae.getActionCommand().startsWith("Goto")) {
			int dest = ae.getActionCommand().charAt(
					ae.getActionCommand().length() - 1) - '0';
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
				core.setPosition(twisterLabel, thetaDest);
				
				if(destVec != null) {
					core.setXYPosition(core.getXYStageDevice(), destVec.getX(),
							destVec.getY());
				
					core.setPosition(core.getFocusDevice(), destVec.getZ());
				}
			} catch(Exception e) {
				ReportingUtils.logError(e);
			}
		} else if(ae.getActionCommand().equals("Recalculate")) {
			calculateRotationAxis();

			redisplayRotData();
			redisplayUmPerPix();
		}
	}
}
