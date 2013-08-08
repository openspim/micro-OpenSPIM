package spim.setup;

import java.util.Arrays;
import java.util.Collection;

import spim.setup.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;

public class PicardTwister extends Stage {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new PicardTwister(core, label);
			}
		}, "Picard Twister", SPIMDevice.STAGE_THETA);
	}

	public PicardTwister(CMMCore core, String label) {
		super(core, label);
	}

	@Override
	public double getVelocity() {
		return 1.0D;
	}

	@Override
	public void setVelocity(double velocity) throws IllegalArgumentException {
		if (velocity != 1.0D)
			throw new IllegalArgumentException("Invalid twister velocity " + velocity);

		// nop
	}

	@Override
	public Collection<Double> getAllowedVelocities() {
		return Arrays.asList(1.0D);
	}

	@Override
	public double getStepSize() {
		return 1.8; // degrees/step
	}

	@Override
	public double getMinPosition() {
		return -180.0D;
	}

	@Override
	public double getMaxPosition() {
		return +180.0D;
	}

	@Override
	public double getPosition() {
		double currentPosition = stepsToDegrees(super.getPosition());

		if (Math.abs(currentPosition) <= 180)
			return currentPosition;
		else if (currentPosition > 180)
			return ((currentPosition - 180) % 360) - 180;
		else
			return ((currentPosition + 180) % 360) + 180;
	}

	@Override
	public void setPosition(double pos) {
		double currentPosition = getPosition();

		if (Math.abs(pos - currentPosition) <= 180.0)
			super.setPosition(super.getPosition() + degreesToSteps(pos - currentPosition));
		else
			super.setPosition(super.getPosition() + degreesToSteps(pos - currentPosition - 360*Math.signum(pos - currentPosition)));
	}

	private double stepsToDegrees(double steps) {
		return steps * 180.0D / 100.0D;
	}

	private double degreesToSteps(double degrees) {
		return degrees * 100.0D / 180.0D;
	}
}
