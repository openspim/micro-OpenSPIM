package spim.progacq;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

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

		public ColorProcessor getDiff(final Projections other, int dx, int dy, int dz) {
			final int w = xy.getWidth();
			final int h = xy.getHeight();
			final int d = xz.getHeight();

			final ColorProcessor result = new ColorProcessor(w + 1 + d, h + 1 + d);
			final ColorBlitter blitter = new ColorBlitter(result);
			blitter.copyBits(getDiff(xy, other.xy, dx, dy), 0, 0, Blitter.COPY);
			blitter.copyBits(getDiff(xz, other.xz, dx, dz), 0, h, Blitter.COPY);
			blitter.copyBits(getDiff(zy, other.zy, dz, dy), w, 0, Blitter.COPY);
			return result;
		}

		public void show() {
			new ImagePlus("XY", xy).show();
			new ImagePlus("XZ", xz).show();
			new ImagePlus("ZY", zy).show();
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

		public AdjusterGUI(final ImagePlus before, final ImagePlus after, final Callback callback) {
			this(Projections.get(before), Projections.get(after), callback);
		}

		public AdjusterGUI(final Projections before, final Projections after, final Callback callback) {
			this.before = before;
			this.after = after;
			this.callback = callback;

			updateDiff();

			panel = new JPanel() {
				@Override
				public void paintComponent(final Graphics g) {
					super.paintComponent(g);
					g.drawImage(diff, 0, 0, null);
				}
			};
			panel.setPreferredSize(preferredImageSize);
			//panel.addKeyListener(this);
			getContentPane().add(panel);
			//getContentPane().addKeyListener(this);
			addKeyListener(this);
			pack();
		}

		public void keyPressed(final KeyEvent e) {
			switch (e.getKeyCode()) {
				case KeyEvent.VK_ENTER:
					dispose();
					callback.offset(dx, dy, dz);
					break;
				case KeyEvent.VK_UP:
					dy--;
					updateDiff();
					break;
				case KeyEvent.VK_DOWN:
					dy++;
					updateDiff();
					break;
				case KeyEvent.VK_LEFT:
					dx--;
					updateDiff();
					break;
				case KeyEvent.VK_RIGHT:
					dx++;
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_UP:
					dz--;
					updateDiff();
					break;
				case KeyEvent.VK_PAGE_DOWN:
					dz++;
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
			final ColorProcessor cp = before.getDiff(after, dx, dy, dz);
			preferredImageSize = new Dimension(cp.getWidth(), cp.getHeight());
			diff = cp.createImage();
			if (panel != null) panel.repaint();
		}
	}

	private Vector3D offset;
	private Projections first, latest;
	private AdjusterGUI gui;

	public ProjDiffAntiDrift() {
		offset = Vector3D.ZERO;
		first = null;
	}

	@Override
	public Vector3D getAntiDriftOffset() {
		return offset;
	}

	@Override
	public void startNewStack() {
		if(gui != null) {
			gui.setVisible(false);
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
		if(!initial) {
			gui = new AdjusterGUI(first, latest, new Callback() {
				@Override
				public void offset(int dx, int dy, int dz) {
					ProjDiffAntiDrift.this.offset.add(new Vector3D(-dx, -dy, -dz));
				}
			});
			gui.setVisible(true);
		}

		first = latest;
	}

}
