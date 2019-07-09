package spim.ui.view.component;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.collections.ObservableList;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ImageUtils;
import spim.acquisition.Row;
import spim.hardware.Device;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.io.OMETIFFHandler;
import spim.io.OutputHandler;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;

import java.io.File;
import java.util.ArrayList;
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

	public static ImagePlus performAcquisition ( SPIMSetup setup, StagePanel stagePanel, int timeSeqs, double timeStep, boolean arduinoSelected, File output, String acqFilenamePrefix, ObservableList<PositionItem> positionItems, List<ChannelItem> channelItems, LongProperty processedImages ) throws Exception
	{
		final MMStudio frame = MMStudio.getInstance();

		boolean liveOn = false;
		if(frame != null) {
			liveOn = frame.live().getIsLiveModeOn();
			if(liveOn)
				frame.live().setLiveMode(false);
		}

		final CMMCore core = frame.getCore();

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

		HashMap<String, OutputHandler> handlers = new HashMap<>(  );
		for(String camera : cameras) {
			OutputHandler handler = new OMETIFFHandler(
					core, output, acqFilenamePrefix + "_" + camera + "_",
					//what is the purpose of defining parameters and then passing null anyway?
					acqRows, channelItems.size(), timeSeqs, timeStep, 1, false);

//			handler = new AsyncOutputHandler(handler, (ij.IJ.maxMemory() - ij.IJ.currentMemory())/(core.getImageWidth()*core.getImageHeight()*core.getBytesPerPixel()*2), false);

			handlers.put( camera, handler );
		}

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
				stagePanel.goToPos( positionItem.getX(), positionItem.getY(), positionItem.getZStart(), positionItem.getR() );
				core.waitForSystem();


				// Setup the lasers
//				if(params.isIllumFullStack() && setup.getLaser() != null)
//					setup.getLaser().setPoweredOn(true);

				for(String camera : cameras)
					beginStack( tp, rown, handlers.get(camera) );

				// Traverse Z stacks
				for(double zStart = positionItem.getZStart(); zStart <= positionItem.getZEnd(); zStart += positionItem.getZStep()) {
					// Move Z stacks
//					setup.getZStage().setPosition(zStart);
					stagePanel.goToPos( positionItem.getX(), positionItem.getY(), zStart, positionItem.getR() );
					core.waitForSystem();

					// Wait for image synchronization
					core.waitForImageSynchro();

					// If the delay is setup, delay it
//					try {
//						Thread.sleep(params.getSettleDelay());
//					} catch(InterruptedException ie) {
//						return cleanAbort(params, liveOn, autoShutter, continuousThread);
//					}

					// Cameras
					if(arduinoSelected) {
						// Channel iteration

						for(String camera : cameras)
						{
							core.setCameraDevice( camera );
							core.waitForDevice( camera );

							int c = 0;
							for( ChannelItem channelItem : channelItems ) {
								// Snap an image
								if(setup.getArduino1() != null)
									setup.getArduino1().setSwitchState( channelItem.getLaser() );

								core.setExposure( channelItem.getValue().doubleValue() );
								core.waitForDevice( camera );

								TaggedImage ti = snapImageCam(setup, false);
								ImageProcessor ip = ImageUtils.makeProcessor(ti);
								handleSlice(setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handlers.get(camera));


//						if(ad != null)
//							addAntiDriftSlice(ad, ip);
//						if(params.isUpdateLive())
//							updateLiveImage(frame, ti);
								if(liveOn)
									frame.live().displayImage( new DefaultImage( ti ) );

								Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
								++c;
							}
						}

					}
					else
					{
						// Channel iteration
//						int c = 0;
//						for( ChannelItem channelItem : channelItems ) {
//							// Snap an image
//							//TaggedImage ti = snapImage(setup, !params.isIllumFullStack());
//
//							TaggedImage ti = snapImage(setup, false);
//
//							ImageProcessor ip = ImageUtils.makeProcessor(ti);
//
//
//							// Handle the slice image
//							handleSlice(setup, channelItem.getValue().intValue(), c, acqBegan, timeSeq, step, ip, handler);
//
//							//						if(ad != null)
//							//							addAntiDriftSlice(ad, ip);
//							//						if(params.isUpdateLive())
//							//							updateLiveImage(frame, ti);
//							Platform.runLater( () -> processedImages.set( processedImages.get() + 1 ) );
//							++c;
//						}
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

						return null;
					}
				}

				if(setup.getArduino1() != null)
					setup.getArduino1().setSwitchState( "0" );


				for(String camera : cameras)
					finalizeStack( tp, rown, handlers.get(camera) );

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

		for(String camera : cameras)
			handlers.get(camera).finalizeAcquisition(true);

//		if(autoShutter)
//			core.setAutoShutter(true);

		return null;
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

		handler.processSlice(exp, channel, time, angle, ip, setup.getXStage().getPosition(),
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
