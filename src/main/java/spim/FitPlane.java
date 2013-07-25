package spim;

import ij.IJ;

import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.geometry.euclidean.threed.Plane;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.direct.NelderMeadSimplex;
import org.apache.commons.math.optimization.direct.SimplexOptimizer;

public class FitPlane {
	private Plane bestFit;

	public FitPlane(final Vector3D[] vecs) {
		Vector3D mean = Vector3D.ZERO;
		Vector3D avgNorm = Vector3D.PLUS_J;

		for(Vector3D v : vecs) {
			mean = mean.add(v);

			for(Vector3D v2 : vecs)
				for(Vector3D v3 : vecs)
					if(!v.equals(v2) && !v.equals(v3) && !v2.equals(v3))
						avgNorm = avgNorm.add(v2.subtract(v).crossProduct(v3.subtract(v)).normalize());
		}

		avgNorm = avgNorm.scalarMultiply(1/(double)vecs.length).normalize();

		final Vector3D begin = avgNorm.scalarMultiply(mean.dotProduct(avgNorm));

		MultivariateRealFunction mrf = new MultivariateRealFunction() {
			@Override
			public double value(double[] params) {
				double totalError = 0;

				for(Vector3D v : vecs)
					totalError += partialError(params, v);

				return totalError;
			}
		};

		SimplexOptimizer opt = new SimplexOptimizer(1e-6, -1);
		opt.setSimplex(new NelderMeadSimplex(new double[] {1, 1, 1}));
		
		try {
			double[] fit = opt.optimize(5000000, mrf, GoalType.MINIMIZE, new double[] {begin.getX(), begin.getY(), begin.getZ()}).getPoint();

			Vector3D fitvec = new Vector3D(fit[0], fit[1], fit[2]);
			bestFit = new Plane(fitvec, fitvec.normalize());
		} catch(Throwable t) {
			IJ.handleException(t);
		}
	}

	public Vector3D getNormal() {
		return (bestFit != null ? bestFit.getNormal() : Vector3D.PLUS_J);
	}

	private static double partialError(double[] model, Vector3D point) {
		Vector3D loc = new Vector3D(model[0], model[1], model[2]);
		Plane test = new Plane(loc, loc.normalize());

		return Math.pow(test.getOffset(point),2);
	}
}
