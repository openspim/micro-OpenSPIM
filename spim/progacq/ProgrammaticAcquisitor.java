package spim.progacq;

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

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

import spim.DeviceManager;
import spim.DeviceManager.SPIMDevice;
import spim.progacq.AcqRow.DeviceValueSet;

public class ProgrammaticAcquisitor {
	/**
	 * Takes a list of steps and concatenates them together recursively. This is
	 * what builds out rows from a list of lists of positions.
	 * 
	 * @param steps
	 *            A list of lists of discrete values used to make up rows.
	 * @return A list of every possible combination of the input.
	 */
	private static Vector<Vector<Double>> getRows(List<double[]> steps) {
		double[] first = (double[]) steps.get(0);
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

	private static void runDevicesAtRow(CMMCore core, spim.DeviceManager mgr, AcqRow row, int step) throws Exception {
		for (SPIMDevice dev : row.getDevices()) {
			String lbl = mgr.getLabel(dev);
			DeviceValueSet[] sets = row.getValueSets(dev);

			if (dev.getMMType().equals(DeviceType.StageDevice))
				core.setPosition(lbl, sets[0].getStartPosition());
			else if (dev.getMMType().equals(DeviceType.XYStageDevice))
				core.setXYPosition(lbl, sets[0].getStartPosition(), sets[1].getStartPosition());
			else
				throw new Exception("Unknown device type for \"" + dev
						+ "\"");
		}
		core.waitForSystem();
	}
	
	private static void updateLiveImage(MMStudioMainFrame f, TaggedImage ti)
	{
		try {
			MDUtils.setChannelIndex(ti.tags, 0);
			MDUtils.setFrameIndex(ti.tags, 0);
			MDUtils.setPositionIndex(ti.tags, 0);
			MDUtils.setSliceIndex(ti.tags, 0);
			ti.tags.put("Summary", f.getAcquisition(MMStudioMainFrame.SIMPLE_ACQ).getSummaryMetadata());
			f.addStagePositionToTags(ti);
			f.addImage(MMStudioMainFrame.SIMPLE_ACQ, ti, true, false);
		} catch (Throwable t) {
			ReportingUtils.logException("Attemped to update live window.", t);
		}
	}

	private static ImagePlus cleanAbort(AcqParams p, boolean live, boolean as, Thread ct) {
		p.getCore().setAutoShutter(as);
		p.getProgressListener().reportProgress(p.getTimeSeqCount() - 1, p.getRows().length - 1, 100.0D);

		try {
			if(ct != null && ct.isAlive()) {
				ct.interrupt();
				ct.join();
			}

			// TEMPORARY: Don't re-enable live mode. This keeps our laser off.
//			MMStudioMainFrame.getInstance().enableLiveMode(live);

			p.getOutputHandler().finalizeAcquisition();
			return p.getOutputHandler().getImagePlus();
		} catch(Exception e) {
			return null;
		}
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
	public static ImagePlus performAcquisition(final AcqParams params) throws Exception {
		if(params.isContinuous() && params.isAntiDriftOn())
			throw new IllegalArgumentException("No continuous acquisition w/ anti-drift!");

		final CMMCore core = params.getCore();

		final MMStudioMainFrame frame = MMStudioMainFrame.getInstance();
		boolean liveOn = frame.isLiveModeOn();
		if(liveOn)
			frame.enableLiveMode(false);

		boolean autoShutter = core.getAutoShutter();
		if(params.isIllumFullStack())
			core.setAutoShutter(false);

		final SPIMDevice[] metaDevs = params.getMetaDevices();

		final DeviceManager mgr = params.getDeviceManager();

		final AcqOutputHandler handler = params.getOutputHandler();

		final double acqBegan = System.nanoTime() / 1e9;

		final Map<AcqRow, AntiDrift> driftCompMap;
		if(params.isAntiDriftOn())
			driftCompMap = new HashMap<AcqRow, AntiDrift>(params.getRows().length);
		else
			driftCompMap = null;

		for(int timeSeq = 0; timeSeq < params.getTimeSeqCount(); ++timeSeq) {
			Thread continuousThread = null;
			if (params.isContinuous()) {
				continuousThread = new Thread() {
					private Throwable lastExc;

					@Override
					public void run() {
						try {
							core.clearCircularBuffer();
							core.startContinuousSequenceAcquisition(0);

							while (!Thread.interrupted()) {
								if (core.getRemainingImageCount() == 0) {
									Thread.yield();
									continue;
								};

								TaggedImage ti = core.popNextTaggedImage();
								handleSlice(core, mgr, metaDevs, acqBegan, ti, handler);

								if(params.isUpdateLive())
									updateLiveImage(frame, ti);
							}

							core.stopSequenceAcquisition();
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
			for(final AcqRow row : params.getRows()) {
				final int tp = timeSeq;
				final int rown = step;

				AntiDrift ad = null;
				if(row.getZContinuous() != true && params.isAntiDriftOn()) {
					if((ad = driftCompMap.get(row)) == null) {
						ad = params.getAntiDrift(row);
						ad.setCallback(new AntiDrift.Callback() {
							@Override
							public void applyOffset(Vector3D offs) {
								offs = new Vector3D(offs.getX()*-core.getPixelSizeUm(), offs.getY()*-core.getPixelSizeUm(), -offs.getZ());
								ij.IJ.log(String.format("TP %d view %d: Offset: %s", tp, rown, offs.toString()));
								row.translate(offs);
							}
						});
					}

					ad.startNewStack();
				};

				runDevicesAtRow(core, mgr, row, step);

				if(params.isIllumFullStack())
					core.setShutterOpen(true);

				handler.beginStack(0);

				if(row.getZStartPosition() == row.getZEndPosition()) {
					core.waitForImageSynchro();
					Thread.sleep(params.getSettleDelay());

					if(!params.isContinuous()) {
						core.snapImage();

						TaggedImage ti = core.getTaggedImage();
						handleSlice(core, mgr, metaDevs, acqBegan, ti, handler);
						if(ad != null)
							tallyAntiDriftSlice(core, mgr, row, ad, ti);
						if(params.isUpdateLive())
							updateLiveImage(frame, ti);
					};
				} else if (!row.getZContinuous()) {
					double start = core.getPosition(mgr.getLabel(SPIMDevice.STAGE_Z));
					double end = start + row.getZEndPosition() - row.getZStartPosition();
					for(double zStart = start; zStart <= end; zStart += row.getZStepSize()) {
						core.setPosition(mgr.getLabel(SPIMDevice.STAGE_Z), zStart);
						core.waitForImageSynchro();

						try {
							Thread.sleep(params.getSettleDelay());
						} catch(InterruptedException ie) {
							return cleanAbort(params, liveOn, autoShutter, continuousThread);
						}

						if(!params.isContinuous()) {
							core.snapImage();
							TaggedImage ti = core.getTaggedImage();
							handleSlice(core, mgr, metaDevs, acqBegan, ti, handler);
							if(ad != null)
								tallyAntiDriftSlice(core, mgr, row, ad, ti);
							if(params.isUpdateLive())
								updateLiveImage(frame, ti);
						}

						double stackProg = Math.max(Math.min((zStart - start)/(end - start),1),0);

						final Double progress = (double) (params.getRows().length * timeSeq + step + stackProg) / (params.getRows().length * params.getTimeSeqCount());

						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								params.getProgressListener().reportProgress(tp, rown, progress);
							}
						});
					}
				} else {
					core.setPosition(mgr.getLabel(SPIMDevice.STAGE_Z), row.getZStartPosition());
					String oldVel = core.getProperty(mgr.getLabel(SPIMDevice.STAGE_Z), "Velocity");

					core.setProperty(mgr.getLabel(SPIMDevice.STAGE_Z), "Velocity", row.getZVelocity());
					core.setPosition(mgr.getLabel(SPIMDevice.STAGE_Z), row.getZEndPosition());

					core.waitForDevice(mgr.getLabel(SPIMDevice.STAGE_Z));
					core.setProperty(mgr.getLabel(SPIMDevice.STAGE_Z), "Velocity", oldVel);
				};

				handler.finalizeStack(0);

				if(params.isIllumFullStack())
					core.setShutterOpen(false);

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
					@Override
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

		handler.finalizeAcquisition();

		if(autoShutter)
			core.setAutoShutter(true);

		// TEMPORARY: Don't re-enable live mode. This keeps our laser off.
//		if(liveOn)
//			frame.enableLiveMode(true);

		return handler.getImagePlus();
	}

	private static void tallyAntiDriftSlice(CMMCore core, DeviceManager mgr, AcqRow row, AntiDrift ad, TaggedImage img) throws Exception {
		ImageProcessor ip = ImageUtils.makeProcessor(img);

		ad.tallySlice(new Vector3D(0,0,core.getPosition(mgr.getLabel(SPIMDevice.STAGE_Z))-row.getZStartPosition()), ip);
	}

	private static void handleSlice(CMMCore core, DeviceManager mgr,
			SPIMDevice[] metaDevs, double start, TaggedImage slice,
			AcqOutputHandler handler) throws Exception {

		slice.tags.put("t", System.nanoTime() / 1e9 - start);

		for(SPIMDevice dev : metaDevs) {
			try {
				if(DeviceType.StageDevice.equals(dev.getMMType())) {
					slice.tags.put(dev.getText(), core.getPosition(mgr.getLabel(dev)));
				} else if(DeviceType.XYStageDevice.equals(dev.getMMType())) {
					slice.tags.put(dev.getText(), core.getXPosition(mgr.getLabel(dev)) + "x" + core.getYPosition(mgr.getLabel(dev)));
				} else {
					slice.tags.put(dev.getText(), "<unknown device type>");
				}
			} catch(Throwable t) {
				slice.tags.put(dev.getText(), "<<<Exception: " + t.getMessage() + ">>>");
			}
		}

		ImageProcessor ip = ImageUtils.makeProcessor(slice);

		handler.processSlice(ip, core.getXPosition(mgr.getLabel(SPIMDevice.STAGE_XY)),
				core.getYPosition(mgr.getLabel(SPIMDevice.STAGE_XY)),
				core.getPosition(mgr.getLabel(SPIMDevice.STAGE_Z)),
				core.getPosition(mgr.getLabel(SPIMDevice.STAGE_THETA)),
				System.nanoTime() / 1e9 - start);
	}
};
