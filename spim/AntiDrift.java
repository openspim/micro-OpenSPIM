package spim;

import ij.IJ;
import ij.process.ImageProcessor;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public class AntiDrift {
	private Vector3D initialMean, cachedMean, runningMean, counts;
	private double runningIntensity, currentMin, currentMax;

	public AntiDrift() {
		initialMean = Vector3D.ZERO;
		cachedMean = Vector3D.ZERO;
		runningMean = Vector3D.ZERO;
		runningIntensity = 0.0D;
		currentMin = Double.MAX_VALUE;
		currentMax = Double.MIN_VALUE;
	}

	public Vector3D getIntensityCenter() {
		double bg = (currentMin + (currentMax - currentMin) * 0.0) * 0.0;
		Vector3D avgc = new Vector3D(counts.getX()/counts.getZ(),counts.getY()/counts.getZ(),counts.getZ());

		double i = runningIntensity - bg*avgc.getX()*avgc.getY()*avgc.getZ();

		Vector3D backgroundContributions = new Vector3D(
			bg*avgc.getX()*(avgc.getX()+1)/2*avgc.getY()*avgc.getZ(),
			bg*avgc.getX()*avgc.getY()*(avgc.getY()+1)/2*avgc.getZ(),
			bg*avgc.getX()*avgc.getY()*avgc.getZ()*(avgc.getZ()+1)/2
		);

		IJ.log("i=" + i + " (" + runningIntensity + ", " + bg + "), sum=" + runningMean.toString());

		return runningMean.subtract(backgroundContributions).scalarMultiply(1/i);
	}

	public Vector3D getAntiDriftOffset() {
		return initialMean.subtract(cachedMean);
	}

	public void startNewStack() {
		runningMean = Vector3D.ZERO;
		counts = Vector3D.ZERO;
		runningIntensity = 0.0D;
		currentMin = Double.MAX_VALUE;
		currentMax = Double.MIN_VALUE;
	}

	public void tallySlice(Vector3D center, ImageProcessor ip) {
		counts = counts.add(new Vector3D(ip.getWidth(), ip.getHeight(), 1));

		// The - center.get*() become positive in the subtraction below.
		double cx = ip.getWidth()/2 - center.getX();
		double cy = ip.getHeight()/2 - center.getY();

		double it = 0;

		for(int y=0; y < ip.getHeight(); ++y) {
			for(int x=0; x < ip.getWidth(); ++x) {
				double pv = ip.getPixelValue(x,y) - (ip.getMin() + (ip.getMax() - ip.getMin())*0.3);
				if(pv < 0) {
					//IJ.log(x + ", " + y + ": " + pv);
					pv = 0;
				};

				runningMean = runningMean.add(new Vector3D((x - cx)*pv, (y - cy)*pv, 0));

				it += pv;

				if(pv < currentMin)
					currentMin = pv;

				if(pv > currentMax)
					currentMax = pv;
			}
		}

		runningIntensity += it;
		runningMean = runningMean.add(new Vector3D(0,0,center.getZ()*it));
	}

	public void finishStack() {
		finishStack(initialMean.getNorm() == 0);
	}

	public void finishStack(boolean initial) {
		cachedMean = getIntensityCenter();

		if(initial)
			initialMean = cachedMean;
	}
}
