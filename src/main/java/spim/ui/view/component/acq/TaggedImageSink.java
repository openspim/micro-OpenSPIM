package spim.ui.view.component.acq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import ij.process.ImageProcessor;
import mmcorej.TaggedImage;
import org.micromanager.PropertyMap;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.events.EventManager;
import org.micromanager.events.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import spim.hardware.SPIMSetup;
import spim.io.OutputHandler;

/**
 * This object spawns a new thread that receives images from the acquisition
 * engine and runs them through a Pipeline to the Datastore. It's also
 * responsible for posting the AcquisitionEndedEvent, which it recognizes when
 * it receives the TaggedImageQueue.POISON object.
 * Functionally this is just glue code between the old acquisition engine and
 * the 2.0 API.
 *
 * @author arthur, modified by Chris Weisiger
 * modified: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2019
 */
public class TaggedImageSink {

	private final BlockingQueue<TaggedImage> imageProducingQueue_;
	private final Datastore store_;
	private final Pipeline pipeline_;
	private final AcquisitionEngine engine_;
	private final EventManager studioEvents_;
	private final int t_;
	private final int angle_;
	private final HashMap<String, OutputHandler > handlers_;
	private final HashMap<String, Integer> camChannels_;
	private final double x_, y_, theta_;
	private final ArrayList<Image>[] mpImages_;

	public TaggedImageSink(BlockingQueue<TaggedImage> queue,
			Pipeline pipeline,
			Datastore store,
			AcquisitionEngine engine,
			EventManager studioEvents,
			int t, int angle,
			HashMap<String, OutputHandler > handlers, double x, double y, double theta, ArrayList<Image>[] mpImages ) {
		imageProducingQueue_ = queue;
		pipeline_ = pipeline;
		store_ = store;
		engine_ = engine;
		studioEvents_ = studioEvents;
		t_ = t;
		angle_ = angle;
		handlers_ = handlers;
		mpImages_ = mpImages;

		camChannels_ = new HashMap<>( handlers_.keySet()
				.stream().collect( Collectors.toMap( Function.identity(), c -> 0 ) ) );

		x_ = x;
		y_ = y;
		theta_ = theta;
	}

	public void start() {
		start(null, null);
	}

	// sinkFullCallback is a way to stop production of images when/if the sink
	// can no longer accept images.
	public void start(final Runnable sinkFullCallback, final Runnable processMIP) {
		Thread savingThread = new Thread("TaggedImage sink thread") {

			@Override
			public void run() {
				long t1 = System.currentTimeMillis();
				int imageCount = 0;
				try {
					while (true) {
						TaggedImage tagged = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
						if (tagged != null) {
							if ( TaggedImageQueue.isPoison(tagged)) {
								// Acquisition has ended. Clean up under "finally"
								System.out.println("IsPoison");
								break;
							}
							try {
								++imageCount;
								// dumpJSON(tagged, System.out);

//								System.out.println(tagged.tags.toString( 2 ));
//								System.out.println(imageCount);
								int slice = tagged.tags.getInt("SliceIndex");
								double exp = tagged.tags.getInt( "Exposure-ms" );
								double zPos = tagged.tags.getDouble( "ZPositionUm" );
								double xPos = tagged.tags.getDouble( "XPositionUm" );
								double yPos = tagged.tags.getDouble( "YPositionUm" );
								int ch = tagged.tags.getInt( "ChannelIndex" );
								String cam = tagged.tags.getString( "Core-Camera" );
								double zStep = tagged.tags.getJSONObject( "Summary" ).getDouble( "z-step_um" );

								if(ch == 0) {
									// initialize cam channels
									camChannels_.keySet().forEach( d -> camChannels_.put(d, 0) );
								}

//								System.out.println(ch);

								if(handlers_.containsKey( cam ))
								{
									int channel = camChannels_.get(cam);

									handlers_.get( cam ).processSlice( t_, angle_, ( int ) exp, channel, ImageUtils.makeProcessor( tagged ),
											x_,
											y_,
											zPos,
											theta_,
											System.currentTimeMillis() - t1 );

									camChannels_.put( cam, channel + 1 );
								}

								DefaultImage image = new DefaultImage(tagged);

								Coords.Builder cb = Coordinates.builder();
								Coords coord = cb.p(angle_).t(t_).c(ch).z(slice).build();
								Image img = image;
								Metadata md = img.getMetadata();
								Metadata.Builder mdb = md.copyBuilderPreservingUUID();
								PropertyMap ud = md.getUserData();
								ud = ud.copyBuilder().putDouble("Z-Step-um", zStep).build();
								String posName = angle_ + "";
								mdb = mdb.xPositionUm(xPos).yPositionUm(yPos).zPositionUm( zPos ).elapsedTimeMs( exp );

								md = mdb.positionName(posName).userData(ud).build();
								img = img.copyWith(coord, md);

								try {
									pipeline_.insertImage(img);
									mpImages_[ch].add(img);
								}
								catch (PipelineErrorException e) {
									// TODO: make showing the dialog optional.
									// TODO: allow user to cancel acquisition from
									// here.
									ReportingUtils.showError(e,
											"There was an error in processing images.");
									pipeline_.clearExceptions();
								}
							}
							catch (OutOfMemoryError e) {
								handleOutOfMemory(e, sinkFullCallback);
								break;
							}
						}
					}
				} catch (Exception ex2) {
					ReportingUtils.logError(ex2);
				} finally {
					pipeline_.halt();
					studioEvents_.post(
							new DefaultAcquisitionEndedEvent(store_, engine_));
				}
				long t2 = System.currentTimeMillis();
				ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");
				processMIP.run();
			}
		};
		savingThread.start();
	}

	private static void handleSlice( SPIMSetup setup, int exp, int channel, double start, int time, int angle, ImageProcessor ip,
			OutputHandler handler) throws Exception {
		if(null != handler)
			handler.processSlice(time, angle, exp, channel, ip, setup.getXStage().getPosition(),
					setup.getYStage().getPosition(),
					setup.getZStage().getPosition(),
					setup.getAngle(),
					System.nanoTime() / 1e9 - start);
	}

	// Never called from EDT
	private void handleOutOfMemory(final OutOfMemoryError e,
			Runnable sinkFullCallback)
	{
		ReportingUtils.logError(e);
		if (sinkFullCallback != null) {
			sinkFullCallback.run();
		}

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JOptionPane.showMessageDialog(null,
						"Out of memory to store images: " + e.getMessage(),
						"Out of image storage memory", JOptionPane.ERROR_MESSAGE);
			}
		});
	}
}
