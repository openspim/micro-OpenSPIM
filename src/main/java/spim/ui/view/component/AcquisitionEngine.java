package spim.ui.view.component;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.collections.ObservableList;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
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
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.UserProfileStaticInterface;
import spim.acquisition.Row;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.OMETIFFHandler;
import spim.io.OutputHandler;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;

import java.awt.Rectangle;
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
public class AcquisitionEngine
{
	public void init() {
		// Setting parameters
	}

	public void scheduledAcquisition() {
		// setup OEMTIFFHandler or HDF5OutputHandler
	}

	public static ImagePlus performAcquisition( SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, double timeStep, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave ) throws Exception
	{
		final MMStudio frame = MMStudio.getInstance();

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

//		SummaryMetadata.Builder smb = frame.data().getSummaryMetadataBuilder();

		PropertyMap.Builder pm = PropertyMaps.builder();

		SummaryMetadata.Builder smb = new DefaultSummaryMetadata.Builder();

		UserProfile profile = UserProfileStaticInterface.getInstance();
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

		// Scheduled timeline
		// Dynamic timeline
		for(int timeSeq = 0; timeSeq < timeSeqs; ++timeSeq) {
//			Thread continuousThread = null;

//			final int finalTimeSeq = timeSeq;

			// User defined location
			// Looping multiple locations
			int step = 0;
			for( PositionItem positionItem : positionItems )
			{
				final int tp = timeSeq;
				final int rown = step;

				display.setCustomTitle( acqFilenamePrefix + String.format( " t=%d, p=%d", timeSeq, step ));

//				AntiDriftController ad = null;
//				if(row.getZContinuous() != true && params.isAntiDriftOn()) {
//					if((ad = driftCompMap.get(row)) == null) {
//						ad = params.getAntiDrift(row);
//						ad.setCallback(new AntiDrift.Callback() {
//							public void applyOffset( Vector3D offs) {
//								Vector3D appliedOffsset = new Vector3D(
//										offs.getX()*-core.getPixelSizeUm(),
//										offs.getY()*-core.getPixelSizeUm(),
//										-offs.getZ());
//
//								ij.IJ.log(String.format("TP %d view %d: Offset: %s", tp, rown, appliedOffsset.toString()));
//								row.translate(appliedOffsset);
//							}
//						});
//					}
//
//					ad.startNewStack();
//				}

				// Move the stage
//				runDevicesAtRow(core, setup, acqRows[step]);
				stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getR() );
				core.waitForSystem();


				// Setup the lasers
//				if(params.isIllumFullStack() && setup.getLaser() != null)
//					setup.getLaser().setPoweredOn(true);

				for( OutputHandler handler : handlers.values() )
					beginStack( tp, rown, handler );

				// Traverse Z stacks
				int noSlice = 0;
				for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep()) {
					// Move Z stacks
					stagePanel.goToZ( zStart );
					core.waitForSystem();

					// Wait for image synchronization
					core.waitForImageSynchro();

					// If the delay is setup, delay it
//					try {
//						Thread.sleep(params.getSettleDelay());
//					} catch(InterruptedException ie) {
//						return cleanAbort(params, liveOn, autoShutter, continuousThread);
//					}

					long now = System.currentTimeMillis();

					// Cameras
					if(arduinoSelected) {
						// Channel iteration

						int c = 0;
						for(String camera : cameras)
						{
							core.setCameraDevice( camera );
							core.waitForDevice( camera );

							for( ChannelItem channelItem : channelItems ) {
								// Snap an image
								if(setup.getArduino1() != null)
									setup.getArduino1().setSwitchState( channelItem.getLaser() );

								core.setExposure( channelItem.getValue().doubleValue() );
								core.waitForDevice( camera );

//								TaggedImage ti = snapImageCam(setup, false);
								TaggedImage ti = snapImage( core );
								ImageProcessor ip = ImageUtils.makeProcessor(ti);
								handleSlice(setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get(camera));

								addImageToAcquisition(frame, store, c, noSlice, zStart,
									positionItem, now - acqStart, ti);

								//						if(ad != null)
								//							addAntiDriftSlice(ad, ip);
								//						if(params.isUpdateLive())
								//							updateLiveImage(frame, ti);

								Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
								++c;
							}
						}

					}
					else
					{
						// Channel iteration
						int c = 0;
						for( ChannelItem channelItem : channelItems ) {
							// Snap an image
							//TaggedImage ti = snapImage(setup, !params.isIllumFullStack());
							core.setCameraDevice( channelItem.getName() );
							core.setExposure( channelItem.getValue().doubleValue() );
							core.waitForDevice( channelItem.getName() );

							core.setShutterOpen( channelItem.getLaser(), true );

							TaggedImage ti = snapImage(core);

							ImageProcessor ip = ImageUtils.makeProcessor(ti);


							// Handle the slice image
							handleSlice(setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get(channelItem.getName()));

							addImageToAcquisition(frame, store, c, noSlice, zStart,
									positionItem, now - acqStart, ti);

							//						if(ad != null)
							//							addAntiDriftSlice(ad, ip);
							//						if(params.isUpdateLive())
							//							updateLiveImage(frame, ti);
							Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );

							core.setShutterOpen( channelItem.getLaser(), false );
							++c;
						}
					}

//					double stackProg = Math.max(Math.min((zStart - start)/(end - start),1),0);

//					final Double progress = (params.getRows().length * timeSeq + step + stackProg) / (params.getRows().length * params.getTimeSeqCount());
//
//					SwingUtilities.invokeLater( new Runnable() {
//						public void run() {
//							params.getProgressListener().reportProgress(tp, rown, progress);
//						}
//					});

					if (Thread.interrupted()) {
						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

//						if(autoShutter)
//							core.setAutoShutter(true);

						for(String camera : cameras)
						{
							finalizeStack( tp, rown, handlers.get( camera ) );
							handlers.get(camera).finalizeAcquisition(true);
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

//				if(params.isIllumFullStack() && setup.getLaser() != null)
//					setup.getLaser().setPoweredOn(false);

//				if(ad != null) {
//					ad.finishStack();
//
//					driftCompMap.put(row, ad);
//				}

//				if(Thread.interrupted())
//					return cleanAbort(params, liveOn, autoShutter, continuousThread);

//				if(params.isContinuous() && !continuousThread.isAlive()) {
//					cleanAbort(params, liveOn, autoShutter, continuousThread);
//					throw new Exception(continuousThread.toString());
//				}

//				final Double progress = (double) (params.getRows().length * timeSeq + step + 1)
//						/ (params.getRows().length * params.getTimeSeqCount());
//
//				SwingUtilities.invokeLater(new Runnable() {
//					public void run() {
//						params.getProgressListener().reportProgress(tp, rown, progress);
//					}
//				});
				++step;
			}

			// End of looping rows

//			if (params.isContinuous()) {
//				continuousThread.interrupt();
//				continuousThread.join();
//			}

			if(timeSeq + 1 < timeSeqs) {
				double wait = (timeStep * (timeSeq + 1)) -
						(System.nanoTime() / 1e9 - acqBegan);

				if(wait > 0D)
					try {
						Thread.sleep((long)(wait * 1e3));
					} catch(InterruptedException ie) {
//						return cleanAbort(params, liveOn, autoShutter, continuousThread);
					}
				else
					core.logMessage("Behind schedule! (next seq in "
							+ wait + "s)");
			}
		}

//		setStatus( AcquisitionStatus.DONE );

		for( OutputHandler handler : handlers.values() )
		{
			handler.finalizeAcquisition( true );
		}

//		if(autoShutter)
//			core.setAutoShutter(true);

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

		return null;
	}

	private static void addImageToAcquisition( MMStudio studio, RewritableDatastore store, int channel,
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
					new String[] {"" + item.getX(), "" + item.getY(), "" + item.getR(), item.getZString()} ) );
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
	private static TaggedImage snapImage( CMMCore core ) throws InterruptedException
	{
		TaggedImage ti = null;

		while(ti == null)
		{
			try
			{
				core.snapImage();
				ti = core.getTaggedImage();
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
