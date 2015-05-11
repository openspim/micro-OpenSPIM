package spim.progacq;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.*;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.io.File;

/**
 * Created by moon on 5/11/15.
 */
public class Projections {
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
		return ImagePlusAdapter.wrapFloat( new ImagePlus( "", fp ) );
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
