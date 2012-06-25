package spim;

import ij.ImagePlus;

import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;

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
	
	public SPIMLiveWindow(CMMCore icore, String xys, String zs, String ts) {
		xyStageLabel = xys;
		zStageLabel = zs;
		thetaStageLabel = ts;
		
		core = icore;

		windowedImage = new ImagePlus("Live", ImageUtils.makeProcessor(icore));
		
		controlsEnabled = true;
		
		lastX = lastY = -1;
	}
	
	public void show() {
		windowedImage.show();
		setControlsEnabled(getControlsEnabled());
	}
	
	public void setControlsEnabled(boolean in) {
		if(in) {
			windowedImage.getCanvas().addMouseMotionListener(this);
			windowedImage.getCanvas().addMouseWheelListener(this);
		} else {
			windowedImage.getCanvas().removeMouseMotionListener(this);
			windowedImage.getCanvas().removeMouseWheelListener(this);
		}
		controlsEnabled = in;
	}
	
	public boolean getControlsEnabled() { 
		return controlsEnabled;
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(!getControlsEnabled())
			return;
		
		if(lastX < 0 || lastY < 0) {
			lastX = e.getX();
			lastY = e.getY();
			return;
		};
		
		int deltaX = e.getX() - lastX;
		int deltaY = e.getY() - lastY;
		
		double umPerPixel = core.getPixelSizeUm();
		
		if(umPerPixel <= 0)
			umPerPixel = 1;
		
		if(!e.isControlDown()) {
			// X/Y motion.
			try {
				double oldX = core.getXPosition(xyStageLabel);
				double oldY = core.getYPosition(xyStageLabel);
				
				core.setXYPosition(xyStageLabel, oldX + deltaX*umPerPixel, oldY + deltaY*umPerPixel);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		} else {
			// Theta rotation.
			try {
				double oldT = core.getPosition(thetaStageLabel);
				
				// TODO: Determine deltaX scaling?
				core.setPosition(thetaStageLabel, oldT + deltaX);
			} catch(Exception e1) {
				e1.printStackTrace();
			}
		}
		
		e.consume();
	}
	
	@Override
	public void mouseWheelMoved(MouseWheelEvent mwe) {
		if(!getControlsEnabled())
			return;
		
		int deltaZ = -mwe.getWheelRotation();
		
		double umPerPixel = core.getPixelSizeUm();
		
		if(umPerPixel <= 0)
			umPerPixel = 1;
		
		try {
			double oldZ = core.getPosition(zStageLabel);
			
			core.setPosition(zStageLabel, oldZ + deltaZ);
		} catch(Exception e1) {
			e1.printStackTrace();
		}
		
		mwe.consume();
	}

}
