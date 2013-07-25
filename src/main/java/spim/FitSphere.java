package spim;

import java.util.Arrays;

import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.direct.NelderMeadSimplex;
import org.apache.commons.math.optimization.direct.SimplexOptimizer;
import org.micromanager.utils.ReportingUtils;

public class FitSphere {
	static int XC = 0;
	static int YC = 1;
	static int ZC = 2;
	static int RADIUS = 3;
	static int PARAMLEN = 4;

	Vector3D center;
	double radius;
	double error;

	public FitSphere(final Vector3D[] points) {
		Vector3D mean = Vector3D.ZERO;

		for(Vector3D point : points)
			mean = mean.add(point.scalarMultiply(1D/(double)points.length));

		ReportingUtils.logMessage("MEAN: " + mean.toString());

		final Vector3D endMean = new Vector3D(mean.getX(), mean.getY(), mean.getZ());
		
		double[] params = new double[PARAMLEN];
		params[RADIUS] = 0.5*points[0].subtract(points[points.length-1]).getNorm();

		Vector3D centerGuess = points[points.length/2].subtract(mean).add(mean.subtract(points[points.length/2]).normalize().scalarMultiply(params[RADIUS]));
		params[XC] = centerGuess.getX(); params[YC] = centerGuess.getY(); params[ZC] = centerGuess.getZ();

		double[] steps = new double[PARAMLEN];
		steps[XC] = steps[YC] = steps[ZC] = 0.001;
		steps[RADIUS] = 0.00001;

		ReportingUtils.logMessage("Initial parameters: " + Arrays.toString(params) + ", steps: " + Arrays.toString(steps));

		MultivariateRealFunction MRF = new MultivariateRealFunction() {
			@Override
			public double value(double[] params) {
				Vector3D center = new Vector3D(params[XC], params[YC], params[ZC]);

				double err = 0;
				for(Vector3D point : points)
					err += Math.pow(point.subtract(endMean).subtract(center).getNorm() - params[RADIUS], 2D);

//				ReportingUtils.logMessage("value(" + Arrays.toString(params) + "): endMean=" + endMean.toString() + ", err=" + err);

				return err;
			}
		};

		SimplexOptimizer opt = new SimplexOptimizer(1e-6, -1);
		NelderMeadSimplex nm = new NelderMeadSimplex(steps);

		opt.setSimplex(nm);

		double[] result = null;
		try {
			result = opt.optimize(5000000, MRF, GoalType.MINIMIZE, params).getPoint();
			error = Math.sqrt(MRF.value(result));
		} catch(Throwable t) {
			ReportingUtils.logError(t, "Optimization failed!");
		}

		ReportingUtils.logMessage("Fit: " + Arrays.toString(result));

		center = new Vector3D(result[XC], result[YC], result[ZC]).add(mean);
		radius = result[RADIUS];
	};

	public Vector3D getCenter() {
		return center;
	};

	public double getRadius() {
		return radius;
	};

	public double getError() {
		return error;
	};
}
