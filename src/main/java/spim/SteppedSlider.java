package spim;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
public abstract class SteppedSlider extends JPanel implements ActionListener, ChangeListener {
	public final static int LABEL_LEFT = 1;
	public final static int INCREMENT_BUTTONS = 2;
	public final static int RANGE_LIMITS = 4;
	public final static int PLACE_VALUE_BOX = 8;
	public final static int CLAMP_VALUE = 16;
	public final static int ALL = LABEL_LEFT | INCREMENT_BUTTONS | RANGE_LIMITS | PLACE_VALUE_BOX | CLAMP_VALUE;

	private double min, max, step, value;
	private double normalMin, normalMax;

	private JLabel titleLabel, toLabel;
	private JSlider slider;
	private JCheckBox limitRange;
	private DoubleField limitMin, limitMax, valueBox;
	private JButton decrement, increment;
	private int options;

	public SteppedSlider(String label, double min, double max, double step, double current, int options) {
		this.min = normalMin = min;
		this.max = normalMax = max;
		this.step = step;
		this.value = current;
		this.options = options;

		if ((options & INCREMENT_BUTTONS) != 0) {
			decrement = new JButton("-");
			decrement.setToolTipText("Shift+Click to decrement by " + 10 * step);
			decrement.addActionListener(this);
			increment = new JButton("+");
			increment.setToolTipText("Shift+Click to increment by " + 10 * step);
			increment.addActionListener(this);
		}

		slider = new JSlider((int) Math.ceil(min / step), (int) Math.floor(max / step), (int) Math.round(current / step));
		slider.addChangeListener(this);
		slider.setPaintLabels(true);
		slider.setPaintTrack(true);
		slider.setPaintTicks(true);
		rangeChanged();

		valueBox = new DoubleField(value) {
			@Override
			public void valueChanged() {
				SteppedSlider.this.setValue(valueBox.getValue());
			}
		};

		if ((options & RANGE_LIMITS) != 0) {
			limitRange = new JCheckBox("Limit range:");
			limitRange.addChangeListener(this);

			limitMin = new DoubleField(min + (max - min) * 0.25) {
				@Override
				public void valueChanged() {
					if (limitRange.isSelected()) {
						SteppedSlider.this.min = this.getValue();
						SteppedSlider.this.rangeChanged();
					}
				}
			};

			limitMax = new DoubleField(max - (max - min) * 0.25) {
				@Override
				public void valueChanged() {
					if (limitRange.isSelected()) {
						SteppedSlider.this.max = this.getValue();
						SteppedSlider.this.rangeChanged();
					}
				}
			};
		}

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		if ((options & LABEL_LEFT) == 0)
			add(LayoutUtils.horizPanel(Box.createHorizontalGlue(), titleLabel = new JLabel(label), Box.createHorizontalGlue()));

		JPanel sliderBox = new JPanel();
		sliderBox.setLayout(new BoxLayout(sliderBox, BoxLayout.X_AXIS));

		int sliderHeight = slider.getPreferredSize().height;

		if ((options & LABEL_LEFT) != 0)
			sliderBox.add(titleLabel = new JLabel(label));

		if ((options & INCREMENT_BUTTONS) != 0) {
			decrement.setPreferredSize(new Dimension(sliderHeight, sliderHeight));
			decrement.setMargin(new Insets(sliderHeight / 2 - 6, sliderHeight / 2 - 6, sliderHeight / 2 - 6, sliderHeight / 2 - 6));
			sliderBox.add(decrement);
		}

		sliderBox.add(slider);

		if ((options & INCREMENT_BUTTONS) != 0) {
			increment.setPreferredSize(new Dimension(sliderHeight, sliderHeight));
			increment.setMargin(new Insets(sliderHeight / 2 - 6, sliderHeight / 2 - 6, sliderHeight / 2 - 6, sliderHeight / 2 - 6));
			sliderBox.add(increment);
		}

		add(sliderBox);

		if ((options & (PLACE_VALUE_BOX | RANGE_LIMITS)) != 0) {
			JPanel bottomBox = new JPanel();
			bottomBox.setLayout(new BoxLayout(bottomBox, BoxLayout.X_AXIS));

			if ((options & PLACE_VALUE_BOX) != 0) {
				bottomBox.add(new JLabel("Current: "));
				bottomBox.add(valueBox);
			}

			if ((options & RANGE_LIMITS) != 0) {
				bottomBox.add(Box.createHorizontalGlue());
				bottomBox.add(limitRange);
				bottomBox.add(limitMin);
				bottomBox.add(toLabel = new JLabel(" to "));
				bottomBox.add(limitMax);
			}

			add(bottomBox);
		}
	}

	public void trySetValue(final double value, final boolean raiseEvent) {
		if (slider.getValueIsAdjusting() || this.value == value)
			return;

		setValue(value, raiseEvent);
	}

	public void setValue(final double value, final boolean raiseEvent) {
		this.value = value;

		updateDisplay.run();

		if (raiseEvent)
			valueChanged();
	}

	protected Runnable updateDisplay = new Runnable() {
		@Override
		public void run() {
			if (!SwingUtilities.isEventDispatchThread()) {
				SwingUtilities.invokeLater(this);
				return;
			}

			if (valueBox.getValue() != value)
				valueBox.setValue(value);

			// This is inelegant, but I don't have many other ideas. And it works pretty fluidly.
			if (slider.getValue() != (int) (value / step)) {
				slider.removeChangeListener(SteppedSlider.this);
				slider.setValue((int) (value / step));
				slider.addChangeListener(SteppedSlider.this);
			}
		}
	};

	public void setValue(double value) {
		setValue(value, true);
	}

	public double getValue() {
		return value;
	}

	public double getMinimum() {
		return min;
	}

	public double getMaximum() {
		return max;
	}

	public JTextField getValueBox() {
		return valueBox;
	}

	public void setEnabled(boolean enable) {
		super.setEnabled(enable);

		valueBox.setEnabled(enable);
		slider.setEnabled(enable);
		titleLabel.setEnabled(enable);

		if (increment != null)
			increment.setEnabled(enable);
		if (decrement != null)
			decrement.setEnabled(enable);

		if (limitRange != null)
			limitRange.setEnabled(enable);
		if (limitMin != null)
			limitMin.setEnabled(enable);
		if (toLabel != null)
			toLabel.setEnabled(enable);
		if (limitMax != null)
			limitMax.setEnabled(enable);
	}

	public abstract void valueChanged();

	@Override
	public void actionPerformed(ActionEvent ae) {
		double delta = (increment == ae.getSource() ? step : -step);

		if ((ae.getModifiers() & ActionEvent.SHIFT_MASK) != 0)
			delta *= 10;

		double value = getValue() + delta;

		if ((this.options & CLAMP_VALUE) != 0)
			value = Math.min(max, Math.max(min, value));

		setValue(value);
	}

	@Override
	public void stateChanged(ChangeEvent ce) {
		if (ce.getSource() == slider) {
			setValue(Math.max(min, Math.min(max, slider.getValue() * step)), !slider.getValueIsAdjusting());
		} else if (ce.getSource() == limitRange) {
			min = limitRange.isSelected() ? limitMin.getValue() : normalMin;
			max = limitRange.isSelected() ? limitMax.getValue() : normalMax;
			rangeChanged();
		}
	}

	private void rangeChanged() {
		slider.setMinimum((int) Math.ceil(min / step));
		slider.setMaximum((int) Math.floor(max / step));
		slider.setMajorTickSpacing((int) ((max - min) / (10 * step)));
		slider.setMinorTickSpacing((int) ((max - min) / (50 * step)));
		slider.setLabelTable(makeLabelTable(min, max, step, 10, step));

		if (value < min)
			setValue(min);
		else if (value > max)
			setValue(max);
	}

	@SuppressWarnings("rawtypes")
	private static Dictionary makeLabelTable(double min, double max, double step, int divisions, double roundto) {
		int imin = (int) Math.ceil(min / step);
		int imax = (int) Math.floor(max / step);

		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();

		table.put(imin, new JLabel("" + min));
		table.put(imax, new JLabel("" + max));

		for (int i = 1; i <= divisions - 1; ++i) {
			int value = imin + (imax - imin) / divisions * i;

			value = (int) (Math.round(value / (roundto / step)) * (roundto / step));

			table.put(value, new JLabel("" + value * step));
		}

		return table;
	}

	private static abstract class DoubleField extends JTextField implements KeyListener, FocusListener {
		public DoubleField(double val) {
			super(String.format("%.2f", val), 6);

			addKeyListener(this);
			addFocusListener(this);

			setMaximumSize(getPreferredSize());
		}

		public double getValue() {
			try {
				return Double.parseDouble(getText());
			} catch (NumberFormatException nfe) {
				return Double.NaN; // this is bad. :( I should probably just let the exception propagate.
			}
		}

		public void setValue(double value) {
			setText(String.format("%.2f", value));
		}

		@Override
		public void keyPressed(KeyEvent ke) {
		}

		@Override
		public void keyReleased(KeyEvent ke) {
			if (ke.getKeyCode() == KeyEvent.VK_ENTER && getValue() != Double.NaN)
				this.transferFocus();
		}

		@Override
		public void keyTyped(KeyEvent ke) {
		}

		@Override
		public void focusGained(FocusEvent e) {
		}

		@Override
		public void focusLost(FocusEvent e) {
			valueChanged();
		}

		public abstract void valueChanged();
	}
}
