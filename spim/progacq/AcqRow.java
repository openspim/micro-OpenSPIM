package spim.progacq;

public class AcqRow {
	public enum ZMode {
		SINGLE_POSITION,
		STEPPED_RANGE,
		CONTINUOUS_SWEEP
	};
	
	private ZMode mode;
	private String[] mainPositions;
	private String specialDevLabel;
	private double start;
	private double end;
	private double stepOrSpeed;

	public AcqRow(String[] primarys, String device, String info) {
		specialDevLabel = device;

		if(info.contains("@")) {
			mode = ZMode.CONTINUOUS_SWEEP;
			start = Double.parseDouble(info.substring(0, info.indexOf('-')));
			end = Double.parseDouble(info.substring(info.indexOf('-')+1,info.indexOf('@')));
			stepOrSpeed = Double.parseDouble(info.substring(info.indexOf('@')+1));
		} else if(info.contains(":")) {
			mode = ZMode.STEPPED_RANGE;
			start = Double.parseDouble(info.substring(0, info.indexOf(':')));
			stepOrSpeed = Double.parseDouble(info.substring(info.indexOf(':')+1,info.lastIndexOf(':')));
			end = Double.parseDouble(info.substring(info.lastIndexOf(':')+1));
		} else {
			mode = ZMode.SINGLE_POSITION;
			start = Double.parseDouble(info);
			stepOrSpeed = 0;
			end = Double.parseDouble(info);
		}

		mainPositions = new String[primarys.length + 1];

		for(int i = 0; i < primarys.length; ++i)
			mainPositions[i] = primarys[i];

		mainPositions[primarys.length] = "" + start;
	}

	public double getStartPosition() {
		return start;
	}

	public double getEndPosition() {
		return end;
	}

	public double getVelocity() {
		return stepOrSpeed;
	}

	public double getStepSize() {
		return stepOrSpeed;
	}

	public int getDepth() {
		return (int) ((end - start + 1) / stepOrSpeed);
	}

	public double getX() {
		return Double.parseDouble(mainPositions[0].substring(0, mainPositions[0].indexOf(',')));
	}

	public double getY() {
		return Double.parseDouble(mainPositions[0].substring(mainPositions[0].indexOf(',') + 2));
	}
	
	public double getTheta() {
		return Double.parseDouble(mainPositions[1]);
	}

	public String[] getPrimaryPositions() {
		return mainPositions;
	}

	public String getDevice() {
		return specialDevLabel;
	}

	public ZMode getZMode() {
		return mode;
	}
}
