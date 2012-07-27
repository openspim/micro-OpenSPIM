package spim;

import java.awt.Color;
import java.util.Dictionary;
import java.util.Enumeration;

import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;

public abstract class MappedMotorSlider extends MotorSlider {

	public MappedMotorSlider(int min, int max, int current) {
		this(min, max, current, null);
	}

	public MappedMotorSlider(int min, int max, int current, String prefsKey) {
		super(min, max, current, prefsKey);

		slider.setMajorTickSpacing((int) ((max - min) / 8));
		setLabelTable(SPIMAcquisition.makeLabelTable(min, max, slider.getMajorTickSpacing(), 25, 0));
	}

	public abstract double displayForValue(double in);

	public abstract double valueForDisplay(double in);

	@Override
	public void stateChanged(ChangeEvent e) {
		final int value = slider.getValue();
		if (slider.getValueIsAdjusting()) {
			if (updating != null) {
				if (background == null)
					background = updating.getBackground();
				updating.setBackground(Color.YELLOW);
				updating.setText("" + displayForValue((double) value));
			}
		} else {
			if (updating != null)
				updating.setBackground(background);

			if (invokeChanger) {
				new Thread() {
					@Override
					public void run() {
						synchronized (MappedMotorSlider.this) {
							valueChanged(value);
						}
					}
				}.start();
			}
		}
	}

	@Override
	public int increment(int delta) {
		int value = slider.getValue();
		if (delta < 0)
			delta = Math.max(delta, slider.getMinimum() - value);
		else if (delta > 0)
			delta = Math.min(delta, slider.getMaximum() - value);
		if (delta != 0)
			setValueInternal(value + delta, true);
		return delta;
	}

	@Override
	public int getValue() {
		return (int) displayForValue((double) slider.getValue());
	}

	private void setValueInternal(double value, boolean updateText) {
		if (value == slider.getValue())
			return;

		slider.setValue((int) value);

		if (updateText && updating != null) {
			updating.setText("" + getValue());
			stateChanged(null);
		}
	}

	@Override
	public void setValue(int value, boolean updateText) {
		setValueInternal(valueForDisplay(value), updateText);
	}

	@Override
	public void setLabelTable(Dictionary dict) {
		Enumeration<Integer> keys = dict.keys();

		while (keys.hasMoreElements()) {
			Integer key = keys.nextElement();
			JLabel lbl = (JLabel) dict.get(key);

			lbl.setText("" + (int) displayForValue(key.doubleValue()));
		}

		super.setLabelTable(dict);
	}
}
