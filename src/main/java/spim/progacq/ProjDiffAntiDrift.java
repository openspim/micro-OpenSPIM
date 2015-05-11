package spim.progacq;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.JPanel;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.Blitter;
import ij.process.ColorBlitter;
import ij.process.ColorProcessor;
import ij.process.FloatBlitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class ProjDiffAntiDrift extends AntiDrift {


	@SuppressWarnings("serial")
	private static class AdjusterGUI extends JFrame implements KeyListener {
		private Projections before, after;
		private Callback callback;
		private Vector3D offset;
		private Dimension preferredImageSize;
		private Image diff;
		private JPanel panel;
		private double scale, zratio;
		private Vector3D center;

		public AdjusterGUI(final Vector3D loc, final double theta, final long tp,
				final double izratio, final Projections first, final Projections latest,
				final Vector3D initial, Vector3D center, final Callback callback) {
			System.gc();

			this.before = first;
			this.after = latest;
			this.callback = callback;
			this.offset = initial;
			this.center = center;

			scale = getToolkit().getScreenSize().height / (after.largestDimension() * 2) * 0.9;
			zratio = izratio;

			updateDiff();

			panel = new JPanel() {
				@Override
				public void paintComponent(final Graphics g) {
					super.paintComponent(g);
					g.drawImage(diff, 0, 0, null);
				}
			};
			panel.setPreferredSize(preferredImageSize);
			getContentPane().add(panel);
			addKeyListener(this);
			pack();
			setTitle(String.format("xyz: %.2f x %.2f x %.2f, theta: %.2f, timepoint %02d", loc.getX(), loc.getY(), loc.getZ(), theta, tp));
		}

		public void keyPressed(final KeyEvent e) {
			switch (e.getKeyCode()) {
				case KeyEvent.VK_ENTER:
					dispose();
					callback.applyOffset(offset);
					break;
				case KeyEvent.VK_ESCAPE:
					dispose();
					callback.applyOffset(Vector3D.ZERO);
					break;
				case KeyEvent.VK_UP:
					offset = offset.add(new Vector3D(0, (e.isShiftDown() ? 10 : 1), 0));
					updateDiff();
					break;
				case KeyEvent.VK_DOWN:
					offset = offset.subtract(new Vector3D(0, (e.isShiftDown() ? 10 : 1), 0));
					updateDiff();
					break;
				case KeyEvent.VK_LEFT:
					offset = offset.add(new Vector3D((e.isShiftDown() ? 10 : 1), 0, 0));
					updateDiff();
					break;
				case KeyEvent.VK_RIGHT:
					offset = offset.subtract(new Vector3D((e.isShiftDown() ? 10 : 1), 0, 0));
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_UP:
					offset = offset.subtract(new Vector3D(0, 0, (e.isShiftDown() ? 10 : 1)));
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_DOWN:
					offset = offset.add(new Vector3D(0, 0, (e.isShiftDown() ? 10 : 1)));
					updateDiff();
					break;
				case KeyEvent.VK_MINUS:
				case KeyEvent.VK_END:
					scale -= 0.1;
					updateDiff();
					break;
				case KeyEvent.VK_EQUALS:
				case KeyEvent.VK_HOME:
					scale += 0.1;
					updateDiff();
					break;
			}
		}

		public void keyReleased(final KeyEvent e) {
			// do nothing
		}

		public void keyTyped(final KeyEvent e) {
			// do nothing
		}

		private void updateDiff() {
			final ColorProcessor cp = before.getDiff(after, scale, zratio, offset, center);
			preferredImageSize = new Dimension(cp.getWidth(), cp.getHeight());
			if(diff != null) {
				diff.flush();
				diff = null;
			}
			diff = cp.createImage();
			if (panel != null) {
				panel.setPreferredSize(preferredImageSize);
				pack();
				panel.repaint();
			}
		}

		@Override
		public void dispose() {
			diff.flush();
			diff = null;
			before = after = null;
			super.dispose();
		}
	}

	private Vector3D lastCorrection;
	private Projections first, latest;
	private AdjusterGUI gui;

	private long tp;
	private Vector3D loc;
	private double theta;
	private double zstep;

	private File outputDir;
	private double zratio;

	public ProjDiffAntiDrift(File outDir, AcqParams p, AcqRow r) {
		lastCorrection = Vector3D.ZERO;
		first = null;

		loc = new Vector3D(r.getX(), r.getY(), r.getZStartPosition());
		theta = r.getTheta();
		tp = 1;
		zstep = r.getZStepSize();
		zratio = zstep/p.getCore().getPixelSizeUm();

		if(outDir != null) {
			String xyz = String.format("XYZ%.2fx%.2fx%.2f_Theta%.2f", loc.getX(), loc.getY(), loc.getZ(), theta);
			File saveDir = new File(new File(outDir, "diffs"), xyz);

			if(!saveDir.exists() && !saveDir.mkdirs()) {
				ij.IJ.log("Couldn't create output directory " + saveDir.getAbsolutePath());
			} else {
				outputDir = saveDir;
			}
		}
	}

	@Override
	public void startNewStack() {
		if(gui != null && gui.isVisible()) {
			gui.callback.applyOffset(gui.offset);

			gui.setVisible(false);
			gui.dispose();
			gui = null;
		}

		latest = new Projections();
	}

	@Override
	public void tallySlice(ImageProcessor ip) {
		latest.addXYSlice(ip);
	}

	@Override
	public void finishStack() {
		finishStack(first == null);
	}
	
	private File getOutFile(String tag) {
		if(outputDir != null)
			return new File(outputDir, String.format("diff_TL%02d_%s.tiff", tp, tag));
		else
			return null;
	}

	@Override
	public void finishStack(boolean initial) {
		if(initial)
			first = latest;
		
		final Vector3D center = lastCorrection.add(latest.getCenter());

		if(outputDir != null)
			latest.writeDiff(first, zratio, lastCorrection, center, getOutFile("initial"));

		Vector3D init = latest.correlateAndAverage(first);

		if(outputDir != null)
			latest.writeDiff(first, zratio, init, center, getOutFile("suggested"));

		gui = new AdjusterGUI(loc, theta, tp, zratio, first, latest, init, center, new AntiDrift.Callback() {
			@Override
			public void applyOffset(Vector3D offs) {
				offs = offs.add(lastCorrection);
				lastCorrection = offs;
				invokeCallback(new Vector3D(-offs.getX(), -offs.getY(), -offs.getZ()*zstep));

				if(outputDir != null)
					latest.writeDiff(first, zratio, offs, center, getOutFile("final"));
			}
		});
		gui.setVisible(true);

		first = latest;
		++tp;
	}

}
