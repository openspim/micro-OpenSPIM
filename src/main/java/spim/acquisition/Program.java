package spim.acquisition;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.TaggedImage;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.micromanager.MMStudio;
import org.micromanager.SnapLiveManager;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.SPIMSetup.SPIMDevice;
import spim.hardware.Stage;
import spim.io.OutputHandler;
import spim.acquisition.Row.DeviceValueSet;
import spim.algorithm.AntiDrift;
import spim.controller.AntiDriftController;
import spim.util.DelayChecker;

public class Program
{
	public static class Profiler {
		private Map<String, Profiler> children;
		private String name;
		private double timer;

		public Profiler(String name) {
			this.name = name;
			children = new java.util.Hashtable<String, Profiler>();
			timer = 0;
		}

		public void start() {
			timer -= System.nanoTime() / 1e9;
		}

		public void stop() {
			timer += System.nanoTime() / 1e9;
		}

		public double getTimer() {
			return timer;
		}

		@Override
		public String toString() {
			return "Profiler(\"" + name + "\")[" + children.size() + " children]";
		}

		public Profiler get(String child) {
			return children.get(child);
		}

		public void create(String child) {
			children.put(child, new Profiler(child));
		}

		public String getLogText() {
			return getLogText(0);
		}

		private void tabs(StringBuilder sb, int c) {
			for(int i=0; i < c; ++i)
				sb.append("    ");
		}

		protected String getLogText(int indent) {
			StringBuilder sb = new StringBuilder(256);
			tabs(sb, indent);
			sb.append(toString());
			sb.append(": ");
			sb.append(String.format("%.4f", getTimer()));
			sb.append("s\n");

			for(Profiler child : children.values()) {
				tabs(sb, indent);
				sb.append(String.format("%.4f%%:", child.getTimer() / getTimer() * 100));
				sb.append(child.getLogText(indent + 1));
			}

			return sb.toString();
		}
	}

	/**
	 * Takes a list of steps and concatenates them together recursively. This is
	 * what builds out rows from a list of lists of positions.
	 * 
	 * @param steps
	 *            A list of lists of discrete values used to make up rows.
	 * @return A list of every possible combination of the input.
	 */
	private static Vector<Vector<Double>> getRows(List<double[]> steps) {
		double[] first = steps.get(0);
		Vector<Vector<Double>> rows = new Vector<Vector<Double>>();

		if (steps.size() == 1) {
			for (double val : first) {
				Vector<Double> row = new Vector<Double>();
				row.add(val);
				rows.add(row);
			}
		} else {
			for (double val : first) {
				Vector<Vector<Double>> subrows = getRows(steps.subList(1,
						steps.size()));

				for (Vector<Double> row : subrows) {
					Vector<Double> newRow = new Vector<Double>(row);
					newRow.add(0, val);
					rows.add(newRow);
				}
			}
		}

		return rows;
	}

	/**
	 * Takes a list of ranges (min/step/max triplets), splits them into discrete
	 * values, permutes them, then condenses X/Y into ordered pairs.
	 * 
	 * @param ranges
	 *            List of triplets corresponding to the devices.
	 * @param devs
	 *            List of devices being used (to determine X/Y stages)
	 * @return A list of string arrays, each element being a column for that
	 *         'row'. Can be passed directly into the 'rows' parameter of the
	 *         performAcquisition method.
	 */
	public static List<String[]> generateRowsFromRanges(CMMCore corei,
			List<double[]> ranges, String[] devs) {
		// Each element of range is a triplet of min/step/max.
		// This function determines the discrete values of each range, then
		// works out all possible values and adds them as rows to the table.
		Vector<double[]> values = new Vector<double[]>(ranges.size());

		for (double[] triplet : ranges) {
			double[] discretes = new double[(int) ((triplet[2] - triplet[0]) / triplet[1]) + 1];

			for (int i = 0; i < discretes.length; ++i)
				discretes[i] = triplet[0] + triplet[1] * i;

			values.add(discretes);
		}

		// Build a quick list of indices of X/Y stage devices.
		// Below, we condense the X and Y coordinates into an ordered pair so
		// they can be inserted into the table. This list is used to determine
		// which sets of indices need to be squished into a single value.
		Vector<Integer> xyStages = new Vector<Integer>(devs.length);
		for (int i = 0; i < devs.length; ++i) {
			try {
				if (corei.getDeviceType(devs[i]).equals(
						DeviceType.XYStageDevice))
					xyStages.add(i);
			} catch (Exception e) {
				// I can't think of a more graceless way to resolve this issue.
				// But then, nor can I think of a more graceful one.
				throw new Error("Couldn't resolve type of device \"" + devs[i]
						+ "\"", e);
			}
		}

		Vector<String[]> finalRows = new Vector<String[]>();

		for (List<Double> row : getRows(values)) {
			Vector<String> finalRow = new Vector<String>();

			for (int i = 0; i < row.size(); ++i)
				if (xyStages.contains(i))
					finalRow.add(row.get(i) + ", " + row.get(++i));
				else
					finalRow.add("" + row.get(i));

			finalRows.add(finalRow.toArray(new String[finalRow.size()]));
		}

		return finalRows;
	}

	private static void runDevicesAtRow(CMMCore core, SPIMSetup setup, Row row, int step) throws Exception {
		for (SPIMDevice devType : row.getDevices()) {
			Device dev = setup.getDevice(devType);
			DeviceValueSet values = row.getValueSet(devType);

			if (dev instanceof Stage) // TODO: should this be different?
				((Stage)dev).setPosition(values.getStartPosition());
			else
				throw new Exception("Unknown device type for \"" + dev
						+ "\"");
		}
		core.waitForSystem();
	}
	
	private static void updateLiveImage(MMStudio f, TaggedImage ti)
	{
		try {
			MDUtils.setChannelIndex(ti.tags, 0);
			MDUtils.setFrameIndex(ti.tags, 0);
			MDUtils.setPositionIndex(ti.tags, 0);
			MDUtils.setSliceIndex(ti.tags, 0);
			ti.tags.put("Summary", f.getAcquisition(SnapLiveManager.SIMPLE_ACQ).getSummaryMetadata());
			f.displayImage(ti);
		} catch (Throwable t) {
			ReportingUtils.logError(t, "Attemped to update live window.");
		}
	}

	private static ImagePlus cleanAbort(Params p, boolean live, boolean as, Thread ct) {
		p.getCore().setAutoShutter(as);
		p.getProgressListener().reportProgress(p.getTimeSeqCount() - 1, p.getRows().length - 1, 100.0D);

		try {
			if(ct != null && ct.isAlive()) {
				ct.interrupt();
				ct.join();
			}

			// TEMPORARY: Don't re-enable live mode. This keeps our laser off.
//			MMStudio.getInstance().enableLiveMode(live);

			p.getOutputHandler().finalizeAcquisition();
			return p.getOutputHandler().getImagePlus();
		} catch(Exception e) {
			return null;
		}
	}

	private static TaggedImage snapImage(SPIMSetup setup, boolean manualLaser) throws Exception {
		if(manualLaser && setup.getLaser() != null)
			setup.getLaser().setPoweredOn(true);

		TaggedImage ti = setup.getCamera().snapImage();

		if(manualLaser && setup.getLaser() != null)
			setup.getLaser().setPoweredOn(false);

		return ti;
	}

	public interface AcqProgressCallback {
		public abstract void reportProgress(int tp, int row, double overall);
	}

	/**
	 * Performs an acquisition sequence according to the parameters passed.
	 * 
	 *
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public static ImagePlus performAcquisition(final Params params) throws Exception {
		if(params.isContinuous() && params.isAntiDriftOn())
			throw new IllegalArgumentException("No continuous acquisition w/ anti-drift!");

		final Profiler prof = (params.doProfiling() ? new Profiler("performAcquisition") : null);

		if(params.doProfiling())
		{
			prof.create("Setup");
			prof.create("Output");
			prof.create("Movement");
			prof.create("Acquisition");

			prof.start();
		}

		if(params.doProfiling())
			prof.get("Setup").start();

		final CMMCore core = params.getCore();

		final MMStudio frame = MMStudio.getInstance();
		boolean liveOn = frame.isLiveModeOn();
		if(liveOn)
			frame.enableLiveMode(false);

		boolean autoShutter = core.getAutoShutter();
		core.setAutoShutter(false);

		final SPIMDevice[] metaDevs = params.getMetaDevices();

		final SPIMSetup setup = params.getSetup();

		final OutputHandler handler = params.getOutputHandler();

		final double acqBegan = System.nanoTime() / 1e9;

		final Map<Row, AntiDriftController > driftCompMap;
		if(params.isAntiDriftOn())
			driftCompMap = new HashMap<Row, AntiDriftController>(params.getRows().length);
		else
			driftCompMap = null;

		if(params.doProfiling())
			prof.get("Setup").stop();

		for(int timeSeq = 0; timeSeq < params.getTimeSeqCount(); ++timeSeq) {
			Thread continuousThread = null;
			if (params.isContinuous()) {
				continuousThread = new Thread() {
					private Throwable lastExc;

					@Override
					public void run() {
						try {
							if(setup.getLaser() != null)
								setup.getLaser().setPoweredOn(true);

							core.clearCircularBuffer();
							core.startContinuousSequenceAcquisition(0);

							while (!Thread.interrupted()) {
								if (core.getRemainingImageCount() == 0) {
									Thread.yield();
									continue;
								}

								TaggedImage ti = core.popNextTaggedImage();
								handleSlice(core, setup, metaDevs, acqBegan, ImageUtils.makeProcessor(ti), handler);

								if(params.isUpdateLive())
									updateLiveImage(frame, ti);
							}

							core.stopSequenceAcquisition();

							if(setup.getLaser() != null)
								setup.getLaser().setPoweredOn(false);
						} catch (Throwable e) {
							lastExc = e;
						}
					}

					@Override
					public String toString() {
						if (lastExc == null)
							return super.toString();
						else
							return lastExc.getMessage();
					}
				};

				continuousThread.start();
			}

			int step = 0;
			for(final Row row : params.getRows()) {
				final int tp = timeSeq;
				final int rown = step;

				AntiDriftController ad = null;
				if(row.getZContinuous() != true && params.isAntiDriftOn()) {
					if((ad = driftCompMap.get(row)) == null) {
						ad = params.getAntiDrift(row);
						ad.setCallback(new AntiDrift.Callback() {
							public void applyOffset(Vector3D offs) {
								offs = new Vector3D(offs.getX()*-core.getPixelSizeUm(), offs.getY()*-core.getPixelSizeUm(), -offs.getZ());
								ij.IJ.log(String.format("TP %d view %d: Offset: %s", tp, rown, offs.toString()));
								row.translate(offs);
							}
						});
					}

					ad.startNewStack();
				}

				if(params.doProfiling())
					prof.get("Movement").start();

				runDevicesAtRow(core, setup, row, step);

				if(params.doProfiling())
					prof.get("Movement").stop();

				if(params.isIllumFullStack() && setup.getLaser() != null)
					setup.getLaser().setPoweredOn(true);

				if(params.doProfiling())
					prof.get("Output").start();

				handler.beginStack(0);

				if(params.doProfiling())
					prof.get("Output").stop();

				if(row.getZStartPosition() == row.getZEndPosition()) {
					DelayChecker checker = new DelayChecker( 5000 );

					//TODO: added logging info before image synchronization
					ReportingUtils.logMessage( "Before 1: core.waitForImageSynchro();" );

					core.waitForImageSynchro();

					ReportingUtils.logMessage( "Before 1: Delay " + params.getSettleDelay() );
					Thread.sleep(params.getSettleDelay());

					ReportingUtils.logMessage( "After 1: done." );

					if(!params.isContinuous()) {
						TaggedImage ti = snapImage(setup, !params.isIllumFullStack());
						ImageProcessor ip = ImageUtils.makeProcessor(ti);

						handleSlice(core, setup, metaDevs, acqBegan, ip, handler);
						if(ad != null)
							addAntiDriftSlice( ad, ip);
						if(params.isUpdateLive())
							updateLiveImage(frame, ti);
					}

					checker.stop();

				} else if (!row.getZContinuous()) {

					ReportingUtils.logMessage( "Before 2 loop: setup.getZStage().getPosition();" );

					double start = setup.getZStage().getPosition();

					ReportingUtils.logMessage( "Before 2 loop: start + row.getZEndPosition() - row.getZStartPosition();" );

					double end = start + row.getZEndPosition() - row.getZStartPosition();

					ReportingUtils.logMessage( "Before 2 loop: started." );

					DelayChecker checker = new DelayChecker( 5000 );

					for(double zStart = start; zStart <= end; zStart += row.getZStepSize()) {
						// Reset delay checker
						checker.reset();


						if(params.doProfiling())
						{
							ReportingUtils.logMessage( "Before 2 in-loop: doProfiling" );
							prof.get( "Movement" ).start();
						}

						ReportingUtils.logMessage( "Before 2 in-loop: setup.getZStage().setPosition(zStart);" );
						setup.getZStage().setPosition(zStart);

						ReportingUtils.logMessage( "Before 2 in-loop: core.waitForImageSynchro();" );
						core.waitForImageSynchro();

						try {
							ReportingUtils.logMessage( "Before 2 in-loop: Delay " + params.getSettleDelay() );
							Thread.sleep(params.getSettleDelay());
							ReportingUtils.logMessage( "After 2 in-loop: Thread.sleep(params.getSettleDelay());" );
						} catch(InterruptedException ie) {
							checker.stop();
							return cleanAbort(params, liveOn, autoShutter, continuousThread);
						}
						ReportingUtils.logMessage( "After 2 in-loop: done." );

						if(params.doProfiling())
							prof.get("Movement").stop();

						if(!params.isContinuous()) {
							if(params.doProfiling())
								prof.get("Acquisition").start();

							TaggedImage ti = snapImage(setup, !params.isIllumFullStack());

							if(params.doProfiling())
								prof.get("Acquisition").stop();

							if(params.doProfiling())
								prof.get("Output").start();

							ImageProcessor ip = ImageUtils.makeProcessor(ti);
							handleSlice(core, setup, metaDevs, acqBegan, ip, handler);

							if(params.doProfiling())
								prof.get("Output").stop();

							if(ad != null)
								addAntiDriftSlice(ad, ip);
							if(params.isUpdateLive())
								updateLiveImage(frame, ti);
						}

						double stackProg = Math.max(Math.min((zStart - start)/(end - start),1),0);

						final Double progress = (params.getRows().length * timeSeq + step + stackProg) / (params.getRows().length * params.getTimeSeqCount());

						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								params.getProgressListener().reportProgress(tp, rown, progress);
							}
						});
					}

					checker.stop();

				} else {
					setup.getZStage().setPosition(row.getZStartPosition());
					Double oldVel = setup.getZStage().getVelocity();

					setup.getZStage().setVelocity(row.getZVelocity());
					setup.getZStage().setPosition(row.getZEndPosition());

					setup.getZStage().waitFor();
					setup.getZStage().setVelocity(oldVel);
				}

				if(params.doProfiling())
					prof.get("Output").start();

				handler.finalizeStack(0);

				if(params.doProfiling())
					prof.get("Output").stop();

				if(params.isIllumFullStack() && setup.getLaser() != null)
					setup.getLaser().setPoweredOn(false);

				if(ad != null) {
					ad.finishStack();

					driftCompMap.put(row, ad);
				}

				if(Thread.interrupted())
					return cleanAbort(params, liveOn, autoShutter, continuousThread);

				if(params.isContinuous() && !continuousThread.isAlive()) {
					cleanAbort(params, liveOn, autoShutter, continuousThread);
					throw new Exception(continuousThread.toString());
				}

				final Double progress = (double) (params.getRows().length * timeSeq + step + 1)
						/ (params.getRows().length * params.getTimeSeqCount());

				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						params.getProgressListener().reportProgress(tp, rown, progress);
					}
				});

				++step;
			}

			if (params.isContinuous()) {
				continuousThread.interrupt();
				continuousThread.join();
			}

			if(timeSeq + 1 < params.getTimeSeqCount()) {
				double wait = (params.getTimeStepSeconds() * (timeSeq + 1)) -
						(System.nanoTime() / 1e9 - acqBegan);
	
				if(wait > 0D)
					try {
						Thread.sleep((long)(wait * 1e3));
					} catch(InterruptedException ie) {
						return cleanAbort(params, liveOn, autoShutter, continuousThread);
					}
				else
					core.logMessage("Behind schedule! (next seq in "
							+ Double.toString(wait) + "s)");
			}
		}

		if(params.doProfiling())
			prof.get("Output").start();

		handler.finalizeAcquisition();

		if(params.doProfiling())
			prof.get("Output").stop();

		if(autoShutter)
			core.setAutoShutter(true);

		// TEMPORARY: Don't re-enable live mode. This keeps our laser off.
//		if(liveOn)
//			frame.enableLiveMode(true);

		if(params.doProfiling())
		{
			prof.stop();
			ij.IJ.log(prof.getLogText());
		}

		return handler.getImagePlus();
	}

	private static void addAntiDriftSlice(AntiDriftController ad, ImageProcessor ip) throws Exception {
		ad.addXYSlice(ip);
	}

	private static void handleSlice(CMMCore core, SPIMSetup setup,
			SPIMDevice[] metaDevs, double start, ImageProcessor ip,
			OutputHandler handler) throws Exception {
/*
		slice.tags.put("t", System.nanoTime() / 1e9 - start);

		for(SPIMDevice devType : metaDevs) {
			Device dev = setup.getDevice(devType);
			try {
				if(dev instanceof Stage) {
					slice.tags.put(devType.getText(), ((Stage)dev).getPosition());
				} else {
					slice.tags.put(devType.getText(), "<unknown device type>");
				}
			} catch(Throwable t) {
				slice.tags.put(devType.getText(), "<<<Exception: " + t.getMessage() + ">>>");
			}
		}
*/
		handler.processSlice(ip, setup.getXStage().getPosition(),
				setup.getYStage().getPosition(),
				setup.getZStage().getPosition(),
				setup.getAngle(),
				System.nanoTime() / 1e9 - start);
	}
}
