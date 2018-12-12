package spim.gui.calibration;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.algorithm.localization.Gaussian;
import net.imglib2.algorithm.localization.LevenbergMarquardtSolver;
import net.imglib2.algorithm.localization.LocalizationUtils;
import net.imglib2.algorithm.localization.MLGaussianEstimator;
import net.imglib2.algorithm.localization.Observation;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.json.JSONException;
import org.micromanager.internal.MMStudio;
import org.micromanager.SnapLiveManager;
//import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import spim.algorithm.FitDisk;
import spim.algorithm.FitHypersphere;
import spim.gui.util.Layout;
import spim.hardware.SPIMSetup;

public class AutoWindow extends JFrame implements CalibrationWindow, ActionListener, MouseListener, MouseMotionListener {
	private static final String XYMODE_GAUSSIAN_FIT = "Gaussian (Beads)";
	private static final String XYMODE_WEIGHTED_AVG = "Weighted Avg.";
	private static final String XYMODE_MAX_INTENSITY = "Max Intens.";

	private static final String ZMODE_WEIGHTED_AVG = "Weighted Avg.";
	private static final String ZMODE_MAX_INTENSITY = "Max Intens.";

	private static final String BTN_INTERRUPT_SCAN = "Interrupt";
	private static final String BTN_3DPLOT = "Plot in 3D";
	private static final String BTN_OBTAIN_NEXT = "Obtain Next";
	private static final String BTN_REVISE = "Revise Sel.";
	private static final String BTN_REMOVE = "Remove Sel.";
	private static final String BTN_RECALCULATE = "Recalculate";
	private static final String BTN_REVERSE = "Reverse Axis";
	private static final String BTN_TWEAKS_FRAME = "Tweak Scan";
	private static final String BTN_IMPORT = "Import...";
	private static final String BTN_AUTOLOC = "Auto-Locate";

	private static final long serialVersionUID = -2162347623413462344L;

	private Line rotAxis;
	private double radius;

	private CMMCore core;
	private MMStudio gui;

	private JList pointsTable;

	private JLabel rotAxisLbl;
	private JLabel rotOrigLbl;

	private JFrame tweaksFrame;
	private JSpinner firstDelta;
	private JSpinner secondDelta;
	private JComboBox xymethod, zmethod;
	private JCheckBox complexGuessZ;
	private JSpinner intbgrThresh;
	private JCheckBox visualFit;
	private JCheckBox hypersphere;

	private SPIMSetup setup;

	public AutoWindow( CMMCore core, MMStudio gui, SPIMSetup isetup ) {
		super("SPIM Automatic Calibration");

		this.core = core;
		this.gui = gui;
		setup = isetup;

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

		JButton tweakScan = new JButton(BTN_TWEAKS_FRAME);
		tweakScan.addActionListener(this);

		JButton autoLocate = new JButton(BTN_AUTOLOC);
		autoLocate.addActionListener(this);

		Layout.addAll( btnsPanel,
				go,
				revise,
				remove,
				recalculate,
				revert,
				show3Dplot,
				tweakScan
		);

		btnsPanel.setMaximumSize(btnsPanel.getPreferredSize());

		add( Layout.horizPanel(
				new JScrollPane( pointsTable = new JList( new DefaultListModel() ) ),
				btnsPanel,
				Box.createVerticalGlue()
		));

		add( Layout.horizPanel(
				Layout.titled( "Calculated Values", ( JComponent ) Layout.vertPanel(
						rotAxisLbl = new JLabel( "Rotational axis: " ),
						rotOrigLbl = new JLabel( "Rot. axis origin: " )
				) ),
				Box.createHorizontalGlue()
		));

		pack();

		tweaksFrame = new JFrame("Scanning Tweaks");
		tweaksFrame.getRootPane().setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));

		JButton importList;

		double ss = isetup.getZStage().getStepSize();

		tweaksFrame.add( Layout.form(
				"First delta:", firstDelta = new JSpinner( new SpinnerNumberModel( 3 * ss, ss, 30 * ss, ss ) ),
				"Second delta:", secondDelta = new JSpinner( new SpinnerNumberModel( 12 * ss, 2 * ss, 60 * ss, ss ) ),
				"X/Y determination:", xymethod = new JComboBox( new String[] { XYMODE_GAUSSIAN_FIT, XYMODE_WEIGHTED_AVG,
				XYMODE_MAX_INTENSITY } ),
				"Z determination:", zmethod = new JComboBox( new String[] { ZMODE_MAX_INTENSITY, ZMODE_WEIGHTED_AVG } ),
				"Complex Z guessing:", complexGuessZ = new JCheckBox(/*"Complex Z Guessing"*/ ),
				"Min Intensity/BG:", intbgrThresh = new JSpinner( new SpinnerNumberModel( 1.3, 1.0, 10.0, 0.1 ) ),
				"Import list:", importList = new JButton( BTN_IMPORT ),
				"Fitting overlays:", visualFit = new JCheckBox(/*"Fitting Overlays"*/ ),
				"Hypersphere fitter:", hypersphere = new JCheckBox(/*"Fit Hypersphere"*/ ),
				"Auto-Locate:", autoLocate = new JButton( BTN_AUTOLOC )
		));

		importList.addActionListener(this);

		tweaksFrame.pack();


		if(gui.getSnapLiveManager() != null) {
			gui.getSnapLiveManager().getDisplay().getAsWindow().addMouseListener(this);
			gui.getSnapLiveManager().getDisplay().getAsWindow().addMouseMotionListener(this);

			highlightBrightest(gui.getSnapLiveManager().getDisplay().getImagePlus());
		}
	}

	@Override
	public Vector3D getRotationOrigin() {
		return rotAxis != null ? rotAxis.getOrigin() : null;
	}

	@Override
	public Vector3D getRotationAxis() {
		return rotAxis != null ? rotAxis.getDirection() : null;
	}

	@Override
	public boolean getIsCalibrated() {
		return rotAxis != null;
	}

	private void highlightBrightest(ImagePlus imp) {
		final ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<net.imglib2.Point> peaks = LocalExtrema.findLocalExtrema(
			ImagePlusAdapter.wrapShort(imp),
			new LocalExtrema.MaximumCheck<UnsignedShortType>(new UnsignedShortType((int)(imp.getProcessor().getMin() + imp.getProcessor().getMax())/2)),
			executors
		);

		if(peaks.size() > 0) {
			net.imglib2.Point bestPeak = null;
			int bestPeakVal = 0;

			for(net.imglib2.Point peak : peaks) {
				int val = imp.getProcessor().getPixel(peak.getIntPosition(0), peak.getIntPosition(1));
				if(val > bestPeakVal) {
					bestPeak = peak;
					bestPeakVal = val;
				}
			}

			if(bestPeak != null) {
				imp.setRoi(
					bestPeak.getIntPosition(0) - imp.getWidth() / 20,
					bestPeak.getIntPosition(1) - imp.getHeight() / 40,
					imp.getWidth() / 10,
					imp.getHeight() / 20
				);
			}
		}
	}

	private Vector3D findLocalBestXY(ImageProcessor ip) {
		if(XYMODE_GAUSSIAN_FIT.equals(xymethod.getSelectedItem())) {
			long[] center = new long[] { (long)ip.getWidth() / 2, (long)ip.getHeight() / 2 };
			net.imglib2.Point cpoint = new net.imglib2.Point(center);

			Observation data = LocalizationUtils.gatherObservationData(ImagePlusAdapter.wrapShort(new ImagePlus("", ip)), cpoint, center);

			double[] params = new MLGaussianEstimator(2.0, 2).initializeFit(cpoint, data);

			try {
				LevenbergMarquardtSolver.solve(data.X, params, data.I, new Gaussian(), 1e-3, 1e-1, 300);
			} catch(Throwable t) {
				t.printStackTrace(); // It's okay to fall through here; it'll return the estimate.
			}

			return new Vector3D(params[0], params[1], params[2] / ip.getMin());
		} else {
			double edgeAvg = 0;
			double x = 0, y = 0;
			double max = 0;
			double sum = 0;

			for(int iy = 0; iy < ip.getHeight(); ++iy) {
				for(int ix = 0; ix < ip.getWidth(); ++ix) {
					double val = ip.getPixelValue(ix, iy);

					if(iy == 0 || ix == 0 || iy == ip.getHeight() - 1 || ix == ip.getHeight() - 1)
						edgeAvg += val / (2*ip.getWidth() + 2*ip.getHeight() - 4);

					if(XYMODE_WEIGHTED_AVG.equals(xymethod.getSelectedItem())) {
						x += ix*val;
						y += iy*val;
						sum += val;
					}

					if(val > max) {
						max = val;

						if(XYMODE_MAX_INTENSITY.equals(xymethod.getSelectedItem())) {
							x = ix;
							y = iy;
						}
					}
				}
			}

			if(XYMODE_WEIGHTED_AVG.equals(xymethod.getSelectedItem())) {
				x /= sum;
				y /= sum;
			}

			return new Vector3D(x, y, max / edgeAvg);
		}
	}

	private Vector2D quickFit() throws Exception {
		ImagePlus img = MMStudio.getInstance().getSnapLiveManager().getDisplay().getImagePlus();
		Point mouse = img.getCanvas().getCursorLoc();

		ImageProcessor ip = img.getProcessor();

		Roi oldRoi = img.getRoi();

		Rectangle croppedRoi = new Rectangle(
				(int) mouse.getX() - img.getWidth()/40,
				(int) mouse.getY() - img.getHeight()/40,
				(int) (img.getWidth()/20), (int) (img.getHeight()/20));

		img.setRoi(croppedRoi);

		ImageProcessor cropped = ip.crop();
		Vector3D loc = findLocalBestXY(cropped);

		img.setRoi(oldRoi);

		Overlay o = new Overlay(new Roi(croppedRoi));

		if(loc.getZ() >= (Double)intbgrThresh.getValue() &&
			loc.getX() >= 0 && loc.getX() < cropped.getWidth() &&
			loc.getY() >= 0 && loc.getY() < cropped.getHeight()) {

			OvalRoi roi = new OvalRoi(croppedRoi.x + loc.getX() - 4, croppedRoi.y + loc.getY() - 4, 8, 8);
			roi.setStrokeColor(java.awt.Color.GREEN);
			img.setRoi(roi);
			o.setStrokeColor(java.awt.Color.GREEN);
			o.add(roi);

			ip.setOverlay(o);

			return new Vector2D(croppedRoi.x + loc.getX(), croppedRoi.y + loc.getY());
		} else {
			OvalRoi roi = new OvalRoi(croppedRoi.x + loc.getX() - 4, croppedRoi.y + loc.getY() - 4, 8, 8);
			roi.setStrokeColor(java.awt.Color.RED);
			img.setRoi(roi);
			o.setStrokeColor(java.awt.Color.RED);
			o.add(roi);

			ip.setOverlay(o);

			return new Vector2D(mouse.getX(), mouse.getY());
		}
	}

	@Override
	public void mouseMoved(MouseEvent me) {
		if(!gui.getSnapLiveManager().getDisplay().getAsWindow().isFocused())
			return;

		if(me.isControlDown())
			try {
				ReportingUtils.logMessage(quickFit().toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {}

	@Override
	public void mouseClicked(MouseEvent arg0) {}

	@Override
	public void mouseEntered(MouseEvent arg0) {}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}

	private Line fitAxis() {
		Object[] vectors = ((DefaultListModel)pointsTable.getModel()).toArray();

		if(!hypersphere.isSelected()) {
			Vector3D[] realVecs = new Vector3D[vectors.length];

			for(int i = 0; i < vectors.length; ++i) {
				realVecs[i] = (Vector3D)vectors[i];
			}

			FitDisk circle = new FitDisk(realVecs);

			ReportingUtils.logMessage("Circle fit: " + circle.getCenter() + ", radius " + circle.getRadius() + ", error " + circle.getError());

			radius = circle.getRadius();
			return new Line(circle.getCenter(), circle.getCenter().add(circle.getNormal()));
		} else {
			Collection<double[]> doublePoints = new LinkedList<double[]>();

			for(int i = 0; i < vectors.length; ++i) {
				Vector3D vec = (Vector3D)vectors[i];
				doublePoints.add(new double[] {vec.getX(), vec.getY(), vec.getZ()});
			}

			FitHypersphere circle = new FitHypersphere(doublePoints);

			ReportingUtils.logMessage("Hypersphere fit: " + Arrays.toString(circle.getCenter()) + ", radius " + circle.getRadius());

			double[] center = circle.getCenter();
			Vector3D axisPoint = new Vector3D(center[0], center[1], center[2]);

			radius = circle.getRadius();
			return new Line(axisPoint, axisPoint.add(Vector3D.PLUS_J));
		}
	};

	private double guessZ() throws Exception {
		int modelSize = pointsTable.getModel().getSize();

		if(modelSize >= 3 && complexGuessZ.isSelected()) {
			Line a = fitAxis();

			Vector3D cur = setup.getPosition();

			Vector3D res = new Rotation(a.getDirection(),
					-1*(Math.PI/100)).applyTo(cur);

			return res.getZ();
		} else if(modelSize >= 2) {
			Vector3D recent = (Vector3D)pointsTable.getModel().getElementAt(modelSize - 1);
			Vector3D older = (Vector3D)pointsTable.getModel().getElementAt(modelSize - 2);

			return recent.getZ() + (recent.getZ() - older.getZ());
		} else {
			return setup.getZStage().getPosition();
		}
	}

	private Vector3D scanBead(double scanDelta) throws Exception {
		double basez = guessZ();

		Vector3D c = Vector3D.ZERO;
		double intsum = 0;

		double maxInt = -1e6;
		double maxZ = -1;

		ReportingUtils.logMessage(String.format("!!!--- SCANNING %.2f to %.2f", basez - scanDelta, basez + scanDelta));

		Rectangle roi = MMStudio.getInstance().getSnapLiveManager().getDisplay().getImagePlus().getProcessor().getRoi();
		ImageStack stack = new ImageStack(roi.width, roi.height);

		for(double z = basez - scanDelta; z <= basez + scanDelta; z += setup.getZStage().getStepSize()) {
			setup.getZStage().setPosition(z);
			setup.getZStage().waitFor();
			Thread.sleep(15);

			TaggedImage ti = setup.getCamera1().snapImage();
			addTags(ti, 0);

			// TODO: correct the below based on MM2 API
//			gui.addImage(SnapLiveManager.SIMPLE_ACQ, ti, true, true);
//
//			VirtualAcquisitionDisplay display = MMStudio.getInstance().getSnapLiveManager().getSnapLiveDisplay();
//			display.updateAndDraw(true);
//			ImageProcessor ip = display.getImagePlus().getProcessor();

			ImageProcessor ip = MMStudio.getInstance().getSnapLiveManager().getDisplay().getImagePlus().getProcessor();

			ImageProcessor cropped = ip.crop();

			stack.addSlice(cropped);

			Vector3D loc = findLocalBestXY(cropped);

			ReportingUtils.logMessage(String.format("!!!--- Gaussian fit: C=%.2f, %.2f, INT/BGR=%.2f.",
					loc.getX(), loc.getY(), loc.getZ()));

			Vector3D curPos = setup.getPosition();

			Vector3D beadPos = curPos.add(new Vector3D(
					(ip.getRoi().getMinX() + loc.getX() - ip.getWidth()/2)*setup.getCore().getPixelSizeUm(),
					(ip.getRoi().getMinY() + loc.getY() - ip.getHeight()/2)*setup.getCore().getPixelSizeUm(),
					0
			));

			if(loc.getZ() >= (Double)intbgrThresh.getValue() &&
					loc.getX() >= 0 && loc.getY() >= 0 &&
					loc.getX() < cropped.getWidth() && loc.getY() < cropped.getHeight()) {
				intsum += loc.getZ();
				c = c.add(loc.getZ(), beadPos);

				java.awt.Color overlayColor = java.awt.Color.RED;

				if(loc.getZ() > maxInt) {
					maxInt = loc.getZ();
					maxZ = z;
					if(ZMODE_MAX_INTENSITY.equals(zmethod.getSelectedItem()))
						overlayColor = java.awt.Color.GREEN;
				}

				if(visualFit.isSelected())
					MMStudio.getInstance().getSnapLiveManager().getDisplay().getImagePlus().setOverlay(
							new OvalRoi((int)(roi.x + loc.getX() - 2), (int)(roi.y + loc.getY() - 2), 4, 4),
							overlayColor, 1, null);

			} else {
				if(visualFit.isSelected())
					MMStudio.getInstance().getSnapLiveManager().getDisplay().getImagePlus().setOverlay(null);
			}
		}

		setup.getZStage().setPosition(basez);
		setup.getZStage().waitFor();

		c = c.scalarMultiply(1/intsum);

		if(zmethod.getSelectedItem().equals(ZMODE_MAX_INTENSITY))
			c = new Vector3D(c.getX(), c.getY(), maxZ);

		ReportingUtils.logMessage("RETURNING " + vToS(c));

		return c;
	};

	private void addTags(TaggedImage ti, int channel) throws JSONException {
		MDUtils.setChannelIndex(ti.tags, channel);
		MDUtils.setFrameIndex(ti.tags, 0);
		MDUtils.setPositionIndex(ti.tags, 0);
		MDUtils.setSliceIndex(ti.tags, 0);
		// TODO: Correct the below based on MM2 API
//		try {
//			ti.tags.put("Summary", MMStudio.getInstance().getAcquisition(SnapLiveManager.SIMPLE_ACQ).getSummaryMetadata());
//		} catch (MMScriptException ex) {
//			ReportingUtils.logError("Error adding summary metadata to tags");
//		}
//		gui.displayImage(ti);
	}

	private boolean getNextBead() throws Exception {
		Vector3D next = scanBead((Double)firstDelta.getValue());

		if(next.isNaN())
			next = scanBead((Double)secondDelta.getValue());

		if(next.isNaN())
			return false;

		setup.setPosition(next);

		((DefaultListModel)pointsTable.getModel()).addElement(next);

		ImageProcessor ip = gui.getSnapLiveManager().getDisplay().getImagePlus().getProcessor();

		Rectangle roi = ip.getRoi();

		roi.setLocation((ip.getWidth() - roi.width) / 2,
				(ip.getHeight() - roi.height) / 2);

		gui.getSnapLiveManager().getDisplay().getImagePlus().setRoi(roi);

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
				double newT = setup.getAngle() + setup.getThetaStage().getStepSize();

				if(newT > 180)
					newT -= 360;

				setup.getThetaStage().setPosition(newT);
				setup.getThetaStage().waitFor();
				Thread.sleep(50);
				if(!getNextBead()) {
					JOptionPane.showMessageDialog(AutoWindow.this,
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
				JOptionPane.showMessageDialog(AutoWindow.this,
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
					boolean live = gui.getSnapLiveManager().getIsLiveModeOn();
					gui.live().setLiveMode( false );
					core.setAutoShutter(false);

					if(setup.getLaser() != null)
						setup.getLaser().setPoweredOn(true);

					do {
						getNextBeadRunnable.run();
					} while(!Thread.interrupted() &&
						(getNextBeadRunnable.getValue() == null ||
						getNextBeadRunnable.getValue() != false));

					if(setup.getLaser() != null)
						setup.getLaser().setPoweredOn(false);

					core.setAutoShutter(true);
					gui.live().setLiveMode(live);
					AutoWindow.this.scanStopped();
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
		} else if(BTN_TWEAKS_FRAME.equals(ae.getActionCommand())) {
			tweaksFrame.setVisible(true);
			tweaksFrame.setLocation(new Point(getLocation().x + getWidth(), getLocation().y));
		} else if(BTN_REVISE.equals(ae.getActionCommand())) {
			DefaultListModel mdl = (DefaultListModel)pointsTable.getModel();

			mdl.set(pointsTable.getSelectedIndex(), setup.getPosition());
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
			double avgY = 0;
			for(int i = 0; i < pointsTable.getModel().getSize(); ++i) {
				Vector3D p = (Vector3D)pointsTable.getModel().getElementAt(i);
				points.add(new Point3f(new float[] {(float) p.getX(), (float) p.getY(), (float) p.getZ()}));
				avgY += p.getY() / pointsTable.getModel().getSize();
			}

			univ.addIcospheres(points, new Color3f(1,0,0), 2, 8, "Beads");

			if(rotAxis != null)
				univ.addLineMesh(customnode.MeshMaker.createDisc(rotAxis.getOrigin().getX(), avgY, rotAxis.getOrigin().getZ(),
						rotAxis.getDirection().getX(), rotAxis.getDirection().getY(), rotAxis.getDirection().getZ(),
						radius, points.size() / 4), new Color3f(0,1,0), "Circle Fit", false);

			univ.show();
		} else if(BTN_IMPORT.equals(ae.getActionCommand())) {
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
		} else if(BTN_AUTOLOC.equals(ae.getActionCommand())) {
			if(gui.getSnapLiveManager().getDisplay() != null)
				highlightBrightest(gui.getSnapLiveManager().getDisplay().getImagePlus());
		}
	}
};
