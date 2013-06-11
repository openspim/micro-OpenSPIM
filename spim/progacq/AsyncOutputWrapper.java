package spim.progacq;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.lang.InterruptedException;
import java.nio.channels.ClosedByInterruptException;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import mmcorej.TaggedImage;

public class AsyncOutputWrapper implements AcqOutputHandler {
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

	private Runnable writerOp = new Runnable() {
		@Override
		public void run() {
			while(!Thread.interrupted()) try {
				writeAll();
			} catch (Exception e) {
				if(!(e instanceof InterruptedException) &&
				   !(e instanceof ClosedByInterruptException) ) {
					ij.IJ.log("Async writer failed!");
					ij.IJ.handleException(e);
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

		writerThread.start();
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		writeAll();
		return handler.getImagePlus();
	}

	@Override
	public void beginStack(int axis) throws Exception {
		handler.beginStack(axis);
	}

	@Override
	public void processSlice(ImageProcessor ip, double X, double Y, double Z,
			double theta, double deltaT) throws Exception {
		IPC store = new IPC(ip, X, Y, Z, theta, deltaT);
		if(!queue.offer(store)) {
			writeNext();
			queue.put(store);
		}
	}

	@Override
	public void finalizeStack(int depth) throws Exception {
		writeAll();
		handler.finalizeStack(depth);
	}

	@Override
	public void finalizeAcquisition() throws Exception {
		writeAll();
		writerThread.interrupt();
		writerThread.join();

		handler.finalizeAcquisition();
	}

	private void writeNext() throws Exception {
		synchronized(queue) {
			IPC write = queue.poll();
			if(write != null)
				handler.processSlice(write.ip, write.x, write.y, write.z, write.t, write.dt);
		}
	}
	
	private void writeAll() throws Exception {
		while(!queue.isEmpty())
			writeNext();
	}
}
