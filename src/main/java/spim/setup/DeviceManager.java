package spim.setup;

import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.micromanager.utils.ReportingUtils;

import spim.LayoutUtils;
import spim.setup.SPIMSetup.SPIMDevice;

public class DeviceManager extends JPanel implements ItemListener, EventListener {
	private SPIMSetup setup;
	private JFrame display;

	public DeviceManager(SPIMSetup setup) {
		this.setup = setup;

		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

		JPanel stages = LayoutUtils.titled("Stages", new JPanel(new GridLayout(4, 2, 0, 2)));
		LayoutUtils.addAll(stages, labelCombo(SPIMDevice.STAGE_X));
		LayoutUtils.addAll(stages, labelCombo(SPIMDevice.STAGE_Y));
		LayoutUtils.addAll(stages, labelCombo(SPIMDevice.STAGE_Z));
		LayoutUtils.addAll(stages, labelCombo(SPIMDevice.STAGE_THETA));
		add(stages);

		JPanel illum = LayoutUtils.titled("Illumination/Detection", new JPanel(new GridLayout(5, 2, 0, 2)));
		LayoutUtils.addAll(illum, labelCombo(SPIMDevice.LASER1));
		LayoutUtils.addAll(illum, labelCombo(SPIMDevice.LASER2));
		LayoutUtils.addAll(illum, labelCombo(SPIMDevice.CAMERA1));
		LayoutUtils.addAll(illum, labelCombo(SPIMDevice.CAMERA2));
		LayoutUtils.addAll(illum, labelCombo(SPIMDevice.SYNCHRONIZER));
		add(illum);
	}

	public void setVisible(boolean show) {
		if (display == null) {
			display = new JFrame("Device Manager");
			display.add(this);
			display.pack();
		}

		display.setVisible(show);
	}

	private JComponent[] labelCombo(SPIMDevice type) {
		Vector<String> devices = new Vector<String>(Arrays.asList(setup.getCore().getLoadedDevices().toArray()));
		Set<String> names = Device.getKnownDeviceNames(type);

		Iterator<String> iter = devices.iterator();
		while (iter.hasNext())
			try {
				if (!names.contains(setup.getCore().getDeviceName(iter.next())))
					iter.remove();
			} catch (Throwable t) {
				ReportingUtils.logError(t);
			}

		try {
			String defaultDevice = setup.getDefaultDeviceLabel(type);

			if(defaultDevice != null && !defaultDevice.isEmpty() && !devices.contains(defaultDevice))
				devices.add(defaultDevice);
		} catch (Throwable t) {
			ReportingUtils.logError(t);
		}

		devices.add("(none)");

		JComboBox combo = new JComboBox(devices);
		combo.setName(type.toString());
		combo.setSelectedItem(setup.getDevice(type) != null ? setup.getDevice(type).getLabel() : "(none)");
		combo.addItemListener(this);

		return new JComponent[] { new JLabel(type.getText()), combo };
	}

	@Override
	public void itemStateChanged(ItemEvent ie) {
		if (!(ie.getSource() instanceof JComboBox))
			return; // what

		JComboBox src = (JComboBox) ie.getSource();
		SPIMDevice type = SPIMDevice.valueOf(src.getName());
		String selectedLabel = src.getSelectedItem().toString();
		
		if(selectedLabel.equals("(none)"))
			selectedLabel = null;

		setup.setDevice(type, selectedLabel);
	}
}
