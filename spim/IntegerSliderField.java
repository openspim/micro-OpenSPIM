
package spim;

import spim.IntegerField;
import spim.MotorSlider;

public class IntegerSliderField extends IntegerField {
	protected MotorSlider slider;

	public IntegerSliderField(MotorSlider slider) {
		super(slider.getValue());
		this.slider = slider;
		((MotorSlider)slider).updating = this;
	}

	@Override
	public void valueChanged(int value) {
		if (slider != null)
			slider.setValue(value);
	}
}
