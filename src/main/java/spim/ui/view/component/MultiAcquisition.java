package spim.ui.view.component;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.internal.utils.MMScriptException;

import java.util.ArrayList;
import java.util.Arrays;
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
	private final CMMCore core;
	private final List<String> cameras;
	private Thread captureThread;
	private volatile boolean done = false, shutterOpen, autoShutter;
	private ConcurrentHashMap<String, TaggedImage> map = new ConcurrentHashMap<>();
	private DefaultImageJConverter ipConverter = new DefaultImageJConverter();

	public MultiAcquisition( CMMCore core, List<String> cameras )
	{
		this.core = core;
		this.cameras = cameras;
	}

	public HashMap<String, ImageProcessor> getDefaultImages() {
		HashMap<String, ImageProcessor> hashMap = new HashMap<>(  );

		for(String key : map.keySet())
		{
			try
			{
				TaggedImage img = map.get(key);
				//new DefaultImage(img)
				ImageProcessor ip = ipConverter.createProcessor( new DefaultImage( img ) );

//				System.out.println(String.format( "%f, %f", ip.getHistogramMin(), ip.getHistogramMax() ));
				// ip.resetMinAndMax();
//				ip.setMinAndMax( 40, 60 );
//				System.out.println(String.format( "%f, %f", ip.getMin(), ip.getMax() ));
//				ip.setAutoThreshold( "Default" );
//				ip.autoThreshold();
//				adjustApply( ip );
//				System.out.println(String.format( "%f, %f", ip.getMin(), ip.getMax() ));
				hashMap.put( key, ip );
			}
			catch ( JSONException | MMScriptException e )
			{
				e.printStackTrace();
			}
		}
		return hashMap;
	}

	public HashMap<String, TaggedImage> getImages() {
		HashMap<String, TaggedImage> hashMap = new HashMap<>(  );

		for(String key : map.keySet())
		{
			hashMap.put( key, map.get(key) );
		}
		return hashMap;
	}

	private void adjustApply(ImageProcessor ip) {
		ImageStatistics stats = ip.getStatistics();
		int limit = stats.pixelCount/10;
		int[] histogram = stats.histogram;
		int threshold = stats.pixelCount/20;
//		System.out.println(	Arrays.toString(histogram));
		int i = -1;
		boolean found = false;
		int count;
		do {
			i++;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count> threshold;
		} while (!found && i<255);
		int hmin = i;
		i = 256;
		do {
			i--;
			count = histogram[i];
			if (count>limit) count = 0;
			found = count > threshold;
		} while (!found && i>0);
		int hmax = i;

		if (hmax>=hmin) {
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			if (min==max)
			{min=stats.min; max=stats.max;}
//			System.out.println(String.format( "hmax, hmin %f, %f", min, max ));
			ip.setMinAndMax( min, max );
		} else {
			ip.reset();
		}

		int bitDepth = ip.getBitDepth();
		if (bitDepth==32) {
			System.err.println("\"Apply\" does not work with 32-bit images");
			return;
		}
		int range = 256;
		if (bitDepth==16) {
			range = 65536;
			int defaultRange = ImagePlus.getDefault16bitRange();
			if (defaultRange>0)
				range = (int)Math.pow(2,defaultRange)-1;
		}
		int tableSize = bitDepth==16?65536:256;
		int[] table = new int[tableSize];
		int min = (int)ip.getMin();
		int max = (int)ip.getMax();


		for (i=0; i<tableSize; i++) {
			if (i<=min)
				table[i] = 0;
			else if (i>=max)
				table[i] = range-1;
			else
				table[i] = (int)(((double)(i-min)/(max-min))*range);
		}

		ip.snapshot();
		ip.applyTable(table);
		ip.reset(ip.getMask());
		ip.reset();
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

						while ( !done )
						{
							if ( core.getRemainingImageCount() > 0 )
							{
								try {
	//								System.out.println( core.getRemainingImageCount() );
									TaggedImage timg = core.popNextTaggedImage();
									String camera = ( String ) timg.tags.get( "Camera" );
									map.put( camera, timg );

									if(camera.equals( "DCam" ))
										map.put( camera + 1, timg );
								} catch ( Exception e ) {
									System.out.println(e);
								}
							}

//							core.clearCircularBuffer();

//							for(int i = 0; i < 4; i++) {
//								TaggedImage timg = core.getNBeforeLastTaggedImage(i);
//								String camera = ( String ) timg.tags.get( "Camera" );
//								map.put( camera, timg );
//							}
							try
							{
								Thread.sleep( 10 );
							}
							catch ( InterruptedException e )
							{
								e.printStackTrace();
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
}
