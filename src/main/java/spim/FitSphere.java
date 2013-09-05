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
		double[] params = new double[PARAMLEN];

		Vector3D mean = Vector3D.ZERO;

		for(Vector3D point : points) {
			mean = mean.add(1D/(double)points.length, point);

			if(point.distance(points[0])/2 > params[RADIUS])
				params[RADIUS] = point.distance(points[0])/2;
		}

		ReportingUtils.logMessage("MEAN: " + mean.toString());

		params[XC] = mean.getX(); params[YC] = mean.getY(); params[ZC] = mean.getZ();

		double[] steps = new double[PARAMLEN];
		steps[XC] = steps[YC] = steps[ZC] = 0.1;
		steps[RADIUS] = 0.001;

		ReportingUtils.logMessage("Initial parameters: " + Arrays.toString(params) + ", steps: " + Arrays.toString(steps));

		MultivariateRealFunction MRF = new MultivariateRealFunction() {
			@Override
			public double value(double[] params) {
				Vector3D center = new Vector3D(params[XC], params[YC], params[ZC]);

				double err = 0;
				for(Vector3D point : points)
					err += Math.pow(point.distance(center) - params[RADIUS], 2D);

				return err;
			}
		};

		SimplexOptimizer opt = new SimplexOptimizer(1e-6, -1);
		NelderMeadSimplex nm = new NelderMeadSimplex(steps);

		opt.setSimplex(nm);

		double[] result = null;
		try {
			result = opt.optimize(Integer.MAX_VALUE, MRF, GoalType.MINIMIZE, params).getPoint();
			error = Math.sqrt(MRF.value(result));
		} catch(Throwable t) {
			ReportingUtils.logError(t, "Optimization failed!");
		}

		ReportingUtils.logMessage("Fit: " + Arrays.toString(result));

		center = new Vector3D(result[XC], result[YC], result[ZC]);
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
