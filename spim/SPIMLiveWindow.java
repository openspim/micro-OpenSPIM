package spim;

import ij.ImagePlus;

import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.Timer;
import java.util.TimerTask;

import mmcorej.CMMCore;

import org.micromanager.utils.ImageUtils;

public class SPIMLiveWindow extends MouseAdapter {

	private ImagePlus windowedImage;

	private String xyStageLabel;
	private String zStageLabel;
	private String thetaStageLabel;

	private boolean controlsEnabled;

	private int lastX, lastY;

	private CMMCore core;

	private Timer updateTimer;

	private final long updateDelayMSec = 100;

	public SPIMLiveWindow(CMMCore icore, String xys, String zs, String ts) {
		xyStageLabel = xys;
		zStageLabel = zs;
		thetaStageLabel = ts;

		core = icore;

		windowedImage = new ImagePlus("Live", ImageUtils.makeProcessor(icore));

		controlsEnabled = true;

		updateTimer = new Timer(true);

		lastX = lastY = -1;
	}

	public void updateImage() {
		try {
			windowedImage.setProcessor(ImageUtils.makeProcessor(core,
					core.getImage()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void show() {
		windowedImage.show();
		setControlsEnabled(getControlsEnabled());

		try {
			core.snapImage();
			core.startContinuousSequenceAcquisition(updateDelayMSec);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		updateTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateImage();
			}
		}, 0, updateDelayMSec); // TODO: Determine a good framerate.
	}

	public void hide() {
		updateTimer.cancel();

		try {
			core.stopSequenceAcquisition();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		setControlsEnabled(false);

		windowedImage.hide();
	}

	public void setControlsEnabled(boolean in) {
		if (in) {
			windowedImage.getCanvas().addMouseMotionListener(this);
			windowedImage.getCanvas().addMouseWheelListener(this);
			windowedImage.getCanvas().addMouseListener(this);
		} else {
			windowedImage.getCanvas().removeMouseMotionListener(this);
			windowedImage.getCanvas().removeMouseWheelListener(this);
			windowedImage.getCanvas().removeMouseListener(this);
		}
		controlsEnabled = in;
	}

	public boolean getControlsEnabled() {
		return controlsEnabled;
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		lastX = lastY = -1;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (!getControlsEnabled())
			return;

		if (lastX < 0 || lastY < 0) {
			lastX = e.getX();
			lastY = e.getY();
			return;
		}

		int deltaX = e.getX() - lastX;
		int deltaY = e.getY() - lastY;

		lastX = e.getX();
		lastY = e.getY();

		double umPerPixel = core.getPixelSizeUm();

		if (umPerPixel <= 0)
			umPerPixel = 1;

		if (!e.isControlDown()) {
			// X/Y motion.
			try {
				double oldX = core.getXPosition(xyStageLabel);
				double oldY = core.getYPosition(xyStageLabel);

				core.setXYPosition(xyStageLabel, oldX + deltaX * umPerPixel,
						oldY + deltaY * umPerPixel);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		} else {
			// Theta rotation.
			try {
				double oldT = core.getPosition(thetaStageLabel);

				// TODO: Determine deltaX scaling?
				core.setPosition(thetaStageLabel, oldT + deltaX);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}

		e.consume();
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent mwe) {
		if (!getControlsEnabled())
			return;

		int deltaZ = -mwe.getWheelRotation();

		double umPerPixel = core.getPixelSizeUm();

		if (umPerPixel <= 0)
			umPerPixel = 1;

		try {
			double oldZ = core.getPosition(zStageLabel);

			core.setPosition(zStageLabel, oldZ + deltaZ);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		mwe.consume();
	}

}
