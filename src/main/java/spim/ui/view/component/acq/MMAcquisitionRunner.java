package spim.ui.view.component.acq;

import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.data.Datastore;
import org.micromanager.events.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;
import spim.ui.view.component.acq.AcqWrapperEngine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2019
 */
public class MMAcquisitionRunner
{
	private IAcquisitionEngine2010 acquisitionEngine2010_;
	Datastore curStore_;

	// SPIMSetup setup,
	// StagePanel stagePanel,
	// Rectangle roiRectangle,
	// int timeSeqs,
	// double timeStep,
	// boolean arduinoSelected,
	// File output,
	// String acqFilenamePrefix,
	// ObservableList< PositionItem > positionItems,
	// List< ChannelItem > channelItems,
	// LongProperty processedImages,
	// boolean bSave,
	// boolean continuous
	public static void startAcquisition() throws Exception
	{
//		final MMStudio frame = MMStudio.getInstance();
//		CMMCore mmc = frame.getCMMCore();
//
//		mmc.setCameraDevice("Multi Camera");
//		mmc.setFocusDevice("ZStage");
//
//		String channelGroup = "Channel";
//		String ch0Preset = "Ch0", ch1Preset = "Ch1";
//		String wheel = "TSwitcher";
//
//		mmc.defineConfig(channelGroup, ch0Preset, wheel, "State", "1");
//		mmc.defineConfig(channelGroup, ch1Preset, wheel, "State", "2");
//
//		IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);
//
//		double exposure = 1.0;
//
//		SequenceSettings mdaSeq = new SequenceSettings();
//		mdaSeq.numFrames = 2;
//		mdaSeq.intervalMs = exposure + 1.0;
//		mdaSeq.channelGroup = channelGroup;
//		mdaSeq.channels = new ArrayList< ChannelSpec >( Arrays.asList(
//				new ChannelSpec[] {
//						new ChannelSpec(),
//						new ChannelSpec()
//				}));
//		mdaSeq.channels.get(0).config = ch0Preset;
//		mdaSeq.channels.get(0).exposure = exposure;
//		mdaSeq.channels.get(1).config = ch1Preset;
//		mdaSeq.channels.get(1).exposure = exposure;
//
//		List< TestImageDecoder.InfoPacket > packets = AE2010ImageDecoder.collectImages(
//				ae2010.run(mdaSeq, true, null, null));

	}

	public Datastore runAcquisition() throws Exception {
		final MMStudio frame = MMStudio.getInstance();
		CMMCore mmc = frame.getCMMCore();

		mmc.setCameraDevice("Multi Camera");
//		mmc.setFocusDevice("ZStage");

//		String channelGroup = "Cam1";
		String ch0Preset = "Multi Camera", ch1Preset = "DCam";

//		String wheel = "Arduino";
//
//		mmc.defineConfig(channelGroup, ch0Preset, wheel, "State", "1");
//		mmc.defineConfig(channelGroup, ch1Preset, wheel, "State", "2");

//		IAcquisitionEngine2010 ae2010 = getAcquisitionEngine2010();

		SequenceSettings mdaSeq = new SequenceSettings();
		mdaSeq.numFrames = 2;
		mdaSeq.intervalMs = 0.0;
		mdaSeq.channelGroup = "Cam1";
		mdaSeq.channels = new ArrayList<>( Arrays.asList(
				new ChannelSpec() ) );
		mdaSeq.channels.get(0).config = ch0Preset;
		mdaSeq.channels.get(0).exposure = 100;
		mdaSeq.channels.get(0).zOffset = 0.0;
		mdaSeq.channels.get(0).doZStack = true;
		mdaSeq.channels.get(0).config = "Multi Camera";
		mdaSeq.channels.get(0).camera = "Multi Camera";
		mdaSeq.channels.get(0).useChannel = true;
		mdaSeq.channels.get(0).color = new Color( 353535 );

		Collection<Double> number
				= IntStream.range(0, 24).asDoubleStream().boxed().collect(toSet());

		mdaSeq.slices = new ArrayList<>( number );
		mdaSeq.relativeZSlice = false;
		mdaSeq.slicesFirst = false;
		mdaSeq.timeFirst = false;
		mdaSeq.keepShutterOpenSlices = false;
		mdaSeq.keepShutterOpenChannels = false;
		mdaSeq.useAutofocus = false;
		mdaSeq.skipAutofocusCount = 0;
		mdaSeq.save = false;
		mdaSeq.zReference = 1.2;
		mdaSeq.comment = "";
		mdaSeq.usePositionList = true;
		mdaSeq.cameraTimeout = 20000;
		mdaSeq.shouldDisplayImages = true;
//		mdaSeq.channels.get(1).config = ch1Preset;
//		mdaSeq.channels.get(1).exposure = 20;

//		List< TestImageDecoder.InfoPacket > packets = AE2010ImageDecoder.collectImages(
//				ae2010.run(mdaSeq, true, null, null));

		PositionList posList = new PositionList();

		try {
			// Start up the acquisition engine
//			BlockingQueue< TaggedImage > engineOutputQueue = getAcquisitionEngine2010().run(
//					mdaSeq, true, posList,
//					frame.getAutofocusManager().getAutofocusMethod());
//			JSONObject summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();

//			boolean shouldShow = mdaSeq.shouldDisplayImages;
//			boolean shouldShow = true;
//
//			Datastore store_ = new DefaultDatastore(frame);
//			Pipeline pipeline_ = frame.data().copyApplicationPipeline(store_, false);

//			AcqWrapperEngine e = new AcqWrapperEngine();
//			e.setParentGUI( frame );
//			e.setSequenceSettings( mdaSeq );
//			e.setPositionList( posList );
//			curStore_ = e.acquire();

//			MMAcquisition acq = new MMAcquisition(frame,
//					summaryMetadata_, e, shouldShow);
//			curStore_ = acq.getDatastore();
//			Pipeline curPipeline_ = acq.getPipeline();
//
//
//			frame.events().post(new DefaultAcquisitionStartedEvent(curStore_,
//					e, mdaSeq));
//
//			// Start pumping images through the pipeline and into the datastore.
//			DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
//					engineOutputQueue, curPipeline_, curStore_, e, frame.events());
//
//			sink.start(new Runnable() {
//				@Override
//				public void run() {
//					getAcquisitionEngine2010().stop();
//				}
//			});

			return curStore_;

		} catch (Throwable ex) {
			ReportingUtils.showError(ex);
			frame.events().post(new DefaultAcquisitionEndedEvent(
					curStore_, this));
			return null;
		}
	}


	protected IAcquisitionEngine2010 getAcquisitionEngine2010() {
		final MMStudio frame = MMStudio.getInstance();
		if (acquisitionEngine2010_ == null) {
			acquisitionEngine2010_ = frame.getAcquisitionEngine2010();
		}
		return acquisitionEngine2010_;
	}

//	protected Datastore runAcquisition(SequenceSettings acquisitionSettings) {
		//Make sure computer can write to selected location and that there is enough space to do so
//		if (saveFiles_) {
//			File root = new File(rootName_);
//			if (!root.canWrite()) {
//				int result = JOptionPane.showConfirmDialog(null,
//						"The specified root directory\n" + root.getAbsolutePath() +
//								"\ndoes not exist. Create it?", "Directory not found.",
//						JOptionPane.YES_NO_OPTION);
//				if (result == JOptionPane.YES_OPTION) {
//					root.mkdirs();
//					if (!root.canWrite()) {
//						ReportingUtils.showError(
//								"Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
//						return null;
//					}
//				} else {
//					ReportingUtils.showMessage("Acquisition canceled.");
//					return null;
//				}
//			} else if (!this.enoughDiskSpace()) {
//				ReportingUtils.showError(
//						"Not enough space on disk to save the requested image set; acquisition canceled.");
//				return null;
//			}
//		}
//		try {
//			PositionList posListToUse = posList_;
//			if (posList_ == null && useMultiPosition_) {
//				posListToUse = studio_.positions().getPositionList();
//			}
//			// Start up the acquisition engine
//			BlockingQueue< TaggedImage > engineOutputQueue = getAcquisitionEngine2010().run(
//					acquisitionSettings, true, posListToUse,
//					studio_.getAutofocusManager().getAutofocusMethod());
//			summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();
//
//			boolean shouldShow = acquisitionSettings.shouldDisplayImages;
//			MMAcquisition acq = new MMAcquisition(studio_, "Acq",
//					summaryMetadata_, acquisitionSettings.save, this,
//					shouldShow);
//			curStore_ = acq.getDatastore();
//			curPipeline_ = acq.getPipeline();
//			if (shouldShow) {
//				new StatusDisplay(studio_, curStore_);
//			}
//
//			studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_,
//					this, acquisitionSettings));
//
//			// Start pumping images through the pipeline and into the datastore.
//			DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
//					engineOutputQueue, curPipeline_, curStore_, this);
//			sink.start(new Runnable() {
//				@Override
//				public void run() {
//					getAcquisitionEngine2010().stop();
//				}
//			});
//
//			return curStore_;
//
//		} catch (Throwable ex) {
//			ReportingUtils.showError(ex);
//			studio_.events().post(new DefaultAcquisitionEndedEvent(
//					curStore_, this));
//			return null;
//		}
//	}
}
