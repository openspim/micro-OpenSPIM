package spim;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.process.ImageProcessor;
import ij3d.Image3DUniverse;

import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import java.util.regex.*;

import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.apache.commons.math.geometry.euclidean.threed.Rotation;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.geometry.euclidean.threed.Line;
import org.apache.commons.math.geometry.euclidean.twod.Vector2D;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

import edu.valelab.GaussianFit.GaussianFit;

public class SPIMAutoCalibrator extends JFrame implements SPIMCalibrator, ActionListener, MouseListener, MouseMotionListener {
	private static final String ZMODE_WEIGHTED_AVG = "Weighted Avg.";
	private static final String ZMODE_MIN_SIGMA = "Min Sigma";
	private static final String ZMODE_MAX_INTENSITY = "Max Intens.";

	private static final String BTN_INTERRUPT_SCAN = "Interrupt";
	private static final String BTN_3DPLOT = "Plot in 3D";
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
	
	private JFrame tweaksFrame;
	private JSpinner firstDelta;
	private JSpinner secondDelta;
	private JComboBox zmethod;
	private JCheckBox complexGuessZ;
	private JSpinner intbgrThresh;
	private JCheckBox visualFit;
	private JCheckBox hypersphere;

	public SPIMAutoCalibrator(CMMCore core, MMStudioMainFrame gui, String itwister) {
		super("SPIM Automatic Calibration");

		this.core = core;
		this.gui = gui;
		this.twisterLabel = itwister;

		rotAxis = null;

		go = new JButton(BTN_OBTAIN_NEXT);
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
		btnsPanel.setLayout(new GridLayout(7, 1));

		JButton show3Dplot = new JButton(BTN_3DPLOT);
		show3Dplot.addActionListener(this);

		JButton tweakScan = new JButton("Tweak Scan");
		tweakScan.addActionListener(this);

		LayoutUtils.addAll(btnsPanel,
				go,
				revise,
				remove,
				recalculate,
				revert,
				show3Dplot,
				tweakScan
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

		tweaksFrame = new JFrame("Scanning Tweaks");
		tweaksFrame.setLayout(new GridLayout(8, 1));

		JButton importList;

		LayoutUtils.addAll((JComponent) tweaksFrame.getContentPane(),
			LayoutUtils.horizPanel(
				new JLabel("First delta:"),
				firstDelta = new JSpinner(new SpinnerNumberModel(10.0, 1.0, 30.0, 1.0))
			),
			LayoutUtils.horizPanel(
				new JLabel("Second delta:"),
				secondDelta = new JSpinner(new SpinnerNumberModel(20.0, 5.0, 50.0, 1.0))
			),
			zmethod = new JComboBox(new String[] {ZMODE_MAX_INTENSITY, ZMODE_WEIGHTED_AVG, ZMODE_MIN_SIGMA}),
			complexGuessZ = new JCheckBox("Complex Z Guessing"),
			LayoutUtils.horizPanel(
				new JLabel("IntBGR:"),
				intbgrThresh = new JSpinner(new SpinnerNumberModel(0.20, 0.0, 0.5, 0.01))
			),
			importList = new JButton("Import..."),
			visualFit = new JCheckBox("Fitting Overlays"),
			hypersphere = new JCheckBox("Fit Hypersphere")
		);

		importList.addActionListener(this);

		tweaksFrame.pack();

		if(gui.getImageWin() != null) {
			gui.getImageWin().getCanvas().addMouseListener(this);
			gui.getImageWin().getCanvas().addMouseMotionListener(this);
		}
	}

	@Override
	public double getUmPerPixel() {
		// TODO Auto-generated method stub
		return umPerPix;
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

	private Vector2D quickFit(Point mouse) throws Exception {
		ImagePlus img = MMStudioMainFrame.getSimpleDisplay().getImagePlus();

		ImageProcessor ip = img.getProcessor();

		Rectangle oldRoi = ip.getRoi();

		Rectangle croppedRoi = new Rectangle(
				(int) mouse.getX() - img.getWidth()/40,
				(int) mouse.getY() - img.getHeight()/40,
				(int) (img.getWidth()/20), (int) (img.getHeight()/20));

		img.setRoi(croppedRoi);

		ImageProcessor cropped = ip.crop();
		double[] params = new GaussianFit(2, 1, true, true).doGaussianFit(cropped, (int) 1e4);

		ip.setRoi(oldRoi);

		double intbgr = params[GaussianFit.INT] / params[GaussianFit.BGR];

		double x = params[GaussianFit.XC];
		double y = params[GaussianFit.YC];
		double sx = params[GaussianFit.S1];
		double sy = params[GaussianFit.S2];

		if(intbgr >= (Double)intbgrThresh.getValue() &&
			x >= 0 && x < cropped.getWidth() &&
			y >= 0 && y < cropped.getHeight()) {
			img.setOverlay(new OvalRoi((int)(croppedRoi.x + x - sx),
					(int)(croppedRoi.y + y - sy), (int)(2*sx), 2*(int)(2*sy)),
					java.awt.Color.GREEN, 0, java.awt.Color.GREEN);

			return new Vector2D(croppedRoi.x + x, croppedRoi.y + y);

//			double Vx = core.getXPosition(core.getXYStageDevice()) +
//					(croppedRoi.x + x - img.getWidth()/2)*getUmPerPixel();
//			double Vy = core.getYPosition(core.getXYStageDevice()) +
//					(croppedRoi.y + y - img.getHeight()/2)*getUmPerPixel();

//			return new Vector3D(Vx, Vy, core.getPosition(core.getFocusDevice()));
		} else {
			img.setOverlay(new OvalRoi((int)(croppedRoi.x + x - sx),
					(int)(croppedRoi.y + y - sy), (int)(2*sx), 2*(int)(2*sy)),
					java.awt.Color.RED, 0, java.awt.Color.RED);

			return new Vector2D(mouse.getX(), mouse.getY());

//			return Vector3D.NaN;
		}
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		if(!inMeasureMode)
			return;

		if(!gui.getImageWin().isFocused())
			return;

		try {
			quickFit(arg0.getPoint());
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}
	}

	@Override
	public void mouseMoved(MouseEvent me) {
		if(!gui.getImageWin().isFocused())
			return;

		if(me.isControlDown())
			try {
				ReportingUtils.logMessage(quickFit(me.getPoint()).toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	private Vector2D point1, point2;
	private boolean inMeasureMode;
	private double umPerPix = 0.43478260869565217391304347826087;

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		if(!inMeasureMode)
			return;

		if(!gui.getImageWin().isFocused())
			return;

		try {
			if(point1 == null || point2 != null) {
				point1 = quickFit(arg0.getPoint());
			} else if(point1 != null && point2 == null) {
				point2 = quickFit(arg0.getPoint());

				String dist = JOptionPane.showInputDialog("Distance: " + point2.distance(point1) + " pixels; um?");

				double pix = Double.parseDouble(dist);

				umPerPix = point2.distance(point1) / pix;
			}
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}
	}

	private Line fitAxis() {
		Object[] vectors = ((DefaultListModel)pointsTable.getModel()).toArray();

		if(!hypersphere.isSelected()) {
			Vector3D[] realVecs = new Vector3D[vectors.length];

			for(int i = 0; i < vectors.length; ++i) {
				realVecs[i] = (Vector3D)vectors[i];
			}

			FitSphere circle = new FitSphere(realVecs);

			ReportingUtils.logMessage("Circle fit: " + circle.getCenter() + ", radius " + circle.getRadius() + ", error " + circle.getError());

			return new Line(circle.getCenter(), circle.getCenter().add(Vector3D.PLUS_J));
		} else {
			Collection<double[]> doublePoints = new LinkedList<double[]>();

			for(int i = 0; i < vectors.length; ++i) {
				Vector3D vec = (Vector3D)vectors[i];
				doublePoints.add(new double[] {vec.getX(), vec.getY(), vec.getZ()});
			}

			FitHypersphere circle = new FitHypersphere(doublePoints);

			ReportingUtils.logMessage("Circle fit: " + Arrays.toString(circle.getCenter()) + ", radius " + circle.getRadius());

			double[] center = circle.getCenter();
			Vector3D axisPoint = new Vector3D(center[0], center[1], center[2]);

			return new Line(axisPoint, axisPoint.add(Vector3D.PLUS_J));
		}
	};

	private double guessZ() throws Exception {
		int modelSize = pointsTable.getModel().getSize();

		if(modelSize >= 3 && complexGuessZ.isSelected()) {
			Line a = fitAxis();

			Vector3D cur = new Vector3D(
					core.getXPosition(core.getXYStageDevice()),
					core.getYPosition(core.getXYStageDevice()),
					core.getPosition(core.getFocusDevice())
				);

			Vector3D res = new Rotation(a.getDirection(),
					-1*(Math.PI/100)).applyTo(cur);

			return res.getZ();
		} else if(modelSize >= 2) {
			Vector3D recent = (Vector3D)pointsTable.getModel().getElementAt(modelSize - 1);
			Vector3D older = (Vector3D)pointsTable.getModel().getElementAt(modelSize - 2);

			return recent.getZ() + (recent.getZ() - older.getZ());
		} else {
			return core.getPosition(core.getFocusDevice());
		}
	}

	private Vector3D scanBead(double scanDelta) throws Exception {
		double basez = guessZ();

		GaussianFit fitter = new GaussianFit(3, 1, true, true);

		double cx = 0, cy = 0, cz = 0, intsum = 0;

		double maxInt = -1e6;
		double maxZ = -1;
		
		double minSigma = 1e6;
		double bestZ = -1;

		ReportingUtils.logMessage(String.format("!!!--- SCANNING %.2f to %.2f", basez - scanDelta, basez + scanDelta));

		Rectangle roi = MMStudioMainFrame.getSimpleDisplay().getImagePlus().getProcessor().getRoi();
		ImageStack stack = new ImageStack(roi.width, roi.height);

		for(double z = basez - scanDelta; z <= basez + scanDelta; ++z) {
			core.setPosition(core.getFocusDevice(), z);
			core.waitForDevice(core.getFocusDevice());
			Thread.sleep(15);

			core.snapImage();
			TaggedImage ti = core.getTaggedImage();
			addTags(ti, 0);
			gui.addImage(MMStudioMainFrame.SIMPLE_ACQ, ti, true, true);

			MMStudioMainFrame.getSimpleDisplay().updateAndDraw();

			ImageProcessor ip = MMStudioMainFrame.getSimpleDisplay().getImagePlus().getProcessor();

			ImageProcessor cropped = ip.crop();

			stack.addSlice(cropped);

			double[] params = fitter.doGaussianFit(cropped, Integer.MAX_VALUE);
			double intbgr = params[GaussianFit.INT] / params[GaussianFit.BGR];
			
			double x = params[GaussianFit.XC];
			double y = params[GaussianFit.YC];
			double sx = params[GaussianFit.S1];
			double sy = params[GaussianFit.S2];

			ReportingUtils.logMessage(String.format("!!!--- Gaussian fit: C=%.2f, %.2f, INT=%.2f, BGR=%.2f.",
					x, y, params[GaussianFit.INT], params[GaussianFit.BGR]));

			double offsx = core.getXPosition(core.getXYStageDevice()) +
					(ip.getRoi().getMinX() + x -
					ip.getWidth()/2)*getUmPerPixel();

			double offsy = core.getYPosition(core.getXYStageDevice()) +
					(ip.getRoi().getMinY() + y -
					ip.getHeight()/2)*getUmPerPixel();

			double sigma = Math.sqrt(Math.pow(sx, 2) + Math.pow(sx, 2));

			if(intbgr >= (Double)intbgrThresh.getValue() &&
					x >= 0 && y >= 0 &&
					x < cropped.getWidth() && y < cropped.getHeight()) {
				intsum += intbgr;

				ReportingUtils.logMessage("!!!--- Including z=" + z + " (" + core.getPosition(core.getFocusDevice()) + "): " + offsx + ", " + offsy + ":");
				ReportingUtils.logMessage(core.getXPosition(core.getXYStageDevice()) + " + (" + ip.getRoi().getMinX() + " + " + x + " - " + ip.getWidth() + "/2)*" + getUmPerPixel() + ")*" + intbgr + ";");
				ReportingUtils.logMessage(core.getYPosition(core.getXYStageDevice()) + " + (" + ip.getRoi().getMinY() + " + " + y + " - " + ip.getHeight() + "/2)*" + getUmPerPixel() + ")*" + intbgr + ";");

				cx += offsx*intbgr;
				cy += offsy*intbgr;
				cz += core.getPosition(core.getFocusDevice())*intbgr;
				
				java.awt.Color overlayColor = java.awt.Color.RED;

				if(intbgr > maxInt) {
					maxInt = intbgr;
					maxZ = z;
					if(ZMODE_MAX_INTENSITY.equals(zmethod.getSelectedItem()))
						overlayColor = java.awt.Color.GREEN;
				}

				if(sigma < minSigma) {
					minSigma = sigma;
					bestZ = z;
					if(ZMODE_MIN_SIGMA.equals(zmethod.getSelectedItem()))
							overlayColor = java.awt.Color.GREEN;
				}

				if(visualFit.isSelected())
					MMStudioMainFrame.getSimpleDisplay().getImagePlus().setOverlay(
							new OvalRoi((int)(roi.x + x - sx),
							(int)(roi.y + y - sy), (int)(2*sx), 2*(int)(2*sy)),
							overlayColor, 0, overlayColor);

			} else {
				if(visualFit.isSelected())
					MMStudioMainFrame.getSimpleDisplay().getImagePlus().setOverlay(null);
				
				ReportingUtils.logMessage("!!!--- Throwing out " + offsx + ", " + offsy + ", " + z + "; intbgr = " + intbgr);
			}
		}

		core.setPosition(core.getFocusDevice(), basez);
		core.waitForDevice(core.getFocusDevice());

		cx /= intsum;
		cy /= intsum;
		cz /= intsum;

		if(zmethod.getSelectedItem().equals(ZMODE_MAX_INTENSITY)) {
			cz = maxZ;
		} else if(zmethod.getSelectedItem().equals(ZMODE_MIN_SIGMA)) {
			cz = bestZ;
		}

		Vector3D ret = new Vector3D(cx, cy, cz);

		ReportingUtils.logMessage("!!!--- RETURNING " + vToS(ret));

		return ret;
	};

	private void addTags(TaggedImage ti, int channel) throws JSONException {
		MDUtils.setChannelIndex(ti.tags, channel);
		MDUtils.setFrameIndex(ti.tags, 0);
		MDUtils.setPositionIndex(ti.tags, 0);
		MDUtils.setSliceIndex(ti.tags, 0);
		try {
			ti.tags.put("Summary", MMStudioMainFrame.getInstance().getAcquisition(MMStudioMainFrame.SIMPLE_ACQ).getSummaryMetadata());
		} catch (MMScriptException ex) {
			ReportingUtils.logError("Error adding summary metadata to tags");
		}
		gui.addStagePositionToTags(ti);
	}

	private boolean getNextBead() throws Exception {
		Vector3D next = scanBead((Double)firstDelta.getValue());

		if(next.isNaN())
			next = scanBead((Double)secondDelta.getValue());

		if(next.isNaN())
			return false;

		try {
			core.setXYPosition(core.getXYStageDevice(), next.getX(), next.getY());
			core.setPosition(core.getFocusDevice(), next.getZ());
		} catch(Exception e) {
			ReportingUtils.logError(e);
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
		return String.format("<%.3f, %.3f, %.3f>", v.getX(), v.getY(), v.getZ());
	}
	
	private interface RunnableWRet<V> extends Runnable {
		public V getValue();
	};

	private RunnableWRet<Boolean> getNextBeadRunnable = new RunnableWRet<Boolean>() {
		private Boolean exitStatus;

		@Override
		public void run() {
			exitStatus = null;

			try {
				core.setPosition(twisterLabel, (core.getPosition(twisterLabel) + 1));
				core.waitForDevice(twisterLabel);
				Thread.sleep(50);
				if(!getNextBead()) {
					JOptionPane.showMessageDialog(SPIMAutoCalibrator.this,
							"Most likely, the bead has been lost. D: Sorry! " +
							"Try moving the stage to the most recent position" +
							" in the list.");
					exitStatus = false;
				} else {
					exitStatus = true;
				}
			} catch(InterruptedException e) {
				exitStatus = false;
			} catch(Exception e) {
				JOptionPane.showMessageDialog(SPIMAutoCalibrator.this,
						"Couldn't scan Z: " + e.getMessage());
				ReportingUtils.logError(e);
				exitStatus = false;
			}
		}

		@Override
		public Boolean getValue() {
			return exitStatus;
		}

	};

	private Thread scanningThread;
	private JButton go;
	
	private void scanStopped() {
		scanningThread = null;
		go.setText(BTN_OBTAIN_NEXT);
		go.setActionCommand(BTN_OBTAIN_NEXT);
	}

	public void actionPerformed(ActionEvent ae) {
		if(BTN_OBTAIN_NEXT.equals(ae.getActionCommand())) {
			scanningThread = new Thread() {
				@Override
				public void run() {
					boolean live = gui.getLiveMode(); 
					gui.enableLiveMode(false);

					do {
						getNextBeadRunnable.run();
					} while(!Thread.interrupted() &&
						(getNextBeadRunnable.getValue() == null ||
						getNextBeadRunnable.getValue() != false));
					
					gui.enableLiveMode(live);
					SPIMAutoCalibrator.this.scanStopped();
				}
			};

			scanningThread.start();
			
			go.setText(BTN_INTERRUPT_SCAN);
			go.setActionCommand(BTN_INTERRUPT_SCAN);
		} else if(BTN_INTERRUPT_SCAN.equals(ae.getActionCommand())) {
			scanningThread.interrupt();
			
			try {
//				go.setEnabled(false);
//				go.setText("Interrupting...");
				scanningThread.join(20*1000);
			} catch (InterruptedException e) {
				JOptionPane.showMessageDialog(this, "Couldn't quit thread gracefully.");
			} finally {
//				go.setText(BTN_OBTAIN_NEXT);
//				go.setActionCommand(BTN_OBTAIN_NEXT);
				go.setEnabled(true);
			}
			
			scanningThread = null;
		} else if("Tweak Scan".equals(ae.getActionCommand())) {
			tweaksFrame.setVisible(true);
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
			if(pointsTable.getModel().getSize() <= 2)
				return;

			rotAxis = fitAxis();

			rotAxisLbl.setText("Rotational axis: " + vToS(rotAxis.getDirection()));
			rotOrigLbl.setText("Rot. axis origin: " + vToS(rotAxis.getOrigin()));
		} else if(BTN_REVERSE.equals(ae.getActionCommand())) {
			rotAxis = rotAxis.revert();

			rotAxisLbl.setText("Rotational axis: " + vToS(rotAxis.getDirection()));
			rotOrigLbl.setText("Rot. axis origin: " + vToS(rotAxis.getOrigin()));
		} else if(BTN_3DPLOT.equals(ae.getActionCommand())) {
			Image3DUniverse univ = new Image3DUniverse(512,512);

			List<Point3f> points = new LinkedList<Point3f>();
			for(int i = 0; i < pointsTable.getModel().getSize(); ++i) {
				Vector3D p = (Vector3D)pointsTable.getModel().getElementAt(i);
				points.add(new Point3f(new float[] {(float) p.getX(), (float) p.getY(), (float) p.getZ()}));
			}

			univ.addIcospheres(points, new Color3f(1,0,0), 2, 4, "Beads");

			univ.show();
		} else if("Import...".equals(ae.getActionCommand())) {
			String res = JOptionPane.showInputDialog(this, "Paste the data:");

			if(res == null)
				return;

			Pattern p = Pattern.compile("\\{([0-9e\\+\\-\\,\\.]*); ([0-9e\\+\\-\\,\\.]*); ([0-9e\\+\\-\\,\\.]*)\\}");

			Matcher m = p.matcher(res);

			int count = 0;
			while(count < res.length() && m.find(count)) {
				ReportingUtils.logMessage("Next v: " + m.group());
				count = m.end() + 1;
				double x = Double.parseDouble(m.group(1).replace(",", ""));
				double y = Double.parseDouble(m.group(2).replace(",", ""));
				double z = Double.parseDouble(m.group(3).replace(",", ""));

				((DefaultListModel)pointsTable.getModel()).addElement(new Vector3D(x,y,z));
			};
		};
	}
};
