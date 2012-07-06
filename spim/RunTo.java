
package spim;

import org.micromanager.utils.ReportingUtils;

public abstract class RunTo extends Thread {
	protected int goal, current = Integer.MAX_VALUE;
	protected String label;

	public RunTo(String label) {
		super(label);
		this.label = label;
	}

	@Override
	public void run() {
		for (;;) try {
			if (goal != current) synchronized (this) {
				if (get() == goal) {
					ReportingUtils.logMessage("Reached goal (" + label + "): " + goal);
					current = goal;
					done();
					notifyAll();
				}
			}
			Thread.sleep(50);
		} catch (Exception e) {
			return;
		}
	}

	public void run(int value) {
		synchronized (this) {
			if (goal == value) {
				done();
				return;
			}
			goal = value;
			ReportingUtils.logMessage("Setting goal: " + goal);
			try {
				set(goal);
			} catch (Exception e) {
				return;
			}
			synchronized (this) {
				if (!isAlive())
					start();
				try {
					wait();
				} catch (InterruptedException e) {
					return;
				}
				ReportingUtils.logMessage("Reached goal & returning: " + goal);
			}
		}
	}

	public abstract int get() throws Exception ;

	public abstract void set(int value) throws Exception;

	public abstract void done();
}
