package spim.gui.calibration;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public interface CalibrationWindow
{

	public abstract Vector3D getRotationOrigin();

	public abstract Vector3D getRotationAxis();

	public abstract boolean getIsCalibrated();

}