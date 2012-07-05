package spim;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class LayoutUtils {
	public static JComponent titled(String title, JComponent c) {
		c.setBorder(BorderFactory.createTitledBorder(title));

		return c;
	}

	public static JComponent vertPanel(JComponent... parts) {
		return addAll(Box.createVerticalBox(), parts);
	}

	public static JComponent horizPanel(JComponent... parts) {
		return addAll(Box.createHorizontalBox(), parts);
	}

	public static JComponent vertPanel(String title, JComponent... parts) {
		return addAll(titled(title, Box.createVerticalBox()), parts);
	}

	public static JComponent horizPanel(String title, JComponent... parts) {
		return addAll(titled(title, Box.createHorizontalBox()), parts);
	};

	public static JComponent addAll(JComponent to, JComponent... parts) {
		for(JComponent c : parts)
			to.add(c);

		return to;
	}

	public static JComponent labelMe(JComponent c, String lbl) {
		return horizPanel(new JLabel(lbl), c);
	}
}
