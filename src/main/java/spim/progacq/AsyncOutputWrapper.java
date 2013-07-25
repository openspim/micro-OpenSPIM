package spim.progacq;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.lang.InterruptedException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.ClosedByInterruptException;

import org.micromanager.utils.ReportingUtils;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import mmcorej.TaggedImage;

public class AsyncOutputWrapper implements AcqOutputHandler, UncaughtExceptionHandler {
	private class IPC {
		public IPC(ImageProcessor ip, double x, double y, double z, double t, double dt) {
			this.ip = ip;
			this.x = x;
			this.y = y;
			this.z = z;
			this.t = t;
			this.dt = dt;
		}

		public ImageProcessor ip;
		public double x, y, z, t, dt;
	}

	private AcqOutputHandler handler;
	private Thread writerThread;
	private BlockingQueue<IPC> queue;
	private Exception rethrow;

	private Runnable writerOp = new Runnable() {
		@Override
		public void run() {
			while(!Thread.interrupted()) try {
				writeAll();
			} catch (Exception e) {
				if(!(e instanceof InterruptedException) &&
				   !(e instanceof ClosedByInterruptException) ) {
					ij.IJ.log("Async writer failed!");
					throw new RuntimeException(e);
				} else if(e instanceof ClosedByInterruptException) {
					ij.IJ.log("Warning: asynchronous writer may have been cancelled before completing. (" + queue.size() + ")");
				}
			}
		}
	};

	public AsyncOutputWrapper(AcqOutputHandler handlerRef, long cap) {
		handler = handlerRef;
		queue = new LinkedBlockingQueue<IPC>((int) cap);
		writerThread = new Thread(writerOp, "Async Output Handler Thread");
		writerThread.setPriority(Thread.MIN_PRIORITY);
		writerThread.setUncaughtExceptionHandler(this);

		rethrow = null;
		writerThread.start();
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		if(rethrow != null)
			throw rethrow;

		writeAll();

		synchronized(handler) {
			return handler.getImagePlus();
		}
	}

	@Override
	public void beginStack(int axis) throws Exception {
		if(rethrow != null)
			throw rethrow;

		synchronized(handler) {
			handler.beginStack(axis);
		}
	}

	@Override
	public void processSlice(ImageProcessor ip, double X, double Y, double Z,
			double theta, double deltaT) throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC store = new IPC(ip, X, Y, Z, theta, deltaT);
		if(!queue.offer(store)) {
			writeNext();
			queue.put(store);
		}
	}

	@Override
	public void finalizeStack(int depth) throws Exception {
		if(rethrow != null)
			throw rethrow;

		writeAll();

		synchronized(handler) {
			handler.finalizeStack(depth);
		}
	}

	@Override
	public void finalizeAcquisition() throws Exception {
		if(rethrow != null)
			throw rethrow;

		writeAll();
		writerThread.interrupt();
		writerThread.join();

		synchronized(handler) {
			handler.finalizeAcquisition();
		}
	}

	private void writeNext() throws Exception {
		if(rethrow != null)
			throw rethrow;

		IPC write = queue.poll();
		if(write != null)
		{
			synchronized(handler) {
				handler.processSlice(write.ip, write.x, write.y, write.z, write.t, write.dt);
			}
		}
	}

	private void writeAll() throws Exception {
		if(rethrow != null)
		{
			if(Thread.currentThread() != writerThread)
				throw rethrow;
			else
				return;
		};

		while(!queue.isEmpty())
			writeNext();
	}

	@Override
	public void uncaughtException(Thread thread, Throwable exc) {
		if(thread != writerThread)
			throw new Error("Unexpected exception mis-caught.", exc);

		if(!(exc instanceof Exception))
		{
			ReportingUtils.logException("Non-exception throwable " + exc.toString() + " caught from writer thread. Wrapping.", exc);
			exc = new Exception("Wrapped throwable; see core log for details: " + exc.getMessage(), exc);
		};

		rethrow = (Exception)exc;
	}
}
