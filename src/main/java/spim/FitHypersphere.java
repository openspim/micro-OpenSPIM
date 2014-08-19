package spim;

import ij.IJ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * Fits a hypersphere to a number of n-dimensional points.
 *
 * The principle is described in {@link fiji.util.Circle_Fitter}.
 */
public class FitHypersphere {
	protected double[] center;
	protected double radius;

	public FitHypersphere(Collection<double[]> points) {
		int dim = -1;
		double[][] lhs = null;
		double[] rhs = null;
		int count = 0;
		double[] mean = null;
		for (double[] point : points) {
			if (count == 0) {
				dim = point.length;
				lhs = new double[dim][dim];
				rhs = new double[dim];
				mean = new double[dim];
			}
			for (int j = 0; j < dim; j++)
				mean[j] += point[j];
			count++;
		}
		for (int j = 0; j < dim; j++)
			mean[j] /= count;
		for (double[] point : points) {
			for (int j = 0; j < dim; j++) {
				double pj = point[j] - mean[j];
				for (int i = 0; i <= j; i++) {
					double pi = point[i] - mean[i];
					lhs[j][i] += pi * pj;
					rhs[j] += pi * pi * pj;
				}
			}
		}
		for (int j = 0; j < dim; j++) {
			for (int i = j + 1; i < dim; i++)
				lhs[i][j] = lhs[j][i];
			rhs[j] /= 2;
		}

		RealMatrix left = MatrixUtils.createRealMatrix(lhs);
		DecompositionSolver solver = new LUDecomposition(left).getSolver();
		RealVector right = MatrixUtils.createRealVector(rhs);
		RealVector solution = solver.solve(right);
		center = solution.toArray();
		for (int j = 0; j < dim; j++)
			center[j] += mean[j];
		radius = Math.sqrt(left.getTrace() / count + solution.dotProduct(solution));
	}

	public double[] getCenter() {
		return center;
	}

	public double getRadius() {
		return radius;
	}

	public static void main(String[] args) {
		int count = 10000;
		double radius = 150;
		double[] center = { 20, 135, -17 };
		Random random = new Random(17);
		List<double[]> points = new ArrayList<double[]>();
		for (int i = 0; i < count; i++) {
			double[] point = new double[center.length];
			double dist = 0;
			for (int j = 0; j < point.length; j++) {
				point[j] = random.nextDouble() * 2 - 1;
				dist += point[j] * point[j];
			}
			dist = Math.sqrt(dist);
			for (int j = 0; j < point.length; j++)
				point[j] = center[j] + point[j] * radius / dist;
			points.add(point);
		}
		FitHypersphere fit = new FitHypersphere(points);
		IJ.log("center: " + Arrays.toString(fit.getCenter()));
		IJ.log("radius: " + fit.getRadius());
	}
}
