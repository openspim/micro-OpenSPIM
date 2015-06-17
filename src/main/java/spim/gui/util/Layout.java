package spim.gui.util;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class Layout
{
	public static <T extends JComponent> T titled(String title, T c) {
		c.setBorder(BorderFactory.createTitledBorder(title));

		return c;
	}

	public static JPanel vertPanel(Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		
		return addAll(panel, parts);
	}

	public static JPanel horizPanel(Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		
		return addAll(panel, parts);
	}

	public static JPanel vertPanel(String title, Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		
		return addAll(titled(title, panel), parts);
	}

	public static JPanel horizPanel(String title, Component... parts) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		
		return addAll(titled(title, panel), parts);
	};

	public static <T extends Container> T addAll(T to, Component... parts) {
		for(Component c : parts)
			to.add(c);

		return to;
	}

	public static JPanel labelMe(Component c, String lbl) {
		return horizPanel(new JLabel(lbl), c);
	}

	public static JPanel form(Map<String, Component> labelCompPairs) {
		JPanel out = new JPanel();
		GroupLayout layout = new GroupLayout(out);
		out.setLayout(layout);

		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		GroupLayout.Group horizontalLabelsGroup = layout.createParallelGroup(GroupLayout.Alignment.TRAILING);
		GroupLayout.Group horizontalComponentsGroup = layout.createParallelGroup(GroupLayout.Alignment.LEADING);
		GroupLayout.Group verticalGroup = layout.createSequentialGroup();

		for(Map.Entry<String, Component> formEntry : labelCompPairs.entrySet()) {
			JLabel label = new JLabel(formEntry.getKey());

			horizontalLabelsGroup.addComponent(label);
			horizontalComponentsGroup.addComponent(formEntry.getValue());

			verticalGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(label)
					.addComponent(formEntry.getValue())
			);
		}

		layout.setVerticalGroup(verticalGroup);
		layout.setHorizontalGroup(layout.createSequentialGroup()
				.addGroup(horizontalLabelsGroup)
				.addGroup(horizontalComponentsGroup)
		);

		return out;
	}

	public static JPanel form(Object... labelCompPairs) {
		if(labelCompPairs.length % 2 != 0)
			throw new IllegalArgumentException("label/component count must be equal");

		Map<String, Component> labelCompMap = new LinkedHashMap<String, Component>();

		for(int i=0; i < labelCompPairs.length; i += 2) {
			if(!(labelCompPairs[i] instanceof String))
				throw new IllegalArgumentException("argument " + i + " not a string: " + labelCompPairs[i].toString());
			if(!(labelCompPairs[i+1] instanceof Component))
				throw new IllegalArgumentException("argument " + (i+1) + " not a component: " + labelCompPairs[i+1].toString());

			labelCompMap.put(labelCompPairs[i].toString(), (Component) labelCompPairs[i+1]);
		}

		return form(labelCompMap);
	}
}
