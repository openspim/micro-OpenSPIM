package spim.progacq;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JOptionPane;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

public class ManualAntiDrift extends AntiDrift implements MouseListener {
	private AcqRow row;
	private ImageStack stack;
	private ImagePlus image;
	private int width, height;

	public ManualAntiDrift(AcqParams p, AcqRow r) {
		row = r;

		width = (int) p.getCore().getImageWidth();
		height = (int) p.getCore().getImageHeight();
	}

	@Override
	public void startNewStack() {
		if(image != null) {
			image.hide();
			image = null;
			stack = null;
			ij.IJ.freeMemory();
		}

		stack = new ImageStack(width, height);
	}

	@Override
	public void tallySlice(Vector3D center, ImageProcessor ip) {
		stack.addSlice(ip);
	}

	@Override
	public void finishStack() {
		image = new ImagePlus("Select center of intensity.", stack);
		image.show();
		image.getCanvas().addMouseListener(this);
		ij.IJ.setTool("rectangle");
	}

	@Override
	public void finishStack(boolean initial) {
		finishStack();
	}

	@Override
	public void mouseReleased(MouseEvent me) {
		if(image.getRoi() != null && image.getRoi().getLength() > 0) {
			if(JOptionPane.showConfirmDialog(null, "Confirm?", "Confirm Selection", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;

			Rectangle roi = image.getRoi().getBounds();
			int zslice = image.getSlice();

			double cx = roi.getCenterX() - (image.getWidth()/2);
			double cy = roi.getCenterY() - (image.getHeight()/2);
			double cz = (zslice - (row.getDepth()/2))*row.getZStepSize();

			image.hide();
			ij.IJ.setTool("hand");

			image = null;
			stack = null;

			ij.IJ.freeMemory();

			invokeCallback(new Vector3D(cx, cy, cz));
		}
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {}
	@Override
	public void mouseEntered(MouseEvent arg0) {}
	@Override
	public void mouseExited(MouseEvent arg0) {}
	@Override
	public void mousePressed(MouseEvent arg0) {}
}
