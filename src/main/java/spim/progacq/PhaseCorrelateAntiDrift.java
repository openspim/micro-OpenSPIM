package spim.progacq;

import ij.process.ImageProcessor;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.transform.FastFourierTransformer;


public class PhaseCorrelateAntiDrift extends AntiDrift {

	public PhaseCorrelateAntiDrift() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void startNewStack() {
		// TODO Auto-generated method stub

	}

	@Override
	public void tallySlice(Vector3D center, ImageProcessor ip) {
		// TODO Auto-generated method stub

	}

	@Override
	public void finishStack() {
		// TODO Auto-generated method stub

	}

	@Override
	public void finishStack(boolean initial) {
		// TODO Auto-generated method stub

	}

	private static Complex[][] Promote(ImageProcessor data) {
		int w = data.getWidth();
		int h = data.getHeight();

		// If width or height are not powers of two, we must round up.
		if(Integer.bitCount(w) != 1)
			w = (int) Math.pow(2, Math.ceil(Math.log(w)/Math.log(2)));

		if(Integer.bitCount(h) != 1)
			h = (int) Math.pow(2, Math.ceil(Math.log(h)/Math.log(2)));

		Complex[][] out = new Complex[h][w];
		for(int y=0; y < h; ++y)
			for(int x=0; x < w; ++x)
				out[y][x] = new Complex(data.getInterpolatedValue(x, y));

		return out;
	}

	private static Complex[] column(Complex[][] in, int c) {
		Complex[] out = new Complex[in[c].length];

		for(int r=0; r < in[c].length; ++r)
			out[r] = in[r][c];

		return out;
	}

	private static Complex[] pit(Complex[][][] in, int x, int y) {
		Complex[] out = new Complex[in[x][y].length];

		for(int z=0; z < in[x][y].length; ++z)
			out[z] = in[x][y][z];

		return out;
	}

	private static void Fourier2D(Complex[][] in, boolean forward) {
		int w = in.length;
		int h = in[0].length;

		FastFourierTransformer fft = new FastFourierTransformer();

		for(int y=0; y < h; ++y)
			if(forward)
				fft.transform(in[y]);
			else
				fft.inversetransform(in[y]);

		for(int x=0; x < w; ++x)
			if(forward)
				fft.transform(column(in,x));
			else
				fft.inversetransform(column(in,x));
	}
}
