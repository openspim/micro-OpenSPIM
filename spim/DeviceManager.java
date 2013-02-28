package spim;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * @author Luke Stuyvenberg
 *
 * This class manages the principal devices involved in a SPIM setup. The goal
 * is to provide an authoritative reference for the device labels Micro-Manager
 * uses within our project.
 */
public class DeviceManager extends JFrame {
	private static final long serialVersionUID = -3345675926744884431L;

	public enum SPIMDevice {
		STAGE_X ("X Stage", DeviceType.StageDevice),
		STAGE_Y ("Y Stage", DeviceType.StageDevice),
		STAGE_XY ("XY Stage", DeviceType.XYStageDevice),
		STAGE_Z ("Z Stage", DeviceType.StageDevice),
		STAGE_THETA ("Rotator", DeviceType.StageDevice),
		LASER1 ("Laser", DeviceType.ShutterDevice),
		LASER2 ("Laser (2)", DeviceType.ShutterDevice),
		CAMERA1 ("Camera", DeviceType.CameraDevice),
		CAMERA2 ("Camera (2)", DeviceType.CameraDevice),
		SYNCHRONIZER ("Synchronizer", DeviceType.SignalIODevice);

		private final String text;
		private final DeviceType mmtype;
		private SPIMDevice(String text, DeviceType type) {
			this.text = text;
			this.mmtype = type;
		}

		public String getText() {
			return this.text;
		}

		public DeviceType getMMType() {
			return this.mmtype;
		}
	};

	private static class SPIMSetupDevices extends JPanel {
		private static final long serialVersionUID = 5356126433493461392L;

		private EnumMap<SPIMDevice, String> labelMap;
		private JTextField name;
		private CMMCore core;

		public SPIMSetupDevices(CMMCore core, String name, boolean fromMM, final DeviceManager parent) {
			this.core = core;
			labelMap = new EnumMap<SPIMDevice, String>(SPIMDevice.class);

			if(fromMM)
				for(SPIMDevice type : SPIMDevice.values())
					labelMap.put(type, getMMDefaultDevice(type));

			setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));

			this.name = new JTextField(name);
			this.name.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent ke) {
					parent.updateSetupName(SPIMSetupDevices.this);
				}
			});
			add(LayoutUtils.titled("Setup Name/Label", (JComponent) LayoutUtils.labelMe(this.name, "Name: ")));

			HashMap<DeviceType, JPanel> panelMap = new HashMap<DeviceType, JPanel>();
			for(final SPIMDevice type : SPIMDevice.values()) {
				JPanel put = panelMap.get(type.getMMType());
				if(put == null) {
					put = new JPanel();
					put.setLayout(new BoxLayout(put, BoxLayout.PAGE_AXIS));
					put.setBorder(BorderFactory.createTitledBorder(cleanName(type.getMMType()))); // TODO: Make titles for these, or drop the titledBorder.
					panelMap.put(type.getMMType(), put);
				}

				JComboBox cmbo = new JComboBox(augmentNone(core.getLoadedDevicesOfType(type.getMMType())));
				cmbo.setSelectedItem(getDeviceLabel(type) != null ? getDeviceLabel(type) : "(none)");
				cmbo.addItemListener(new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent ie) {
						labelMap.put(type, ie.getItem().toString().equals("(none)") ? null : ie.getItem().toString());
					}
				});

				put.add(LayoutUtils.labelMe(cmbo, type.getText() + ": "));

				add(put);
			}
		}

		@Override
		public String getName() {
			return name.getText();
		}

		public String getDeviceLabel(SPIMDevice type) {
			return labelMap.get(type);
		}

		private String getMMDefaultDevice(SPIMDevice type) {
			switch(type) {
			case STAGE_X:
				if(core.getXYStageDevice() != null && coreHasDevOfType(DeviceType.StageDevice, core.getXYStageDevice() + ".X"))
					return core.getXYStageDevice() + ".X";

				return null;
			case STAGE_Y:
				if(core.getXYStageDevice() != null && strVecContains(core.getLoadedDevicesOfType(DeviceType.StageDevice), core.getXYStageDevice() + ".Y"))
					return core.getXYStageDevice() + ".Y";

				return null;
			case STAGE_XY:
				return core.getXYStageDevice();
			case STAGE_Z:
				return core.getFocusDevice();
			case STAGE_THETA:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.StageDevice))
					if(!lbl.equals(core.getFocusDevice()) && !lbl.endsWith(".X") && !lbl.endsWith(".Y"))
						return lbl;

				return null;
			case LASER1:
				return core.getShutterDevice();
			case LASER2:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.ShutterDevice))
					if(!lbl.equals(core.getShutterDevice()))
						return lbl;

				return null;
			case CAMERA1:
				return core.getCameraDevice();
			case CAMERA2:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.CameraDevice))
					if(!lbl.equals(core.getCameraDevice()))
						return lbl;

				return null;
			case SYNCHRONIZER:
				return core.getShutterDevice();
			default:
				return null;
			}
		}

		private boolean coreHasDevOfType(DeviceType t, String lbl) {
			return strVecContains(core.getLoadedDevicesOfType(t), lbl);
		}

		private static String[] augmentNone(StrVector arg) {
			String[] out = new String[(int) (arg.size() + 1)];

			out[0] = "(none)";
			for(int s = 0; s < arg.size(); ++s)
				out[s + 1] = arg.get(s);

			return out;
		}

		private static boolean strVecContains(StrVector v, String s) {
			for(String s2 : v)
				if(s2.equals(s))
					return true;

			return false;
		}
		
		private static String cleanName(DeviceType type) {
			String cleaned = type.toString().replace("Device", "");

			java.util.regex.Pattern pat = java.util.regex.Pattern.compile("([a-z]|XY|IO)([A-Z])");
			return pat.matcher(cleaned).replaceAll("$1 $2");
		}
	}

	private CMMCore core;
	private JTabbedPane setupTabs;
	private List<SPIMSetupDevices> setups;

	/**
	 * Returns the Micro-Manager device label for the specified device type, in
	 * the first index.
	 *
	 * @param type The device type to determine the label of
	 * @return Micro-Manager's device label for the current device, or null if
	 * 		   not available.
	 */
	public String getLabel(SPIMDevice type) {
		return getDeviceLabel(type, 0);
	}

	/**
	 * Returns the MM device label for the specified device type in the first
	 * SPIM setup.
	 * 
	 * @param type The device type to determine the label of.
	 * @param setup Which attached setup to get the device of.
	 * @return The correct device label, or null if not available.
	 */
	public String getDeviceLabel(SPIMDevice type, int setup) {
		if(setups.size() <= setup)
			return null;

		return setups.get(setup).getDeviceLabel(type);
	}

	public DeviceManager(CMMCore core) {
		super("SPIM Device Manager");

		this.core = core;
		this.setups = new ArrayList<SPIMSetupDevices>(1);

		// Build the first setup automatically from MM's devices.
		this.setups.add(new SPIMSetupDevices(core, "Default Setup", true, this));

		java.awt.Container me = getContentPane();
		me.setLayout(new BoxLayout(me, BoxLayout.PAGE_AXIS));

		setupTabs = new JTabbedPane(1);
		setupTabs.add("Default Setup", setups.get(0));

		setupTabs.add("+", new JPanel());
		setupTabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent ce) {
				final int lastIdx = setupTabs.getTabCount() - 1;
				if(setupTabs.getSelectedIndex() == lastIdx) {
					SPIMSetupDevices setup = new SPIMSetupDevices(DeviceManager.this.core,
							"Setup " + (lastIdx+1), lastIdx == 0, DeviceManager.this);

					if(lastIdx >= setups.size())
						setups.add(setup);

					setupTabs.setSelectedIndex(0);
					setupTabs.insertTab(setup.getName(), null, setup, null, lastIdx);
					setupTabs.setSelectedComponent(setup);
				}
			}
		});
		me.add(setupTabs);

		JButton removeBtn = new JButton("Remove Setup");
		removeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				removeSetup((SPIMSetupDevices)setupTabs.getSelectedComponent());
			}
		});
		me.add(LayoutUtils.horizPanel(Box.createHorizontalStrut(386), removeBtn));

		pack();

		setMaximumSize(getPreferredSize());
	}

	public void save() {
		// TODO
	}

	public void load() {
		// TODO
	}

	public String getSetupName(int setup) {
		return setupTabs.getTitleAt(setup);
	}

	protected void updateSetupName(SPIMSetupDevices setup) {
		int idx = setupTabs.indexOfComponent(setup);

		if(idx >= 0)
			setupTabs.setTitleAt(idx, setup.getName());
		else
			System.out.println("Couldn't find tab...");
	}
	
	private void removeSetup(SPIMSetupDevices setup) {
		int idx = setupTabs.indexOfComponent(setup);
		if(idx == setupTabs.getTabCount() - 2) {
			if(idx == 0)
				return;
			else
				setupTabs.setSelectedIndex(idx - 1);
		}

		setups.remove(setup);
		setupTabs.remove(setup);
	}
}
