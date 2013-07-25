package spim;

import spim.SPIMAcquisition;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import java.awt.BorderLayout;
import java.awt.Insets;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import java.awt.Color;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.util.Dictionary;
import java.util.Hashtable;

public abstract class MotorSlider extends JPanel implements ChangeListener {
	protected JSlider slider;
	protected JButton plus, minus;
	protected JTextField updating;
	protected Color background;
	protected String prefsKey;

	public MotorSlider(int min, int max, int current) {
		this(min, max, current, null);
	}

	public MotorSlider(int min, int max, int current, String prefsKey) {
		slider = new JSlider(JSlider.HORIZONTAL, min, max, Math.min(max, Math.max(min, SPIMAcquisition.prefsGet(prefsKey, current))));

		this.prefsKey = prefsKey;

		slider.setMinorTickSpacing((int)((max - min) / 40));
		slider.setMajorTickSpacing((int)((max - min) / 5));
		slider.setPaintTrack(true);
		slider.setPaintTicks(true);

		if (min == 1)
			setLabelTable(SPIMAcquisition.makeLabelTable(min, max, 5));
		slider.setPaintLabels(true);

		slider.addChangeListener(this);

		minus = new JButton("-");
		minus.setToolTipText("Shift+Click to decrement by 10");
		minus.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				increment((e.getModifiers() & InputEvent.SHIFT_MASK) == 0 ? -1 : -10);
			}
		});
		minus.setMargin(new Insets(0, 10, 0, 10));
		plus = new JButton("+");
		plus.setToolTipText("Shift+Click to increment by 10");
		plus.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				increment((e.getModifiers() & InputEvent.SHIFT_MASK) == 0 ? +1 : +10);
			}
		});
		plus.setMargin(new Insets(0, 10, 0, 10));

		setLayout(new BorderLayout());
		add(minus, BorderLayout.WEST);
		add(slider, BorderLayout.CENTER);
		add(plus, BorderLayout.EAST);

		invokeChanger = true;
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		final int value = slider.getValue();
		if (slider.getValueIsAdjusting()) {
			if (updating != null) {
				if (background == null)
					background = updating.getBackground();
				updating.setBackground(Color.YELLOW);
				updating.setText("" + value);
			}
		} else {
			if (updating != null)
				updating.setBackground(background);

			if(invokeChanger) {
				new Thread() {
					@Override
					public void run() {
						synchronized (MotorSlider.this) {
							valueChanged(value);
						}
					}
				}.start();
			}
		}
	}

	protected void handleChange(int value) {
		valueChanged(value);
		SPIMAcquisition.prefsSet(prefsKey, value);
	}

	public abstract void valueChanged(int value);

	public int increment(int delta) {
		int value = getValue();
		if (delta < 0)
			delta = Math.max(delta, slider.getMinimum() - value);
		else if (delta > 0)
			delta = Math.min(delta, slider.getMaximum() - value);
		if (delta != 0)
			setValue(value + delta, true);
		return delta;
	}

	public int getValue() {
		return slider.getValue();
	}

	public void setValue(int value) {
		setValue(value, false);
	}

	public void setValue(int value, boolean updateText) {
		if(value == getValue())
			return;

		slider.setValue(value);

		if (updateText && updating != null) {
			updating.setText("" + getValue());
			stateChanged(null);
		}
	}

	protected boolean invokeChanger;

	public void updateValueQuietly(int value) {
		if(slider.getValueIsAdjusting())
			return;

		invokeChanger = false;
		setValue(value, true);
		invokeChanger = true;
	}

	public boolean isEnabled() {
		return slider.isEnabled();
	}

	public void setEnabled(boolean enabled) {
		slider.setEnabled(enabled);
	}

	public int getMinimum() {
		return slider.getMinimum();
	}

	public int getMaximum() {
		return slider.getMaximum();
	}

	public void setMinimum(int value) {
		slider.setMinimum(value);
	}

	public void setMaximum(int value) {
		slider.setMaximum(value);
	}

	public Dictionary getLabelTable() {
		return slider.getLabelTable();
	}

	public void setLabelTable(Dictionary table) {
		slider.setLabelTable(table);
	}
}
