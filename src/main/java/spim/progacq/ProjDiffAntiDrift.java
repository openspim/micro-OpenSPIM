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

import net.imglib2.algorithm.legacy.fft.PhaseCorrelation;
import net.imglib2.algorithm.legacy.fft.PhaseCorrelationPeak;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public class ProjDiffAntiDrift extends AntiDrift {
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

		public void writeDiff(final Projections other, double zratio, Vector3D d, Vector3D c, File file) {
			final ColorProcessor cp = getDiff(other, 1.0D, zratio, d, c);
			ij.IJ.save(new ImagePlus("diff", cp), file.getAbsolutePath());
		}

		public ColorProcessor getDiff(final Projections other, double scale, double zratio, Vector3D dv, Vector3D center) {
			final int w = (int) (xy.getWidth()*scale);
			final int h = (int) (xy.getHeight()*scale);
			final int d = (int) (xz.getHeight()*zratio*scale);

			return makePanel(
					drawCrosshair(getDiff(xy, other.xy, dv.getX(), dv.getY()), w, h, center.getX(), center.getY()),
					drawCrosshair(getDiff(xz, other.xz, dv.getX(), dv.getZ()), w, d, center.getX(), center.getZ()),
					drawCrosshair(getDiff(zy, other.zy, dv.getZ(), dv.getY()), d, h, center.getZ(), center.getY())
					);
		}

		private ColorProcessor drawCrosshair(ColorProcessor on, int fw, int fh, double cx, double cy) {
			int centerX = (int) (cx * fw / on.getWidth() + 0.5);
			int centerY = (int) (cy * fh / on.getHeight() + 0.5);

			on = (ColorProcessor) on.resize(fw, fh);

			int[] pixels = (int[]) on.getPixels();

			if(centerX >= 0 && centerX < fw)
				for(int y = 0; y < fh; ++y)
					pixels[centerX + y * fw] ^= 0x00FFFFFF;

			if(centerY >= 0 && centerY < fh)
				for(int x = 0; x < fw; ++x)
					pixels[x + centerY * fw] ^= 0x00FFFFFF;

			return on;
		}

		private static ColorProcessor makePanel(final ImageProcessor xy, final ImageProcessor xz, final ImageProcessor zy) {
			final int w = xy.getWidth();
			final int h = xy.getHeight();
			final int d = xz.getHeight();
			assert w == xz.getWidth() && h == zy.getHeight() && d == zy.getWidth();

			final ColorProcessor result = new ColorProcessor(w + 1 + d, h + 1 + d);
			final ColorBlitter blitter = new ColorBlitter(result);
			blitter.copyBits(xy, 0, 0, Blitter.COPY);
			blitter.copyBits(xz, 0, h, Blitter.COPY);
			blitter.copyBits(zy, w, 0, Blitter.COPY);
			result.setColor(java.awt.Color.YELLOW);
			result.drawLine(0, h, w+d, h);
			result.drawLine(w, 0, w, h+d);
			return result;
		}

		private static ColorProcessor getDiff(final FloatProcessor fp1, final FloatProcessor fp2, double dx, double dy) {
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
						0 : normalize(fp2.getf(x + (int)dx, y + (int)dy), min2, max2);
					pixels[x + w1 * y] = (value1 << 16) | (value2 << 8) | value1;
				}
			}

			return result;
		}

		private static Img<FloatType> wrap(FloatProcessor fp) {
			return ImagePlusAdapter.wrapFloat(new ImagePlus("", fp));
		}

		private static long[] correlate(final FloatProcessor first, final FloatProcessor second) {
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
			long[] xyc = correlate(xy, other.xy);
			long[] xzc = correlate(xz, other.xz);
			long[] zyc = correlate(zy, other.zy);

			if(xyc == null || xzc == null || zyc == null)
				return Vector3D.ZERO;

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

			for (int i = 1; i <= stack.getSize(); i++)
				p.addXYSlice(stack.getProcessor(i));

			return p;
		}

		public Vector3D getCenter() {
			return new Vector3D(xy.getWidth() / 2.0, xy.getHeight() / 2.0, xz.getHeight() / 2.0);
		}
	}

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
	public void tallySlice(Vector3D center, ImageProcessor ip) {
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
