package spim;

import spim.LayoutUtils;

import java.awt.GridLayout;
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
import javax.swing.JPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import ij.ImagePlus;
import ij.gui.Roi;

import mmcorej.CMMCore;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Rotation;

import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.QRDecompositionImpl;

public class SPIMCalibrator extends JFrame implements ActionListener {
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

	private JPanel labelTF(JTextField f, String s) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.LINE_AXIS));

		p.add(new JLabel(s));
		p.add(f);

		return p;
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

	private Vector3D cachedAxis;
	private Vector3D cachedPos;

	private Vector3D roiToVec(Roi roi, double z) {
		Rectangle r = roi.getBounds();

		return new Vector3D(r.getX() + r.getWidth() / 2,
				    r.getY() + r.getHeight() / 2,
				    z);
	}

	private RealVector v3dToRVec(Vector3D in) {
		return new ArrayRealVector(new double[] {
			in.getX(),
			in.getY(),
			in.getZ()
		});
	}

	private Vector3D rvecToV3D(RealVector rv) {
		double[] comps = rv.toArray();

		return new Vector3D(comps[0], comps[1], comps[2]);
	};

	private void calculateRotationAxis() {
		// TODO: Find a way to compute both the position and direction in-place.
		Rotation rot = new Rotation(rotVecInit, rotVecMid, rotVecMid, rotVecFinal);

		RealMatrix rotm = new Array2DRowRealMatrix(rot.getMatrix());

		RealVector b = rotm.operate(v3dToRVec(rotVecInit)).subtract(v3dToRVec(rotVecMid));

		RealMatrix a = rotm.add(new Array2DRowRealMatrix(new double[][] {{-1,0,0},{0,-1,0},{0,0,-1}}));

		DecompositionSolver s = new QRDecompositionImpl(a).getSolver();

		RealVector pos = s.solve(b);

		cachedAxis = rot.getAxis();
		cachedPos = rvecToV3D(pos);
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
		rotAxisLbl.setText("Rotational axis: " + (cachedAxis != null ? vToString(cachedAxis) : "Unknown"));
		rotPosLbl.setText("Rot. axis origin: " + (cachedPos != null ? vToString(cachedPos) : "Unknown"));
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
