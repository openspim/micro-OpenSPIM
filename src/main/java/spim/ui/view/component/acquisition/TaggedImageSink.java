package spim.ui.view.component.acquisition;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import ij.process.ImageProcessor;
import javafx.beans.property.LongProperty;
import mmcorej.TaggedImage;
import org.micromanager.PropertyMap;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.events.EventManager;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import spim.algorithm.DefaultAntiDrift;

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
	private final AcqWrapperEngine engine_;
	private final EventManager studioEvents_;
	private final int t_;
	private final int angle_;
	private final List<String> cameras_;
	private final HashMap<String, Integer> camChannels_;
	private final double x_, y_, theta_;
	private final TreeMap<Integer, Image>[] mpImages_;
	private final LongProperty processedImages_;
	private final DefaultAntiDrift antiDrift_;
	private final Integer antiDriftRefChannel_;
	private final String dirName_;
	private final Boolean onTheFly_;
	Thread savingThread;

	public TaggedImageSink(BlockingQueue<TaggedImage> queue,
						   Pipeline pipeline,
						   Datastore store,
						   AcqWrapperEngine engine,
						   EventManager studioEvents,
						   int t, int angle,
						   List<String> cameras, double x, double y, double theta,
						   TreeMap<Integer, Image>[] mpImages,
						   LongProperty processedImages,
						   DefaultAntiDrift antiDrift,
						   Integer antiDriftReferenceChannel, String dirName, Boolean onTheFly) {
		imageProducingQueue_ = queue;
		pipeline_ = pipeline;
		store_ = store;
		engine_ = engine;
		studioEvents_ = studioEvents;
		t_ = t;
		angle_ = angle;
		cameras_ = cameras;
		mpImages_ = mpImages;
		processedImages_ = processedImages;
		antiDrift_ = antiDrift;
		antiDriftRefChannel_ = antiDriftReferenceChannel - 1;
		dirName_ = dirName;
		onTheFly_ = onTheFly;

		camChannels_ = new HashMap<>( cameras_
				.stream().collect( Collectors.toMap( Function.identity(), c -> 0 ) ) );

		x_ = x;
		y_ = y;
		theta_ = theta;
	}

	public void start() {
		start(null, null);
	}

	public void stop() {
		if(savingThread != null) {
			try {
				savingThread.interrupt();
				savingThread.join(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		savingThread = null;
	}

	// sinkFullCallback is a way to stop production of images when/if the sink
	// can no longer accept images.
	public void start(final Runnable sinkFullCallback, final Runnable processMIP) {
		savingThread = new Thread("TaggedImage sink thread") {

			@Override
			public void run() {
				long t1 = System.currentTimeMillis();
				int imageCount = 0;
				try {
					if(antiDrift_ != null) {
						antiDrift_.startNewStack();
					}

					while (true) {
						TaggedImage tagged = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
						if (tagged != null) {
							if ( TaggedImageQueue.isPoison(tagged)) {
								// Acquisition has ended. Clean up under "finally"
//								System.out.println("IsPoison");
								break;
							}
							try {
								++imageCount;

								// dumpJSON(tagged, System.out);

//								System.out.println(tagged.tags.getJSONObject( "Summary" ).toString( 2 ));
//								System.out.println(imageCount);

								int slice = tagged.tags.getInt("SliceIndex");
								double exp = tagged.tags.getInt( "Exposure-ms" );
								double zPos = tagged.tags.getDouble( "ZPositionUm" );
								double xPos = tagged.tags.getDouble( "XPositionUm" );
								double yPos = tagged.tags.getDouble( "YPositionUm" );
								int ch = tagged.tags.getInt( "ChannelIndex" );
								String cam = tagged.tags.getString( "Camera" );
								String coreCam = tagged.tags.getString( "Core-Camera" );
								double zStep = tagged.tags.getJSONObject( "Summary" ).getDouble( "z-step_um" );
								int slices = tagged.tags.getJSONObject( "Summary" ).getInt( "Slices" );

								// Check which information is correct for choosing the number of total channels
								int channels = tagged.tags.getJSONObject( "Summary" ).getInt( "Channels" );
//								int channels = camChannels_.size();

								if(ch == 0) {
									// initialize cam channels
									camChannels_.keySet().forEach( d -> camChannels_.put(d, 0) );
								}

//								System.out.println(ch);
								int channel = ch;

								if(camChannels_.containsKey( cam ))
								{
									channel = camChannels_.get( cam );
								}

								DefaultImage image = new DefaultImage(tagged);

								Coords.Builder cb = Coordinates.builder();
								Coords coord = cb.p(angle_).t(t_).c(channel).z(slice).index("view", cameras_.indexOf( cam )).build();

								// Calling the OnTheFly processor
								if(onTheFly_) {
									engine_.onImageReceived(dirName_, coord, tagged);
									if(store_ instanceof RewritableDatastore) {
										if (slice == 0) ((RewritableDatastore)store_).deleteAllImages();
										coord = cb.p(angle_).t(0).c(channel).z(slice).index("view", cameras_.indexOf( cam )).build();
									}
								}

								Image img = image;
								Metadata md = img.getMetadata();
								Metadata.Builder mdb = md.copyBuilderPreservingUUID();
								PropertyMap ud = md.getUserData();
								ud = ud.copyBuilder().putDouble("Z-Step-um", zStep).putInteger("Slices", slices).putInteger("Channels", channels).build();
								String posName = angle_ + "";
								mdb = mdb.xPositionUm(xPos).yPositionUm(yPos).zPositionUm(zPos).elapsedTimeMs( exp );

								md = mdb.positionName(posName).userData(ud).build();
								img = img.copyWith(coord, md);

								try {
									pipeline_.insertImage(img);
									if(null != mpImages_ && mpImages_.length > ch)
										mpImages_[ch].put(slice, img);
								}
								catch (PipelineErrorException e) {
									// TODO: make showing the dialog optional.
									// TODO: allow user to cancel acquisition from
									// here.
									ReportingUtils.showError(e,
											"There was an error in processing images.");
									pipeline_.clearExceptions();
								}

								ImageProcessor ip = ImageUtils.makeProcessor( tagged );

								if(camChannels_.containsKey( cam ))
								{
									camChannels_.put( cam, channel + 1 );
								}

								if(antiDriftRefChannel_ == ch && antiDrift_ != null) {
									antiDrift_.addXYSlice( ip );
								}

								processedImages_.set(processedImages_.get() + 1);
							}
							catch (OutOfMemoryError e) {
								handleOutOfMemory(e, sinkFullCallback);
								break;
							}
							catch (DatastoreFrozenException ex) {
								pipeline_.clearExceptions();
								break;
							}
							catch (RuntimeException e) {
								ReportingUtils.logMessage("Runtime exception");
								pipeline_.clearExceptions();
								break;
							}
						}
					}
				} catch (Exception ex2) {
					ReportingUtils.logError(ex2);
				} finally {
					pipeline_.halt();
					ReportingUtils.logMessage("The pipeline is halted and the acquisition ended");
					studioEvents_.post(
							new DefaultAcquisitionEndedEvent(store_, engine_));
				}
				long t2 = System.currentTimeMillis();
				ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");
//				System.out.println("Total Images: " + processedImages_.get());
				if(antiDrift_ != null) {
					antiDrift_.updateOffset( antiDrift_.finishStack() );
				}
				processMIP.run();
			}
		};
		savingThread.start();
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
