package spim;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Line;
import org.apache.commons.math.optimization.fitting.GaussianFitter;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

import edu.valelab.GaussianFit.GaussianFit;

import progacq.AcqParams;
import progacq.ProgrammaticAcquisitor;

public class SPIMAutoCalibrator extends JFrame implements SPIMCalibrator, ActionListener {
	private static final long serialVersionUID = -2162347623413462344L;

	private Line rotAxis;

	private CMMCore core;
	private MMStudioMainFrame gui;
	private String twisterLabel;

	private List<Vector3D> points;

	public SPIMAutoCalibrator(CMMCore core, MMStudioMainFrame gui, String itwister) {
		// TODO Auto-generated constructor stub
		this.core = core;
		this.gui = gui;
		this.twisterLabel = itwister;

		rotAxis = null;

		JButton go = new JButton("Go!");
		go.addActionListener(this);

		this.getContentPane().setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

		add(go);

		lastVecLbl = new JLabel("Points: Last:");
		add(lastVecLbl);

		points = new java.util.LinkedList<Vector3D>();
	}

	@Override
	public double getUmPerPixel() {
		// TODO Auto-generated method stub
		return 0.43478260869565217391304347826087;
	}

	@Override
	public Vector3D getRotationOrigin() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Vector3D getRotationAxis() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getIsCalibrated() {
		// TODO Auto-generated method stub
		return false;
	}

	static double minIntensity = 10;

	private JLabel lastVecLbl;

	private Vector3D scanBead(double scanDelta) throws Exception {
		double basez = core.getPosition(core.getFocusDevice());

		GaussianFit fitter = new GaussianFit(3, 1);

		double cx = 0, cy = 0, cz = 0, intsum = 0;

		for(double z = basez - scanDelta; z <= basez + scanDelta; ++z) {
			core.setPosition(core.getFocusDevice(), z);
			core.waitForDevice(core.getFocusDevice());

			ImageProcessor ip = gui.getImageWin().getImagePlus().getProcessor();

			double[] params = fitter.doGaussianFit(ip.crop(), (int)1e12);
			double intensity = params[GaussianFit.INT];

			if(intensity >= minIntensity) {
				intsum += intensity;

				cx += (core.getXPosition(core.getXYStageDevice()) +
						(ip.getRoi().getMinX() + params[GaussianFit.XC] -
						ip.getWidth()/2)*getUmPerPixel())*intensity;

				cy += (core.getYPosition(core.getXYStageDevice()) +
						(ip.getRoi().getMinY() + params[GaussianFit.YC] -
						ip.getHeight()/2)*getUmPerPixel())*intensity;

				cz += z*intensity;
			} else {
				ReportingUtils.logMessage("Throwing out z=" + z + "; intensity=" + intensity);
			}
		};

		cx /= intsum;
		cy /= intsum;
		cz /= intsum;

		return new Vector3D(cx, cy, cz);
	};

	private void getNextBead() throws Exception {
		Vector3D next = scanBead(5);

		if(next.isNaN())
			next = scanBead(15);

		if(next.isNaN()) {
			JOptionPane.showMessageDialog(this, "Couldn't locate the bead. Please highlight it manually and click Go again.");
			return;
		};

		points.add(next);

		lastVecLbl.setText("Points: " + points.size() + " Last: " + vToS(next));

		try {
			core.setXYPosition(core.getXYStageDevice(), next.getX(), next.getY());
			core.setPosition(core.getFocusDevice(), next.getZ());
		} catch(Exception e) {
			JOptionPane.showMessageDialog(this, "Couldn't recenter: " + e.getMessage());
			return;
		};

		ImageProcessor ip = gui.getImageWin().getImagePlus().getProcessor();

		Rectangle roi = ip.getRoi();

		roi.setLocation((ip.getWidth() - roi.width) / 2,
				(ip.getHeight() - roi.height) / 2);

		ip.setRoi(roi);
	};

	private String vToS(Vector3D v) {
		return String.format("<%.3f %.3f %.3f>", v.getX(), v.getY(), v.getZ());
	}

	public void actionPerformed(ActionEvent ae) {
		try {
			core.setPosition(twisterLabel, (core.getPosition(twisterLabel) + 1));
			core.waitForDevice(twisterLabel);
			getNextBead();
		} catch(Exception e) {
			JOptionPane.showMessageDialog(this, "Couldn't scan Z: " + e.getMessage());
		}
	};
}
