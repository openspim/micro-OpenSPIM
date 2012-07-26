package spim;

import spim.LayoutUtils;

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

import ij.gui.Roi;

import mmcorej.CMMCore;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Plane;
import org.apache.commons.math.geometry.euclidean.threed.Line;

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
				rotPickInit = new JButton(PICK_ROI),
				rotPickMid = new JButton(PICK_ROI),
				rotPickFinal = new JButton(PICK_ROI)
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

		rotPickInit.setActionCommand(PICK_ROI);
		rotPickMid.setActionCommand(PICK_ROI);
		rotPickFinal.setActionCommand(PICK_ROI);

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

		return mean;
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

		rotAxis = (new Plane(plane1Pos, firstVec.normalize())).intersection(new Plane(plane2Pos, secondVec.normalize()));
	};

	public Vector3D getRotationOrigin() {return null;}

	public Vector3D getRotationAxis() {return null;}

	public boolean getIsCalibrated() {return false;}

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

	private Vector3D pickRoiVec(Rectangle roi) {
		try {
			double x = (roi.getX() + roi.getWidth() / 2)*getUmPerPixel();
			double y = (roi.getY() + roi.getWidth() / 2)*getUmPerPixel();
			double z = core.getPosition(core.getFocusDevice());

			x += core.getXPosition(core.getXYStageDevice());
			y += core.getYPosition(core.getXYStageDevice());

			return new Vector3D(x, y, z);
		} catch(Exception e) {
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
				pixelSizeRoi = activeRoi;
				roiText = activeRoi.getBounds().getWidth() + " x " + activeRoi.getBounds().getHeight();
				redisplayUmPerPix();
			} else {
				try {
					Vector3D vec = pickRoiVec(activeRoi.getBounds());
					if(rotPickInit.equals(ae.getSource())) {
						rotVecInit = vec;
					} else if(rotPickMid.equals(ae.getSource())) {
						rotVecMid = vec;
					} else if(rotPickFinal.equals(ae.getSource())) {
						rotVecFinal = vec;
					} else {
						throw new Error("PICK_ROI from unknown component!");
					}

					roiText = vToString(vec);
				} catch(Exception e) {
					throw new Error("Couldn't determine Z");
				}

				redisplayRotData();
			}

			((JButton)ae.getSource()).setText(roiText);
		} else if(ae.getActionCommand().startsWith("Goto")) {
			double thetaDest = Double.parseDouble(thetaInit.getText());
			if(!ae.getActionCommand().endsWith("0")) {
				thetaDest += Double.parseDouble(dTheta.getText()) * (ae.getActionCommand().endsWith("1") ? 1 : 2);
			}

			try {
				core.setPosition(twisterLabel, thetaDest);
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
