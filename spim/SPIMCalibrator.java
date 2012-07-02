package spim;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import ij.ImagePlus;
import ij.gui.Roi;

import mmcorej.CMMCore;

public class SPIMCalibrator extends JFrame implements ActionListener {
	private Roi pixelSizeRoi;
	private JLabel psrDynaLbl;
	private JTextField psrX, psrY;

	private Roi rotInitRoi, rotMidRoi, rotFinalRoi;
	private JLabel rotInitLbl, rotMidLbl, rotFinalLbl;
	private JTextField thetaInit, dTheta1, dTheta2; 
	
	public SPIMCalibrator(CMMCore core) {
		super("SPIM Calibration");

		setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));

		JPanel pixSize = new JPanel();
		pixSize.setLayout(new BoxLayout(pixSize, BoxLayout.PAGE_AXIS));
		pixSize.setBorder(BorderFactory.createTitledBorder("Pixel Size"));

		pixSize.add(psrDynaLbl = new JLabel("No ROI Yet"));

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
		rotAxis.setLayout(new BoxLayout(rotAxis, BoxLayout.PAGE_AXIS));
		rotAxis.setBorder(BorderFactory.createTitledBorder("Rotational Axis"));

		JPanel rotAxisBox = new JPanel();
		rotAxisBox.setLayout(new BoxLayout(rotAxisBox, BoxLayout.LINE_AXIS));

		rotAxisBox.add(new JLabel("Initial \u03B8:"));
		rotAxisBox.add(thetaInit = new JTextField(8));
		rotAxisBox.add(Box.createHorizontalStrut(4));
		rotAxisBox.add(new JLabel("First \u0394\u03B8:"));
		rotAxisBox.add(dTheta1 = new JTextField(8));
		rotAxisBox.add(Box.createHorizontalStrut(4));
		rotAxisBox.add(new JLabel("Second \u0394\u03B8:"));
		rotAxisBox.add(dTheta2 = new JTextField(8));

		rotAxis.add(rotAxisBox);

		add(rotAxis);

		pack();
	}

	public double getPixelsPerUM() {return 0;}

//	public Vector3D getRotationOrigin() {}

//	public Vector3D getRotationAxis() {}

	public boolean getIsCalibrated() {return false;}

	@Override
	public void actionPerformed(ActionEvent ae) {}
}
