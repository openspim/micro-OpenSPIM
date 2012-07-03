package spim;

import java.awt.GridLayout;
import java.awt.Rectangle;

import java.lang.Math;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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

import org.apache.commons.math.geometry.Vector3D;
import org.apache.commons.math.geometry.Rotation;

public class SPIMCalibrator extends JFrame implements ActionListener {
	private CMMCore core;
	private MMStudioMainFrame gui;
	private String twisterLabel;

	private Roi pixelSizeRoi;
	private JButton psrRoiPickerBtn;
	private JTextField psrX, psrY;

	private Roi rotInitRoi, rotMidRoi, rotFinalRoi;
	private double rotInitZ, rotMidZ, rotFinalZ;
	private JButton rotPickInit, rotPickMid, rotPickFinal;
	private JTextField thetaInit, dTheta1, dTheta2;

	private JLabel umPerPixLbl, rotAxisLbl, rotPosLbl;

	private final String PICK_ROI = "Pick ROI";
	
	public SPIMCalibrator(CMMCore icore, MMStudioMainFrame igui, String itwister) {
		super("SPIM Calibration");

		core = icore;
		gui = igui;
		twisterLabel = itwister;

		setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));

		JPanel pixSize = new JPanel();
		pixSize.setLayout(new GridLayout(2, 1, 2, 2));
		pixSize.setBorder(BorderFactory.createTitledBorder("Pixel Size"));

		pixSize.add(psrRoiPickerBtn = new JButton(PICK_ROI));
		psrRoiPickerBtn.setActionCommand(PICK_ROI);
		psrRoiPickerBtn.addActionListener(this);

		JPanel pixSizeTBs = new JPanel();
		pixSizeTBs.setLayout(new BoxLayout(pixSizeTBs, BoxLayout.LINE_AXIS));

		pixSizeTBs.add(new JLabel("Width (\u03BCm):"));
		pixSizeTBs.add(psrX = new JTextField(8));
		pixSizeTBs.add(Box.createHorizontalStrut(4));
		pixSizeTBs.add(new JLabel("Height (\u03BCm):"));
		pixSizeTBs.add(psrY = new JTextField(8));

		pixSize.add(pixSizeTBs);

		add(pixSize);

		JPanel rotAxis = new JPanel();
		rotAxis.setLayout(new GridLayout(3, 3, 2, 2));
		rotAxis.setBorder(BorderFactory.createTitledBorder("Rotational Axis"));

		rotAxis.add(rotPickInit = new JButton(PICK_ROI));
		rotAxis.add(rotPickMid = new JButton(PICK_ROI));
		rotAxis.add(rotPickFinal = new JButton(PICK_ROI));

		rotPickInit.setActionCommand(PICK_ROI);
		rotPickMid.setActionCommand(PICK_ROI);
		rotPickFinal.setActionCommand(PICK_ROI);

		rotPickInit.addActionListener(this);
		rotPickMid.addActionListener(this);
		rotPickFinal.addActionListener(this);

		rotAxis.add(labelTF(thetaInit = new JTextField(8), "Initial \u03B8:"));
		rotAxis.add(labelTF(dTheta1 = new JTextField(8), "First \u0394\u03B8:"));
		rotAxis.add(labelTF(dTheta2 = new JTextField(8), "Second \u0394\u03B8:"));

		JButton goto0 = new JButton("Goto \u03B80");
		JButton goto1 = new JButton("Goto \u03B81");
		JButton goto2 = new JButton("Goto \u03B82");

		goto0.addActionListener(this);
		goto1.addActionListener(this);
		goto2.addActionListener(this);

		rotAxis.add(goto0);
		rotAxis.add(goto1);
		rotAxis.add(goto2);

		add(rotAxis);

		JPanel btn = new JPanel();
		btn.setLayout(new BoxLayout(btn, BoxLayout.LINE_AXIS));

		JPanel calcResults = new JPanel();
		calcResults.setLayout(new BoxLayout(calcResults, BoxLayout.PAGE_AXIS));
		calcResults.setBorder(BorderFactory.createTitledBorder("Calculated Results"));

		calcResults.add(umPerPixLbl = new JLabel("\u03BCm per pixel: Unknown"));
		calcResults.add(rotAxisLbl = new JLabel("Rotational axis: Unknown"));
		calcResults.add(rotPosLbl = new JLabel("Rot. axis origin: Unknown"));

		btn.add(calcResults);

		btn.add(Box.createHorizontalGlue());

		JButton recalc = new JButton("Recalculate");
		recalc.addActionListener(this);

		btn.add(recalc);

		JButton ok = new JButton("OK");
		ok.addActionListener(this);

		btn.add(ok);

		add(btn);

		pack();

		dTheta1.setText("25");
		dTheta2.setText("25");

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

	private Rotation cachedRot;
	private Vector3D cachedPos;

	private void calculateRotationAxis() {};

	public Vector3D getRotationOrigin() {return null;}

	public Vector3D getRotationAxis() {return null;}

	public boolean getIsCalibrated() {return false;}

	private void redisplayUmPerPix() {
		umPerPixLbl.setText("\u03BCm per pixel: " + (getUmPerPixel() > 0 ? Double.toString(getUmPerPixel()) : "Unknown"));
	}

	private void redisplayRotData() {
	};

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
					if(rotPickInit.equals(ae.getSource())) {
						rotInitRoi = activeRoi;
						rotInitZ = core.getPosition(core.getFocusDevice());
					} else if(rotPickMid.equals(ae.getSource())) {
						rotMidRoi = activeRoi;
						rotMidZ = core.getPosition(core.getFocusDevice());
					} else if(rotPickFinal.equals(ae.getSource())) {
						rotFinalRoi = activeRoi;
						rotFinalZ = core.getPosition(core.getFocusDevice());
					} else {
						throw new Error("PICK_ROI from unknown component!");
					}
				} catch(Exception e) {
					throw new Error("Couldn't determine Z");
				}
				Rectangle r = activeRoi.getBounds();
				double x = r.getX() + r.getWidth() / 2;
				double y = r.getY() + r.getHeight() / 2;
				roiText = x + ", " + y;

				redisplayRotData();
			}

			((JButton)ae.getSource()).setText(roiText);
		} else if(ae.getActionCommand().startsWith("Goto")) {
			double thetaDest = Double.parseDouble(thetaInit.getText());
			if(!ae.getActionCommand().endsWith("0")) {
				thetaDest += Double.parseDouble(dTheta1.getText());
				if(ae.getActionCommand().endsWith("2"))
					thetaDest += Double.parseDouble(dTheta2.getText());
			}

			try {
				core.setPosition(twisterLabel, thetaDest);
			} catch(Exception e) {
				ReportingUtils.logError(e);
			}
		}
	}
}
