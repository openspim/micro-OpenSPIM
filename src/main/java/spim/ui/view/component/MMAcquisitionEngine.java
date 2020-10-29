package spim.ui.view.component;

import ij.process.ImageProcessor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.collections.ObservableList;
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
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.AcquisitionStartedEvent;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.UserProfileManager;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import spim.acquisition.Row;
import spim.algorithm.DefaultAntiDrift;
import spim.controller.AntiDriftController;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.OMETIFFHandler;
import spim.io.OpenSPIMSinglePaneTiffSeries;
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

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2019
 */
public class MMAcquisitionEngine
{
	static volatile boolean done;
	static Thread captureThread;
	static volatile boolean stopRequest = false;

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
	 * @param savingFormatValue the value set from { "None", "Separate the channel dimension", "Image stack (include multi-channel)" }
	 * @param saveMIP Save Maximum Intensity Projection or not
	 * @param antiDrift Anti-Drift function used or not
	 * @throws Exception the exception
	 */
	@SuppressWarnings("Duplicates")
	public static void performAcquisition( Studio studio, SPIMSetup setup, StagePanel stagePanel, Rectangle roiRectangle, int timeSeqs, ObservableList< TimePointItem > timePointItems, DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems, LongProperty processedImages, boolean bSave, Object savingFormatValue, boolean saveMIP, boolean antiDrift ) throws Exception
	{
		final Studio frame = studio;

		if(frame == null) return;

		boolean liveOn = false;
//		RewritableDatastore store = null;
		Datastore store = null;
		DisplayWindow display = null;

		if(frame != null) {
			liveOn = frame.live().getIsLiveModeOn();
			if(liveOn)
				frame.live().setLiveMode(false);

			if(bSave) {
				DefaultDatastore result = new DefaultDatastore(frame);
				result.setStorage(new OpenSPIMSinglePaneTiffSeries(result, output.getAbsolutePath(), acqFilenamePrefix, true));
				store = result;
			} else {
				store = frame.data().createRewritableRAMDatastore();
			}
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

		boolean saveSpim = true;
		if(savingFormatValue != null && savingFormatValue.equals( "None" ))
			saveSpim = false;

		if(bSave && saveSpim)
		{
			boolean separateChannel = false;
			if(savingFormatValue != null && savingFormatValue.equals( "Separate the channel dimension" ))
				separateChannel = true;

			File outFolder = new File(output, acqFilenamePrefix + "-spim");

			if (!outFolder.exists() && !outFolder.mkdirs()) {
				System.err.println( "Couldn't create output directory " + outFolder.getAbsolutePath() );
			}

			for (String camera : cameras) {
				if (camera.startsWith("Multi")) {
					int chSize = arduinoSelected ? channelItems.size() : (int) channelItems.stream().filter(c -> c.getName().equals(camera)).count();
					OutputHandler handler = new OMETIFFHandler(
							core, outFolder, acqFilenamePrefix + "_" + camera + "_",
							//what is the purpose of defining parameters and then passing null anyway?
							acqRows, chSize * multis.size(), timeSeqs, 1, smb.userData(pm.build()).build(), false, separateChannel, saveMIP);

					handlers.put(camera, handler);
				} else {
					int chSize = arduinoSelected ? channelItems.size() : (int) channelItems.stream().filter(c -> c.getName().equals(camera)).count();
					OutputHandler handler = new OMETIFFHandler(
							core, outFolder, acqFilenamePrefix + "_" + camera + "_",
							//what is the purpose of defining parameters and then passing null anyway?
							acqRows, chSize, timeSeqs, 1, smb.userData(pm.build()).build(), false, separateChannel, saveMIP);

					handlers.put(camera, handler);
				}
			}
		}

		// For checking Max intensity projection is needed or not
		if(!bSave) {
			output = null;
			saveMIP = false;
		}

		if(!saveMIP) acqFilenamePrefix = null;

		executeNormalAcquisition(setup, frame, store, stagePanel, currentCamera, cameras, output, acqFilenamePrefix, handlers,
				timePointItems, positionItems, channelItems, currentTP, waitSeconds, arduinoSelected, processedImages, acqBegan, antiDrift);
	}

	@SuppressWarnings("Duplicates")
	private static void executeNormalAcquisition(SPIMSetup setup, final Studio frame, Datastore store,
			StagePanel stagePanel, String currentCamera, List<String> cameras, File outFolder, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, final boolean antiDrift) throws Exception
	{

		long acqStart = System.currentTimeMillis();

		// Dynamic timeline
		runNormalSmartImagingMMAcq(setup, frame, store, stagePanel, currentCamera, cameras,
				outFolder, acqFilenamePrefix, handlers,
				timePointItems, positionItems, channelItems, currentTP, waitSeconds, arduinoSelected, processedImages, acqBegan, acqStart, antiDrift);

		finalize(true, setup, currentCamera, cameras, frame, 0, 0, handlers, store);
	}

	@SuppressWarnings("Duplicates")
	private static void runNormalSmartImagingMMAcq(SPIMSetup setup, final Studio frame, Datastore store,
			StagePanel stagePanel, String currentCamera, List<String> cameras, File outFolder, String acqFilenamePrefix, HashMap<String, OutputHandler> handlers,
			ObservableList< TimePointItem > timePointItems, ObservableList< PositionItem > positionItems, List< ChannelItem > channelItems,
			DoubleProperty currentTP, DoubleProperty waitSeconds, boolean arduinoSelected,
			LongProperty processedImages, final double acqBegan, long acqStart, final boolean antiDrift) throws Exception
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
				driftCompMap.put(positionItem, new DefaultAntiDrift(10));
		}

		AcqWrapperEngine engine = new AcqWrapperEngine( setup, frame, store, currentCamera, cameras, outFolder, acqFilenamePrefix, handlers, channelItems, arduinoSelected, processedImages, driftCompMap );

		mainLoop:
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

						if(driftCompMap != null) {
							Vector3D offset = driftCompMap.get(positionItem).getUpdatedOffset();
							double xOffset = offset.getX() * core.getPixelSizeUm();
							double yOffset = offset.getY() * core.getPixelSizeUm();
							System.out.println("PixelSizeUm = " + core.getPixelSizeUm());
							System.out.println("Anti-Drift used offset only X:" + xOffset + " Y:" + yOffset + " Z:" + offset.getZ());
							positionItem.setX(positionItem.getX() + xOffset);
							positionItem.setY(positionItem.getY() + yOffset);
						}

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
								break mainLoop;
							}
						}

						System.out.println("MMAcquisition finished");

						if(setup.getArduino1() != null)
							setup.getArduino1().setSwitchState( "0" );

						for( OutputHandler handler : handlers.values() )
						{
							finalizeStack( tp, rown, handler );
						}

						if(stopRequest) {
							engine.stop(true);
							break mainLoop;
						}

						++step;
					}

					double wait = tpItem.getIntervalSeconds();

					if(wait > 0D) {
						System.err.println("Interval delay. (next seq in " + wait + "s)");

						for(int i = 0; i < (int) wait; i++)
						{
							waitSeconds.set(wait - i);
							++passedTimePoints;
							currentTP.set( 1 / totalTimePoints * passedTimePoints );
							try
							{
								Thread.sleep( ( long ) ( 1e3 ) );
							}
							catch ( InterruptedException ie )
							{
								finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
								break mainLoop;
							}

							if(stopRequest) {
								engine.stop(true);
								waitSeconds.set(-1);
								break mainLoop;
							}
						}
						waitSeconds.set(-1);
					}
					++timePoints;

					currentTP.set( 1 / totalTimePoints * passedTimePoints );
				}
			}
			else if(tpItem.getType().equals( TimePointItem.Type.Wait ))
			{
				double wait = tpItem.getIntervalSeconds();

				if(wait > 0D) {
					System.err.println("Wait delay. (next seq in " + wait + "s)");

					for(int i = 0; i < (int) wait; i++)
					{
						waitSeconds.set(wait - i);
						++passedTimePoints;
						currentTP.set( 1 / totalTimePoints * passedTimePoints );
						try
						{
							Thread.sleep( ( long ) ( 1e3 ) );
						}
						catch ( InterruptedException ie )
						{
							finalize( false, setup, currentCamera, cameras, frame, 0, 0, handlers, store );
							break mainLoop;
						}

						if(stopRequest) {
							engine.stop(true);
							waitSeconds.set(-1);
							break mainLoop;
						}
					}
					waitSeconds.set(-1);
				}
			}
		}

		engine.exit();
	}

	private static int addSlice(TaggedImage ti, SPIMSetup setup, int exp, int ch, double acqBegan, int tp, int step, OutputHandler handler,
			Studio frame, Datastore store, int noSlice, double zStart, PositionItem positionItem, long ms, LongProperty processedImages) throws Exception
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

	private static void finalize(boolean finalizeStack, SPIMSetup setup, final String currentCamera, List<String> cameras, final Studio frame, final int tp, final int rown, HashMap<String, OutputHandler> handlers, Datastore store) throws Exception
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
