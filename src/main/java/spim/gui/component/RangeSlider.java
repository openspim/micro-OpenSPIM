package spim.gui.component;

import spim.gui.util.Layout;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.PanelUI;

public class RangeSlider extends JPanel implements ChangeListener, KeyListener {
	private static final long serialVersionUID = -4704266057756694946L;

	private JTextField min, step, max;
	private JSlider sliderMin, sliderStep, sliderMax;
	private boolean triggering;

	public RangeSlider(Double minv, Double maxv) {
		triggering = true;

		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

		final JLabel minLbl = new JLabel("Min:");
		final JLabel maxLbl = new JLabel("Max:");
		final JLabel stpLbl = new JLabel("Step:");

		min = new JTextField(4);
		min.setMaximumSize(min.getPreferredSize());
		min.addKeyListener(this);

		step = new JTextField(4);
		step.setMaximumSize(step.getPreferredSize());
		step.addKeyListener(this);

		max = new JTextField(4);
		max.setMaximumSize(max.getPreferredSize());
		max.addKeyListener(this);

		sliderMin = new JSlider(minv.intValue(), maxv.intValue(),
				minv.intValue());
		sliderMin.setPaintTicks(true);
		sliderMin.setPaintLabels(true);
		sliderMin.addChangeListener(this);

		sliderMax = new JSlider(minv.intValue(), maxv.intValue(),
				maxv.intValue());
		sliderMax.setPaintTicks(true);
		sliderMax.setPaintLabels(true);
		sliderMax.addChangeListener(this);

		JPanel stepBox = new JPanel();
		stepBox.setLayout(new BoxLayout(stepBox, BoxLayout.LINE_AXIS));

		sliderStep = new JSlider();
		sliderStep.setPaintLabels(true);
		sliderStep.setPaintTicks(true);
		sliderStep.addChangeListener(this);

		setMinMax(minv, maxv);

		JPanel boxesBox = new JPanel();
		boxesBox.setLayout(new GridLayout(3,2,4,4));

		Layout.addAll( boxesBox, minLbl, min, stpLbl, step, maxLbl, max );

		Layout.addAll( this,
				Layout.vertPanel(
						Layout.horizPanel( sliderMin, sliderMax ),
						sliderStep
				),
				Box.createHorizontalGlue(),
				Layout.vertPanel(
						Box.createVerticalGlue(),
						boxesBox,
						Box.createVerticalGlue()
				)
		);

		this.setUI(new PanelUI() {
			@Override
			public Dimension getPreferredSize(JComponent c) {
				int width = (int)(sliderStep.getPreferredSize().width*1.5)
						+ stpLbl.getPreferredSize().width
						+ step.getPreferredSize().width;

				int height = sliderMin.getPreferredSize().height
						+ sliderStep.getPreferredSize().height;

				return new Dimension(width, height);
			}
		});

		triggering = false;
	}

	public double[] getRange() {
		return new double[] { Double.parseDouble(min.getText()),
				Double.parseDouble(step.getText()),
				Double.parseDouble(max.getText()) };
	}

	private static Dictionary<Integer, JLabel> makeLabelTable(int min, int max,
			int step, int round, int align) {
		if (Math.abs(step) < 1)
			step = 1;

		int count = (max - min) / step;

		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();

		table.put(min, new JLabel("" + min));
		table.put(max, new JLabel("" + max));

		float start = min;

		if (align == 0) {
			float offset = ((max - min) % step) / 2;

			start = min + (int) offset;
		} else if (align > 0) {
			start = max;
			step = -step;
		}

		for (int lbl = 1; lbl < count; ++lbl) {
			float nearPos = start + step * lbl;

			if (round > 0)
				nearPos = Math.round(nearPos / round) * round;

			table.put((int) nearPos, new JLabel("" + (int) nearPos));
		}

		return table;
	}

	public void setMinMax(Double imin, Double imax) {
		int estStep = (int) Math.round((imax - imin) / 2);
		int estStepFlr = (int) ((imax - imin) / 2);

		sliderMin.setMinimum(imin.intValue());
		sliderMin.setLabelTable(makeLabelTable(imin.intValue(),
				imax.intValue(), estStepFlr / 2, estStep / 2, 0));
		sliderMin.setMinorTickSpacing(estStepFlr / 2);
		sliderMin.setMajorTickSpacing(estStepFlr);

		sliderMax.setMaximum(imax.intValue());
		sliderMax.setLabelTable(makeLabelTable(imin.intValue(),
				imax.intValue(), estStepFlr / 2, estStep / 2, 0));
		sliderMax.setMinorTickSpacing(estStepFlr / 2);
		sliderMax.setMajorTickSpacing(estStepFlr);

		sliderStep.setMaximum(estStep * 2);
		sliderStep.setValue(estStep);
		sliderStep.setLabelTable(makeLabelTable(0, estStep * 2, estStep / 2,
				estStep / 2, 0));
		sliderStep.setMinorTickSpacing(estStepFlr / 2);
		sliderStep.setMajorTickSpacing(estStepFlr);

		min.setText("" + imin.intValue());
		step.setText("" + estStep);
		max.setText("" + imax.intValue());
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		// A slider moved. Figure out which and update everything else.
		if (triggering)
			return;
		else
			triggering = true;

		if (e.getSource().equals(sliderMin)) {
			sliderMax.setMinimum(sliderMin.getValue());
			min.setText("" + sliderMin.getValue());
		} else if (e.getSource().equals(sliderMax)) {
			sliderMin.setMaximum(sliderMax.getValue());
			max.setText("" + sliderMax.getValue());
		} else if (e.getSource().equals(sliderStep)) {
			int val = sliderStep.getValue();
			step.setText("" + val);

			// Need to clamp this for label generation.
			// Note that the value of the text box should never be clamped.
			if (val <= 0)
				val = 1;

			sliderMin.setLabelTable(makeLabelTable(sliderMin.getValue(), sliderMax.getValue(), val / 2, val / 2, 0));
			sliderMax.setLabelTable(makeLabelTable(sliderMin.getValue(), sliderMax.getValue(), val / 2, val / 2, 0));
			sliderMin.setMajorTickSpacing(val);
			sliderMax.setMajorTickSpacing(val);
			sliderMin.setMinorTickSpacing(val / 2);
			sliderMax.setMinorTickSpacing(val / 2);
		}

		triggering = false;
	}

	@Override
	public void setEnabled(boolean b) {
		super.setEnabled(b);

		min.setEnabled(b);
		step.setEnabled(b);
		max.setEnabled(b);
		sliderMin.setEnabled(b);
		sliderStep.setEnabled(b);
		sliderMax.setEnabled(b);
	};

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (triggering)
			return;
		else
			triggering = true;

		int value = 1;

		try {
			value = Integer.parseInt(((JTextField) e.getComponent()).getText());
		} catch (NumberFormatException nfe) {
			triggering = false;
			return;
		}

		if (e.getComponent().equals(min)) {
			sliderMin.setValue(value);
			sliderMax.setMinimum(value);
		} else if (e.getComponent().equals(max)) {
			sliderMin.setMaximum(value);
			sliderMax.setValue(value);
		} else if (e.getComponent().equals(step)) {
			sliderStep.setValue(value);
			if (value <= 0)
				value = 1;

			sliderMin.setMajorTickSpacing(value);
			sliderMax.setMajorTickSpacing(value);
			sliderMin.setMinorTickSpacing(value / 2);
			sliderMax.setMinorTickSpacing(value / 2);
			sliderMin.setLabelTable(makeLabelTable(sliderMin.getValue(), sliderMax.getValue(), value / 2, value / 2, 0));
			sliderMax.setLabelTable(makeLabelTable(sliderMin.getValue(), sliderMax.getValue(), value / 2, value / 2, 0));
		}

		triggering = false;
	}
}
