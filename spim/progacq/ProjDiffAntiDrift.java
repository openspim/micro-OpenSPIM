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

import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.algorithm.fft.PhaseCorrelationPeak;
import mpicbg.imglib.image.ImagePlusAdapter;
import mpicbg.imglib.type.numeric.real.FloatType;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public class ProjDiffAntiDrift implements AntiDrift {
	private static class Projections {
		private FloatProcessor xy, xz, zy;

		public void addXYSlice(ImageProcessor ip) {
			if (!(ip instanceof FloatProcessor)) {
				ip = (FloatProcessor)ip.convertToFloat();
			}

			final int w = ip.getWidth();
			final int h = ip.getHeight();
			final int d;
			final float[] pixels = (float[])ip.getPixels();

			if (xy == null) {
				d = 1;
				xy = (FloatProcessor)ip.duplicate();
				xz = new FloatProcessor(xy.getWidth(), 1);
				zy = new FloatProcessor(1, xy.getHeight());
			} else {
				if (w != xy.getWidth() || h != xy.getHeight()) {
					new IllegalArgumentException("" + w + "x" + h
						+ " is incompatible with previously recorded "
						+ xy.getWidth() + "x" + xy.getHeight());
				}

				xz = extend(xz, 0, 1);
				zy = extend(zy, 1, 0);
				d = xz.getHeight();

				final float[] xyPixels = (float[])xy.getPixels();
				for (int i = 0; i < w * h; i++) {
					xyPixels[i] = (xyPixels[i] * (d - 1) + pixels[i]) / d;
				}
			}

			final float[] xzPixels = (float[])xz.getPixels();
			for (int x = 0; x < w; x++) {
				float sum = 0;
				for (int y = 0; y < h; y++) {
					sum += pixels[x + y * w];
				}
				xzPixels[x + (d - 1) * w] = sum / h;
			}

			final float[] zyPixels = (float[])zy.getPixels();
			for (int y = 0; y < h; y++) {
				float sum = 0;
				for (int x = 0; x < w; x++) {
					sum += pixels[x + y * w];
				}
				zyPixels[(d - 1) + y * d] = sum / w;
			}
		}

		public double largestDimension() {
			double d = 0;

			if(xy.getWidth() > d)
				d = xy.getWidth();
			if(xy.getHeight() > d)
				d = xy.getHeight();
			if(xz.getWidth() > d)
				d = xz.getWidth();
			if(xz.getHeight() > d)
				d = xz.getHeight();
			if(zy.getWidth() > d)
				d = zy.getWidth();
			if(zy.getHeight() > d)
				d = zy.getHeight();

			return d;
		}

		public void show() {
			new ImagePlus("XY", xy).show();
			new ImagePlus("XZ", xz).show();
			new ImagePlus("ZY", zy).show();
		}

		public ColorProcessor getDiff(final Projections other, double scale, double zratio, int dx, int dy, int dz) {
			final int w = (int) (xy.getWidth()*scale);
			final int h = (int) (xy.getHeight()*scale);
			final int d = (int) (xz.getHeight()*zratio*scale);

			final ColorProcessor result = new ColorProcessor(w + 1 + d, h + 1 + d);
			final ColorBlitter blitter = new ColorBlitter(result);
			blitter.copyBits(getDiff(xy, other.xy, dx, dy).resize(w, h), 0, 0, Blitter.COPY);
			blitter.copyBits(getDiff(xz, other.xz, dx, dz).resize(w, d), 0, h, Blitter.COPY);
			blitter.copyBits(getDiff(zy, other.zy, dz, dy).resize(d, h), w, 0, Blitter.COPY);
			result.setColor(java.awt.Color.YELLOW);
			result.drawLine(0, h, w+d, h);
			result.drawLine(w, 0, w, h+d);
			return result;
		}

		private static ColorProcessor getDiff(final FloatProcessor fp1, final FloatProcessor fp2, int dx, int dy) {
			fp1.findMinAndMax();
			final double min1 = fp1.getMin();
			final double max1 = fp1.getMax();
			final int w1 = fp1.getWidth();
			final int h1 = fp1.getHeight();

			fp2.findMinAndMax();
			final double min2 = fp2.getMin();
			final double max2 = fp2.getMax();
			final int w2 = fp2.getWidth();
			final int h2 = fp2.getHeight();

			final ColorProcessor result = new ColorProcessor(w1, h1);
			final int[] pixels = (int[])result.getPixels();

			for (int y = 0; y < h1; y++) {
				for (int x = 0; x < w1; x++) {
					int value1 = normalize(fp1.getf(x, y), min1, max1);
					int value2 = x + dx < 0 || x + dx >= w2 || y + dy < 0 || y + dy >= h2 ?
						0 : normalize(fp2.getf(x + dx, y + dy), min2, max2);
					pixels[x + w1 * y] = (value1 << 16) | (value2 << 8) | value1;
				}
			}

			return result;
		}

		private static mpicbg.imglib.image.Image<FloatType> wrap(FloatProcessor fp) {
			return ImagePlusAdapter.wrapFloat(new ImagePlus("", fp));
		}

		private static int[] correlate(final FloatProcessor first, final FloatProcessor second) {
			PhaseCorrelation<FloatType, FloatType> pc = new PhaseCorrelation<FloatType, FloatType>(wrap(first), wrap(second));

			if(!pc.checkInput()) {
				ij.IJ.log(pc.getErrorMessage());
				return null;
			}

			if(!pc.process()) {
				ij.IJ.log(pc.getErrorMessage());
				return null;
			}

			PhaseCorrelationPeak peak = pc.getShift();
			return peak.getPosition();
		}

		public Vector3D correlateAndAverage(final Projections other) {
			int[] xyc = correlate(xy, other.xy);
			int[] xzc = correlate(xz, other.xz);
			int[] zyc = correlate(zy, other.zy);

			if(xyc == null || xzc == null || zyc == null)
				return Vector3D.ZERO;

			// TODO: Figure out why this seems to be too little...
			return new Vector3D(xyc[0] + xzc[0], xyc[1] + zyc[1],
				xzc[1] + zyc[0]).scalarMultiply(0.5D);
		}

		private static int normalize(float value, double min, double max) {
			if (value < min) return 0;
			if (value >= max) return 255;
			return (int)((value - min) * 256 / (max - min));
		}

		private static FloatProcessor extend(final FloatProcessor fp, int dx, int dy) {
			final FloatProcessor result = new FloatProcessor(fp.getWidth() + dx, fp.getHeight() + dy);
			new FloatBlitter(result).copyBits(fp, 0, 0, Blitter.COPY);
			return result;
		}

		public static Projections get(final ImagePlus imp) {
			final ImageStack stack = imp.getStack();
			final Projections p = new Projections();
			for (int i = 1; i <= stack.getSize(); i++) {
				p.addXYSlice(stack.getProcessor(i));
			}
			return p;
		}
	}

	public interface Callback {
		void offset(int dx, int dy, int dz);
	}

	private static class AdjusterGUI extends JFrame implements KeyListener {
		private Projections before, after;
		private Callback callback;
		private int dx, dy, dz;
		private Dimension preferredImageSize;
		private Image diff;
		private JPanel panel;
		private double scale, zratio;
		private File outputFile;

		public AdjusterGUI(Vector3D loc, double theta, long tp, double izratio,
				File saveDir, final Projections first, final Projections latest,
				final Callback callback) {
			System.gc();

			this.before = first;
			this.after = latest;
			this.callback = callback;

			if(saveDir != null) {
				String xyz = String.format("XYZ%.2fx%.2fx%.2f_Theta%.2f", loc.getX(), loc.getY(), loc.getZ(), theta);
				String fn = String.format("diff_TL%02d", tp);
				outputFile = new File(new File(saveDir, xyz), fn);

				if(!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
					ij.IJ.log("Couldn't create output directory for diff. (Continuing.)");
					outputFile = null;
				}
			} else {
				outputFile = null;
			}

			scale = getToolkit().getScreenSize().width / (after.largestDimension() * 2) * 0.9;
			zratio = izratio;
			writeDiff("initial");

			Vector3D offs = latest.correlateAndAverage(first);
			dx = (int) offs.getX();
			dy = (int) offs.getY();
			dz = (int) offs.getZ();
			updateDiff();

			writeDiff("suggested");

			ij.IJ.log("Suggested offset: " + offs.toString());

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
					if(outputFile != null)
						writeDiff("final_saved");
					dispose();
					callback.offset(dx, dy, dz);
					break;
				case KeyEvent.VK_ESCAPE:
					if(outputFile != null) {
						dx = dy = dz = 0;
						writeDiff("final_aborted");
					}
					dispose();
					break;
				case KeyEvent.VK_UP:
					dy += (e.isShiftDown() ? 10 : 1);
					updateDiff();
					break;
				case KeyEvent.VK_DOWN:
					dy -= (e.isShiftDown() ? 10 : 1);
					updateDiff();
					break;
				case KeyEvent.VK_LEFT:
					dx += (e.isShiftDown() ? 10 : 1);
					updateDiff();
					break;
				case KeyEvent.VK_RIGHT:
					dx -= (e.isShiftDown() ? 10 : 1);
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_UP:
					dz -= (e.isShiftDown() ? 10 : 1);
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_DOWN:
					dz += (e.isShiftDown() ? 10 : 1);
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
			final ColorProcessor cp = before.getDiff(after, scale, zratio, dx, dy, dz);
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

		private void writeDiff(String suffix) {
			ImagePlus imp = new ImagePlus(getTitle(), before.getDiff(after, scale, zratio, dx, dy, dz));
			ij.IJ.save(imp, outputFile.getAbsolutePath() + "_" + suffix + ".tiff");
			imp = null; // TODO: kill this; temporarily make sure we return memory to initial state with checkMem.
		}

		@Override
		public void dispose() {
			super.dispose();
			diff.flush();
			diff = null;
			before = after = null;
		}
	}

	private Vector3D offset;
	private Projections first, latest;
	private AdjusterGUI gui;

	private long tp;
	private Vector3D loc;
	private double theta;
	private double zstep;

	private File saveDir;
	private double zratio;

	public ProjDiffAntiDrift(File outDir, AcqRow r) {
		offset = Vector3D.ZERO;
		first = null;

		loc = new Vector3D(r.getX(), r.getY(), r.getStartPosition());
		theta = r.getTheta();
		tp = 1;
		zstep = r.getStepSize();
		zratio = zstep*2.3; // TODO: This is hard-coded.

		if(outDir != null)
			saveDir = new File(outDir, "diffs");
	}

	@Override
	public Vector3D getAntiDriftOffset() {
		return offset;
	}

	@Override
	public void startNewStack() {
		if(gui != null && gui.isVisible()) {
			if(gui.outputFile != null)
				gui.writeDiff("final_slid");
			gui.callback.offset(gui.dx, gui.dy, gui.dz);

			gui.setVisible(false);
			gui.dispose();
			gui = null;
		}

		latest = new Projections();
	}

	@Override
	public void tallySlice(Vector3D center, ImageProcessor ip) {
		latest.addXYSlice(ip);
	}

	@Override
	public void finishStack() {
		finishStack(first == null);
	}

	@Override
	public void finishStack(boolean initial) {
		if(first == null)
			first = latest;

		gui = new AdjusterGUI(loc, theta, tp, zratio, saveDir, first, latest, new Callback() {
			@Override
			public void offset(int dx, int dy, int dz) {
				offset = offset.add(new Vector3D(-dx, -dy, -dz*zstep));
			}
		});
		gui.setVisible(true);

		first = latest;
		++tp;
	}

}
