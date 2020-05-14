package spim.ui.view.component;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.collections.ObservableList;
import loci.common.DebugTools;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import mmcorej.org.json.JSONException;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
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
import org.micromanager.internal.utils.ReportingUtils;
import spim.acquisition.Row;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.OMETIFFHandler;
import spim.io.OutputHandler;
import spim.mm.MicroManager;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.data.TimePointItem;
import spim.ui.view.component.acq.AcqWrapperEngine;
import spim.ui.view.component.testing.AE2010ImageDecoder;

import javax.swing.SwingUtilities;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;

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
	 * @return the image plus
	 * @throws Exception the exception
	 */
	@SuppressWarnings("Duplicates")
	public static ImagePlus performAcquisition( Studio studio, SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, double timeStep, ObservableList< TimePointItem > timePointItems, DoubleProperty currentTP, double cylinderSize, boolean smartImagingSelected, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave ) throws Exception
	{
		final Studio frame = studio;

		if(frame == null) return null;

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

		int ch = 0;
		if(arduinoSelected) {
			cameras.add(currentCamera);
			ch++;
		} else {
			for(ChannelItem chItem: channelItems) {
				if(!cameras.contains( chItem.getName() )) {
					if(chItem.getName().startsWith( "Multi" )) ch += 2;
					else ch += 1;
					cameras.add( chItem.getName() );
				}
			}
		}

//		if(setup.getCamera1() != null)
//		{
//			cameras.add( setup.getCamera1().getLabel() );
//		}
//
//		if(setup.getCamera2() != null)
//		{
//			cameras.add( setup.getCamera2().getLabel() );
//		}

		String[] channelNames = new String[ch];

		ch = 0;
		for(String cam : cameras) {
			for(ChannelItem chItem : channelItems)
			{
				if(!cam.startsWith( "Multi" ))
					channelNames[ ch++ ] = cam + "-" + chItem.getName();
				else {
					channelNames[ ch++ ] = "Multi-Cam0-" + chItem.getName();
					channelNames[ ch++ ] = "Multi-Cam1-" + chItem.getName();
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
			for(String camera : cameras) {
				if(camera.startsWith( "Multi" ))
				{
					OutputHandler handler = new OMETIFFHandler(
							core, output, acqFilenamePrefix + "_" + camera + "_",
							//what is the purpose of defining parameters and then passing null anyway?
							acqRows, channelItems.size() * 2, timeSeqs, timeStep, 1, smb.userData( pm.build() ).build(), false );

					handlers.put( camera, handler );
				} else {
					OutputHandler handler = new OMETIFFHandler(
							core, output, acqFilenamePrefix + "_" + camera + "_",
							//what is the purpose of defining parameters and then passing null anyway?
							acqRows, channelItems.size(), timeSeqs, timeStep, 1, smb.userData( pm.build() ).build(), false );

					handlers.put( camera, handler );
				}
			}

		long acqStart = System.currentTimeMillis();

		// Scheduled timeline
		// Dynamic timeline
		if(smartImagingSelected) {
			int timePoints = 0;
			int  passedTimePoints = 0;
			double totalTimePoints = timePointItems.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

			for(TimePointItem tpItem : timePointItems ) {
				if(tpItem.getType().equals( TimePointItem.Type.Acq ))
				{
					timeSeqs = tpItem.getNoTimePoints();
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


												core.setExposure( camera, channelItem.getValue().doubleValue() );
												core.waitForDevice( camera );

												for( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh ++)
												{
													TaggedImage ti = snapImage( core, currentCh );

													ImageProcessor ip = ImageUtils.makeProcessor( ti );
													handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, tp, step, ip, handlers.get( camera ) );

													addImageToAcquisition( frame, store, c, noSlice, zStart,
															positionItem, now - acqStart, ti );

													Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
													++c;
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
											core.setCameraDevice( channelItem.getName() );
											core.setShutterOpen( channelItem.getLaser(), true );
											core.setExposure( channelItem.getName(), channelItem.getValue().doubleValue() );
											core.waitForDevice( channelItem.getName() );

											for( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh ++) {
												TaggedImage ti = snapImage( core, currentCh );

												ImageProcessor ip = ImageUtils.makeProcessor( ti );

												// Handle the slice image
												handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, tp, step, ip, handlers.get( channelItem.getName() ) );

												addImageToAcquisition( frame, store, c, noSlice, zStart,
														positionItem, now - acqStart, ti );

												Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );

												core.setShutterOpen( channelItem.getLaser(), false );
												++c;
											}
										}
									}

								} catch (Exception e) {
									System.err.println(e);
									finalize(true, setup, cameras, frame, tp, rown, handlers, store);
									return null;
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
						if(timeSeq + 1 < timeSeqs) {
							double wait = tpItem.getIntervalSeconds();

							if(wait > 0D) {
								System.err.println("Interval delay. (next seq in " + wait + "s)");
								try {
									Thread.sleep((long)(wait * 1e3));
								} catch(InterruptedException ie) {
									finalize(false, setup, cameras, frame, 0, 0, handlers, store);
									return null;
								}
							}
						}
						++timePoints;
						++passedTimePoints;

						currentTP.set( cylinderSize / totalTimePoints * passedTimePoints );
					}
				}
				else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
				{
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
								finalize( false, setup, cameras, frame, 0, 0, handlers, store );
								return null;
							}
						}
					}
				}
			}
		} else {
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


										core.setExposure( camera, channelItem.getValue().doubleValue() );
										core.waitForDevice( camera );
										for( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh ++)
										{
											TaggedImage ti = snapImage( core, currentCh );

											ImageProcessor ip = ImageUtils.makeProcessor( ti );
											handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get( camera ) );

											addImageToAcquisition( frame, store, c, noSlice, zStart,
													positionItem, now - acqStart, ti );

											Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
											++c;
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
									core.setCameraDevice( channelItem.getName() );
									core.setShutterOpen( channelItem.getLaser(), true );
									core.setExposure( channelItem.getName(), channelItem.getValue().doubleValue() );
									core.waitForDevice( channelItem.getName() );

									for( int currentCh = 0; currentCh < core.getNumberOfCameraChannels(); currentCh ++)
									{
										TaggedImage ti = snapImage( core, currentCh );

										ImageProcessor ip = ImageUtils.makeProcessor( ti );

										// Handle the slice image
										handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get( channelItem.getName() ) );

										addImageToAcquisition( frame, store, c, noSlice, zStart,
												positionItem, now - acqStart, ti );

										Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );

										core.setShutterOpen( channelItem.getLaser(), false );
										++c;
									}
								}
							}

						} catch (Exception e) {
							System.err.println(e);
							finalize(true, setup, cameras, frame, tp, rown, handlers, store);
							return null;
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
							//						return cleanAbort(params, liveOn, autoShutter, continuousThread);
							finalize(false, setup, cameras, frame, 0, 0, handlers, store);
							return null;
						}
					else
						core.logMessage("Behind schedule! (next seq in "
								+ wait + "s)");
				}
			}
		}

		finalize(false, setup, cameras, frame, 0, 0, handlers, store);

		return null;
	}

	@SuppressWarnings("Duplicates")
	public static ImagePlus performAcquisitionMM( SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, double timeStep, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave ) throws Exception
	{
		final MMStudio frame = MMStudio.getInstance();

		if(frame == null) return null;

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

		final CMMCore core = frame.getCore();

		//		final AcquisitionSettings acqSettings = acqSettingsOrig;
		//
		//		// generate string for log file
		//		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		//		final String acqSettingsJSON = gson.toJson(acqSettings);

		final double acqBegan = System.nanoTime() / 1e9;

		//		boolean autoShutter = core.getAutoShutter();
		//		core.setAutoShutter(false);

		Row[] acqRows = generateRows( positionItems );

		List<String> cameras = new ArrayList<>();

		if(setup.getCamera1() != null)
		{
			cameras.add( setup.getCamera1().getLabel() );
		}

		if(setup.getCamera2() != null)
		{
			cameras.add( setup.getCamera2().getLabel() );
		}

		String[] channelNames = new String[cameras.size() * channelItems.size()];

		int ch = 0;
		for(String cam : cameras) {
			for(ChannelItem chItem : channelItems)
				channelNames[ch++] = cam + "-" + chItem.getName();
		}

		MultiStagePosition[] multiStagePositions = positionItems.stream().map( c -> new MultiStagePosition( c.toString(), c.getX(), c.getY(), c.getZString(), c.getZStart() )).toArray(MultiStagePosition[]::new);

//		Metadata.Builder mb = frame.data().getMetadataBuilder();
//		mb.
//
//		SummaryMetadata.Builder smb = frame.data().getSummaryMetadataBuilder();

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


		//		pmb.putString("FirstSide", firstSide);
		//		pmb.putString("SlicePeriod_ms", actualSlicePeriodLabel_.getText());
		//		pmb.putDouble("LaserExposure_ms",
		//				(double) PanelUtils.getSpinnerFloatValue(durationLaser_));
		//		pmb.putString("VolumeDuration",
		//				actualVolumeDurationLabel_.getText());
		//		pmb.putString("SPIMmode", spimMode.toString());
		//		// Multi-page TIFF saving code wants this one:
		//		// TODO: support other types than GRAY16  (NS: Why?? Cameras are all 16-bits, so not much reason for anything else
		//		pmb.putString("PixelType", "GRAY16");
		//		pmb.putDouble("z-step_um", getVolumeSliceStepSize());
		//		// Properties for use by MultiViewRegistration plugin
		//		// Format is: x_y_z, set to 1 if we should rotate around this axis.
		//		pmb.putString("MVRotationAxis", "0_1_0");
		//		pmb.putString("MVRotations", viewString);

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
			for(String camera : cameras) {
				OutputHandler handler = new OMETIFFHandler(
						core, output, acqFilenamePrefix + "_" + camera + "_",
						//what is the purpose of defining parameters and then passing null anyway?
						acqRows, channelItems.size(), timeSeqs, timeStep, 1, smb.userData(pm.build()).build(), false);

				//			handler = new AsyncOutputHandler(handler, (ij.IJ.maxMemory() - ij.IJ.currentMemory())/(core.getImageWidth()*core.getImageHeight()*core.getBytesPerPixel()*2), false);

				handlers.put( camera, handler );
			}

		long acqStart = System.currentTimeMillis();

		String channelGroup = "Channel";

		if(arduinoSelected) {
			if ( setup.getArduino1() != null )
			{
				String wheel = setup.getArduino1().getLabel();
				for ( ChannelItem channelItem : channelItems )
				{
					core.defineConfig( channelGroup, channelItem.getName(), wheel, "State", channelItem.getLaser() );
				}
			} else {
				for ( ChannelItem channelItem : channelItems )
				{
					core.defineConfig( channelGroup, channelItem.getName(), "DShutter", "State", "0" );
				}
			}
		} else {
			// define channel based on the user input
			String wheel = "Core";
			for ( ChannelItem channelItem : channelItems )
			{
				core.defineConfig(channelGroup, channelItem.getName(), channelItem.getName(), "Exposure", channelItem.getValue().toString());
				core.defineConfig(channelGroup, channelItem.getName(), wheel, "Camera", channelItem.getName());
			}
		}

		// Scheduled timeline
		// Dynamic timeline
		for(int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq) {
			// User defined location
			// Looping multiple locations
			int step = 0;
			for( PositionItem positionItem : positionItems )
			{
				final int tp = timeSeq;
				final int rown = step;

				display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timeSeq, step ));

				stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
				core.waitForSystem();

				for( OutputHandler handler : handlers.values() )
					beginStack( tp, rown, handler );

				// Traverse Z stacks
				ArrayList stack = new ArrayList<Double>();
				for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep())
				{
					stack.add( zStart );
					try {





//									ImageProcessor ip = ImageUtils.makeProcessor( ti );
//									handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get( camera ) );
//
//									addImageToAcquisition( frame, store, c, noSlice, zStart,
//											positionItem, now - acqStart, ti );
//
//									//						if(ad != null)
//									//							addAntiDriftSlice(ad, ip);
//									//						if(params.isUpdateLive())
//									//							updateLiveImage(frame, ti);
//
//									Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
//									++c;
//
//								ImageProcessor ip = ImageUtils.makeProcessor( ti );
//
//								// Handle the slice image
//								handleSlice( setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get( channelItem.getName() ) );
//
//								addImageToAcquisition( frame, store, c, noSlice, zStart,
//										positionItem, now - acqStart, ti );
//
//								//						if(ad != null)
//								//							addAntiDriftSlice(ad, ip);
//								//						if(params.isUpdateLive())
//								//							updateLiveImage(frame, ti);
//								Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
//
//								core.setShutterOpen( channelItem.getLaser(), false );
//								++c;

					} catch (Exception e) {
						//					if (Thread.interrupted()) {
//						finalize(true, setup, cameras, frame, tp, rown, handlers, store);

						core.deleteConfigGroup(channelGroup);
						return null;
					}
				}

				ArrayList<ChannelSpec> channels = new ArrayList<>();

				int chNo = 0;
				for ( ChannelItem channelItem : channelItems )
				{
					ChannelSpec spec = new ChannelSpec();
					spec.config = channelItem.getName();
					spec.exposure = channelItem.getValue().doubleValue();
					spec.zOffset = 0.0;
					spec.doZStack = true;
					spec.useChannel = true;
					spec.color = new Color( 1 << chNo );
					if(arduinoSelected)
						spec.camera = "Multi Camera";
					else
						spec.camera = channelItem.getName();

					channels.add( spec );
					chNo++;
				}

				SequenceSettings mdaSeq = new SequenceSettings();
				mdaSeq.numFrames = 1;
				mdaSeq.intervalMs = 0.0;
				mdaSeq.channelGroup = "Channel";
				mdaSeq.channels = channels;

				mdaSeq.slices = stack;
				mdaSeq.relativeZSlice = false;
				mdaSeq.slicesFirst = false;
				mdaSeq.timeFirst = false;
				mdaSeq.keepShutterOpenSlices = false;
				mdaSeq.keepShutterOpenChannels = false;
				mdaSeq.useAutofocus = false;
				mdaSeq.skipAutofocusCount = 0;
				mdaSeq.save = false;
				mdaSeq.zReference = 0;
				mdaSeq.comment = "";
				mdaSeq.usePositionList = true;
				mdaSeq.cameraTimeout = 20000;
				mdaSeq.shouldDisplayImages = true;

				PositionList posList = new PositionList();
//				MultiStagePosition p = new MultiStagePosition(  );
//				StagePosition pp = StagePosition.create2D( "XYStage", 0, 0 );
//
//				posList.addPosition(  );

//				AcqWrapperEngine e = new AcqWrapperEngine();
//				e.setParentGUI( frame );
//				e.setSequenceSettings( mdaSeq );
//				e.setPositionList( posList );
//				e.acquire();

				IAcquisitionEngine2010 ae2010 = getAcquisitionEngine2010();
				// ae2010.attachRunnable(  );

				List< TaggedImage > imgs = AE2010ImageDecoder.collectImages(
								ae2010.run(mdaSeq, true, posList,
										frame.getAutofocusManager().getAutofocusMethod()));


				long now = System.currentTimeMillis();

				double zStart = positionItem.getZStart();

				int totCh = channels.size() * (int) frame.core().getNumberOfCameraChannels();
				int noSlice = 0;
				int i = 0;
				for(TaggedImage ti : imgs) {
					ImageProcessor ip = ImageUtils.makeProcessor( ti );

					int c = i % totCh;
					ChannelSpec spec = channels.get(c % channels.size());

					// Handle the slice image
					handleSlice( setup, (int) spec.exposure, c, acqBegan, timeSeq, step, ip, handlers.get( spec.config ) );

					addImageToAcquisition( frame, store, c, noSlice, zStart,
							positionItem, now - acqStart, ti );

					//						if(ad != null)
					//							addAntiDriftSlice(ad, ip);
					//						if(params.isUpdateLive())
					//							updateLiveImage(frame, ti);
					Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
//					++c;

					i++;

					if(0 == c)
					{
						zStart += positionItem.getZStep();
						noSlice++;
					}
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
						//						return cleanAbort(params, liveOn, autoShutter, continuousThread);
//						finalize(false, setup, cameras, frame, 0, 0, handlers, store);
						return null;
					}
				else
					core.logMessage("Behind schedule! (next seq in "
							+ wait + "s)");
			}
		}

		//		setStatus( AcquisitionStatus.DONE );
//		finalize(false, setup, cameras, frame, 0, 0, handlers, store);

		core.deleteConfigGroup(channelGroup);

		return null;
	}

	protected static IAcquisitionEngine2010 getAcquisitionEngine2010() {
		final MMStudio frame = MMStudio.getInstance();

		return frame.getAcquisitionEngine2010();
	}

	private static void finalize(boolean finalizeStack, SPIMSetup setup, List<String> cameras, final Studio frame, final int tp, final int rown, HashMap<String, OutputHandler> handlers, RewritableDatastore store) throws Exception
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

		core.waitForSystem();

		if (finalizeStack) {
			for(String camera : cameras)
			{
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
		mdb = mdb.xPositionUm(position.getX()).yPositionUm(position.getY()).zPositionUm( zPos );

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

	/**
	 * To avoid the null returned from the system for snapImage
	 * @param core
	 * @return
	 * @throws InterruptedException
	 */
	private static TaggedImage snapImage( CMMCore core, int ch ) throws InterruptedException
	{
		TaggedImage ti = null;

		while(ti == null)
		{
			try
			{
				core.snapImage();
				ti = core.getTaggedImage( ch );
			}
			catch ( Exception e )
			{
				ReportingUtils.logError( e );
			}
			Thread.sleep( 10 );
		}

		return ti;
	}

	private static void runDevicesAtRow(CMMCore core, SPIMSetup setup, Row row) throws Exception {
		for ( SPIMSetup.SPIMDevice devType : row.getDevices() ) {
			Device dev = setup.getDevice(devType);
			Row.DeviceValueSet values = row.getValueSet(devType);

			if (dev instanceof Stage ) // TODO: should this be different?
			{
				if( devType == SPIMSetup.SPIMDevice.STAGE_THETA ) {
					( ( Stage ) dev ).setPosition( values.getStartPosition() - 180 );
				}
				else
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
