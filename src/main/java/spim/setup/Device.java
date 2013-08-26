package spim.setup;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.micromanager.utils.ReportingUtils;

import spim.setup.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

public abstract class Device {
	protected CMMCore core;
	protected String label;

	/**
	 * Basic constructor for a device.
	 * 
	 * @param core The CMMCore instance to which this device is attached.
	 * @param label The MM label of this device.
	 */
	public Device(CMMCore core, String label) {
		this.core = core;
		this.label = label;
	}

	/**
	 * Returns the device's internal name. This is the string passed to the
	 * device adapter when creating a particular instance of the same ilk as
	 * this device; that is, this name is more like a class name than a variable
	 * name.
	 * 
	 * @return The internal name of this device.
	 */
	public String getDeviceName() {
		try {
			return core.getDeviceName(label);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't get device name for " + label, e);
			return null;
		}
	};

	/**
	 * Returns true if this device contains a property by the given name. By
	 * default, just maps to the core call.
	 */
	public boolean hasProperty(String property) {
		try {
			return core.hasProperty(label, property);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't check for \"" + property + "\" on " + label, e);
			return false;
		}
	}

	/**
	 * Sets a property on this device. The exact meaning depends on the property
	 * (and the device).
	 * 
	 * @param property The property name to set.
	 * @param value The value to set the property to.
	 */
	public void setProperty(String property, String value) {
		try {
			core.setProperty(label, property, value);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't set \"" + property + "\" to \"" + value + "\" on " + label, e);
		}
	}

	/**
	 * Sets a property on this device (as a double). See above.
	 * 
	 * @param property The property name to set.
	 * @param value The value to set the property to.
	 */
	public void setProperty(String property, double value) {
		try {
			core.setProperty(label, property, value);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't set \"" + property + "\" to " + value + " on " + label, e);
		}
	}

	/**
	 * Retrieves the value of a property from this device. The exact meaning
	 * depends on the property and device.
	 * 
	 * @param property The property name to get.
	 * @return A string representation of the property's value.
	 */
	public String getProperty(String property) {
		try {
			return core.getProperty(label, property);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't get \"" + property + "\" from " + label, e);
			return null;
		}
	}

	/**
	 * Retrieve the value of a property as a double. See above.
	 * 
	 * @param property The property name to get.
	 * @return The numeric value of the property.
	 */
	public double getPropertyDouble(String property) {
		try {
			return Double.parseDouble(getProperty(property));
		} catch (NumberFormatException e) {
			ReportingUtils.logException("\"" + property + "\" on " + label + " is non-numeric.", e);
			return Double.NaN; // This is going to mess some stuff up...
		}
	}

	/**
	 * Retrieve the set of valid values for the named property.
	 * 
	 * @param property The name of the property to inquire after
	 * @return A set of valid values, or null if the property has not specified
	 *         or does not exist.
	 */
	public Collection<String> getPropertyAllowedValues(String property) {
		try {
			StrVector vec = core.getAllowedPropertyValues(label, property);
			return Arrays.asList(vec.toArray());
		} catch (Exception e) {
			ReportingUtils.logException("\"" + property + "\" on " + label + " does not exist.", e);
			return null;
		}
	}

	/**
	 * Instruct MM to wait for this device to finish doing something.
	 */
	public void waitFor() {
		try {
			core.waitForDevice(label);
		} catch (Exception e) {
			ReportingUtils.logException("Couldn't wait for " + label, e);
		}
	}

	/**
	 * Get this device's MM label.
	 * 
	 * @return The MM label of this device.
	 */
	public String getLabel() {
		return label;
	};

	/**
	 * Get this device's MM device type.
	 * 
	 * @return The MM type of this device.
	 */
	public abstract DeviceType getMMType();

	/*
	 * Device factory stuff
	 */
	public static interface Factory {
		public abstract spim.setup.Device manufacture(CMMCore core, String label);
	}

	protected static void installFactory(Factory fact, String name, SPIMDevice... types) {
		for (SPIMDevice type : types)
			if (factoryMap.get(type).get(name) == null)
				factoryMap.get(type).put(name, fact);
			else
				throw new Error("Attempt to add second factory for device name \"" + name + "\". Each device name can have only one factory.");
	}

	protected static Map<SPIMDevice, Map<String, Factory>> factoryMap = new EnumMap<SPIMDevice, Map<String, Factory>>(SPIMDevice.class);

	static {
		for (SPIMDevice type : SPIMDevice.values())
			factoryMap.put(type, new HashMap<String, Factory>());
	}

	public static Device createDevice(CMMCore core, SPIMDevice type, String label) throws Exception {
		String deviceName = core.getDeviceName(label);

		if (!hasFactory(type, deviceName)) {
			if (hasFactory(type, "*"))
				return factoryMap.get(type).get("*").manufacture(core, label);
			else
				return null;
		}

		return factoryMap.get(type).get(deviceName).manufacture(core, label);
	}

	public static Set<String> getKnownDeviceNames(SPIMDevice type) {
		return factoryMap.get(type).keySet();
	}

	public static boolean hasFactory(SPIMDevice type, String deviceName) {
		return factoryMap.get(type).get(deviceName) != null;
	}
}