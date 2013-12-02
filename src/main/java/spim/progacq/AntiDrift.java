package spim.progacq;

import ij.process.ImageProcessor;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public abstract class AntiDrift {
	public abstract void startNewStack();

	public abstract void tallySlice(Vector3D center, ImageProcessor ip);

	public abstract void finishStack();

	public abstract void finishStack(boolean initial);

	public interface Factory {
		public abstract AntiDrift manufacture(AcqParams p, AcqRow r);
	}

	public interface Callback {
		public abstract void applyOffset(Vector3D offset);
	}

	private Callback callback;

	public void setCallback(Callback cb) {
		callback = cb;
	}

	protected void invokeCallback(Vector3D offset) {
		if(callback != null)
			callback.applyOffset(offset);
		else
			ij.IJ.log("Anti-drift with no callback tried to invoke!");
	}
}
