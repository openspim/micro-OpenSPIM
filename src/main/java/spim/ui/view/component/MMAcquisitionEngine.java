package spim.ui.view.component;

import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import loci.common.DebugTools;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import mmcorej.org.json.JSONException;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.*;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;

import org.micromanager.internal.MMStudio;

import spim.acquisition.Row;
import spim.algorithm.AntiDrift;
import spim.algorithm.DefaultAntiDrift;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.*;

import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.data.TimePointItem;
import spim.ui.view.component.acq.AcqWrapperEngine;
import spim.util.SystemInfo;

import java.awt.*;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2019
 */
public class MMAcquisitionEngine
{
	private static final Color[] DEFAULT_COLORS = { Color.red, Color.green, Color.blue, Color.yellow, Color.pink, new Color(160, 32, 240) };

	volatile boolean done;
	Thread captureThread;
	volatile boolean stopRequest = false;
	DisplayWindow display_ = null;

	static {
		DebugTools.enableLogging( "OFF" );
	}

	public void init() {
		// Setting parameters
		// AcquisitionEngine2010
		stopRequest = false;
	}

	public void stop() {
		stopRequest = true;
	}

	public void scheduledAcquisition() {
		// setup OEMTIFFHandler or HDF5OutputHandler
	}

	/**
	 * Perform acquisition image plus.
	 * @param studio the studio
	 * @param setup the setup
	 * @param stagePanel the stage panel
	 * @param roiRectangle the roi rectangle
	 * @param timeSeqs the time seqs
	 * @param timePointItems the time point items
	 * @param currentTP the current tp
	 * @param waitSeconds waitSeconds if it's waiting
	 * @param arduinoSelected the arduino selected
	 * @param output the output
	 * @param acqFilenamePrefix the acq filename prefix
	 * @param positionItems the position items
	 * @param channelItems the channel items
	 * @param processedImages the processed images
	 * @param bSave the b save
	 * @param savingFormatValue the value set from { "Single Plane TIFF", "OMETIFF Image stack", "N5 format" }
	 * @param saveMIP Save Maximum Intensity Projection or not
	 * @param antiDrift Anti-Drift function used or not
	 * @param experimentNote Experiment Note saved in the metadata
	 * @param antiDriftLog holds anti drift log during acquisition
	 * @param antiDriftReferenceChannel the reference channel for anti-drift
	 * @param antiDriftTypeToggle the type of anti-drift, CentreOfMass or PhaseCorrelation
	 * @throws Exception the exception
	 */
	@SuppressWarnings("Duplicates")
	public void performAcquisition(Studio studio, SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, ObservableList< TimePointItem > timePointItems, DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave, Object savingFormatValue, boolean saveMIP, boolean antiDrift, String experimentNote, StringProperty antiDriftLog, Integer antiDriftReferenceChannel, ReadOnlyObjectProperty<Toggle> antiDriftTypeToggle) throws Exception
	{
		final Studio frame = studio;

		if(frame == null) return;

		boolean liveOn = false;
//		RewritableDatastore store = null;
		Datastore store = null;

		if(frame != null) {
			liveOn = frame.live().getIsLiveModeOn();
			if(liveOn)
				frame.live().setLiveMode(false);

			if(bSave) {
				DefaultDatastore result = new DefaultDatastore(frame);

				if(savingFormatValue.equals( "Single Plane TIFF" ))
					result.setStorage(new OpenSPIMSinglePlaneTiffSeries(result, output.getAbsolutePath(), acqFilenamePrefix, true));
				else if(savingFormatValue.equals( "OMETIFF Image stack" ))
					result.setStorage(new OMETIFFStorage(result, output.getAbsolutePath(), acqFilenamePrefix, true));
				else if(savingFormatValue.equals( "N5 format" ))
					result.setStorage(new N5MicroManagerStorage(result, output.getAbsolutePath(), acqFilenamePrefix, timeSeqs, true));

				store = result;
			} else {
				store = frame.data().createRewritableRAMDatastore();
			}
			display_ = frame.displays().createDisplay(store);
			display_.setCustomTitle( acqFilenamePrefix );
			frame.displays().manage(store);
		}

		final CMMCore core = frame.core();

//		final AcquisitionSettings acqSettings = acqSettingsOrig;
//
//		// generate string for log file
//		Gson gson = new GsonBuilder().setPrettyPrinting().create();
//		final String acqSettingsJSON = gson.toJson(acqSettings);

		final double acqBegan = System.nanoTime() / 1e9;

//		boolean autoShutter = core.getAutoShutter();
//		core.setAutoShutter(false);

		Row[] acqRows = generateRows( positionItems );

		String currentCamera = core.getCameraDevice();

		List<String> cameras = new ArrayList<>();

		// To support multi camera
		List<String> multis = getMultiCams(core);

		int ch = 0;

		if(arduinoSelected) {
			cameras.add(currentCamera);
			for(ChannelItem chItem: channelItems) {
				if(currentCamera.startsWith( "Multi" )) ch += multis.size();
				else ch += 1;
			}
		} else {
			for(ChannelItem chItem: channelItems) {
				if(chItem.getName().startsWith( "Multi" )) ch += multis.size();
				else ch += 1;

				if(!cameras.contains( chItem.getName() )) {
					cameras.add( chItem.getName() );
				}
			}
		}

		String[] channelNames = new String[ch];

		ch = 0;


		if(arduinoSelected) {
			for(ChannelItem chItem: channelItems) {
				if(currentCamera.startsWith( "Multi" )) {
					for(String name: multis) {
						channelNames[ ch++ ] = name + "-" + chItem.getName();
					}
				} else {
					channelNames[ ch++ ] = chItem.getName();
				}
			}
		} else {
			for(ChannelItem chItem : channelItems)
			{
				String cam = chItem.getName();
				if(!cam.startsWith( "Multi" ))
					channelNames[ ch++ ] = cam;
				else {
					for(String c : multis)
						channelNames[ ch++ ] = "Multi-" + c + "-" + chItem.getName();
				}
			}
		}


		MultiStagePosition[] multiStagePositions = positionItems.stream().map( c -> new MultiStagePosition( c.toString(), c.getX(), c.getY(), c.getZString(), c.getZStart() )).toArray(MultiStagePosition[]::new);

		PropertyMap.Builder pm = PropertyMaps.builder();

		DefaultSummaryMetadata.Builder smb = new DefaultSummaryMetadata.Builder();

		UserProfile profile = frame.profile();
		smb = smb.userName( System.getProperty("user.name") )
				.profileName( profile.getProfileName() );

		try {
			smb = smb.computerName( InetAddress.getLocalHost().getHostName());
		}
		catch ( Exception ignored ) {
		}

		smb = smb.channelNames(channelNames).
				zStepUm( core.getPixelSizeUm() ).
				prefix(acqFilenamePrefix).
				stagePositions(multiStagePositions).
				startDate((new Date()).toString());

		smb = smb.intendedDimensions( Coordinates.builder().
				channel(channelItems.size()).
				z(timeSeqs).
				t(timeSeqs).
				stagePosition(acqRows.length).
				build());

		if(setup.getCamera1() != null)
		{
			pm.putString("Camera-1", setup.getCamera1().getLabel() );
		}

		if(setup.getCamera2() != null)
		{
			pm.putString("Camera-2", setup.getCamera2().getLabel() );
		}

		pm.putString("ExperimentNote", experimentNote);

		if(null != roiRectangle)
		{
			for ( String camera : cameras )
			{
				frame.core().setROI( camera, roiRectangle.x, roiRectangle.y, roiRectangle.width, roiRectangle.height );
			}

			pm.putIntegerList("ROI", roiRectangle.x, roiRectangle.y, roiRectangle.width, roiRectangle.height );
		}

		Datastore finalStore1 = store;
		frame.events().post( new AcquisitionStartedEvent()
		{
			@Override public Datastore getDatastore()
			{
				return finalStore1;
			}

			@Override public Object getSource()
			{
				return this;
			}
		} );

		// Setting up the channel colors
		int[] chColors = new int[channelNames.length];

		for(int i = 0; i < chColors.length; i++) {
			chColors[i] = DEFAULT_COLORS[i % 6].getRGB();
		}

		pm.putIntegerList("ChColors", chColors);

		store.setSummaryMetadata(smb.userData(pm.build()).build());

		DisplaySettings dsTmp = DefaultDisplaySettings.getStandardSettings(
				PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());

		DisplaySettings.Builder displaySettingsBuilder
				= dsTmp.copyBuilder();

		final int nrChannels = chColors.length;


		if (nrChannels == 1) {
			displaySettingsBuilder.colorModeGrayscale();
		} else {
			displaySettingsBuilder.colorModeComposite();
		}
		for (int channelIndex = 0; channelIndex < nrChannels; channelIndex++) {
			ChannelDisplaySettings channelSettings
					= displaySettingsBuilder.getChannelSettings(channelIndex);
			Color chColor = new Color(chColors[channelIndex]);
			ChannelDisplaySettings.Builder csb =
					channelSettings.copyBuilder().color(chColor);

			csb.name(channelNames[channelIndex]);

			displaySettingsBuilder.channel(channelIndex, csb.build());
		}

		display_.compareAndSetDisplaySettings(display_.getDisplaySettings(), displaySettingsBuilder.build());

		// For checking Max intensity projection is needed or not
		if(!bSave) {
			output = null;
			saveMIP = false;
		}

		if(!saveMIP) acqFilenamePrefix = null;

		executeNormalAcquisition(setup, frame, store, stagePanel, currentCamera, cameras, output, acqFilenamePrefix, timePointItems, positionItems, channelItems, currentTP, waitSeconds, arduinoSelected, processedImages, acqBegan, antiDrift, antiDriftLog, antiDriftReferenceChannel, antiDriftTypeToggle);
	}

	private void executeNormalAcquisition(SPIMSetup setup, final Studio frame, Datastore store,
										  StagePanel stagePanel, String currentCamera, List<String> cameras, File outFolder, String acqFilenamePrefix,
										  ObservableList<TimePointItem> timePointItems, ObservableList<PositionItem> positionItems, List<ChannelItem> channelItems,
										  DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected,
										  LongProperty processedImages, final double acqBegan, final boolean antiDrift, StringProperty antiDriftLog, Integer adReferenceChannel, ReadOnlyObjectProperty<Toggle> antiDriftTypeToggle) throws Exception
	{

		// Dynamic timeline
		runNormalSmartImagingMMAcq(setup, frame, store, stagePanel, currentCamera, cameras,
				outFolder, acqFilenamePrefix,
				timePointItems, positionItems, channelItems, currentTP, waitSeconds, arduinoSelected, processedImages, antiDrift, antiDriftLog, adReferenceChannel, antiDriftTypeToggle);

		finalize(true, setup, currentCamera, cameras, frame, 0, 0, store);
	}

	private void runNormalSmartImagingMMAcq(SPIMSetup setup, final Studio frame, Datastore store,
											StagePanel stagePanel, String currentCamera, List<String> cameras, File outFolder, String acqFilenamePrefix,
											ObservableList<TimePointItem> timePointItems, ObservableList<PositionItem> positionItems, List<ChannelItem> channelItems,
											DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected,
											LongProperty processedImages, final boolean antiDrift, StringProperty antiDriftLog, Integer adReferenceChannel, ReadOnlyObjectProperty<Toggle> antiDriftTypeToggle) throws Exception
	{

		final CMMCore core = frame.core();

		int timePoints = 0;
		int  passedTimePoints = 0;
		double totalTimePoints = timePointItems.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

		// Anti-Drift setup
		HashMap< PositionItem, DefaultAntiDrift > driftCompMap = null;
		if(antiDrift) {
			driftCompMap = new HashMap<>(positionItems.size());
			for( PositionItem positionItem : positionItems )
			{
				RadioButton selectedRadioButton = (RadioButton) antiDriftTypeToggle.getValue();
				String toogleGroupValue = selectedRadioButton.getText();

				if(toogleGroupValue.equals("Centre of mass")) {
					driftCompMap.put(positionItem, new DefaultAntiDrift());
				} else if(toogleGroupValue.equals("Phase correlation")) {
					driftCompMap.put(positionItem, new DefaultAntiDrift(10));
				}
			}
		}

		AcqWrapperEngine engine = new AcqWrapperEngine( setup, frame, store, currentCamera, cameras, outFolder, acqFilenamePrefix, channelItems, arduinoSelected, processedImages, driftCompMap, adReferenceChannel);

		mainLoop:
		for(TimePointItem tpItem : timePointItems ) {
			if(tpItem.getType().equals( TimePointItem.Type.Acq ))
			{
				int timeSeqs = tpItem.getNoTimePoints();
				for ( int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq )
				{
					final double acqBegan = System.nanoTime() / 1e9;

					int step = 0;

					SystemInfo.dumpMemoryStatusToLog(core);

					for ( PositionItem positionItem : positionItems )
					{
						final int tp = timePoints;
						final int rown = step;

//						display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timePoints, step ) );

						// Offset change log
						if(driftCompMap != null) {
							int binningFactor = setup.getCamera1().getBinning();
							Vector3D offset = driftCompMap.get(positionItem).getUpdatedOffset();
//							double xOffset = offset.getX() < 50 ? offset.getX() * binningFactor * core.getPixelSizeUm() * -1 : 0;
//							double yOffset = offset.getY() < 50 ? offset.getY() * binningFactor * core.getPixelSizeUm() : 0;
//							double zOffset = offset.getZ() < 20 ? offset.getZ() * positionItem.getZStep() * -1 : 0;

							double xOffset = offset.getX() * binningFactor * core.getPixelSizeUm() * -1;
							double yOffset = offset.getY() * binningFactor * core.getPixelSizeUm() * -1;
							double zOffset = offset.getZ() * positionItem.getZStep() * -1;

							// Applying inversion status of X and Z
							if(setup.getXStage().inversedProperty().get()) {
								xOffset *= -1;
							}
							if(setup.getZStage().inversedProperty().get()) {
								zOffset *= -1;
							}

							// Remove double applying calibration value
							if(driftCompMap.get(positionItem).getType().equals(AntiDrift.Type.CenterOfMass)) {
								xOffset = xOffset / core.getPixelSizeUm();
								yOffset = yOffset / core.getPixelSizeUm();
							}

							// Only use integer values
							if(Math.abs(xOffset) > 1) xOffset = (int) xOffset;
							else xOffset = 0;

							if(Math.abs(yOffset) > 1) yOffset = (int) yOffset;
							else yOffset = 0;

							if(Math.abs(zOffset) > 1) zOffset = (int) zOffset;
							else zOffset = 0;

							StringBuffer sb =  new StringBuffer();
							sb.append("PixelSizeUm = " + core.getPixelSizeUm() + "\n");
							sb.append(driftCompMap.get(positionItem).getType() + " Anti-Drift used X:" + xOffset + " Y:" + yOffset + " Z:" + zOffset + "\n");
							System.out.println(sb.toString());

							// Logging the anti-drift values
							sb.append(driftCompMap.get(positionItem).getType()).append("\n");
							sb.append("Position: #").append(step).append("\n");
							sb.append(new Date()).append("\n");
							sb.append("X:").append(positionItem.getX()).append(" Y:").append(positionItem.getY()).append(" Z:").append(positionItem.getZString()).append("\n");
							positionItem.setX(positionItem.getX() + xOffset);
							positionItem.setY(positionItem.getY() + yOffset);
							positionItem.setZStart(positionItem.getZStart() + zOffset);
							positionItem.setZEnd(positionItem.getZEnd() + zOffset);
							sb.append("->\nX:").append(positionItem.getX()).append(" Y:").append(positionItem.getY()).append(" Z:").append(positionItem.getZString()).append("\n");

							core.logMessage(sb.toString());
						}

						// Move the stage
						if(stagePanel != null)
							stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
						try
						{
							// wait until the all devices in the system stop moving
							core.waitForSystem();
						} catch ( Exception e ) {
							core.logMessage(e.toString());
							System.out.println(e.toString());
						}

						core.clearCircularBuffer();

						while( core.systemBusy() ) {
							core.logMessage("System is busy. Wait for 100ms..");
							Thread.sleep( 100 );
						}

						core.logMessage("MMAcquisition started");
						System.out.println("MMAcquisition started");
						engine.startAcquire( timePoints, step, positionItem );

						while(engine.isAcquisitionRunning()) {
							try
							{
								Thread.sleep( 10 );
							} catch ( InterruptedException ie )
							{
								core.logMessage(ie.toString());
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, store );
								break mainLoop;
							}
						}

						core.logMessage("MMAcquisition finished");
						System.out.println("MMAcquisition finished");

						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

						if(stopRequest) {
							engine.stop(true);
							break mainLoop;
						}

						++step;
					}

					double elapsed = (System.nanoTime() / 1e9 - acqBegan);
					double wait = tpItem.getIntervalSeconds() - elapsed;

					if(timeSeq < (timeSeqs - 1) && wait > 0D) {
						System.err.println("Interval delay. (next seq in " + wait + "s)");
						core.logMessage("Interval delay. (next seq in " + wait + "s)");

						wait = tpItem.getIntervalSeconds();
						passedTimePoints += (int) elapsed;

						for(int i = (int) elapsed; i < (int) wait; i++)
						{
							++passedTimePoints;
							updateTimeProperties( waitSeconds, wait - i, currentTP, 1 / totalTimePoints * passedTimePoints );
							try
							{
								Thread.sleep( ( long ) ( 1e3 ) );
							}
							catch ( InterruptedException ie )
							{
								core.logMessage(ie.toString());
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, store );
								break mainLoop;
							}

							if(stopRequest) {
								System.err.println("Stop requested.");
								core.logMessage("Stop requested.");

								engine.stop(true);
								updateWaitTimeProperty( waitSeconds, -1 );
								break mainLoop;
							}
						}
						updateWaitTimeProperty( waitSeconds, -1 );
					} else {
						core.logMessage("Behind schedule! (next seq in " + wait + "s)");
						passedTimePoints += tpItem.getIntervalSeconds();
					}
					++timePoints;

					updateCurrentTPProperty( currentTP, 1 / totalTimePoints * passedTimePoints );
				}
			}
			else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
			{
				double wait = tpItem.getIntervalSeconds();

				if(wait > 0D) {
					System.err.println("Wait delay. (next seq in " + wait + "s)");
					core.logMessage("Wait delay. (next seq in " + wait + "s)");

					for(int i = 0; i < (int) wait; i++)
					{
						++passedTimePoints;
						updateTimeProperties( waitSeconds, wait - i, currentTP, 1 / totalTimePoints * passedTimePoints );
						try
						{
							Thread.sleep( ( long ) ( 1e3 ) );
						}
						catch ( InterruptedException ie )
						{
							core.logMessage(ie.toString());
							finalize( false, setup, currentCamera, cameras, frame, 0, 0, store );
							break mainLoop;
						}

						if(stopRequest) {
							System.err.println("Stop requested.");
							core.logMessage("Stop requested.");
							engine.stop(true);
							updateWaitTimeProperty( waitSeconds, -1 );
							break mainLoop;
						}
					}
					updateWaitTimeProperty( waitSeconds, -1 );
				}
			}
		}

		engine.exit();
		store.freeze();
		System.err.println("AcquisitionEngine exited.");
		core.logMessage("AcquisitionEngine exited.");
	}

	private static void updateCurrentTPProperty(DoubleProperty currentTP, double updatedCurrentTP) {
		Platform.runLater(() -> {
			currentTP.set( updatedCurrentTP );
		});
	}

	private static void updateWaitTimeProperty(DoubleProperty waitSeconds, double updatedWaitSeconds) {
		Platform.runLater(() -> {
			waitSeconds.set( updatedWaitSeconds );
		});
	}

	private static void updateTimeProperties(DoubleProperty waitSeconds, double updatedWaitSeconds, DoubleProperty currentTP, double updatedCurrentTP) {
		Platform.runLater(() -> {
			waitSeconds.set( updatedWaitSeconds );
			currentTP.set( updatedCurrentTP );
		});
	}

	/**
	 * Gets multi cameras.
	 * @param core the core
	 * @return the multi cams
	 */
	public static List<String> getMultiCams( CMMCore core )
	{
		ArrayList<String> list = new ArrayList<>();

		try
		{
			core.getDeviceName( "Multi Camera" );
		}
		catch ( Exception e )
		{
			System.out.println("There is no Multi Camera in the system");
			return list;
		}

		try
		{
			if(core.hasProperty( "Multi Camera", "Name" )) {
				for(int i = 1; i < 5; i++) {
					String c = core.getProperty( "Multi Camera", "Physical Camera " + i );
					if(!c.equals( "Undefined" )) list.add( c );
				}
			}
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return list;
	}

	protected static IAcquisitionEngine2010 getAcquisitionEngine2010() {
		final MMStudio frame = MMStudio.getInstance();

		return frame.getAcquisitionEngine2010();
	}

	private void finalize(boolean finalizeStack, SPIMSetup setup, final String currentCamera, List<String> cameras, final Studio frame, final int tp, final int rown, Datastore store) throws Exception
	{
		final CMMCore core = frame.core();

//		if(setup.getArduino1() != null)
//			setup.getArduino1().setSwitchState( "0" );

//		if(autoShutter)
//			core.setAutoShutter(true);

		done = true;

		if(captureThread != null)
		{
			captureThread.join();
		}

		captureThread = null;

		for(String camera : cameras)
		{
			if(core.isSequenceRunning(camera))
				core.stopSequenceAcquisition( camera );
		}

		core.setCameraDevice( currentCamera );

//		core.waitForDevice( currentCamera );
		try
		{
			core.waitForSystem();
		} catch ( Exception e ) {
			System.out.println(e.toString());
		}

		if (store != null) {
			store.freeze();
		}

		Datastore finalStore = store;
		frame.events().post( new AcquisitionEndedEvent()
		{
			@Override public Datastore getStore()
			{
				return finalStore;
			}

			@Override public Object getSource()
			{
				return this;
			}
		});

//		ij.IJ.log("finalize");
	}

	private static void addImageToAcquisition( Studio studio, Datastore store, int channel,
			int slice, double zPos, PositionItem position, long ms, TaggedImage taggedImg ) throws
			JSONException, DatastoreFrozenException,
			DatastoreRewriteException, Exception
	{
		if(store instanceof RewritableDatastore) {
			if (slice == 0) ((RewritableDatastore)store).deleteAllImages();
		}

		Coords.Builder cb = Coordinates.builder();

		Coords coord = cb.p(0).t(0).c(channel).z(slice).build();
		Image img = studio.data().convertTaggedImage(taggedImg);
		Metadata md = img.getMetadata();
		Metadata.Builder mdb = md.copyBuilderPreservingUUID();
		PropertyMap ud = md.getUserData();
		ud = ud.copyBuilder().putDouble("Z-Step-um", position.getZStep()).build();
		String posName = position.toString();
		mdb = mdb.xPositionUm(position.getX()).yPositionUm(position.getY()).zPositionUm( zPos ).elapsedTimeMs( (double) ms );

		md = mdb.positionName(posName).userData(ud).build();
		img = img.copyWith(coord, md);

		store.putImage(img);

      /*
      // create required coordinate tags
      try {
         MDUtils.setFrameIndex(tags, frame);
         tags.put(MMTags.Image.FRAME, frame);
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setChannelName(tags, channelNames_[channel]);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPositionIndex(tags, position);
         MDUtils.setElapsedTimeMs(tags, ms);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
         MDUtils.setZStepUm(tags, PanelUtils.getSpinnerFloatValue(stepSize_));

         if (!tags.has(MMTags.Summary.SLICES_FIRST) && !tags.has(MMTags.Summary.TIME_FIRST)) {
            // add default setting
            tags.put(MMTags.Summary.SLICES_FIRST, true);
            tags.put(MMTags.Summary.TIME_FIRST, false);
         }
         if (acq.getPositions() > 1) {
            // if no position name is defined we need to insert a default one
            if (tags.has(MMTags.Image.POS_NAME)) {
               tags.put(MMTags.Image.POS_NAME, "Pos" + position);
            }
         }
         // update frames if necessary
         if (acq.getFrames() <= frame) {
            acq.setProperty(MMTags.Summary.FRAMES, Integer.toString(frame + 1));
         }
      } catch (JSONException e) {
         throw new Exception(e);
      }
      bq.put(taggedImg);
              */
	}

	@SuppressWarnings("Duplicates")
	private static void addImageToDisplay( Studio studio, Datastore store, int pos, int tp, int channel,
			int slice, double zPos, PositionItem position, long ms, TaggedImage taggedImg ) throws
			JSONException, DatastoreFrozenException,
			DatastoreRewriteException, Exception
	{
		Coords.Builder cb = Coordinates.builder();

		Coords coord = cb.p(pos).t(tp).c(channel).z(slice).build();
		Image img = studio.data().convertTaggedImage(taggedImg);
		Metadata md = img.getMetadata();
		Metadata.Builder mdb = md.copyBuilderPreservingUUID();
		PropertyMap ud = md.getUserData();
		ud = ud.copyBuilder().putDouble("Z-Step-um", position.getZStep()).build();
		String posName = position.toString();
		mdb = mdb.xPositionUm(position.getX()).yPositionUm(position.getY()).zPositionUm( zPos ).elapsedTimeMs( (double) ms );

		md = mdb.positionName(posName).userData(ud).build();
		img = img.copyWith(coord, md);

		store.putImage(img);

      /*
      // create required coordinate tags
      try {
         MDUtils.setFrameIndex(tags, frame);
         tags.put(MMTags.Image.FRAME, frame);
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setChannelName(tags, channelNames_[channel]);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPositionIndex(tags, position);
         MDUtils.setElapsedTimeMs(tags, ms);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
         MDUtils.setZStepUm(tags, PanelUtils.getSpinnerFloatValue(stepSize_));

         if (!tags.has(MMTags.Summary.SLICES_FIRST) && !tags.has(MMTags.Summary.TIME_FIRST)) {
            // add default setting
            tags.put(MMTags.Summary.SLICES_FIRST, true);
            tags.put(MMTags.Summary.TIME_FIRST, false);
         }
         if (acq.getPositions() > 1) {
            // if no position name is defined we need to insert a default one
            if (tags.has(MMTags.Image.POS_NAME)) {
               tags.put(MMTags.Image.POS_NAME, "Pos" + position);
            }
         }
         // update frames if necessary
         if (acq.getFrames() <= frame) {
            acq.setProperty(MMTags.Summary.FRAMES, Integer.toString(frame + 1));
         }
      } catch (JSONException e) {
         throw new Exception(e);
      }
      bq.put(taggedImg);
              */
	}

	private static Row[] generateRows( ObservableList< PositionItem > positionItems )
	{
		ArrayList<Row> list = new ArrayList<>();

		SPIMSetup.SPIMDevice[] canonicalDevices = new SPIMSetup.SPIMDevice[] { SPIMSetup.SPIMDevice.STAGE_X, SPIMSetup.SPIMDevice.STAGE_Y, SPIMSetup.SPIMDevice.STAGE_THETA, SPIMSetup.SPIMDevice.STAGE_Z};

		for( PositionItem item : positionItems )
		{
			list.add( new Row( canonicalDevices,
					new String[] {"" + item.getX(), "" + item.getY(), "" + item.getR(), item.getPosZString()} ) );
		}

		return list.toArray( new Row[0] );
	}

	private static TaggedImage snapImageCam( SPIMSetup setup, boolean manualLaser) throws Exception {
		if(manualLaser && setup.getLaser() != null)
			setup.getLaser().setPoweredOn(true);

		TaggedImage ti = setup.getCamera1().snapImage();

		if(manualLaser && setup.getLaser() != null)
			setup.getLaser().setPoweredOn(false);

		return ti;
	}

	private static TaggedImage getTaggedImage( CMMCore core, int ch )
	{
		try
		{
			return core.getTaggedImage( ch );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		return null;
	}

	private static void runDevicesAtRow(CMMCore core, SPIMSetup setup, Row row) throws Exception {
		for ( SPIMSetup.SPIMDevice devType : row.getDevices() ) {
			Device dev = setup.getDevice(devType);
			Row.DeviceValueSet values = row.getValueSet(devType);

			if (dev instanceof Stage ) // TODO: should this be different?
			{
				( ( Stage ) dev ).setPosition( values.getStartPosition() );
			}
			else
				throw new Exception("Unknown device type for \"" + dev
						+ "\"");
		}
		core.waitForSystem();
	}
}
