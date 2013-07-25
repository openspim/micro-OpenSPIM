package spim;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public interface SPIMCalibrator {

	public abstract double getUmPerPixel();

	public abstract Vector3D getRotationOrigin();

	public abstract Vector3D getRotationAxis();

	public abstract boolean getIsCalibrated();

}