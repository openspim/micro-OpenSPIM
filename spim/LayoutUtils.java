package spim;

import java.awt.Component;
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

	public static Component vertPanel(Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		
		return addAll(panel, parts);
	}

	public static Component horizPanel(Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		
		return addAll(panel, parts);
	}

	public static Component vertPanel(String title, Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		
		return addAll(titled(title, panel), parts);
	}

	public static Component horizPanel(String title, Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		
		return addAll(titled(title, panel), parts);
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
