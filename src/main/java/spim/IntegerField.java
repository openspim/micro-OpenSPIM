
package spim;

import javax.swing.JTextField;

import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusAdapter;

import spim.SPIMAcquisition;

public class IntegerField extends JTextField {
	protected String prefsKey;

	public IntegerField(int value) {
		this(value, 4, null);
	}

	public IntegerField(int value, String prefsKey) {
		this(value, 4, prefsKey);
	}

	public IntegerField(int value, int columns) {
		this(value, columns, null);
	}

	public IntegerField(int value, int columns, String prefsKey) {
		super(columns);
		this.prefsKey = prefsKey;
		setText("" + SPIMAcquisition.prefsGet(prefsKey, value));
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() != KeyEvent.VK_ENTER)
					return;
				handleChange();
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				handleChange();
			}
		});
	}

	protected void handleChange() {
		int value = getValue();
		valueChanged(value);
		SPIMAcquisition.prefsSet(prefsKey, value);
	}

	public int getValue() {
		String typed = getText();
		if(!typed.matches("\\d+"))
			return 0;
		return Integer.parseInt(typed);
	}

	public void valueChanged(int value) {}
}
