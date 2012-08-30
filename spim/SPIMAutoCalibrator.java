package spim;

import ij.process.ImageProcessor;

import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import mmcorej.CMMCore;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Line;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

import edu.valelab.GaussianFit.GaussianFit;

public class SPIMAutoCalibrator extends JFrame implements SPIMCalibrator, ActionListener {
	private static final String BTN_OBTAIN_NEXT = "Obtain Next";
	private static final String BTN_REVISE = "Revise Sel.";
	private static final String BTN_REMOVE = "Remove Sel.";
	private static final String BTN_RECALCULATE = "Recalculate";
	private static final String BTN_REVERSE = "Reverse Axis";

	private static final long serialVersionUID = -2162347623413462344L;

	private Line rotAxis;

	private CMMCore core;
	private MMStudioMainFrame gui;
	private String twisterLabel;

	private JList pointsTable;

	private JLabel rotAxisLbl;

	private JLabel rotOrigLbl;

	public SPIMAutoCalibrator(CMMCore core, MMStudioMainFrame gui, String itwister) {
		super("SPIM Automatic Calibration");

		this.core = core;
		this.gui = gui;
		this.twisterLabel = itwister;

		rotAxis = null;

		JButton go = new JButton(BTN_OBTAIN_NEXT);
		go.addActionListener(this);

		JButton revise = new JButton(BTN_REVISE);
		revise.addActionListener(this);

		JButton remove = new JButton(BTN_REMOVE);
		remove.addActionListener(this);

		JButton recalculate = new JButton(BTN_RECALCULATE);
		recalculate.addActionListener(this);

		JButton revert = new JButton(BTN_REVERSE);
		revert.addActionListener(this);

		this.getContentPane().setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

		JPanel btnsPanel = new JPanel();
		btnsPanel.setLayout(new GridLayout(6, 1));

		LayoutUtils.addAll(btnsPanel,
				go,
				revise,
				remove,
				recalculate,
				revert
		);

		btnsPanel.setMaximumSize(btnsPanel.getPreferredSize());

		add(LayoutUtils.horizPanel(
			new JScrollPane(pointsTable = new JList(new DefaultListModel())),
			btnsPanel,
			Box.createVerticalGlue()
		));

		add(LayoutUtils.horizPanel(
			LayoutUtils.titled("Calculated Values", (JComponent) LayoutUtils.vertPanel(
				rotAxisLbl = new JLabel("Rotational axis: "),
				rotOrigLbl = new JLabel("Rot. axis origin: ")
			)),
			Box.createHorizontalGlue()
		));

		pack();
	}

	@Override
	public double getUmPerPixel() {
		// TODO Auto-generated method stub
		return 0.43478260869565217391304347826087;
	}

	@Override
	public Vector3D getRotationOrigin() {
		// TODO Auto-generated method stub
		return rotAxis != null ? rotAxis.getOrigin() : null;
	}

	@Override
	public Vector3D getRotationAxis() {
		// TODO Auto-generated method stub
		return rotAxis != null ? rotAxis.getDirection() : null;
	}

	@Override
	public boolean getIsCalibrated() {
		// TODO Auto-generated method stub
		return getUmPerPixel() != 0 && rotAxis != null;
	}

	static double minIntBGR = 0.20;

	private Line fitAxis() {
		Object[] vectors = ((DefaultListModel)pointsTable.getModel()).toArray();
		LinkedList<double[]> doublePoints = new LinkedList<double[]>();

		for(Object vec : vectors) {
			Vector3D v = (Vector3D)vec;
			doublePoints.add(new double[] {v.getX(), v.getY(), v.getZ()});
		}

		FitHypersphere circle = new FitHypersphere(doublePoints);

		double[] center = circle.getCenter();

		Vector3D axisPoint = new Vector3D(center[0], center[1], center[2]);

		return new Line(axisPoint, axisPoint.add(Vector3D.PLUS_J));
	};

	private Vector3D scanBead(double scanDelta) throws Exception {
		double basez = core.getPosition(core.getFocusDevice());

		GaussianFit fitter = new GaussianFit(3, 1);

		double cx = 0, cy = 0, cz = 0, intsum = 0;

		for(double z = basez - scanDelta; z <= basez + scanDelta; ++z) {
			core.setPosition(core.getFocusDevice(), z);
			core.waitForDevice(core.getFocusDevice());
			Thread.sleep(10); // Test: sleep 10ms before checking...

			ImageProcessor ip = gui.getImageWin().getImagePlus().getProcessor();

			double[] params = fitter.doGaussianFit(ip.crop(), (int)1e12);
			double intensity = params[GaussianFit.INT];
			double intbgr = params[GaussianFit.INT] / params[GaussianFit.BGR];

			if(intbgr >= minIntBGR) {
				intsum += intensity;

				cx += (core.getXPosition(core.getXYStageDevice()) +
						(ip.getRoi().getMinX() + params[GaussianFit.XC] -
						ip.getWidth()/2)*getUmPerPixel())*intensity;

				cy += (core.getYPosition(core.getXYStageDevice()) +
						(ip.getRoi().getMinY() + params[GaussianFit.YC] -
						ip.getHeight()/2)*getUmPerPixel())*intensity;

				cz += z*intensity;
			} else {
				ReportingUtils.logMessage("Throwing out z=" + z + "; intbgr = " + intbgr);
			}
		}

		cx /= intsum;
		cy /= intsum;
		cz /= intsum;

		return new Vector3D(cx, cy, cz);
	};

	private boolean getNextBead() throws Exception {
		Vector3D next = scanBead(5);

		if(next.isNaN())
			next = scanBead(15);

		if(next.isNaN())
			return false;

		try {
			core.setXYPosition(core.getXYStageDevice(), next.getX(), next.getY());
			core.setPosition(core.getFocusDevice(), next.getZ());
		} catch(Exception e) {
			JOptionPane.showMessageDialog(this, "Couldn't recenter: " + e.getMessage());
			return false;
		};

		((DefaultListModel)pointsTable.getModel()).addElement(next);

		ImageProcessor ip = gui.getImageWin().getImagePlus().getProcessor();

		Rectangle roi = ip.getRoi();

		roi.setLocation((ip.getWidth() - roi.width) / 2,
				(ip.getHeight() - roi.height) / 2);

		gui.getImageWin().getImagePlus().setRoi(roi);

		return true;
	};

	private String vToS(Vector3D v) {
		return String.format("<%.3f %.3f %.3f>", v.getX(), v.getY(), v.getZ());
	}

	public void actionPerformed(ActionEvent ae) {
		if(BTN_OBTAIN_NEXT.equals(ae.getActionCommand())) {
			try {
				for(int i=0; i < ((ae.getModifiers() & ActionEvent.ALT_MASK) != 0 ? 5 : 1); ++i) {
					core.setPosition(twisterLabel, (core.getPosition(twisterLabel) + 1));
					core.waitForDevice(twisterLabel);
					if(!getNextBead()) {
						JOptionPane.showMessageDialog(this, "Most likely, the bead has been lost. D: Sorry! Try moving the stage to the most recent position in the list.");
						break;
					} else {
						Thread.sleep(250);
					}
				}
			} catch(Exception e) {
				JOptionPane.showMessageDialog(this, "Couldn't scan Z: " + e.getMessage());
			}
		} else if(BTN_REVISE.equals(ae.getActionCommand())) {
			DefaultListModel mdl = (DefaultListModel)pointsTable.getModel();

			try {
				mdl.set(pointsTable.getSelectedIndex(),
						new Vector3D(core.getXPosition(core.getXYStageDevice()),
								core.getYPosition(core.getXYStageDevice()),
								core.getPosition(core.getFocusDevice())));
			} catch(Exception e) {
				JOptionPane.showMessageDialog(this, "Couldn't fetch: " + e.getMessage());
			}
		} else if(BTN_REMOVE.equals(ae.getActionCommand())) {
			DefaultListModel mdl = (DefaultListModel)pointsTable.getModel();

			for(Object obj : pointsTable.getSelectedValues())
				mdl.removeElement(obj);

			pointsTable.clearSelection();
		} else if(BTN_RECALCULATE.equals(ae.getActionCommand())) {
			rotAxis = fitAxis();

			rotAxisLbl.setText("Rotational axis: " + vToS(rotAxis.getDirection()));
			rotOrigLbl.setText("Rot. axis origin: " + vToS(rotAxis.getOrigin()));
		} else if(BTN_REVERSE.equals(ae.getActionCommand())) {
			rotAxis = rotAxis.revert();

			rotAxisLbl.setText("Rotational axis: " + vToS(rotAxis.getDirection()));
			rotOrigLbl.setText("Rot. axis origin: " + vToS(rotAxis.getOrigin()));
		}
	};
}
