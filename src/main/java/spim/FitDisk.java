package spim;

import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

public class FitDisk {
	Vector3D center;
	Vector3D normal;
	double radius;
	double error;

	public FitDisk(Vector3D[] pts) {
		// First, acknowledgements:
		// http://www.mathworks.com/matlabcentral/fileexchange/37775-plane-fitting-and-normal-calculation
		// http://www.dtcenter.org/met/users/docs/write_ups/circle_fit.pdf
		//
		// The first task is to fit the planar normal. We then use this plane to map the points into a 2D
		// subspace, to which we fit a hypersphere (actually a circle).
		//
		// Please note that coordinates in the 3D space are referred to as 'u', 'v', and 'w', while 'x'
		// and 'y' are used in the context of the 2D subspace.
		//
		// For the 2D system, we can derive simple algebraic equations for X and Y (which gives us R):
		//
		// Original system:
		// xx + yy + N(x^2 + y^2 - r^2) = 0
		// xxx + xyy = 2(xx*x + xy*y)
		// yxx + yyy = 2(xy*x + yy*y)
		//
		// Uncoupled system:
		// x = (xy*yyy+xy*yxx-xyy*yy-xxx*yy)/(2xy^2-2xx*yy)
		// y = (-xx*yyy-xx*yxx+xyy*xy+xxx*xy)/(2xy^2-2xx*yy)
		// And, of course, R^2 = (uu + vv)/N + x^2 + y^2

		Vector3D mean = Vector3D.ZERO;
		for(Vector3D pt : pts)
			mean = mean.add(pt.scalarMultiply(1/(double)pts.length));

		double uu = uvw(pts, mean, new Vector3D(2, 0, 0));
		double uv = uvw(pts, mean, new Vector3D(1, 1, 0));
		double uw = uvw(pts, mean, new Vector3D(1, 0, 1));
		double vv = uvw(pts, mean, new Vector3D(0, 2, 0));
		double vw = uvw(pts, mean, new Vector3D(0, 1, 1));
		double ww = uvw(pts, mean, new Vector3D(0, 0, 2));

		double[][] uvws = new double[][] {
			new double[] { uu, uv, uw },
			new double[] { uv, vv, vw },
			new double[] { uw, vw, ww }
		};

		EigenDecomposition decomp = new EigenDecomposition(new BlockRealMatrix(uvws));

		double[] leastEigenVector = null;
		double leastEigenValue = Double.MAX_VALUE;

		for(int i=0; i < 3; ++i) {
			double eval = decomp.getRealEigenvalue(i);

			if(eval < leastEigenValue) {
				leastEigenValue = eval;
				leastEigenVector = decomp.getEigenvector(i).unitVector().toArray();
			}
		}

		normal = new Vector3D(leastEigenVector[0], leastEigenVector[1], leastEigenVector[2]);

		// If normal is 'negative', reverse it -- the major axis should always be a positive component.
		if(normal.getX() + normal.getY() + normal.getZ() < 0)
			normal = normal.negate();

		// Having decided on the normal, we turn it into a plane onto which the points will be mapped. 
		Plane uvPlane = new Plane(normal);

		Vector2D[] localPoints = new Vector2D[pts.length];
		for(int i=0; i < pts.length; ++i)
			localPoints[i] = uvPlane.toSubSpace(pts[i].subtract(mean));

		// TODO: These can probably all be generated at once...
		double xx = xy(localPoints, new Vector2D(2, 0));
		double xy = xy(localPoints, new Vector2D(1, 1));
		double yy = xy(localPoints, new Vector2D(0, 2));

		double xxx = xy(localPoints, new Vector2D(3, 0));
		double xyy = xy(localPoints, new Vector2D(1, 2));
		double yyy = xy(localPoints, new Vector2D(0, 3));
		double yxx = xy(localPoints, new Vector2D(2, 1));

		double denom = 2*(xy*xy - xx*yy);

		double xc = -(xy*(-yyy-yxx)+xyy*yy+xxx*yy) / denom;
		double yc = (xx*(-yyy-yxx)+xy*xyy+xxx*xy) / denom;

		double r = Math.sqrt((xx + yy)/pts.length + (xc*xc + yc*yc));

		center = mean.add(uvPlane.toSpace(new Vector2D(xc, yc)));
		radius = r;

		error = 0;
		for(Vector3D pt : pts)
			error += Math.pow(pt.distance(center) - radius, 2) / pts.length;
		error = Math.sqrt(error);
	}

	// These next two methos implement the same functionality -- sums of products of components of a list of points,
	// sans mean, where the product is described by 'dot' (representing powers of each component to multiply).
	// Unfortunately there is no simple way to implement both at once.

	private double uvw(Vector3D[] pts, Vector3D mean, Vector3D dot) {
		double sum = 0;

		for(Vector3D pt : pts)
			sum += Math.pow(pt.getX() - mean.getX(), dot.getX())*Math.pow(pt.getY() - mean.getY(), dot.getY())*Math.pow(pt.getZ() - mean.getZ(), dot.getZ());

		return sum;
	}

	// NOTE: There is one important difference: xy assumes the mean of the points is the 2D origin -- that the
	// 3D mean has already been subtracted out.
	private double xy(Vector2D[] pts, Vector2D dot) {
		double sum = 0;

		for(Vector2D pt : pts)
			sum += Math.pow(pt.getX(), dot.getX())*Math.pow(pt.getY(), dot.getY());

		return sum;
	}

	/**
	 * Returns the vector representing the center of the sphere fitted to the points given.
	 * @return Vector3D representing center of fitted sphere
	 */
	public Vector3D getCenter() {
		return center;
	};

	/**
	 * Returns the vector normal to the best-fit plane.
	 * @return
	 */
	public Vector3D getNormal() {
		return normal;
	};

	/**
	 * Gets the fitted sphere's radius
	 * @return Radius of the fitted sphere
	 */
	public double getRadius() {
		return radius;
	};

	/**
	 * Gets the RMSE error in the fit
	 * @return RMSE error of fit
	 */
	public double getError() {
		return error;
	};
}
