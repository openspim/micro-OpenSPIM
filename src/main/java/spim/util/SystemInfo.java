package spim.util;


import mmcorej.CMMCore;

/**
 * Description: SystemInfo logs the memory footage every acquisition
 * in order to check the memory status.
 * This code is from PhysicalMemoryInfoSection class written by Mark Tsuchida
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2021
 */
public class SystemInfo {
	public static void dumpMemoryStatusToLog(CMMCore core) {
		String report = new SystemInfo().getReport();
		System.out.println(report);
		core.logMessage(report);
	}

	public String getReport() {
		StringBuilder sb = new StringBuilder();

		java.lang.management.OperatingSystemMXBean osMXB =
				java.lang.management.ManagementFactory.getOperatingSystemMXBean();

		try { // Use HotSpot extensions if available
			Class<?> sunOSMXBClass = Class.forName("com.sun.management.OperatingSystemMXBean");

			java.lang.reflect.Method totalMemMethod = sunOSMXBClass.getMethod("getTotalPhysicalMemorySize");
			long totalRAM = ((Long) totalMemMethod.invoke(osMXB)).longValue();
			sb.append("Total physical memory (caveats apply if JVM is 32-bit): ").
					append(formatMemSize(totalRAM)).append('\n');

			try {
				java.lang.reflect.Method committedMemMethod = sunOSMXBClass.getMethod("getCommittedVirtualMemorySize");
				long committedVM = ((Long) committedMemMethod.invoke(osMXB)).longValue();
				sb.append("Committed virtual memory size: ").
						append(formatMemSize(committedVM)).append('\n');
			}
			catch (Exception e) {
				sb.append("Committed virtual memory size: unavailable\n");
			}

			java.lang.reflect.Method freeMemMethod = sunOSMXBClass.getMethod("getFreePhysicalMemorySize");
			long freeRAM = ((Long) freeMemMethod.invoke(osMXB)).longValue();
			sb.append("Free physical memory (may be meaningless if JVM is 32-bit): ").
					append(formatMemSize(freeRAM)).append('\n');
		}
		catch (Exception e) {
			// Possible exceptions: ClassNotFoundException, NoSuchMethodException,
			// IllegalAccessException, java.lang.reflect.InvocationTargetException
			sb.append("Physical memory information: unavailable");
		}

		java.lang.management.MemoryMXBean memMXB = java.lang.management.ManagementFactory.getMemoryMXBean();
		sb.append("JVM heap memory usage: ").append(formatMemUsage(memMXB.getHeapMemoryUsage())).append('\n');
		sb.append("JVM non-heap memory usage: ").append(formatMemUsage(memMXB.getNonHeapMemoryUsage()));

		return sb.toString();
	}

	private String formatMemSize(long size) {
		if (size == -1) {
			return "unavailable";
		}
		if (size < 1024) {
			return Long.toString(size) + " bytes";
		}

		double bytes = size;
		java.text.NumberFormat format = new java.text.DecimalFormat("#.0");

		if (size < 1024 * 1024) {
			return Long.toString(size) + " (" + format.format(bytes / 1024) + " KiB)";
		}
		if (size < 1024 * 1024 * 1024) {
			return Long.toString(size) + " (" + format.format(bytes / (1024 * 1024)) + " MiB)";
		}
		return Long.toString(size) + " (" + format.format(bytes / (1024 * 1024 * 1024)) + " GiB)";
	}

	private String formatMemUsage(java.lang.management.MemoryUsage usage) {
		StringBuilder sb = new StringBuilder();
		sb.append("used = ").append(formatMemSize(usage.getUsed())).
				append("; committed = ").append(formatMemSize(usage.getCommitted())).
				append("; max = ").append(formatMemSize(usage.getMax()));
		return sb.toString();
	}
}
