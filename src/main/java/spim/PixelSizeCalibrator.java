package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.direct.NelderMeadSimplex;
import org.apache.commons.math.optimization.direct.SimplexOptimizer;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ImageUtils;

import mmcorej.CMMCore;

import spim.LayoutUtils;

public class PixelSizeCalibrator extends JFrame implements MouseListener,
		ActionListener {

	private static final long serialVersionUID = 9061494796278418821L;

	private static final String FORTYFIVE_DEG = "45\u00B0 (Laser)";
	private static final String ZERO_DEG = "0\u00B0 (Transmission)";
	private static final String APPLY_BTN = "Apply";
	private static final String UPDATE_IMAGE_BTN = "Update Image";

	private JRadioButton rulerModeRadBtn, gridModeRadBtn;
	private ButtonGroup radioGroup;
	private JTextField actualLengthBox;
	private JComboBox gridRotCmbo;

	private CMMCore core;
	private MMStudioMainFrame gui;
	private ImagePlus workingImage;

	private JLabel umPerPixLbl;
	private double umPerPix;

	public PixelSizeCalibrator(CMMCore icore, MMStudioMainFrame igui) {
		super("Pixel Size Calibration");

		this.getRootPane().setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		this.getContentPane().setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

		JButton refreshBtn = new JButton(UPDATE_IMAGE_BTN);
		refreshBtn.addActionListener(this);

		add(LayoutUtils.horizPanel(
			LayoutUtils.labelMe(actualLengthBox = new JTextField(4), "Length (um):"),
			Box.createHorizontalGlue(),
			refreshBtn
		));

		add(LayoutUtils.horizPanel(
			rulerModeRadBtn = new JRadioButton(),
			LayoutUtils.titled("Ruler Mode", (JComponent) LayoutUtils.vertPanel(
				LayoutUtils.horizPanel(
					new JLabel("Set Length to the physical length of the line."),
					Box.createHorizontalGlue()
				),
				LayoutUtils.horizPanel(
					new JLabel("Click and drag a line across the image to calibrate."),
					Box.createHorizontalGlue()
				)
			))
		));

		add(LayoutUtils.horizPanel(
			gridModeRadBtn = new JRadioButton(),
			LayoutUtils.titled("Grid Mode", (JComponent) LayoutUtils.vertPanel(
				LayoutUtils.horizPanel(
					new JLabel("Set Length to the grid spacing in um."),
					Box.createHorizontalGlue()
				),
				LayoutUtils.horizPanel(
					new JLabel("Click and drag from one grid line to an adjacent line."),
					Box.createHorizontalGlue()
				),
				LayoutUtils.labelMe(gridRotCmbo = new JComboBox(new String[] {ZERO_DEG, FORTYFIVE_DEG}), "Image angle:")
			))
		));

		JButton applyBtn = new JButton(APPLY_BTN);
		applyBtn.addActionListener(this);

		add(LayoutUtils.horizPanel(
			umPerPixLbl = new JLabel("um/pix: --"),
			Box.createHorizontalGlue(),
			applyBtn
		));

		JButton dbgBtn = new JButton("Debug: Use last image");
		dbgBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(workingImage != null) {
					if(workingImage.getCanvas() != null)
						workingImage.getCanvas().removeMouseListener(PixelSizeCalibrator.this);

					workingImage.changes = false;
					workingImage.close();
				}

				workingImage = IJ.getImage();

				if(workingImage.getCanvas() != null)
					workingImage.getCanvas().addMouseListener(PixelSizeCalibrator.this);
			}
		});

		add(LayoutUtils.horizPanel(
			Box.createHorizontalGlue(),
			dbgBtn,
			Box.createHorizontalGlue()
		));

		radioGroup = new ButtonGroup();
		radioGroup.add(rulerModeRadBtn);
		radioGroup.add(gridModeRadBtn);

		rulerModeRadBtn.setSelected(true);
		gridModeRadBtn.setSelected(false);

		core = icore;
		gui = igui;

		umPerPix = -1D;

		pack();

		fetchFreshImage();
		IJ.setTool("line");

		this.toFront();
		actualLengthBox.requestFocusInWindow();
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		if(UPDATE_IMAGE_BTN.equals(ae.getActionCommand())) {
			fetchFreshImage();
		} else if(APPLY_BTN.equals(ae.getActionCommand())) {
			if(umPerPix > 0) try {
				core.definePixelSizeConfig("SPIM", "Core", "Initialize", "1");
				core.setPixelSizeUm("SPIM", umPerPix);

				if(workingImage != null)
				{
					workingImage.changes = false;
					workingImage.close();
				}

				setVisible(false);
			} catch(Exception e) {
				IJ.handleException(e);
			}
		}
	}

	@Override
	public void mouseReleased(MouseEvent me) {
		if("line".equals(IJ.getToolName()) &&
			workingImage.getRoi() != null && Roi.LINE == workingImage.getRoi().getType()) {
			Line roi = (Line) workingImage.getRoi();

			if(rulerModeRadBtn.isSelected())
				handleRulerModeLine(roi);
			else
				handleGridModeLine(roi);
		}
	}

	private void handleRulerModeLine(Line roi) {
		try {
			umPerPix = Double.parseDouble(actualLengthBox.getText()) / roi.getLength();

			umPerPixLbl.setText("um/pix: " + umPerPix);
		} catch(NumberFormatException nfe) {
			umPerPixLbl.setText("um/pix: --");
			IJ.showMessage("Length must be a validly-formatted decimal number.");
		}
	}

	private void handleGridModeLine(Line roi) {
		ImageProcessor sip = workingImage.getProcessor();
		double roiangle = Math.atan2(roi.y2d-roi.y1d, roi.x2d-roi.x1d);

		double wh = roi.getLength() / Math.sqrt(2);

		// This is arbitrary.
		if(wh < (sip.getWidth() / 256)) {
			IJ.showMessage("Too short.");
			return;
		}

		Point r1p = new Point((int)(roi.x1d - wh/2), (int)(roi.y1d - wh/2));
		Point r2p = new Point((int)(roi.x2d - wh/2), (int)(roi.y2d - wh/2));
		Dimension rd = new Dimension((int)wh, (int)wh);

		sip.setRoi(new Rectangle(r1p, rd));
		ImageProcessor r1 = sip.crop();

		sip.setRoi(new Rectangle(r2p, rd));
		ImageProcessor r2 = sip.crop();

		GridLineFinder l1 = new GridLineFinder(r1, roiangle);
		GridLineFinder l2 = new GridLineFinder(r2, roiangle);

		IJ.log("Theta1: " + l1.getAngle() + ", theta2: " + l2.getAngle());
		IJ.log("X1: " + l1.getX() + ", Y1: " + l1.getY());
		IJ.log("X2: " + l2.getX() + ", Y2: " + l2.getY());

		if(workingImage.getOverlay() != null)
			workingImage.getOverlay().clear();

		drawGLF(workingImage, new Rectangle(r1p, rd), l1);
		drawGLF(workingImage, new Rectangle(r2p, rd), l2);

		double avg = Math.IEEEremainder(l1.getAngle() + l2.getAngle(), 180) / 2;

		double dx = (r2p.x + l2.getX()) - (r1p.x + l1.getX());
		double dy = (r2p.y + l2.getY()) - (r1p.y + l1.getY());

		double latdist = Math.abs(dx*Math.cos(avg) + dy*-Math.sin(avg));

		if(FORTYFIVE_DEG.equals(gridRotCmbo.getSelectedItem()))
			latdist *= Math.sqrt(2);

		IJ.log("Avg. theta: " + avg);
		IJ.log("Parallel distance: " + latdist);

		try {
			umPerPix = Double.parseDouble(actualLengthBox.getText()) / latdist;

			umPerPixLbl.setText("um/pix: " + umPerPix);
		} catch(Throwable t) {
			umPerPixLbl.setText("um/pix: --");
			IJ.showMessage("Length must be a validly-formatted decimal number.");
		}
	}

	private void drawGLF(ImagePlus on, Rectangle rect, GridLineFinder glf) {
		Line ol = new Line(
			rect.x + glf.getX(),
			rect.y + glf.getY(),
			rect.x + glf.getX() - Math.sin(glf.getAngle())*glf.getWidth(),
			rect.y + glf.getY() - Math.cos(glf.getAngle())*glf.getWidth());
		ol.setStrokeWidth(glf.getWidth() / 2);
		ol.setStrokeColor(Color.GREEN);

		PointRoi p = new PointRoi(rect.x + glf.getX(), rect.y + glf.getY());
		p.setStrokeWidth(glf.getWidth());
		p.setFillColor(Color.CYAN);

		Roi rr = new Roi(rect);
		rr.setStrokeWidth(1);
		rr.setStrokeColor(Color.RED);

		if(on.getOverlay() == null)
			on.setOverlay(new Overlay());

		on.getOverlay().addElement(ol);
		on.getOverlay().addElement(p);
		on.getOverlay().addElement(rr);
	}

	private void fetchFreshImage() {
		if(workingImage != null) {
			if(workingImage.getCanvas() != null)
				workingImage.getCanvas().removeMouseListener(this);
			workingImage.changes = false;
			workingImage.close();
		}

		try {
			if(gui.isLiveModeOn()) {
				workingImage = new ImagePlus("Calibration", ImageUtils.makeProcessor(core.getLastTaggedImage()));
			} else {
				core.snapImage();
				workingImage = new ImagePlus("Calibration", ImageUtils.makeProcessor(core.getTaggedImage()));
			}
		} catch(Throwable t) {
			IJ.handleException(t);
			JOptionPane.showMessageDialog(this, "Couldn't update image!");
		}

		if(workingImage != null) {
			workingImage.show();
			workingImage.getCanvas().addMouseListener(this);
		}
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {}
	@Override
	public void mouseEntered(MouseEvent arg0) {}
	@Override
	public void mouseExited(MouseEvent arg0) {}
	@Override
	public void mousePressed(MouseEvent arg0) {}

	private static class GridLineFinder {
		public static int CX = 0;
		public static int CY = 1;
		public static int WIDTH = 2;
		public static int HEIGHT = 3;
		public static int BACKGROUND = 4;

		private double[] bestFit;

		public GridLineFinder(final ImageProcessor region, final double theta) {
			region.smooth(); region.smooth(); region.smooth(); region.smooth();

			final double r = Math.min(region.getWidth(), region.getHeight())*0.9;
			final double cx = region.getWidth() / 2D;
			final double cy = region.getHeight() / 2D;

			MultivariateRealFunction mrf = new MultivariateRealFunction() {
				@Override
				public double value(double[] model) {
					double totalError = 0;

					for(double y = 0; y < region.getHeight(); y += 1)
						for(double x = 0; x < region.getWidth(); x += 1)
							totalError += error(model, region, x, y);

					return totalError;
				}
			};

			SimplexOptimizer opt = new SimplexOptimizer();
			opt.setSimplex(new NelderMeadSimplex(new double[] {r / 64, r / 64, r / 32, region.getMax() / 16, (region.getMax() - region.getMin()) / 64}));

			try {
				bestFit = opt.optimize(5000000, mrf, GoalType.MINIMIZE, new double[] {rectX(cx, cy, theta), rectY(cx, cy, theta), r / 3, region.getMax(), (region.getMax() - region.getMin()) * 0.2}).getPoint();
			} catch(Throwable t) {
				IJ.handleException(t);
				return;
			}
		}

		private static double rectX(double cx, double cy, double theta) {
			if(theta > -Math.PI*3/4 && theta < -Math.PI/4 ||
				theta > Math.PI/4 && theta < Math.PI*3/4)
				return cy*Math.cos(theta)/Math.sin(theta);
			else
				return cx;
		}

		private static double rectY(double cx, double cy, double theta) {
			if(theta > -Math.PI*3/4 && theta < -Math.PI/4 ||
				theta > Math.PI/4 && theta < Math.PI*3/4)
				return cy;
			else
				return cx*Math.tan(theta);
		}

		public double getX() {
			return bestFit[CX];
		}

		public double getY() {
			return bestFit[CY];
		}

		public double getAngle() {
			return Math.atan2(-bestFit[CY], bestFit[CX]);
		}

		public double getWidth() {
			return bestFit[WIDTH];
		}

		private static double error(double[] model, ImageProcessor data, double x, double y) {
			double actual = data.getInterpolatedValue(x, y);

			double dx = x - model[CX];
			double dy = y - model[CY];

			double nl = Math.sqrt(model[CX]*model[CX] + model[CY]*model[CY]);
			double nx = model[CX]/nl;
			double ny = model[CY]/nl;

			double dist = dx*nx + dy*ny;

			// FIXME: Currently, this line is infinitely sharp. I should model some sort of falloff.
			if(dist > model[WIDTH]/2)
				return Math.pow(actual - model[BACKGROUND], 2);
			else
				return Math.pow((actual - model[BACKGROUND]) - model[HEIGHT], 2);
		}
	}
}
