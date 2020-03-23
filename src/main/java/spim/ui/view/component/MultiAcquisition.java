package spim.ui.view.component;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: December 2018
 */
public class MultiAcquisition
{
	private static final int MAX_DISPLAY_HISTORY = 20;
	private Thread captureThread;
	private volatile boolean done = false, shutterOpen, autoShutter, camera;
	private ConcurrentHashMap<String, TaggedImage> map = new ConcurrentHashMap<>();
	private final ArrayList<Long> displayUpdateTimes_;
	private double exposureMs_;

	private CMMCore core;
	private List<String> cameras;

	public MultiAcquisition( CMMCore core, List<String> cameras )
	{
		this.core = core;
		this.cameras = cameras;
		displayUpdateTimes_ = new ArrayList<Long>();
	}

	public void setSetup( CMMCore core, List<String> cameras )
	{
		this.core = core;
		this.cameras = cameras;
	}

	public HashMap<String, TaggedImage> getImages(double exposure) {
		exposureMs_ = exposure;
		onImagesCalled();
		HashMap<String, TaggedImage> hashMap = new HashMap<>(  );

		for(String key : map.keySet())
		{
			hashMap.put( key, map.get(key) );
		}
		return hashMap;
	}

	public void onImagesCalled() {
		synchronized(displayUpdateTimes_) {
			displayUpdateTimes_.add(System.currentTimeMillis());
			while (displayUpdateTimes_.size() > MAX_DISPLAY_HISTORY) {
				// Limit the history we maintain.
				displayUpdateTimes_.remove(displayUpdateTimes_.get(0));
			}
		}
	}

	public void start() throws Exception
	{
		done = false;

		autoShutter = core.getAutoShutter();
		shutterOpen = core.getShutterOpen();

		if (autoShutter) {
			core.setAutoShutter(false);
			if (!shutterOpen) {
				core.setShutterOpen(true);
			}
		}

		synchronized(displayUpdateTimes_) {
			displayUpdateTimes_.clear();
		}

		if(cameras.size() == 1)
			core.startContinuousSequenceAcquisition(0);
		else
		{
			for(String camera : cameras)
			{
				if(!core.isSequenceRunning(camera))
					core.prepareSequenceAcquisition( camera );
			}

			for(String camera : cameras)
			{
				if(!core.isSequenceRunning(camera))
					core.startSequenceAcquisition( camera, Integer.MAX_VALUE, 0, false );
			}
		}

		if(null == captureThread) {
			captureThread = new Thread( new Runnable()
			{
				@Override public void run()
				{
					while ( !done )
					{
						waitForNextDisplay();
						for(int i = 0; i < cameras.size(); i++) {
							try {
								TaggedImage timg = core.getLastTaggedImage( i );
								String camera = ( String ) timg.tags.get( "Camera" );
								map.put( camera, timg );
							} catch ( Exception e ) {
							}
						}

						if(!core.isSequenceRunning() && core.getRemainingImageCount() > 0) {
							try
							{
								core.popNextImage();
							}
							catch ( Exception e )
							{
							}
						}
					}
				}
			} );
			captureThread.start();
		}
	}

	public void stop() throws Exception
	{
		done = true;

		if(captureThread != null)
			captureThread.join();

		captureThread = null;

		core.setShutterOpen(shutterOpen);
		core.setAutoShutter(autoShutter);

		for(String camera : cameras)
		{
			if(core.isSequenceRunning(camera))
				core.stopSequenceAcquisition( camera );
		}
	}

	private void waitForNextDisplay() {
		long shortestWait = -1;
		// Determine our sleep time based on image display times (a.k.a. the
		// amount of time passed between PixelsSetEvents).
		synchronized(displayUpdateTimes_) {
			for (int i = 0; i < displayUpdateTimes_.size() - 1; ++i) {
				long delta = displayUpdateTimes_.get(i + 1) - displayUpdateTimes_.get(i);
				if (shortestWait == -1) {
					shortestWait = delta;
				}
				else {
					shortestWait = Math.min(shortestWait, delta);
				}
			}
		}
		// Sample faster than shortestWait because glitches should not cause the
		// system to permanently bog down; this allows us to recover if we
		// temporarily end up displaying images slowly than we normally can.
		// On the other hand, we don't want to sample faster than the exposure
		// time, or slower than 2x/second.
		int rateLimit = (int) Math.max(33, exposureMs_);
		int waitTime = (int) Math.min(500, Math.max(rateLimit, shortestWait * .75));
		try {
			Thread.sleep(waitTime);
		}
		catch (InterruptedException e) {}
	}
}
