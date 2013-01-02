package spim.progacq;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.TaggedImage;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;

public class ProgrammaticAcquisitor {
	public static final String STACK_DIVIDER = "-- STACK DIVIDER --";

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

	private static void runDevicesAtRow(CMMCore core, String[] devices,
			String[] positions, int step) throws Exception {
		for (int i = 0; i < devices.length; ++i) {
			String dev = devices[i];
			String pos = positions[i];
			try {
				if (core.getDeviceType(dev).equals(DeviceType.StageDevice))
					core.setPosition(dev, Double.parseDouble(pos));
				else if (core.getDeviceType(dev).equals(
						DeviceType.XYStageDevice))
					core.setXYPosition(dev, parseX(pos), parseY(pos));
				else
					throw new Exception("Unknown device type for \"" + dev
							+ "\"");
			} catch (NumberFormatException e) {
				throw new Exception("Malformed number \"" + pos
						+ "\" for device \"" + dev + "\", row " + step, e);
			}
		}
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
		final CMMCore core = params.getCore();

		MMStudioMainFrame frame = MMStudioMainFrame.getInstance();
		boolean liveOn = frame.isLiveModeOn();
		if(liveOn)
			frame.enableLiveMode(false);

		final String[] metaDevs = params.getMetaDevices();
		String[] devices = params.getStepDevices();

		final AcqOutputHandler handler = params.getOutputHandler();

		final double acqBegan = System.nanoTime() / 1e9;

		if(params.isContinuous() && params.isAntiDriftOn())
			throw new IllegalArgumentException("No continuous acquisition w/ anti-drift!");

		final Map<AcqRow, double[]> driftCompMap;
		if(params.isAntiDriftOn())
			driftCompMap = new HashMap<AcqRow, double[]>(params.getRows().length);
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

								handleSlice(core, metaDevs, acqBegan, core.popNextTaggedImage(), handler);
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
			for(AcqRow row : params.getRows()) {
				runDevicesAtRow(core, devices, row.getPrimaryPositions(), step);

				double[] xyzi = null;
				if(row.getZMode() != AcqRow.ZMode.CONTINUOUS_SWEEP && params.isAntiDriftOn()) {
					double[] offs;
					if((offs = driftCompMap.get(row)) != null)
						compensateForDrift(core, row, offs);
					else
						xyzi = new double[] {0, 0, 0, 0, 0, 0};
				};

				handler.beginStack(0);

				switch(row.getZMode()) {
				case SINGLE_POSITION:
					core.waitForImageSynchro();

					if(!params.isContinuous()) {
						core.snapImage();
						handleSlice(core, metaDevs, acqBegan, core.getTaggedImage(), handler);
						if(xyzi != null)
							xyzi = tallyAntiDriftSlice(core, row, xyzi, core.getTaggedImage());
					};
					break;
				case STEPPED_RANGE: {
					double start = core.getPosition(row.getDevice());
					double end = start + row.getEndPosition() - row.getStartPosition();
					for(double zStart = start; zStart <= end; zStart += row.getStepSize()) {
						core.setPosition(row.getDevice(), zStart);
						core.waitForImageSynchro();

						if(!params.isContinuous()) {
							core.snapImage();
							handleSlice(core, metaDevs, acqBegan, core.getTaggedImage(), handler);
							if(xyzi != null)
								xyzi = tallyAntiDriftSlice(core, row, xyzi, core.getTaggedImage());
						}

						double stackProg = Math.max(Math.min((zStart - start)/(end - start),1),0);

						final Double progress = (double) (params.getRows().length * timeSeq + step + stackProg) / (params.getRows().length * params.getTimeSeqCount());

						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								params.getProgressListener().stateChanged(new ChangeEvent(progress));
							}
						});
					};
					break;
				}
				case CONTINUOUS_SWEEP: {
					core.setPosition(row.getDevice(), row.getStartPosition());
					String oldVel = core.getProperty(row.getDevice(), "Velocity");

					core.setProperty(row.getDevice(), "Velocity", row.getVelocity());
					core.setPosition(row.getDevice(), row.getEndPosition());

					core.waitForDevice(row.getDevice());
					core.setProperty(row.getDevice(), "Velocity", oldVel);
					break;
				}
				};

				handler.finalizeStack(0);

				if(xyzi != null && xyzi[3] != 1) {
					xyzi = finalizeAntiDriftData(core, row, xyzi);

					ReportingUtils.logMessage("--- !!! --- !!! --- Determined CINT1: " + Arrays.toString(xyzi));

					xyzi[0] = core.getXPosition(core.getXYStageDevice()) - xyzi[0];
					xyzi[1] = core.getYPosition(core.getXYStageDevice()) - xyzi[1];
					xyzi[2] = row.getStartPosition() - xyzi[2]; 

					driftCompMap.put(row, xyzi);

				}

				if(Thread.interrupted())
					return handler.getImagePlus();

				if(params.isContinuous() && !continuousThread.isAlive())
					throw new Exception(continuousThread.toString());

				final Double progress = (double) (params.getRows().length * timeSeq + step + 1)
						/ (params.getRows().length * params.getTimeSeqCount());

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						params.getProgressListener().stateChanged(new ChangeEvent(progress));
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
					core.sleep(wait * 1e3);
				else
					core.logMessage("Behind schedule! (next seq in "
							+ Double.toString(wait) + "ms)");
			}
		}

		handler.finalizeAcquisition();

		if(liveOn)
			frame.enableLiveMode(true);

		return handler.getImagePlus();
	}

	private static void compensateForDrift(CMMCore core, AcqRow row, double[] offset) throws Exception {
		if(offset[3] != 1)
			throw new Error("Attempt to anti-drift with unfinished offset.");

		// Re-determine the center of intensity.
		double[] cint = new double[] {0, 0, 0, 0, 0, 0};

		// isAntiDriftOn() => !isContinuous(), so we can use snapImage...
		switch(row.getZMode()) {
		case SINGLE_POSITION: {
			core.waitForImageSynchro();

			core.snapImage();

			cint = tallyAntiDriftSlice(core, row, cint, core.getTaggedImage());
			};
			break;
		case STEPPED_RANGE: {
			for(double zStart = row.getStartPosition(); zStart <= row.getEndPosition(); zStart += row.getStepSize()) {
				core.setPosition(row.getDevice(), zStart);
				core.waitForImageSynchro();
				core.snapImage();

				cint = tallyAntiDriftSlice(core, row, cint, core.getTaggedImage());
			};
			break;
		}
		default:
			throw new Error("Bad mode during anti-drift...");
		};

		cint = finalizeAntiDriftData(core, row, cint);

		ReportingUtils.logMessage("--- !!! --- !!! --- Determined CINT2: " + Arrays.toString(cint));

		// Note that all the following is very Picard-specific. Other stage
		// motors will doubtless have different property names.

		// Move nearly there so moving back at speed 1 doesn't take too long.
		core.setXYPosition(core.getXYStageDevice(), cint[0] + offset[0]*0.9, cint[1] + offset[1]*0.9);
		core.setPosition(row.getDevice(), cint[2] + offset[2]*0.9);
		core.waitForDevice(row.getDevice());

		String oldVelZ = core.getProperty(row.getDevice(), "Velocity");
		String oldVelX = core.getProperty(core.getXYStageDevice(), "X-Velocity");
		String oldVelY = core.getProperty(core.getXYStageDevice(), "Y-Velocity");
		core.setProperty(row.getDevice(),"Velocity",1);
		core.setProperty(core.getXYStageDevice(), "X-Velocity", 1);
		core.setProperty(core.getXYStageDevice(), "Y-Velocity", 1);

		core.setXYPosition(core.getXYStageDevice(), cint[0] + offset[0], cint[1] + offset[1]);
		core.setPosition(row.getDevice(), cint[2] + offset[2]);
		core.waitForDevice(row.getDevice());

		core.setProperty(row.getDevice(), "Velocity", oldVelZ);
		core.setProperty(core.getXYStageDevice(), "X-Velocity", oldVelX);
		core.setProperty(core.getXYStageDevice(), "Y-Velocity", oldVelY);
	}

	private static double[] tallyAntiDriftSlice(CMMCore core, AcqRow row, double[] offs, TaggedImage img) throws Exception {
		double[] pix = new double[(int) (core.getImageWidth()*core.getImageHeight())];

		if(img.pix instanceof byte[]) {
			byte[] bytepix = (byte[])img.pix;
			for(int xy = 0; xy < core.getImageWidth()*core.getImageHeight(); ++xy)
				pix[xy] = bytepix[xy];
		} else if(img.pix instanceof short[]) {
			short[] shortpix = (short[])img.pix;
			for(int xy = 0; xy < core.getImageWidth()*core.getImageHeight(); ++xy)
				pix[xy] = shortpix[xy];
		} else {
			throw new Error("Unhandled image type! Implement more!");
		}

		if(offs == null)
			offs = new double[] {0, 0, 0, 0, Double.MAX_VALUE, Double.MIN_VALUE};

		for(int x=0; x < core.getImageWidth(); ++x) {
			double first = pix[x];
			double last = pix[(int) ((core.getImageHeight()-1)*core.getImageWidth() + x)];

			if(first < offs[4])
				offs[4] = first;
			if(last < offs[4])
				offs[4] = last;
			if(first > offs[5])
				offs[5] = first;
			if(last > offs[5])
				offs[5] = last;
		}
		for(int y=1; y < core.getImageHeight()-1; ++y) {
			double first = pix[(int) (core.getImageWidth()*y)];
			double last = pix[(int) ((core.getImageWidth()+1)*y-1)];
			if(first < offs[4])
				offs[4] = first;
			if(last < offs[4])
				offs[4] = last;
			if(first > offs[5])
				offs[5] = first;
			if(last > offs[5])
				offs[5] = last;
		}

		double xt = 0, yt = 0, it = 0;
		for(int y=0; y < core.getImageHeight(); ++y) {
			for(int x=0; x < core.getImageWidth(); ++x) {
				double i = pix[(int) (y*core.getImageWidth()+x)];
				xt += x*i;
				yt += y*i;
				it += i;
			}
		}

		ReportingUtils.logMessage("XT, YT, IT: " + (xt/it) + ", " + (yt/it) + ", " + it);

		offs[0] += xt;
		offs[1] += yt;
		offs[2] += (core.getPosition(row.getDevice()) - row.getStartPosition()) * it;
		offs[3] += it;

		return offs;
	}

	private static double[] finalizeAntiDriftData(CMMCore core, AcqRow row, double[] data) throws Exception {
		double divisor = 0;

		if(row.getZMode().equals(AcqRow.ZMode.SINGLE_POSITION))
			divisor = 1;
		else if(row.getZMode().equals(AcqRow.ZMode.STEPPED_RANGE))
			divisor = (row.getEndPosition() - row.getStartPosition())/row.getStepSize();
		else
			throw new Error("Bwuh?");

		ReportingUtils.logMessage("Pre-finalized data: " + Arrays.toString(data));

		// Finish averaging the coordinates and put them in motor space.
		double background = (data[5] - data[4])*0.6 + data[4];
		double w = core.getImageWidth();
		double h = core.getImageHeight();
		data[0] -= background*divisor*h*(w*(w+1))/2;
		data[1] -= background*divisor*w*(h*(h+1))/2;
		data[2] -= background*w*h*(divisor*(divisor+1))/2;

		data[0] /= data[3] - background*w*h*divisor;
		data[1] /= data[3] - background*w*h*divisor;
		data[2] /= data[3] - background*w*h*divisor;
		data[3] = 1;
		data[4] = data[5] = 0;

		data[0] = (data[0] - core.getImageWidth()/2)*core.getPixelSizeUm() + core.getXPosition(core.getXYStageDevice());
		data[1] = (data[1] - core.getImageHeight()/2)*core.getPixelSizeUm() + core.getYPosition(core.getXYStageDevice());
		data[2] = row.getStartPosition() + data[2];

		ReportingUtils.logMessage("Finalized CINT: " + Arrays.toString(data));

		return data;
	}

	private static void handleSlice(CMMCore core, String[] devices, double start,
			TaggedImage slice, AcqOutputHandler handler) throws Exception {

		slice.tags.put("t", System.nanoTime() / 1e9 - start);

		for(String dev : devices) {
			try {
				if(core.getDeviceType(dev) == DeviceType.StageDevice) {
					slice.tags.put(dev, core.getPosition(dev));
				} else if(core.getDeviceType(dev) == DeviceType.XYStageDevice) {
					slice.tags.put(dev, core.getXPosition(dev) + "x" + core.getYPosition(dev));
				} else {
					slice.tags.put(dev, "<unknown device type>");
				}
			} catch(Throwable t) {
				slice.tags.put(dev, "<<<Exception: " + t.getMessage() + ">>>");
			}
		}

		ImageProcessor ip = newImageProcessor(core, slice.pix);

		// FIXME: Don't assume the name is 'Picard Twister'...
		handler.processSlice(ip, core.getXPosition(core.getXYStageDevice()),
				core.getYPosition(core.getXYStageDevice()),
				core.getPosition(core.getFocusDevice()),
				core.getPosition("Picard Twister"),
				System.nanoTime() / 1e9 - start);
	}

	/**
	 * Utility function to convert an array of bytes into an array of integers.
	 * Effectively a reinterpret_cast.
	 * 
	 * @param b
	 *            Array of bytes (image data).
	 * @return Array of integers b represents.
	 * @throws Exception
	 *             If b has an impossible length.
	 */
	private static int[] bToI(byte[] b) throws Exception {
		if (b.length % 4 != 0)
			throw new Exception("4-byte length mismatch!");

		int[] i = new int[b.length / 4];

		for (int bi = 0; bi < i.length; ++bi) {
			// These might not actually be ARGB. All 32-bit pixel formats are
			// passed through this function.
			int alpha = b[bi * 4 + 3] & 0xFF;
			int red = b[bi * 4 + 2] & 0xFF;
			int green = b[bi * 4 + 1] & 0xFF;
			int blue = b[bi * 4 + 0] & 0xFF;

			i[bi] = (alpha << 24) | (red << 16) | (green << 8) | blue;
		}

		return i;
	}

	/**
	 * Like above, but reinterprets as an array of floats.
	 * 
	 * @param b
	 *            Array of bytes (image data).
	 * @return Array of floats b represents.
	 * @throws Exception
	 *             If b has an impossible length.
	 */
	private static double[] bToF(byte[] b) throws Exception {
		if (b.length % 4 != 0)
			throw new Exception("4-byte float length mismatch!");

		double[] f = new double[b.length / 4];

		for (int bi = 0; bi < f.length; ++bi) {
			int hh = b[bi * 4 + 3] & 0xFF;
			int hl = b[bi * 4 + 2] & 0xFF;
			int lh = b[bi * 4 + 1] & 0xFF;
			int ll = b[bi * 4 + 0] & 0xFF;

			f[bi] = (double) Float.intBitsToFloat((hh << 24) | (hl << 16)
					| (lh << 8) | ll);
		}

		return f;
	}

	/**
	 * Just like above, but as doubles.
	 * 
	 * @param b
	 *            Array of bytes (64-bit floating point image data).
	 * @return Array of doubles represented by b.
	 * @throws Exception
	 *             If b has an impossible length.
	 */
	private static double[] bToD(byte[] b) throws Exception {
		if (b.length % 8 != 0)
			throw new Exception("8-byte length mismatch!");

		double[] d = new double[b.length / 8];

		for (int bi = 0; bi < d.length; ++bi) {
			int hhh = b[bi * 8 + 7] & 0xFF;
			int hhl = b[bi * 8 + 6] & 0xFF;
			int hlh = b[bi * 8 + 5] & 0xFF;
			int hll = b[bi * 8 + 4] & 0xFF;
			int lhh = b[bi * 8 + 3] & 0xFF;
			int lhl = b[bi * 8 + 2] & 0xFF;
			int llh = b[bi * 8 + 1] & 0xFF;
			int lll = b[bi * 8 + 0] & 0xFF;

			d[bi] = Double.longBitsToDouble((hhh << 56) | (hhl << 48)
					| (hlh << 40) | (hll << 32) | (lhh << 24) | (lhl << 16)
					| (llh << 8) | (lll << 0));
		}

		return d;
	}

	/**
	 * Generates an image processor object (IJ) based off the latest image taken
	 * by the specified core.
	 * 
	 * @param core
	 *            The Micro-Manager core reference to acquire via.
	 * @return An ImageProcessor object containing the pixels in MM's image.
	 * @throws Exception
	 *             On unsupported image modes.
	 */
	public static ImageProcessor newImageProcessor(CMMCore core, Object image)
			throws Exception {
		if (core.getBytesPerPixel() == 1) {
			return new ByteProcessor((int) core.getImageWidth(),
					(int) core.getImageHeight(), (byte[]) image, null);
		} else if (core.getBytesPerPixel() == 2) {
			return new ShortProcessor((int) core.getImageWidth(),
					(int) core.getImageHeight(), (short[]) image, null);
		} else if (core.getBytesPerPixel() == 4) {
			if (core.getNumberOfComponents() > 1) {
				return new ColorProcessor((int) core.getImageWidth(),
						(int) core.getImageHeight(), bToI((byte[]) image));
			} else {
				return new FloatProcessor((int) core.getImageWidth(),
						(int) core.getImageHeight(), bToF((byte[]) image));
			}
		} else if (core.getBytesPerPixel() == 8) {
			if (core.getNumberOfComponents() > 1) {
				throw new Exception("No support for 64-bit color!");
			} else {
				return new FloatProcessor((int) core.getImageWidth(),
						(int) core.getImageHeight(), bToD((byte[]) image));
			}
		} else {
			// TODO: Expand support to include all modes...
			throw new Exception("Unsupported image depth ("
					+ core.getBytesPerPixel() + " bytes/pixel)");
		}
	}

	/**
	 * Simple function to pull the X coordinate out of an ordered pair string.
	 * This function doesn't care at all if the string you pass it is wrong, so
	 * don't pass it a wrong string. :)
	 * 
	 * @param pair
	 *            An ordered pair.
	 * @return The X coordinate of that ordered pair.
	 */
	private static double parseX(String pair) {
		return Double.parseDouble(pair.substring(0, pair.indexOf(',')));
	}

	/**
	 * Pulls the Y component of an ordered pair.
	 * 
	 * @see parseX
	 * 
	 * @param pair
	 *            An ordered pair.
	 * @return The Y coordinate of that ordered pair.
	 */
	private static double parseY(String pair) {
		return Double.parseDouble(pair.substring(pair.indexOf(' ') + 1));
	}
};
