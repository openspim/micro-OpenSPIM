package spim.ui.view.component.acquisition;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JOptionPane;

import javafx.beans.property.LongProperty;
import javafx.event.Event;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import mmcorej.org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.data.*;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.acquisition.internal.DefaultAcquisitionStartedEvent;
import org.micromanager.events.internal.DefaultChannelGroupChangedEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import spim.algorithm.DefaultAntiDrift;
import spim.hardware.SPIMSetup;
import spim.hardware.VersaLase;
import spim.io.OpenSPIMSinglePlaneTiffSeries;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.MMAcquisitionEngine;

/**
 * This is the modified version of AcquisitionWrapperEngine.java
 * The original source is located in https://github.com/nicost/micro-manager/blob/ViewerPlusCV/mmstudio/src/main/java/org/micromanager/acquisition/internal/AcquisitionWrapperEngine.java
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2019
 */
public class AcqWrapperEngine implements AcquisitionEngine
{
	final private SPIMSetup spimSetup_;
	private CMMCore core_;
	private Studio studio_;
	private PositionList posList_;
	private String zstage_;
	private double sliceZStepUm_;
	private double sliceZBottomUm_;
	private double sliceZTopUm_;
	private boolean useSlices_;
	private boolean useFrames_;
	private boolean useChannels_;
	private boolean useMultiPosition_;
	private boolean keepShutterOpenForStack_;
	private boolean keepShutterOpenForChannels_;
	private ArrayList<ChannelSpec> channels_;

	private String rootName_;
	private String dirName_;
	private int numFrames_;
	private double interval_;
	private String comment_;
	private boolean saveFiles_;
	private int acqOrderMode_;
	private boolean useAutoFocus_;
	private int afSkipInterval_;
	private boolean absoluteZ_;
	private int cameraTimeout_ = 20000;
	private boolean shouldDisplayImages_ = false;
	private IAcquisitionEngine2010 acquisitionEngine2010_;
	private ArrayList<Double> customTimeIntervalsMs_;
	private boolean useCustomIntervals_;
	private JSONObject summaryMetadata_;
	private ArrayList<AcqSettingsListener> settingsListeners_;
	private Datastore curStore_;
	private Pipeline curPipeline_;
	private String orgChannelGroup;

	private int t_, angle_;
	private List<String> cameras_;
	private List< ChannelItem > channelItems_;
	private boolean arduinoSelected_;
	private LongProperty processedImages_;
	private HashMap< PositionItem, DefaultAntiDrift> driftCompMap_;
	private DefaultAntiDrift currentAntiDrift_;
	private Integer antiDriftReferenceChannel_;
	private Boolean onTheFly_;
	private Boolean ablationSupport_;
	private String prefix_;
	private File outFolder_;

	final String channelGroupName = "OpenSPIM-channels";

	private ReentrantLock rlock = new ReentrantLock(true);

	Datastore mpStore_;
	Pipeline mpPipeline_;
	String ablationFilePrefix_;
	TreeMap<Integer, Image>[] mpImages_;
	TaggedImageSink sink;

	private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.red, Color.green, Color.blue, Color.yellow, Color.pink };

	public AcqWrapperEngine(SPIMSetup setup, final Studio frame, Datastore store,
							String currentCamera, List<String> cameras, File outFolder, String acqFilenamePrefix,
							List<ChannelItem> channelItems,
							boolean arduinoSelected,
							LongProperty processedImages, HashMap<PositionItem, DefaultAntiDrift> driftCompMap, Integer adReferenceChannel, Boolean saveMIP, Boolean onTheFly, Boolean ablationSupport) throws Exception
	{
		curStore_ = store;

		cameras_ = cameras;
		dirName_ = outFolder != null ? outFolder.getAbsolutePath() : null;

		channelItems_ = channelItems;
		arduinoSelected_ = arduinoSelected;
		processedImages_ = processedImages;
		driftCompMap_ = driftCompMap;

		spimSetup_ = setup;
		studio_ = frame;
		core_ = frame.core();

		absoluteZ_ = true;
		useAutoFocus_ = false;
		saveFiles_ = false;
		useCustomIntervals_ = false;
		useSlices_ = true;
		useFrames_ = false;
		useChannels_ = true;
		useMultiPosition_ = true;

		keepShutterOpenForStack_ = false;
		keepShutterOpenForChannels_ = false;

		settingsListeners_ = new ArrayList<AcqSettingsListener>();
		posList_ = new PositionList();
		antiDriftReferenceChannel_ = adReferenceChannel;
		onTheFly_ = onTheFly;
		ablationSupport_ = ablationSupport;
		prefix_ = acqFilenamePrefix;
		outFolder_ = outFolder;

		// Initial setting

		// Channel settings
		orgChannelGroup = core_.getChannelGroup();

		if( core_.isGroupDefined( channelGroupName ) ) {
			core_.deleteConfigGroup( channelGroupName );
		}
		core_.defineConfigGroup( channelGroupName );

		// Channel iteration
		ArrayList< ChannelSpec > channels = new ArrayList<>();
		int ch = 0;
		if(arduinoSelected_) {
			for ( String camera : cameras_ )
			{
				for ( ChannelItem channelItem : channelItems_ )
				{
					String config = "Ch-" + ch++;

					if ( spimSetup_.getArduino1() != null )
						spimSetup_.getArduino1().setSwitchState( channelItem.getLaser() );

					core_.defineConfig(channelGroupName, config, spimSetup_.getArduino1().getLabel(), "State", channelItem.getLaser());
					core_.defineConfig(channelGroupName, config, "Core", "Camera", camera);

					double exp = channelItem.getValue().doubleValue();
					ChannelSpec spec = new ChannelSpec.Builder()
							.channelGroup( channelGroupName )
							.camera( camera )
							.color( DEFAULT_COLORS[ch % 6] )
							.config( config )
							.doZStack( true )
							.exposure( exp )
							.build();

					channels.add( spec );
				}
			}
		} else {
			for ( ChannelItem channelItem : channelItems_ )
			{
				String config = "Ch-" + ch++;

				if(spimSetup_.getLaser() != null) {
					if(spimSetup_.getLaser().getLabel().startsWith("VLT_VersaLase")) {
						core_.defineConfig(channelGroupName, config, "Core", "Shutter", spimSetup_.getLaser().getLabel());
						VersaLase.VersaLaseLaser laser = ((VersaLase)spimSetup_.getLaser()).getLaser(channelItem.getLaser());

						core_.defineConfig(channelGroupName, config, spimSetup_.getLaser().getLabel(), laser.getShutter(), "ON");
					} else {
						core_.defineConfig(channelGroupName, config, "Core", "Shutter", channelItem.getLaser());
					}
				}


				core_.defineConfig(channelGroupName, config, "Core", "Camera", channelItem.getName());
				double exp = channelItem.getValue().doubleValue();

				ChannelSpec spec = new ChannelSpec.Builder()
						.channelGroup( channelGroupName )
						.camera( channelItem.getName() )
						.color( DEFAULT_COLORS[ch % 6] )
						.config( config )
						.doZStack( true )
						.exposure( exp )
						.build();

				channels.add( spec );
			}
		}
		channels_ = channels;
		setChannelGroup( channelGroupName );

		if (ablationSupport) {
			ablationFilePrefix_ = acqFilenamePrefix + "_ablation";
		}

		if ( outFolder != null && saveMIP ) {
			File saveDir = new File(outFolder, acqFilenamePrefix + "-MIP");

			if (!outFolder.exists() && !outFolder.mkdirs()) {
				System.err.println( "Couldn't create output directory " + outFolder.getAbsolutePath() );
			}
			else {
				if ( saveDir.exists() ) {
					Arrays.stream(saveDir.listFiles()).forEach(c -> c.delete());
					saveDir.delete();
				}
				saveDir.mkdirs();
				if (saveMIP) {
					List<String> multis = MMAcquisitionEngine.getMultiCams(core_);

					DefaultDatastore result = new DefaultDatastore(frame);
					result.setStorage(new OpenSPIMSinglePlaneTiffSeries(result, saveDir.getAbsolutePath(), acqFilenamePrefix, true));
					mpStore_ = result;

					mpPipeline_ = studio_.data().copyApplicationPipeline(mpStore_, false);
					mpStore_.registerForEvents(this);

					DisplayWindow display = frame.displays().createDisplay(mpStore_);

					// Channel setting for the display
					DisplaySettings dsTmp = DefaultDisplaySettings.getStandardSettings(
							PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());

					DisplaySettings.Builder displaySettingsBuilder
							= dsTmp.copyBuilder();

					final int nrChannels = currentCamera.startsWith("Multi") ? multis.size() * channels.size() : channels.size();

					if (nrChannels == 1) {
						displaySettingsBuilder.colorModeGrayscale();
					} else {
						displaySettingsBuilder.colorModeComposite();
					}
					for (int channelIndex = 0; channelIndex < nrChannels; channelIndex++) {
						ChannelDisplaySettings channelSettings
								= displaySettingsBuilder.getChannelSettings(channelIndex);
						Color chColor = DEFAULT_COLORS[(channelIndex+1) % 6];
						ChannelDisplaySettings.Builder csb =
								channelSettings.copyBuilder().color(chColor);

						csb.name(channelIndex + "");

						displaySettingsBuilder.channel(channelIndex, csb.build());
					}

					display.compareAndSetDisplaySettings(display.getDisplaySettings(), displaySettingsBuilder.build());

					display.setCustomTitle("MIP:" + acqFilenamePrefix);
					frame.displays().manage(mpStore_);
					mpImages_ = new TreeMap[currentCamera.startsWith("Multi") ? multis.size() * channels.size() : channels.size()];
					for (int i = 0; i < mpImages_.length; i++) {
						mpImages_[i] = new TreeMap<>();
					}
				}
			}
		}
	}

	public void exit() {
		try
		{
			core_.setChannelGroup( orgChannelGroup );
			core_.deleteConfigGroup( channelGroupName );
			if(null != mpStore_) {
				while(rlock.isLocked()) {
					Thread.sleep(100);
				}
				mpStore_.freeze();
			}

		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	public void startAcquire(int t, int angle, PositionItem positionItem) throws MMException
	{
		t_ = t;
		angle_ = angle;

		sliceZBottomUm_ = positionItem.getZStart();
		sliceZTopUm_ = positionItem.getZEnd();
		sliceZStepUm_ = positionItem.getZStep();

		posList_.clearAllPositions();
		posList_.addPosition( 0, new MultiStagePosition( spimSetup_.getXStage().getLabel(), positionItem.getX(), positionItem.getY(), spimSetup_.getZStage().getLabel(), positionItem.getZStart() ) );

		if(driftCompMap_ != null)
			currentAntiDrift_ = driftCompMap_.get(positionItem);

		acquire();
	}

	@Override
	public Datastore acquire() throws MMException {
		return runAcquisition(getSequenceSettings());
	}

	@Override
	public Datastore getAcquisitionDatastore() {
		return curStore_;
	}

	@Override
	public void addSettingsListener(AcqSettingsListener listener) {
		settingsListeners_.add(listener);
	}

	@Override
	public void removeSettingsListener(AcqSettingsListener listener) {
		settingsListeners_.remove(listener);
	}

	public void settingsChanged() {
		for (AcqSettingsListener listener:settingsListeners_) {
			listener.settingsChanged();
		}
	}

	protected IAcquisitionEngine2010 getAcquisitionEngine2010() {
		if (acquisitionEngine2010_ == null) {
			acquisitionEngine2010_ = ((MMStudio) studio_).getAcquisitionEngine2010();
		}
		return acquisitionEngine2010_;
	}

	protected Datastore runAcquisition(SequenceSettings acquisitionSettings) {
		try {
			studio_.events().registerForEvents(this);
			// Start up the acquisition engine
			BlockingQueue<TaggedImage> engineOutputQueue = getAcquisitionEngine2010().run(
					acquisitionSettings, true, posList_,
					studio_.getAutofocusManager().getAutofocusMethod());
			summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();

			curStore_.registerForEvents(this);
			curPipeline_ = studio_.data().copyApplicationPipeline(curStore_, false);

			studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_,
					this, acquisitionSettings));

			double x = spimSetup_.getXStage().getPosition();
			double y = spimSetup_.getYStage().getPosition();
			double theta = spimSetup_.getAngle();

			// Start pumping images through the pipeline and into the datastore.
			sink = new TaggedImageSink(
					engineOutputQueue, curPipeline_, curStore_, this, studio_.events(),
					t_, angle_, cameras_, x, y, theta, mpImages_, processedImages_, currentAntiDrift_, antiDriftReferenceChannel_, dirName_, onTheFly_ );

			sink.start(() -> getAcquisitionEngine2010().stop(), () -> {
				rlock.lock();
				if(ablationSupport_) {
					List<String> multis = MMAcquisitionEngine.getMultiCams(core_);
					for( int i = 0; i < multis.size(); i++ ) {
						File latestFile = new File(outFolder_, getLatestFile(i, t_, angle_));
						if (latestFile.exists()) {
							File ablationFile = new File(outFolder_, getAblationFilename(i, angle_));
							try {
								Files.copy(latestFile.toPath(), ablationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
				generateMIP();
				rlock.unlock();
				curStore_.unregisterForEvents(AcqWrapperEngine.this);
				if (mpStore_ != null) {
					studio_.events().post(new DefaultAcquisitionEndedEvent(
							mpStore_, this));

					mpStore_.unregisterForEvents(this);
				}
			});

			return curStore_;

		} catch (Throwable ex) {
			ReportingUtils.showError(ex);
			studio_.events().post(new DefaultAcquisitionEndedEvent(
					curStore_, this));
			return null;
		}
	}

	private void generateMIP() {
		if(null != mpImages_) {
			for (TreeMap<Integer, Image> stack : mpImages_) {
				// Could be moved outside processImage() ?
				Image img = stack.get(0);
				if (img == null) continue;

				int bitDepth = img.getMetadata().getBitDepth();
				int width = img.getWidth();
				int height = img.getHeight();
				int bytesPerPixel = img.getBytesPerPixel();
				int numComponents = img.getNumComponents();
				Coords coords = img.getCoords();
				Metadata metadata = img.getMetadata();

				Object resultPixels = null;

				if (bytesPerPixel == 1) {

					// Create new array
					float[] newPixels = new float[width * height];
					byte[] newPixelsFinal = new byte[width * height];

					float currentValue;
					float actualValue;

					// Init the new array
					for (int i = 0; i < newPixels.length; i++) {
						newPixels[i] = 0;
					}

					// Iterate over all frames
					for (int i = 0; i < stack.size(); i++) {

						// Get current frame pixels
						img = stack.get(i);
						byte[] imgPixels = (byte[]) img.getRawPixels();

						// Iterate over all pixels
						for (int index = 0; index < newPixels.length; index++) {
							currentValue = (float) (int) (imgPixels[index] & 0xffff);
							actualValue = (float) newPixels[index];
							newPixels[index] = (float) Math.max(currentValue, actualValue);
						}
					}

					// Convert to short
					for (int index = 0; index < newPixels.length; index++) {
						newPixelsFinal[index] = (byte) newPixels[index];
					}

					resultPixels = newPixelsFinal;

				} else if (bytesPerPixel == 2) {

					// Create new array
					float[] newPixels = new float[width * height];
					short[] newPixelsFinal = new short[width * height];

					float currentValue;
					float actualValue;

					// Init the new array
					for (int i = 0; i < newPixels.length; i++) {
						newPixels[i] = 0;
					}


					// Iterate over all frames
					for (int i = 0; i < stack.size(); i++) {

						// Get current frame pixels
						img = stack.get(i);
						short[] imgPixels = (short[]) img.getRawPixels();

						// Iterate over all pixels
						for (int index = 0; index < newPixels.length; index++) {
							currentValue = (float) (int) (imgPixels[index] & 0xffff);
							actualValue = (float) newPixels[index];
							newPixels[index] = (float) Math.max(currentValue, actualValue);
						}
					}

					// Convert to short
					for (int index = 0; index < newPixels.length; index++) {
						newPixelsFinal[index] = (short) newPixels[index];
					}

					resultPixels = newPixelsFinal;

				}

				// Create the processed image
				Image processedImage_ = studio_.data().createImage(resultPixels, width, height,
						bytesPerPixel, numComponents, coords, metadata);

				if (null != mpStore_ && !mpStore_.isFrozen())
					try {
						mpPipeline_.insertImage(processedImage_);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (PipelineErrorException e) {
						e.printStackTrace();
					}
			}

			for(int i = 0; i < mpImages_.length; i++) {
				mpImages_[i] = new TreeMap<>();
			}
		}
	}

	private String getLatestFile(int c, int t, int p) {
		String chString = String.format("_channel%03d", c);
		String posString = String.format("_position%03d", p);

//		System.out.println(String.format(prefix_ + chString + posString + "_time%09d_view000_z000.tif", t));
		return String.format(prefix_ + chString + posString + "_time%09d_view000_z000.tif", t);
	}

	private String getAblationFilename(int c, int p) {
		String chString = String.format("_channel%03d", c);
		String posString = String.format("_position%02d", p);

		return String.format(ablationFilePrefix_ + chString + posString + ".tif");
	}

	private int getNumChannels() {
		int numChannels = 0;
		if (useChannels_) {
			for (ChannelSpec channel : channels_) {
				if (channel.useChannel) {
					++numChannels;
				}
			}
		} else {
			numChannels = 1;
		}
		return numChannels;
	}

	public int getNumFrames() {
		int numFrames = numFrames_;
		if (!useFrames_) {
			numFrames = 1;
		}
		return numFrames;
	}

	private int getNumPositions() {
		int numPositions = Math.max(1, posList_.getNumberOfPositions());
		if (!useMultiPosition_) {
			numPositions = 1;
		}
		return numPositions;
	}

	private int getNumSlices() {
		if (!useSlices_) {
			return 1;
		}
		if (sliceZStepUm_ == 0) {
			// XXX How should this be handled?
			return Integer.MAX_VALUE;
		}
		return 1 + (int)Math.abs((sliceZTopUm_ - sliceZBottomUm_) / sliceZStepUm_);
	}

	private int getTotalImages() {
		int totalImages = getNumFrames() * getNumSlices() * getNumChannels() * getNumPositions();
		return totalImages;
	}

	private long getTotalMB() {
		CMMCore core = studio_.core();
		long totalMB = core.getImageWidth() * core.getImageHeight() * core.getBytesPerPixel() * ((long) getTotalImages()) / 1048576L;
		return totalMB;
	}

	private void updateChannelCameras() {
		for (ChannelSpec channel : channels_) {
			channel.camera = getSource(channel);
		}
	}

	/*
	 * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
	 * be specified. Passing a value of -1 results in the runnable being attached
	 * at all values of that index. For example, if the first argument is -1,
	 * then the runnable will execute at every frame.
	 */
	@Override
	public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
		getAcquisitionEngine2010().attachRunnable(frame, position, channel, slice, runnable);
	}
	/*
	 * Clear all attached runnables from the acquisition engine.
	 */

	@Override
	public void clearRunnables() {
		getAcquisitionEngine2010().clearRunnables();
	}

	private String getSource(ChannelSpec channel) {
		try {
			Configuration state = core_.getConfigState(core_.getChannelGroup(), channel.config);
			if (state.isPropertyIncluded("Core", "Camera")) {
				return state.getSetting("Core", "Camera").getPropertyValue();
			} else {
				return "";
			}
		} catch (Exception ex) {
			ReportingUtils.logError(ex);
			return "";
		}
	}

	public SequenceSettings getSequenceSettings() {
		SequenceSettings.Builder ssb = new SequenceSettings.Builder();

		updateChannelCameras();

		// Frames
		ssb.useFrames(useFrames_);
		ssb.useCustomIntervals(useCustomIntervals_);
		ssb.numFrames(numFrames_);
		ssb.intervalMs(interval_);

		// Slices
		ssb.useSlices(useSlices_);
		if (useSlices_) {
			double start = sliceZBottomUm_;
			double stop = sliceZTopUm_;
			double step = Math.abs(sliceZStepUm_);
			if (step == 0.0) {
				throw new UnsupportedOperationException("zero Z step size");
			}
			int count = getNumSlices();
			if (start > stop) {
				step = -step;
			}
			ArrayList<Double> list = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				list.add(start + i * step);
			}
			ssb.slices(list);
		}

		ssb.relativeZSlice(!this.absoluteZ_);
		try {
			String zdrive = core_.getFocusDevice();
			ssb.zReference((zdrive.length() > 0)
					? core_.getPosition(core_.getFocusDevice()) : 0.0);
		} catch (Exception ex) {
			ReportingUtils.logError(ex);
		}

		// Channels
		ssb.useChannels(this.useChannels_);
		ssb.channels(this.channels_);

		//since we're just getting this from the core, it should be safe to get
		//regardless of whether we're using any channels. This also makes the
		//behavior more consistent with the setting behavior.
		ssb.channelGroup(getChannelGroup());

		//timeFirst = true means that time points are collected at each position
		ssb.timeFirst((acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
				|| acqOrderMode_ == AcqOrderMode.POS_TIME_SLICE_CHANNEL));
		ssb.slicesFirst((acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
				|| acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE));

		ssb.useAutofocus(useAutoFocus_);
		ssb.skipAutofocusCount(afSkipInterval_);

		ssb.keepShutterOpenChannels(keepShutterOpenForChannels_);
		ssb.keepShutterOpenSlices(keepShutterOpenForStack_);

		ssb.save(saveFiles_);
		ssb.comment(comment_);

		ssb.usePositionList(this.useMultiPosition_);
		ssb.cameraTimeout(this.cameraTimeout_);
		ssb.shouldDisplayImages(shouldDisplayImages_);

		ssb.saveMode(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);

		return ssb.build();
	}

	public void setSequenceSettings(SequenceSettings ss) {

		updateChannelCameras();

		// Frames
		useFrames_ = true;
		useCustomIntervals_ = ss.useCustomIntervals;
		if (useCustomIntervals_) {
			customTimeIntervalsMs_ = ss.customIntervalsMs;
			numFrames_ = ss.customIntervalsMs.size();
		} else {
			numFrames_ = ss.numFrames;
			interval_ = ss.intervalMs;
		}

		// Slices
		useSlices_ = true;
		if (ss.slices.size() == 0)
			useSlices_ = false;
		else if (ss.slices.size() == 1) {
			sliceZBottomUm_ = ss.slices.get(0);
			sliceZTopUm_ = sliceZBottomUm_;
			sliceZStepUm_ = 0.0;
		} else {
			sliceZBottomUm_ = ss.slices.get(0);
			sliceZTopUm_ = ss.slices.get(ss.slices.size()-1);
			sliceZStepUm_ = ss.slices.get(1) - ss.slices.get(0);
			if (sliceZBottomUm_ > sliceZBottomUm_)
				sliceZStepUm_ = -sliceZStepUm_;
		}

		absoluteZ_ = !ss.relativeZSlice;
		// NOTE: there is no adequate setting for ss.zReference

		// Channels
		if (ss.channels.size() > 0)
			useChannels_ = true;
		else
			useChannels_ = false;

		channels_ = ss.channels;
		//should check somewhere that channels actually belong to channelGroup
		//currently it is possible to set channelGroup to a group other than that
		//which channels belong to.
		setChannelGroup(ss.channelGroup);

		//timeFirst = true means that time points are collected at each position
		if (ss.timeFirst && ss.slicesFirst) {
			acqOrderMode_ = AcqOrderMode.POS_TIME_CHANNEL_SLICE;
		}

		if (ss.timeFirst && !ss.slicesFirst) {
			acqOrderMode_ = AcqOrderMode.POS_TIME_SLICE_CHANNEL;
		}

		if (!ss.timeFirst && ss.slicesFirst) {
			acqOrderMode_ = AcqOrderMode.TIME_POS_CHANNEL_SLICE;
		}

		if (!ss.timeFirst && !ss.slicesFirst) {
			acqOrderMode_ = AcqOrderMode.TIME_POS_SLICE_CHANNEL;
		}

		useAutoFocus_ = ss.useAutofocus;
		afSkipInterval_ = ss.skipAutofocusCount;

		keepShutterOpenForChannels_ = ss.keepShutterOpenChannels;
		keepShutterOpenForStack_ = ss.keepShutterOpenSlices;

		saveFiles_ = ss.save;
		rootName_ = ss.root;
		dirName_ = ss.prefix;
		comment_ = ss.comment;

		useMultiPosition_ = ss.usePositionList;
		cameraTimeout_ = ss.cameraTimeout;
		shouldDisplayImages_ = ss.shouldDisplayImages;
	}


	//////////////////// Actions ///////////////////////////////////////////
	@Override
	public void stop(boolean interrupted) {
		try {
			if (null != sink) sink.stop();
			sink = null;

			if (acquisitionEngine2010_ != null) {
				acquisitionEngine2010_.stop();
			}
			acquisitionEngine2010_ = null;
		} catch (Exception ex) {
			ReportingUtils.showError(ex, "Acquisition engine stop request failed");
		}
	}

	@Override
	public boolean abortRequest() {
		if (isAcquisitionRunning()) {
			String[] options = { "Abort", "Cancel" };
			int result = JOptionPane.showOptionDialog(null,
					"Abort current acquisition task?",
					"Micro-Manager",
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE, null,
					options, options[1]);
			if (result == 0) {
				stop(true);
				return true;
			}
			else {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean abortRequested() {
		return acquisitionEngine2010_.stopHasBeenRequested();
	}

	@Override
	public void shutdown() {
		stop(true);
	}

	@Override
	public void setPause(boolean state) {
		if (state) {
			acquisitionEngine2010_.pause();
		} else {
			acquisitionEngine2010_.resume();
		}
	}

	//// State Queries /////////////////////////////////////////////////////
	@Override
	public boolean isAcquisitionRunning() {
		// Even after the acquisition finishes, if the pipeline is still "live",
		// we should consider the acquisition to be running.
		if (acquisitionEngine2010_ != null) {
			return (acquisitionEngine2010_.isRunning() ||
					(curPipeline_ != null && !curPipeline_.isHalted()));
		} else {
			return false;
		}
	}

	@Override
	public boolean isFinished() {
		if (acquisitionEngine2010_ != null) {
			return acquisitionEngine2010_.isFinished();
		} else {
			return false;
		}
	}

	@Override
	public boolean isMultiFieldRunning() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public long getNextWakeTime() {
		return acquisitionEngine2010_.nextWakeTime();
	}


	//////////////////// Setters and Getters ///////////////////////////////

	@Override
	public void setPositionList(PositionList posList) {
		posList_ = posList;
	}

	@Override
	public void setParentGUI(Studio parent) {
		studio_ = parent;
		core_ = studio_.core();
		studio_.events().registerForEvents(this);
	}

	@Override
	public void setZStageDevice(String stageLabel_) {
		zstage_ = stageLabel_;
	}

	@Override
	public void setUpdateLiveWindow(boolean b) {
		// do nothing
	}

	@Override
	public void setFinished() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public double getFrameIntervalMs() {
		return interval_;
	}

    public boolean isZSliceSettingEnabled() {
      return useSlices_;
	}

	@Override
	public void setChannel(int row, ChannelSpec channel) {
		channels_.set(row, channel);
	}

	/**
	 * Get first available config group
	 */
	@Override
	public String getFirstConfigGroup() {
		if (core_ == null) {
			return "";
		}

		String[] groups = getAvailableGroups();

		if (groups == null || groups.length < 1) {
			return "";
		}

		return getAvailableGroups()[0];
	}

	/**
	 * Find out which channels are currently available for the selected channel group.
	 * @return - list of channel (preset) names
	 */
	@Override
	public String[] getChannelConfigs() {
		if (core_ == null) {
			return new String[0];
		}
		return core_.getAvailableConfigs(core_.getChannelGroup()).toArray();
	}

	@Override
	public String getChannelGroup() {
		return core_.getChannelGroup();
	}

	@Override
	public boolean setChannelGroup(String group) {
		String curGroup = core_.getChannelGroup();
		if (!(group != null &&
				(curGroup == null || !curGroup.contentEquals(group)))) {
			// Don't make redundant changes.
			return false;
		}
		if (groupIsEligibleChannel(group)) {
			try {
				core_.setChannelGroup(group);
			} catch (Exception e) {
				try {
					core_.setChannelGroup("");
				} catch (Exception ex) {
					ReportingUtils.showError(ex);
				}
				return false;
			}
			studio_.events().post(new DefaultChannelGroupChangedEvent(group));
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Resets the engine.
	 */
	@Override
	public void clear() {
		if (channels_ != null) {
			channels_.clear();
		}
		numFrames_ = 0;
	}

	@Override
	public void setChannels(ArrayList<ChannelSpec> channels) {
		channels_ = channels;
	}

	@Override
	public void setShouldDisplayImages(boolean shouldDisplay) {
		shouldDisplayImages_ = shouldDisplay;
	}

	protected boolean enoughDiskSpace() {
		File root = new File(rootName_);
		//Need to find a file that exists to check space
		while (!root.exists()) {
			root = root.getParentFile();
			if (root == null) {
				return false;
			}
		}
		long usableMB = root.getUsableSpace() / (1024 * 1024);
		return (1.25 * getTotalMB()) < usableMB;
	}

	@Override
	public String getVerboseSummary() {
		int numFrames = getNumFrames();
		int numSlices = getNumSlices();
		int numPositions = getNumPositions();
		int numChannels = getNumChannels();

		int totalImages = getTotalImages();
		long totalMB = getTotalMB();

		double totalDurationSec = 0;
		if (!useCustomIntervals_) {
			totalDurationSec = interval_ * numFrames / 1000.0;
		} else {
			for (Double d : customTimeIntervalsMs_) {
				totalDurationSec += d / 1000.0;
			}
		}
		int hrs = (int) (totalDurationSec / 3600);
		double remainSec = totalDurationSec - hrs * 3600;
		int mins = (int) (remainSec / 60);
		remainSec = remainSec - mins * 60;

		String txt;
		txt =
				"Number of time points: " + (!useCustomIntervals_
						? numFrames : customTimeIntervalsMs_.size())
						+ "\nNumber of positions: " + numPositions
						+ "\nNumber of slices: " + numSlices
						+ "\nNumber of channels: " + numChannels
						+ "\nTotal images: " + totalImages
						+ "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" : NumberUtils.doubleToDisplayString(totalMB/1024.0) + " GB")
						+ "\nDuration: " + hrs + "h " + mins + "m " + NumberUtils.doubleToDisplayString(remainSec) + "s";

		if (useFrames_ || useMultiPosition_ || useChannels_ || useSlices_) {
			StringBuffer order = new StringBuffer("\nOrder: ");
			if (useFrames_ && useMultiPosition_) {
				if (acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE
						|| acqOrderMode_ == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
					order.append("Time, Position");
				} else {
					order.append("Position, Time");
				}
			} else if (useFrames_) {
				order.append("Time");
			} else if (useMultiPosition_) {
				order.append("Position");
			}

			if ((useFrames_ || useMultiPosition_) && (useChannels_ || useSlices_)) {
				order.append(", ");
			}

			if (useChannels_ && useSlices_) {
				if (acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE
						|| acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
					order.append("Channel, Slice");
				} else {
					order.append("Slice, Channel");
				}
			} else if (useChannels_) {
				order.append("Channel");
			} else if (useSlices_) {
				order.append("Slice");
			}

			return txt + order.toString();
		} else {
			return txt;
		}
	}

	/**
	 * Find out if the configuration is compatible with the current group.
	 * This method should be used to verify if the acquisition protocol is consistent
	 * with the current settings.
	 * @param config Configuration to be tested
	 * @return True if the parameter is in the current group
	 */
	@Override
	public boolean isConfigAvailable(String config) {
		StrVector vcfgs = core_.getAvailableConfigs(core_.getChannelGroup());
		for (int i = 0; i < vcfgs.size(); i++) {
			if (config.compareTo(vcfgs.get(i)) == 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String[] getAvailableGroups() {
		StrVector groups;
		try {
			groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
		} catch (Exception ex) {
			ReportingUtils.logError(ex);
			return new String[0];
		}
      	ArrayList<String> strGroups = new ArrayList<>();
		for (String group : groups) {
			if (groupIsEligibleChannel(group)) {
				strGroups.add(group);
			}
		}

		return strGroups.toArray(new String[0]);
	}

	@Override
	public double getCurrentZPos() {
		if (isFocusStageAvailable()) {
			double z = 0.0;
			try {
				//core_.waitForDevice(zstage_);
				// NS: make sure we work with the current Focus device
				z = core_.getPosition(core_.getFocusDevice());
			} catch (Exception e) {
				ReportingUtils.showError(e);
			}
			return z;
		}
		return 0;
	}

	@Override
	public boolean isPaused() {
		return acquisitionEngine2010_.isPaused();
	}

	protected boolean isFocusStageAvailable() {
		if (zstage_ != null && zstage_.length() > 0) {
			return true;
		} else {
			return false;
		}
	}

	private boolean groupIsEligibleChannel(String group) {
		StrVector cfgs = core_.getAvailableConfigs(group);
		if (cfgs.size() == 1) {
			Configuration presetData;
			try {
				presetData = core_.getConfigData(group, cfgs.get(0));
				if (presetData.size() == 1) {
					PropertySetting setting = presetData.getSetting(0);
					String devLabel = setting.getDeviceLabel();
					String propName = setting.getPropertyName();
					if (core_.hasPropertyLimits(devLabel, propName)) {
						return false;
					}
				}
			} catch (Exception ex) {
				ReportingUtils.logError(ex);
				return false;
			}

		}
		return true;
	}

	/*
	 * Returns the summary metadata associated with the most recent acquisition.
	 */
	@Override
	public JSONObject getSummaryMetadata() {
		return summaryMetadata_;
	}

	@Override
	public String getComment() {
		return this.comment_;
	}

	@Subscribe
	public void onAcquisitionEnded(AcquisitionEndedEvent event) {
//		curStore_ = null;
//		curPipeline_ = null;
//		System.out.println("Acq Ended: " + event.getSource());
		studio_.events().unregisterForEvents(this);
	}

	@Subscribe
	public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
		if (!event.isCanceled() && isAcquisitionRunning()) {
			int result = JOptionPane.showConfirmDialog(null,
					"Acquisition in progress. Are you sure you want to exit and discard all data?",
					"Micro-Manager", JOptionPane.YES_NO_OPTION,
					JOptionPane.INFORMATION_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				getAcquisitionEngine2010().stop();
			}
			else {
				event.cancelShutdown();
			}
		}
	}

	void onImageReceived( String outputFolder, Coords coord, TaggedImage image ) {
		Event.fireEvent( spimSetup_, new ControlEvent( ControlEvent.MM_IMAGE_CAPTURED, outputFolder, coord, image ));
	}
}
