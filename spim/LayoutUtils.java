package spim;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;

public class LayoutUtils {
	public static JComponent titled(String title, JComponent c) {
		c.setBorder(BorderFactory.createTitledBorder(title));

		return c;
	}

	public static Component vertPanel(Component... parts) {
		return addAll(Box.createVerticalBox(), parts);
	}

	public static Component horizPanel(Component... parts) {
		return addAll(Box.createHorizontalBox(), parts);
	}

	public static Component vertPanel(String title, Component... parts) {
		return addAll(titled(title, Box.createVerticalBox()), parts);
	}

	public static Component horizPanel(String title, Component... parts) {
		return addAll(titled(title, Box.createHorizontalBox()), parts);
	};

	public static JComponent addAll(JComponent to, Component... parts) {
		for(Component c : parts)
			to.add(c);

		return to;
	}

	public static Component labelMe(Component c, String lbl) {
		return horizPanel(new JLabel(lbl), c);
	}
}
