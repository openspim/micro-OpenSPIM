package spim.hardware;

import mmcorej.CMMCore;

public class PicardTwister extends GenericRotator {
	static {
		Device.installFactory(new Factory() {
			@Override
			public Device manufacture(CMMCore core, String label) {
				return new PicardTwister(core, label);
			}
		}, "Picard Twister", SPIMSetup.SPIMDevice.STAGE_THETA);
	}

	public PicardTwister(CMMCore core, String label) {
		super(core, label);
	}

	@Override
	public double getStepSize() {
		return 1.8; // degrees/step
	}

	@Override
	public double getPosition() {
		double currentPosition = stepsToDegrees(super.getPosition());

//		if (Math.abs(currentPosition) <= 180) // TODO: If wrap-around is really important, these should be uncommented and fixed.
			return currentPosition;
/*		else if (currentPosition > 180) // Alternatively, this code might be moved to the Picard DAL -- almost makes more sense there.
			return ((currentPosition - 180) % 360) - 180;
		else
			return ((currentPosition + 180) % 360) + 180;*/
	}

	@Override
	public void setPosition(double pos) {
		double currentPosition = getPosition();

//		if (Math.abs(pos - currentPosition) <= 180.0)
			super.setPosition(super.getPosition() + degreesToSteps(pos - currentPosition));
/*		else
			super.setPosition(super.getPosition() + degreesToSteps(pos - currentPosition - 360*Math.signum(pos - currentPosition)));*/
	}

	private double stepsToDegrees(double steps) {
		return steps * 180.0D / 100.0D;
	}

	private double degreesToSteps(double degrees) {
		return degrees * 100.0D / 180.0D;
	}
}
