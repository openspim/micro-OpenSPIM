package spim.progacq;

import ij.process.ImageProcessor;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public interface AntiDrift {
	public abstract Vector3D getAntiDriftOffset();

	public abstract void startNewStack();

	public abstract void tallySlice(Vector3D center, ImageProcessor ip);

	public abstract void finishStack();

	public abstract void finishStack(boolean initial);

	public interface Factory {
		public abstract AntiDrift Manufacture(AcqParams p, AcqRow r);
	}

	public interface Callback {
		public abstract void Apply(Vector3D offset);
	}
}