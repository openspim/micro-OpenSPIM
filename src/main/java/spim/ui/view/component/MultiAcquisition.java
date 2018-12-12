package spim.ui.view.component;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.internal.utils.MMScriptException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: December 2018
 */
public class MultiAcquisition
{
	private final CMMCore core;
	private final List<String> cameras;
	private Thread captureThread;
	private volatile boolean done = false, shutterOpen, autoShutter;
	private ConcurrentHashMap<String, TaggedImage> map = new ConcurrentHashMap<>();


	public MultiAcquisition( CMMCore core, List<String> cameras )
	{
		this.core = core;
		this.cameras = cameras;
	}

	public List< Image > getImages() {
		List< Image > images = new ArrayList<>(  );

		for(TaggedImage img : map.values())
		{
			try
			{
				images.add( new DefaultImage(img) );
			}
			catch ( JSONException e )
			{
				e.printStackTrace();
			}
			catch ( MMScriptException e )
			{
				e.printStackTrace();
			}
		}
		return images;
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
					core.startSequenceAcquisition( camera, Integer.MAX_VALUE, 200, false );
			}
		}

		if(null == captureThread) {
			captureThread = new Thread( new Runnable()
			{
				@Override public void run()
				{
					try {
						while ( !done )
						{
							if ( core.getRemainingImageCount() > 0 )
							{
//								System.out.println( core.getRemainingImageCount() );
								TaggedImage timg = core.popNextTaggedImage();
								String camera = ( String ) timg.tags.get( "Camera" );
								map.put( camera, timg );
							}
							Thread.sleep( 10 );
						}
					} catch ( Exception e ) {
						System.out.println(e);
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
}
