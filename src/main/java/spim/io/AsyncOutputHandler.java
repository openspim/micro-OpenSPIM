package spim.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.awt.Color;
import java.lang.InterruptedException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.ClosedByInterruptException;

import org.micromanager.internal.utils.ReportingUtils;

import ij.ImagePlus;
import ij.process.ImageProcessor;

public class AsyncOutputHandler implements OutputHandler, UncaughtExceptionHandler {
	private static class IPC {
		public static enum Type {
			StartStack,
			Slice,
			EndStack,
		}

		public IPC(Type type, ImageProcessor ip, double x, double y, double z, double t, double dt, int time, int angle, int exp, int c) {
			this.type = type;
			this.ip = ip;
			this.x = x;
			this.y = y;
			this.z = z;
			this.t = t;
			this.dt = dt;
			this.time = time;
			this.angle = angle;
			this.exp = exp;
			this.c = c;
		}

		public ImageProcessor ip;
		public double x, y, z, t, dt;
		public int time, angle, c, exp;
		public Type type;
	}

	private OutputHandler handler;
	private Thread writerThread, monitorThread;
	private BlockingQueue<IPC> queue;
	private Exception rethrow;

	private volatile boolean finishing;
	private volatile boolean writing = false;

	private Runnable monitorOp = new Runnable() {
		@Override
		public void run() {
			ImageProcessor statusImg = new ij.process.ByteProcessor(256, 128);
			statusImg.setColor(Color.WHITE);
			statusImg.fill();
			statusImg.setColor(Color.BLACK);
			ImagePlus imp = new ImagePlus("Async Status", statusImg);
			imp.show();

			while(!Thread.interrupted()) {
				int n = AsyncOutputHandler.this.queue.size();
				int m = AsyncOutputHandler.this.queue.remainingCapacity();
				String statStr = n + "/" + (n + m) + (AsyncOutputHandler.this.writing ? " (Writing)" : " (Idle)");

				int y = 16 + (int) (((double)m / (double)(n + m)) * (statusImg.getHeight() - 16));

				statusImg.setColor(Color.WHITE);
				statusImg.copyBits(statusImg, -1, 0, ij.process.Blitter.COPY);
				statusImg.drawLine(statusImg.getWidth() - 1, 0, statusImg.getWidth() - 1, statusImg.getHeight());
				statusImg.fill(new ij.gui.Roi(0, 0, statusImg.getWidth(), 16));
				if(AsyncOutputHandler.this.writing)
				{
					statusImg.setColor(Color.RED);
					statusImg.drawPixel(statusImg.getWidth() - 1, statusImg.getHeight() - 1);
				}
				statusImg.setColor(Color.BLACK);
				statusImg.drawPixel(statusImg.getWidth() - 1, y);
				statusImg.drawString(statStr, 4, 16, Color.WHITE);
				imp.updateAndDraw();

				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					break;
				}
			}

			imp.close();
		}
	};

	private Runnable writerOp = new Runnable() {
		@Override
		public void run() {
			try {
				while(!Thread.interrupted() && !AsyncOutputHandler.this.finishing)
				{
					Thread.sleep(100);
					handleNext();
				}

				handleAll();
			} catch (InterruptedException ie) {
				// Something under writeNext noticed the thread was being interrupted and whined.
				// Log a message/stack trace, but we're too mellow to actually throw a fit.
				ReportingUtils.logError(ie);
			} catch (ClosedByInterruptException cbie) {
				// The writing to disk was interrupted. This can be a serious problem; the last slice in queue probably wasn't
				// written correctly... May need to catch this earlier on; see writeNext?
				ij.IJ.log("Warning: asynchronous writer may have been cancelled before completing. (" + queue.size() + ")");
				ReportingUtils.logError(cbie);
			} catch (Exception e) {
				ij.IJ.log("Async writer failed!");
				throw new RuntimeException(e);
			}
		}
	};

	public AsyncOutputHandler( OutputHandler handlerRef, long cap, boolean monitor ) {
		finishing = false;
		handler = handlerRef;
		queue = new LinkedBlockingQueue<IPC>((int) cap);
		writerThread = new Thread(writerOp, "Async Output Handler Thread");
		writerThread.setPriority(Thread.MIN_PRIORITY);
		writerThread.setUncaughtExceptionHandler(this);

		rethrow = null;
		writerThread.start();

		if(monitor) {
			monitorThread = new Thread(monitorOp, "Async Output Monitor Daemon");
			monitorThread.setDaemon(true);
			monitorThread.start();
		} else {
			monitorThread = null;
		}
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		if(rethrow != null)
			throw rethrow;

		handleAll();

		synchronized(handler) {
			return handler.getImagePlus();
		}
	}

	@Override
	public void beginStack(int time, int angle) throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC store = new IPC( IPC.Type.StartStack, null, 0, 0, 0, 0, 0, time, angle, 0, 0 );
		if(!queue.offer(store)) {
			handleNext();
			queue.put(store);
		}
	}

	@Override
	public void processSlice(int time, int angle, int exp, int c, ImageProcessor ip, double X, double Y, double Z,
			double theta, double deltaT) throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC store = new IPC(IPC.Type.Slice, ip, X, Y, Z, theta, deltaT, time, angle, exp, c);
		if(!queue.offer(store)) {
			handleNext();
			queue.put(store);
		}
	}

	@Override
	public void finalizeStack(int time, int angle) throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC store = new IPC(IPC.Type.EndStack, null, 0, 0, 0, 0, 0, time, angle, 0, 0 );
		if(!queue.offer(store)) {
			handleNext();
			queue.put(store);
		}
	}

	@Override
	public void finalizeAcquisition(boolean bSuccess) throws Exception {

		if(rethrow != null)
			throw rethrow;

		finishing = true; // Tell the writer thread to finish up...
		writerThread.setPriority(Thread.MAX_PRIORITY); // ...and give it more CPU time.

		try {
			// Wait an hour before cancelling the output. Don't force an interrupt unless it's taking forever;
			// interrupts can mess up the output.
			writerThread.join(60 * 60 * 1000);
		} catch(InterruptedException ie) {
			ReportingUtils.logError(ie, "Couldn't keep waiting...");
		} finally {
			if(writerThread.isAlive()) {
				writerThread.interrupt();
				writerThread.join();
				handleAll();
			}

			if(monitorThread != null && monitorThread.isAlive()) {
				monitorThread.interrupt();
				monitorThread.join();
			}
		}

		synchronized(handler) {
			handler.finalizeAcquisition(bSuccess);
		}
	}

	private synchronized void handleNext() throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC write = queue.peek();
		if(write != null) {
			synchronized(handler) {
				writing = true;
				switch(write.type){
				case StartStack:
					handler.beginStack(write.time, write.angle);
					break;
				case Slice:
					handler.processSlice(write.time, write.angle, write.exp, write.c, write.ip, write.x, write.y, write.z, write.t, write.dt);
					break;
				case EndStack:
					handler.finalizeStack(write.time, write.angle);
					break;
				}
				writing = false;
			}
			queue.remove(write);
		}
	}

	private void handleAll() throws Exception {
		if(rethrow != null) {
			if(Thread.currentThread() != writerThread)
				throw rethrow;
			else
				return;
		};

		while(!queue.isEmpty())
			handleNext();
	}

	@Override
	public void uncaughtException(Thread thread, Throwable exc) {
		if(thread != writerThread)
			throw new Error("Unexpected exception mis-caught.", exc);

		if(!(exc instanceof Exception))
		{
			ReportingUtils.logError(exc, "Non-exception throwable " + exc.toString() + " caught from writer thread. Wrapping.");
			exc = new Exception("Wrapped throwable; see core log for details: " + exc.getMessage(), exc);
		};

		rethrow = (Exception)exc;
	}
}
