package spim;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import spim.MotorSlider;
import spim.IntegerField;

import java.util.Dictionary;

public class LimitedRangeCheckbox extends JPanel implements ActionListener, ItemListener {
	protected IntegerField min, max;
	protected JButton zoomIn, zoomOut;
	protected JCheckBox checkbox;
	protected MotorSlider slider;
	protected Dictionary originalLabels, limitedLabels;
	protected int originalMin, originalMax;
	protected int limitedMin, limitedMax;
	protected String prefsKey;

	public LimitedRangeCheckbox(String label, MotorSlider slider, int min, int max, String prefsKey) {
		String prefsKeyMin = null, prefsKeyMax = null;
		if (prefsKey != null) {
			this.prefsKey = prefsKey;
			prefsKeyMin = prefsKey + ".x";
			prefsKeyMax = prefsKey + ".y";
		}

		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

		zoomIn = new JButton("Zoom In");
		add(zoomIn);

		zoomOut = new JButton("Zoom Out");
		add(zoomOut);

		checkbox = new JCheckBox(label);
		add(checkbox);
		this.min = new IntegerField(min, prefsKeyMin);
		add(this.min);
		add(new JLabel(" to "));
		this.max = new IntegerField(max, prefsKeyMax);
		add(this.max);

		this.slider = slider;
		originalLabels = slider.getLabelTable();
		originalMin = slider.getMinimum();
		originalMax = slider.getMaximum();
		limitedMin = this.min.getValue();
		limitedMax = this.max.getValue();
		checkbox.setSelected(false);

		zoomIn.addActionListener(this);
		zoomOut.addActionListener(this);
		checkbox.addItemListener(this);
	}

	@Override
	public void setEnabled(boolean enabled) {
		checkbox.setEnabled(enabled);
		min.setEnabled(enabled);
		max.setEnabled(enabled);
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			limit(min.getValue(), max.getValue());
		}
		else {
			reset();
		}
	}

	protected void limit(int min, int max) {
		if (min == originalMin && max == originalMax) {
			reset();
			return;
		}
		limitedMin = min;
		limitedMax = max;
		limitedLabels = SPIMAcquisition.makeLabelTable(limitedMin, limitedMax, 5);
		int current = slider.getValue();
		if (current < limitedMin)
			slider.setValue(limitedMin, true);
		else if (current > limitedMax)
			slider.setValue(limitedMax, true);
		slider.setMinimum(limitedMin);
		slider.setMaximum(limitedMax);
		slider.setLabelTable(limitedLabels);
		if (!checkbox.isSelected())
			checkbox.setSelected(true);
	}

	protected void reset() {
		slider.setMinimum(originalMin);
		slider.setMaximum(originalMax);
		slider.setLabelTable(originalLabels);
		if (checkbox.isSelected())
			checkbox.setSelected(false);
	}

	protected void adjustRange(int range) {
		int current = slider.getValue();
		int min, max;
		if (current - originalMin < range / 2)
			min = originalMin;
		else if (originalMax - current < range / 2)
			min = Math.max(originalMin, originalMax - range);
		else
			min = current - range / 2;
		max = Math.min(originalMax, min + range);
		this.min.setText("" + min);
		this.max.setText("" + max);
		limit(min, max);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		int min = originalMin, max = originalMax;
		if (checkbox.isSelected()) {
			min = limitedMin;
			max = limitedMax;
		}
		Object source = e.getSource();
		if (source == zoomIn) {
			int range = (max - min) / 2;
			if (range >= 50)
				adjustRange(range);
		}
		else if (source == zoomOut)
			adjustRange((max - min) * 2);
	}
}
