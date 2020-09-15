package spim.ui.view.component;

import ij.process.ImageProcessor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.collections.ObservableList;
import loci.common.DebugTools;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import mmcorej.org.json.JSONException;
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
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.AcquisitionStartedEvent;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.UserProfileManager;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import spim.acquisition.Row;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.OMETIFFHandler;
import spim.io.OutputHandler;

import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.data.TimePointItem;
import spim.ui.view.component.acq.AcqWrapperEngine;

import java.awt.Rectangle;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.micromanager.acquisition.internal.TaggedImageQueue.POISON;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2019
 */
public class MMAcquisitionEngine
{
	static volatile boolean done;
	static Thread captureThread;

	static {
		DebugTools.enableLogging( "OFF" );
	}

	public void init() {
		// Setting parameters
		// AcquisitionEngine2010
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
	 * @param timeStep the time step
	 * @param timePointItems the time point items
	 * @param currentTP the current tp
	 * @param cylinderSize the cylinder size
	 * @param smartImagingSelected the smart imaging selected
	 * @param arduinoSelected the arduino selected
	 * @param output the output
	 * @param acqFilenamePrefix the acq filename prefix
	 * @param positionItems the position items
	 * @param channelItems the channel items
	 * @param processedImages the processed images
	 * @param bSave the b save
	 * @param continuous if the acquisition is continuous or snap-based
	 * @param savingFormatValue the value set from { "Separate the channel dimension", "Image stack (include multi-channel)" }
	 * @throws Exception the exception
	 */
	@SuppressWarnings("Duplicates")
	public static void performAcquisition( Studio studio, SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, double timeStep, ObservableList< TimePointItem > timePointItems, DoubleProperty currentTP, double cylinderSize, boolean smartImagingSelected, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave, boolean continuous, Object savingFormatValue ) throws Exception
	{
		final Studio frame = studio;

		if(frame == null) return;

		boolean liveOn = false;
		RewritableDatastore store = null;
		DisplayWindow display = null;

		if(frame != null) {
			liveOn = frame.live().getIsLiveModeOn();
			if(liveOn)
				frame.live().setLiveMode(false);

//			store = frame.data().createMultipageTIFFDatastore(output.getAbsolutePath(), false, false);

			store = frame.data().createRewritableRAMDatastore();
			display = frame.displays().createDisplay(store);
			display.setCustomTitle( acqFilenamePrefix );
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
		if(continuous) {
			ch = multis.size();
		} else {
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
		}

		String[] channelNames = new String[ch];

		ch = 0;

		if(continuous) {
			for(String c : multis)
				channelNames[ ch++ ] = c;
		} else {
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
		}

		MultiStagePosition[] multiStagePositions = positionItems.stream().map( c -> new MultiStagePosition( c.toString(), c.getX(), c.getY(), c.getZString(), c.getZStart() )).toArray(MultiStagePosition[]::new);

		PropertyMap.Builder pm = PropertyMaps.builder();

		SummaryMetadata.Builder smb = new DefaultSummaryMetadata.Builder();

		UserProfile profile = new UserProfileManager().getProfile();
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

		if(null != roiRectangle)
		{
			for ( String camera : cameras )
			{
				frame.core().setROI( camera, roiRectangle.x, roiRectangle.y, roiRectangle.width, roiRectangle.height );
			}

			pm.putIntegerList("ROI", roiRectangle.x, roiRectangle.y, roiRectangle.width, roiRectangle.height );
		}

		store.setSummaryMetadata(smb.userData(pm.build()).build());

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

		HashMap<String, OutputHandler> handlers = new HashMap<>(  );

		if(bSave)
		{
			boolean separateChannel = false;
			if(savingFormatValue != null && savingFormatValue.equals( "Separate the channel dimension" ))
				separateChannel = true;

			if(continuous) {
				OutputHandler handler = new OMETIFFHandler(
						core, output, acqFilenamePrefix + "_" + currentCamera + "_",
						//what is the purpose of defining parameters and then passing null anyway?
						acqRows, channelItems.size(), timeSeqs, timeStep, 1, smb.userData( pm.build() ).build(), false, separateChannel );

				handlers.put( currentCamera, handler );
				cameras.clear();
				cameras.add( currentCamera );
			} else {
				for ( String camera : cameras )
				{
					if ( camera.startsWith( "Multi" ) )
					{
						int chSize = arduinoSelected ? channelItems.size() : (int) channelItems.stream().filter( c -> c.getName().equals( camera ) ).count();
						OutputHandler handler = new OMETIFFHandler(
								core, output, acqFilenamePrefix + "_" + camera + "_",
								//what is the purpose of defining parameters and then passing null anyway?
								acqRows, chSize * multis.size(), timeSeqs, timeStep, 1, smb.userData( pm.build() ).build(), false, separateChannel );

						handlers.put( camera, handler );
					}
					else
					{
						int chSize = arduinoSelected ? channelItems.size() : (int) channelItems.stream().filter( c -> c.getName().equals( camera ) ).count();
						OutputHandler handler = new OMETIFFHandler(
								core, output, acqFilenamePrefix + "_" + camera + "_",
								//what is the purpose of defining parameters and then passing null anyway?
								acqRows, chSize, timeSeqs, timeStep, 1, smb.userData( pm.build() ).build(), false, separateChannel );

						handlers.put( camera, handler );
					}
				}
			}
		} else {
			if(continuous) {
				cameras.clear();
				cameras.add( currentCamera );
			}
		}

		if(continuous) executeContinuousAcquisition(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
				timeSeqs, timeStep, timePointItems, positionItems, channelItems, currentTP, cylinderSize, smartImagingSelected, arduinoSelected, processedImages, acqBegan);
		else executeNormalAcquisition(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
				timeSeqs, timeStep, timePointItems, positionItems, channelItems, currentTP, cylinderSize, smartImagingSelected, arduinoSelected, processedImages, acqBegan);

	}

	@SuppressWarnings("Duplicates")
	private static void executeNormalAcquisition(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			int timeSeqs, double timeStep, ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, double cylinderSize, boolean smartImagingSelected, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan) throws Exception
	{

		long acqStart = System.currentTimeMillis();

		// Scheduled timeline
		// Dynamic timeline
		if(smartImagingSelected) {
			runNormalSmartImagingMMAcq(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
					timePointItems, positionItems, channelItems, currentTP, cylinderSize, arduinoSelected, processedImages, acqBegan, acqStart);
//			runNormalSmartImaging(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
//					timePointItems, positionItems, channelItems, currentTP, cylinderSize, arduinoSelected, processedImages, acqBegan, acqStart);
		} else {
			runNormalImagingMMAcq(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
					timeSeqs, timeStep, positionItems, channelItems, arduinoSelected, processedImages, acqBegan, acqStart);
//			runNormalImaging(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
//					timeSeqs, timeStep, positionItems, channelItems, arduinoSelected, processedImages, acqBegan, acqStart);
		}

		finalize(true, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
	}

	@SuppressWarnings("Duplicates")
	private static void runNormalSmartImaging(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, double cylinderSize, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart) throws Exception
	{

		final CMMCore core = frame.core();

		int timePoints = 0;
		int  passedTimePoints = 0;
		double totalTimePoints = timePointItems.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

		for(TimePointItem tpItem : timePointItems ) {
			if(tpItem.getType().equals( TimePointItem.Type.Acq ))
			{
				int timeSeqs = tpItem.getNoTimePoints();
				for ( int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq )
				{
					int step = 0;
					for ( PositionItem positionItem : positionItems )
					{
						final int tp = timePoints;
						final int rown = step;

						display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timePoints, step ) );

						// Move the stage
						if(stagePanel != null)
							stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
						core.waitForSystem();

						for( OutputHandler handler : handlers.values() )
							beginStack( tp, rown, handler );

						// Traverse Z stacks
						int noSlice = 0;
						for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep())
						{
							try {
								// Move Z stacks
								if(stagePanel != null)
									stagePanel.goToZ( zStart );
								core.waitForSystem();

								// Wait for image synchronization
								core.waitForImageSynchro();

								long now = System.currentTimeMillis();

								// Cameras
								if ( arduinoSelected )
								{
									// Channel iteration
									int c = 0;

									for ( String camera : cameras )
									{
										for ( ChannelItem channelItem : channelItems )
										{
											// Snap an image
											if ( setup.getArduino1() != null )
												setup.getArduino1().setSwitchState( channelItem.getLaser() );

											double exp = channelItem.getValue().doubleValue();
											core.setExposure( camera, exp );
											core.snapImage();
											core.waitForDevice( camera );

											for ( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh++ )
											{
												c = addSlice( getTaggedImage( core, currentCh ), setup, ( int ) exp, c, acqBegan, tp, step, handlers.get( camera ),
														frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages );
											}
										}
									}
								}
								else
								{
									// Channel iteration
									int c = 0;
									for ( ChannelItem channelItem : channelItems )
									{
										double exp = channelItem.getValue().doubleValue();
										core.setCameraDevice( channelItem.getName() );
										core.setShutterOpen( channelItem.getLaser(), true );
										core.setExposure( channelItem.getName(), exp );
										core.snapImage();
										core.waitForDevice( channelItem.getName() );

										for ( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh++ )
										{
											c = addSlice( getTaggedImage( core, currentCh ), setup, ( int ) exp, c, acqBegan, tp, step,
													handlers.get( channelItem.getName() ),
													frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages);

											core.setShutterOpen( channelItem.getLaser(), false );
										}
									}
								}

							} catch (Exception e) {
								System.err.println(e);
								finalize(true, setup, currentCamera, cameras, frame, tp, rown, handlers, store);
								return;
							}

							noSlice++;
						}

						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

						for( OutputHandler handler : handlers.values() )
						{
							finalizeStack( tp, rown, handler );
						}

						++step;
					}
					double wait = tpItem.getIntervalSeconds();

					if(wait > 0D) {
						System.err.println("Interval delay. (next seq in " + wait + "s)");

						for(int i = 0; i < (int) wait; i++)
						{
							++passedTimePoints;
							currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
							try
							{
								Thread.sleep( ( long ) ( 1e3 ) );
							}
							catch ( InterruptedException ie )
							{
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
								return;
							}
						}
					}
					++timePoints;

					currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
				}
			}
			else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
			{
				double wait = tpItem.getIntervalSeconds();

				if(wait > 0D) {
					System.err.println("Wait delay. (next seq in " + wait + "s)");

					for(int i = 0; i < (int) wait; i++)
					{
						++passedTimePoints;
						currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
						try
						{
							Thread.sleep( ( long ) ( 1e3 ) );
						}
						catch ( InterruptedException ie )
						{
							finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
							return;
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("Duplicates")
	private static void runNormalSmartImagingMMAcq(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, double cylinderSize, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart) throws Exception
	{

		final CMMCore core = frame.core();

		int timePoints = 0;
		int  passedTimePoints = 0;
		double totalTimePoints = timePointItems.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

		AcqWrapperEngine engine = new AcqWrapperEngine( setup, frame, store, currentCamera, cameras, handlers, channelItems, arduinoSelected, processedImages, acqBegan, acqStart );

		for(TimePointItem tpItem : timePointItems ) {
			if(tpItem.getType().equals( TimePointItem.Type.Acq ))
			{
				int timeSeqs = tpItem.getNoTimePoints();
				for ( int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq )
				{
					int step = 0;

					for ( PositionItem positionItem : positionItems )
					{
						final int tp = timePoints;
						final int rown = step;

//						display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timePoints, step ) );

						// Move the stage
						if(stagePanel != null)
							stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
						try
						{
							core.waitForSystem();
						} catch ( Exception e ) {
							System.out.println(e.toString());
						}

						for( OutputHandler handler : handlers.values() )
							beginStack( tp, rown, handler );

						System.out.println("MMAcquisition started");
						engine.startAcquire( timePoints, step, positionItem );

						while(engine.isAcquisitionRunning()) {
							try
							{
								Thread.sleep( 10 );
							} catch ( InterruptedException ie )
							{
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
								engine.exit();
							}
						}

						System.out.println("MMAcquisition finished");

						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

						for( OutputHandler handler : handlers.values() )
						{
							finalizeStack( tp, rown, handler );
						}

						++step;
					}

					double wait = tpItem.getIntervalSeconds();

					if(wait > 0D) {
						System.err.println("Interval delay. (next seq in " + wait + "s)");

						for(int i = 0; i < (int) wait; i++)
						{
							++passedTimePoints;
							currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
							try
							{
								Thread.sleep( ( long ) ( 1e3 ) );
							}
							catch ( InterruptedException ie )
							{
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
								engine.exit();
								return;
							}
						}
					}
					++timePoints;

					currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
				}
			}
			else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
			{
				double wait = tpItem.getIntervalSeconds();

				if(wait > 0D) {
					System.err.println("Wait delay. (next seq in " + wait + "s)");

					for(int i = 0; i < (int) wait; i++)
					{
						++passedTimePoints;
						currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
						try
						{
							Thread.sleep( ( long ) ( 1e3 ) );
						}
						catch ( InterruptedException ie )
						{
							finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
							engine.exit();
							return;
						}
					}
				}
			}
		}

		engine.exit();
	}

	@SuppressWarnings("Duplicates")
	private static void runNormalImaging(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			int timeSeqs, double timeStep, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart) throws Exception
	{

		final CMMCore core = frame.core();

		for(int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq) {
			// User defined location
			// Looping multiple locations
			int step = 0;
			for( PositionItem positionItem : positionItems )
			{
				final int tp = timeSeq;
				final int rown = step;

				display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timeSeq, step ));

				// Move the stage
				if(stagePanel != null)
					stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
				core.waitForSystem();

				for( OutputHandler handler : handlers.values() )
					beginStack( tp, rown, handler );

				// Traverse Z stacks
				int noSlice = 0;
				for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep())
				{
					try {
						// Move Z stacks
						if(stagePanel != null)
							stagePanel.goToZ( zStart );
						core.waitForSystem();

						// Wait for image synchronization
						core.waitForImageSynchro();

						long now = System.currentTimeMillis();

						// Cameras
						if ( arduinoSelected )
						{
							// Channel iteration
							int c = 0;

							for ( String camera : cameras )
							{
								for ( ChannelItem channelItem : channelItems )
								{
									// Snap an image
									if ( setup.getArduino1() != null )
										setup.getArduino1().setSwitchState( channelItem.getLaser() );

									double exp = channelItem.getValue().doubleValue();
									core.setExposure( camera, exp );
									core.snapImage();
									core.waitForDevice( camera );

									for ( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh++ )
									{
										c = addSlice( getTaggedImage( core, currentCh ), setup, ( int ) exp, c, acqBegan, tp, step, handlers.get( camera ),
												frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages );
									}
								}
							}
						}
						else
						{
							// Channel iteration
							int c = 0;

							for ( ChannelItem channelItem : channelItems )
							{
								double exp = channelItem.getValue().doubleValue();
								core.setCameraDevice( channelItem.getName() );
								core.setShutterOpen( channelItem.getLaser(), true );
								core.setExposure( channelItem.getName(), exp );
								core.snapImage();
								core.waitForDevice( channelItem.getName() );

								for ( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh++ )
								{
									c = addSlice( getTaggedImage( core, currentCh ), setup, ( int ) exp, c, acqBegan, tp, step, handlers.get( channelItem.getName() ),
											frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages );

									core.setShutterOpen( channelItem.getLaser(), false );
								}
							}
						}

					} catch (Exception e) {
						System.err.println(e);
						finalize(true, setup, currentCamera, cameras, frame, tp, rown, handlers, store);
						return;
					}

					noSlice++;
				}

				if(setup.getArduino1() != null)
					setup.getArduino1().setSwitchState( "0" );

				for( OutputHandler handler : handlers.values() )
				{
					finalizeStack( tp, rown, handler );
				}

				++step;
			}

			// End of looping rows

			if(timeSeq + 1 < timeSeqs) {
				double wait = (timeStep * (timeSeq + 1)) -
						(System.nanoTime() / 1e9 - acqBegan);

				if(wait > 0D)
					try {
						Thread.sleep((long)(wait * 1e3));
					} catch(InterruptedException ie) {
						finalize(false, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
						return;
					}
				else
					core.logMessage("Behind schedule! (next seq in " + wait + "s)");
			}
		}
	}

	@SuppressWarnings("Duplicates")
	private static void runNormalImagingMMAcq(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			int timeSeqs, double timeStep, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart) throws Exception
	{
		final CMMCore core = frame.core();

		AcqWrapperEngine engine = new AcqWrapperEngine( setup, frame, store, currentCamera, cameras, handlers, channelItems, arduinoSelected, processedImages, acqBegan, acqStart );

		for(int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq) {
			// User defined location
			// Looping multiple locations
			int step = 0;
			for( PositionItem positionItem : positionItems )
			{
				final int tp = timeSeq;
				final int rown = step;

				// display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timeSeq, step ));

				// Move the stage
				if(stagePanel != null)
					stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );

				try
				{
					core.waitForSystem();
				} catch ( Exception e ) {
					System.out.println(e.toString());
				}

				for( OutputHandler handler : handlers.values() )
					beginStack( tp, rown, handler );

				System.out.println("MMAcquisition started");
				engine.startAcquire( timeSeq, step, positionItem );

				while(engine.isAcquisitionRunning()) {
					try
					{
						Thread.sleep( 10 );
					} catch ( InterruptedException ie )
					{
						finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
						engine.exit();
					}
				}

				System.out.println("MMAcquisition finished");

				if(setup.getArduino1() != null)
					setup.getArduino1().setSwitchState( "0" );

				for( OutputHandler handler : handlers.values() )
				{
					finalizeStack( tp, rown, handler );
				}

				++step;
			}

			// End of looping rows

			if(timeSeq + 1 < timeSeqs) {
				double wait = (timeStep * (timeSeq + 1)) -
						(System.nanoTime() / 1e9 - acqBegan);

				if(wait > 0D)
					try {
						Thread.sleep((long)(wait * 1e3));
					} catch(InterruptedException ie) {
						finalize(false, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
						engine.exit();
						return;
					}
				else
					core.logMessage("Behind schedule! (next seq in " + wait + "s)");
			}
		}
		engine.exit();
	}

	@SuppressWarnings("Duplicates")
	private static void executeContinuousAcquisition(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			int timeSeqs, double timeStep, ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, double cylinderSize, boolean smartImagingSelected, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan ) throws Exception
	{
		final CMMCore core = frame.core();
		long acqStart = System.currentTimeMillis();

		captureThread = null;
		done = false;
		ConcurrentHashMap<String, TaggedImage> captureImages = new ConcurrentHashMap<>();

		// Initialize a capture thread
		{
			core.startContinuousSequenceAcquisition(0);

			if(null == captureThread) {
				captureThread = new Thread( new Runnable()
				{
					@Override public void run()
					{
						while (core.getRemainingImageCount() == 0) {
							try
							{
								Thread.sleep(5);
							}
							catch ( InterruptedException e )
							{
								e.printStackTrace();
							}
						}

						while ( !done )
						{
							try
							{
								if ( core.getRemainingImageCount() > 0 )
								{
									// TaggedImage timg = core.popNextTaggedImage();
									TaggedImage timg = core.getLastTaggedImage();
									if(timg != POISON)
									{
										String camera = ( String ) timg.tags.get( "Camera" );
										captureImages.put( camera, timg );
									}
								}

								// sleep a bit to free some CPU time
								Thread.sleep( 1 );
							}
							catch ( Exception e )
							{
								e.printStackTrace();
							}
						}

						try
						{
							core.stopSequenceAcquisition();
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
				} );
				captureThread.start();
			}

			while ( captureImages.isEmpty() )
			{
				Thread.sleep( 100 );
			}
		}

		// Scheduled timeline
		// Dynamic timeline

		if(smartImagingSelected) {
			runContinuousSmartImaging(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
					timePointItems, positionItems, channelItems, currentTP, cylinderSize, arduinoSelected,
					processedImages, acqBegan, acqStart, captureImages);
		} else {
			runContinuousImaging(setup, frame, store, display, stagePanel, currentCamera, cameras, acqFilenamePrefix, handlers,
					timeSeqs, timeStep, positionItems, channelItems,
					arduinoSelected, processedImages, acqBegan, acqStart, captureImages);
		}

		finalize(true, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
	}

	@SuppressWarnings("Duplicates")
	private static void runContinuousSmartImaging(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, double cylinderSize, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart, ConcurrentHashMap<String, TaggedImage> captureImages) throws Exception
	{
		final CMMCore core = frame.core();

		int timePoints = 0;
		int  passedTimePoints = 0;
		double totalTimePoints = timePointItems.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

		for(TimePointItem tpItem : timePointItems ) {
			if(tpItem.getType().equals( TimePointItem.Type.Acq ))
			{
				int timeSeqs = tpItem.getNoTimePoints();
				for ( int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq )
				{
					int step = 0;
					for ( PositionItem positionItem : positionItems )
					{
						final int tp = timePoints;
						final int rown = step;

						display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timePoints, step ) );

						// Move the stage
						if(stagePanel != null)
							stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
						core.waitForSystem();

						for( OutputHandler handler : handlers.values() )
							beginStack( tp, rown, handler );

						// Traverse Z stacks
						int noSlice = 0;
						for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep())
						{
							try {
								// Move Z stacks
								if(stagePanel != null)
									stagePanel.goToZ( zStart );
								core.waitForSystem();

								// Wait for image synchronization
								core.waitForImageSynchro();

								long now = System.currentTimeMillis();

								// Cameras
								if ( arduinoSelected )
								{
									// Channel iteration
									int c = 0;
									for ( String camera : cameras )
									{
										for ( ChannelItem channelItem : channelItems )
										{
											// Snap an image
											if ( setup.getArduino1() != null )
												setup.getArduino1().setSwitchState( channelItem.getLaser() );

											// Do not change exposure during continuous acquisition

											for ( String cam : captureImages.keySet() )
											{
												c = addSlice( captureImages.get( cam ), setup, ( int ) core.getExposure(), c, acqBegan, tp, step, handlers.get( camera ),
														frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages );
											}
										}
									}
								}
								else
								{
									// Channel iteration
									int c = 0;
									for ( String cam : captureImages.keySet() )
									{
										c = addSlice( captureImages.get( cam ), setup, ( int ) core.getExposure(), c, acqBegan, tp, step, handlers.get( currentCamera ),
												frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages);
									}
								}

							} catch (Exception e) {
								System.err.println(e);
								finalize(true, setup, currentCamera, cameras, frame, tp, rown, handlers, store);
								return;
							}

							noSlice++;
						}

						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

						for( OutputHandler handler : handlers.values() )
						{
							finalizeStack( tp, rown, handler );
						}

						++step;
					}
					double wait = tpItem.getIntervalSeconds();

					if(wait > 0D) {
						System.err.println("Interval delay. (next seq in " + wait + "s)");

						for(int i = 0; i < (int) wait; i++)
						{
							++passedTimePoints;
							currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
							try
							{
								Thread.sleep( ( long ) ( 1e3 ) );
							}
							catch ( InterruptedException ie )
							{
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
								return;
							}
						}
					}
					++timePoints;
				}
			}
			else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
			{
				double wait = tpItem.getIntervalSeconds();

				if(wait > 0D) {
					System.err.println("Wait delay. (next seq in " + wait + "s)");

					for(int i = 0; i < (int) wait; i++)
					{
						++passedTimePoints;
						currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
						try
						{
							Thread.sleep( ( long ) ( 1e3 ) );
						}
						catch ( InterruptedException ie )
						{
							finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
							return;
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("Duplicates")
	private static void runContinuousImaging(SPIMSetup setup, final Studio frame, RewritableDatastore store,
			DisplayWindow display, StagePanel stagePanel, String currentCamera, List<String> cameras, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			int timeSeqs, double timeStep, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			boolean arduinoSelected, LongProperty processedImages, final double acqBegan, long acqStart, ConcurrentHashMap<String, TaggedImage> captureImages) throws Exception
	{
		final CMMCore core = frame.core();

		for(int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq) {
			// User defined location
			// Looping multiple locations
			int step = 0;
			for( PositionItem positionItem : positionItems )
			{
				final int tp = timeSeq;
				final int rown = step;

				display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timeSeq, step ));

				// Move the stage
				if(stagePanel != null)
					stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
				core.waitForSystem();

				for( OutputHandler handler : handlers.values() )
					beginStack( tp, rown, handler );

				// Traverse Z stacks
				int noSlice = 0;
				for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep())
				{
					try {
						// Move Z stacks
						if(stagePanel != null)
							stagePanel.goToZ( zStart );
						core.waitForSystem();

						// Wait for image synchronization
						core.waitForImageSynchro();

						long now = System.currentTimeMillis();

						// Cameras
						if ( arduinoSelected )
						{
							// Channel iteration
							int c = 0;
							for ( String camera : cameras )
							{
								for ( ChannelItem channelItem : channelItems )
								{
									// Snap an image
									if ( setup.getArduino1() != null )
										setup.getArduino1().setSwitchState( channelItem.getLaser() );

									// Do not change exposure during continuous acquisition

									for ( String cam : captureImages.keySet() )
									{
										c = addSlice( captureImages.get( cam ), setup, ( int ) core.getExposure(), c, acqBegan, tp, step, handlers.get( camera ),
												frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages );
									}
								}
							}
						}
						else
						{
							// Channel iteration
							int c = 0;
							for ( String cam : captureImages.keySet() )
							{
								c = addSlice( captureImages.get( cam ), setup, ( int ) core.getExposure(), c, acqBegan, tp, step, handlers.get( currentCamera ),
										frame, store, noSlice, zStart, positionItem, now - acqStart, processedImages);
							}
						}

					} catch (Exception e) {
						System.err.println(e);
						finalize(true, setup, currentCamera, cameras, frame, tp, rown, handlers, store);
						return;
					}

					noSlice++;
				}

				if(setup.getArduino1() != null)
					setup.getArduino1().setSwitchState( "0" );

				for( OutputHandler handler : handlers.values() )
				{
					finalizeStack( tp, rown, handler );
				}

				++step;
			}

			// End of looping rows

			if(timeSeq + 1 < timeSeqs) {
				double wait = (timeStep * (timeSeq + 1)) -
						(System.nanoTime() / 1e9 - acqBegan);

				if(wait > 0D)
					try {
						Thread.sleep((long)(wait * 1e3));
					} catch(InterruptedException ie) {
						finalize(false, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
						return;
					}
				else
					core.logMessage("Behind schedule! (next seq in "
							+ wait + "s)");
			}
		}
	}

	private static int addSlice(TaggedImage ti, SPIMSetup setup, int exp, int ch, double acqBegan, int tp, int step, OutputHandler handler,
			Studio frame, RewritableDatastore store, int noSlice, double zStart, PositionItem positionItem, long ms, LongProperty processedImages) throws Exception
	{
		// Convert TaggedImage to ImageProcessor
		ImageProcessor ip = ImageUtils.makeProcessor( ti );

		// Handle a slice to save in the handler
		if(handler != null)
		{
			handleSlice( setup, exp, ch, acqBegan, tp, step, ip, handler );
			// Add an image to the store for display
//			addImageToAcquisition( frame, store, ch, noSlice, zStart,
//					positionItem, ms, ti );
			addImageToDisplay( frame, store, step, tp, ch, noSlice, zStart,
					positionItem, ms, ti );
		} else {
			addImageToDisplay( frame, store, step, tp, ch, noSlice, zStart,
					positionItem, ms, ti );
		}


		// Increase the number of processed image
		processedImages.set( processedImages.get() + 1 );

		// return ch
		return ch + 1;
	}

	/**
	 * Gets multi cameras.
	 * @param core the core
	 * @return the multi cams
	 */
	private static List<String> getMultiCams( CMMCore core )
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

	private static void finalize(boolean finalizeStack, SPIMSetup setup, final String currentCamera, List<String> cameras, final Studio frame, final int tp, final int rown, HashMap<String, OutputHandler> handlers, RewritableDatastore store) throws Exception
	{
		final CMMCore core = frame.core();

		if(setup.getArduino1() != null)
			setup.getArduino1().setSwitchState( "0" );

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

		if (finalizeStack) {
			for(String camera : cameras)
			{
				if(null != handlers.get( camera ))
					finalizeStack( tp, rown, handlers.get( camera ) );
			}
		}

		for( OutputHandler handler : handlers.values() )
		{
			handler.finalizeAcquisition( true );
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

	private static void addImageToAcquisition( Studio studio, RewritableDatastore store, int channel,
			int slice, double zPos, PositionItem position, long ms, TaggedImage taggedImg ) throws
			JSONException, DatastoreFrozenException,
			DatastoreRewriteException, Exception
	{
		if(slice == 0) store.deleteAllImages();

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
	private static void addImageToDisplay( Studio studio, RewritableDatastore store, int pos, int tp, int channel,
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

	private static void handleSlice(SPIMSetup setup, int exp, int channel, double start, int time, int angle, ImageProcessor ip,
			OutputHandler handler) throws Exception {
		if(null != handler)
			handler.processSlice(time, angle, exp, channel, ip, setup.getXStage().getPosition(),
				setup.getYStage().getPosition(),
				setup.getZStage().getPosition(),
				setup.getAngle(),
				System.nanoTime() / 1e9 - start);
	}

	private static void beginStack(int time, int angle, OutputHandler handler) throws Exception {
		ij.IJ.log("beginStack - Time:" + time + " Row: "+ angle);
		handler.beginStack( time, angle );
	}

	private static void finalizeStack(int time, int angle, OutputHandler handler) throws Exception {
		ij.IJ.log("finalizeStack - Time:" + time + " Row: "+ angle);
		handler.finalizeStack( time, angle );
	}
}
