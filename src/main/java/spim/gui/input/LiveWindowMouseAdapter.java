package spim.gui.input;

import ij.gui.Toolbar;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import spim.gui.calibration.CalibrationWindow;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;

public class LiveWindowMouseAdapter extends MouseAdapter {
	private static final String mmHooksWarnMsg = "Micro-Manager's mouse control hooks are in place. Override?\n\nYes: Remove MM's hooks.\nNo: Don't hook the SPIM controls.";
	private static final String mmHooksWarnTitle = "Live Window Mouse Controls";

	private SPIMSetup setup;
	private CalibrationWindow calibrator;

	private Component hookedOn;

	private org.micromanager.navigation.CenterAndDragListener mmMouseListener;
	private org.micromanager.navigation.ZWheelListener mmMouseWheelListener;

	private Point dragStart;

	private Vector3D stageStart;
	private Double thetaStart;

	public LiveWindowMouseAdapter() {
		hookedOn = null;
		setup = null;
		calibrator = null;

		dragStart = null;
		stageStart = null;
		thetaStart = null;

		mmMouseListener = null;
		mmMouseWheelListener = null;
	}

	/**
	 * Attaches these controls to the given component (probably MM's live window canvas.)
	 *
	 * @param to Component to attach controls to.
	 * @param isetup SPIM setup to control.
	 * @param icalibrator Calibrator to use for determing rotation axis. May be null.
	 * @param unhookMM One of JOptionPane's result constants. Only matters if MM is hooked. Yes means remove MM's old hooks, No means abort. -1 means ask.
	 * @return True if the controls were attached; false if 
	 */
	public boolean attach(Component to, SPIMSetup isetup, CalibrationWindow icalibrator, int unhookMM) {
		if(hookedOn != null)
			detach();

		// First, check if MM has its claws in the target component (i.e. the live window).
		// If so, ask the user if we want to a) remove MM's hooks, b) leave both hooks in, or c) don't hook.
		// TODO: Should this check be external?
		List<MouseListener> mls = Arrays.asList(to.getMouseListeners());
		List<MouseMotionListener> mmls = Arrays.asList(to.getMouseMotionListeners());
		List<MouseWheelListener> mwls = Arrays.asList(to.getMouseWheelListeners());

		MouseListener ml = null;
		MouseMotionListener mml = null;
		MouseWheelListener mwl = null;

		Iterator<MouseListener> mli = mls.iterator();
		while(mli.hasNext())
			if((ml = mli.next()) instanceof org.micromanager.navigation.CenterAndDragListener)
				break;

		Iterator<MouseMotionListener> mmli = mmls.iterator();
		while(mmli.hasNext())
			if((mml = mmli.next()) instanceof org.micromanager.navigation.CenterAndDragListener)
				break;

		Iterator<MouseWheelListener> mwli = mwls.iterator();
		while(mwli.hasNext())
			if((mwl = mwli.next()) instanceof org.micromanager.navigation.ZWheelListener)
				break;

		if(ml != null || mml != null || mwl != null) {
			if(unhookMM < 0)
				unhookMM = JOptionPane.showConfirmDialog(to, mmHooksWarnMsg, mmHooksWarnTitle, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if(unhookMM == JOptionPane.NO_OPTION) {
				return false;
			} else if(unhookMM == JOptionPane.YES_OPTION) {
				mmMouseListener = (org.micromanager.navigation.CenterAndDragListener)(ml != null ? ml : mml);
				mmMouseWheelListener = (org.micromanager.navigation.ZWheelListener)mwl;

				if(mmMouseListener != null)
					mmMouseListener.stop();

				if(mmMouseWheelListener != null)
					mmMouseWheelListener.stop();
			}
		}

		to.addMouseListener(this);
		to.addMouseMotionListener(this);
		to.addMouseWheelListener(this);

		hookedOn = to;
		setup = isetup;
		calibrator = icalibrator;

		dragStart = null;
		stageStart = null;
		thetaStart = null;

		return true;
	}

	/**
	 * @return True if the object believes it's attached to something. False otherwise.
	 */
	public boolean isAttached() {
		return hookedOn != null;
	}

	/**
	 * Detaches this instance from whatever it was last hooked onto. If it found MM listeners,
	 * those are restarted.
	 */
	public void detach() {
		if(hookedOn == null)
			return;

		hookedOn.removeMouseListener(this);
		hookedOn.removeMouseMotionListener(this);
		hookedOn.removeMouseWheelListener(this);

		if(mmMouseListener != null)
			((org.micromanager.navigation.CenterAndDragListener)mmMouseListener).start();

		if(mmMouseWheelListener != null)
			((org.micromanager.navigation.ZWheelListener)mmMouseWheelListener).start();

		hookedOn = null;
		setup = null;
		calibrator = null;

		dragStart = null;
		stageStart = null;
		thetaStart = null;

		mmMouseListener = null;
		mmMouseWheelListener = null;
	}

	private Vector3D applyRotation(Vector3D pos, double dtheta) {
		if(calibrator == null || !calibrator.getIsCalibrated())
			return pos;

		Vector3D rotOrigin = calibrator.getRotationOrigin();
		Vector3D rotAxis = calibrator.getRotationAxis();

		Rotation rot = new Rotation(rotAxis, -dtheta * Math.PI / 180D);

		return rotOrigin.add(rot.applyTo(pos.subtract(rotOrigin)));
	}

	@Override
	public void mouseClicked(MouseEvent me) {
		if(setup == null)
			throw new Error("LWMC mouseClicked with no setup.");

		if(me.getButton() != MouseEvent.BUTTON1 || me.getClickCount() != 2 || Toolbar.getToolId() != Toolbar.HAND)
			return;

		double dx = (me.getX() - hookedOn.getWidth() / 2) * setup.getCore().getPixelSizeUm();
		double dy = (me.getY() - hookedOn.getHeight() / 2) * setup.getCore().getPixelSizeUm();

		Vector3D dest = setup.getPosition().add(new Vector3D(dx, dy, 0));

		if(Math.sqrt(dx*dx + dy*dy) < (setup.getXStage().getStepSize() + setup.getYStage().getStepSize()))
			return;

		setup.setPosition(dest);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent me) {
		if(setup == null)
			throw new Error("LWMC mouseWheelMoved with no setup.");

		if(Toolbar.getToolId() != Toolbar.HAND)
			return;

		Stage z = setup.getZStage();

		if(z.isBusy())
			return;

		z.setPosition(z.getPosition() - me.getWheelRotation()*z.getStepSize()*(me.isShiftDown() ? 4 : 1));
	}

	@Override
	public void mousePressed(MouseEvent me) {
		if(setup == null)
			throw new Error("LWMC mousePressed with no setup.");

		if(Toolbar.getToolId() != Toolbar.HAND)
			return;

		dragStart = me.getPoint();
		stageStart = setup.getPosition();
		thetaStart = setup.getAngle();
	}

	@Override
	public void mouseReleased(MouseEvent me) {
		if(setup == null)
			throw new Error("LWMC mouseReleased with no setup.");

		if(Toolbar.getToolId() != Toolbar.HAND)
			return;

		dragStart = null;
		stageStart = null;
		thetaStart = null;
	}

	@Override
	public void mouseDragged(MouseEvent me) {
		if(setup == null)
			throw new Error("LWMC mouseDragged with no setup.");

		if(Toolbar.getToolId() != Toolbar.HAND)
			return;

		Point drag = new Point(me.getX() - dragStart.x, me.getY() - dragStart.y);

		if(!me.isAltDown()) {
			Vector3D d = new Vector3D(drag.x * setup.getCore().getPixelSizeUm() * (me.isShiftDown() ? 2 : 1),
									  drag.y * setup.getCore().getPixelSizeUm() * (me.isShiftDown() ? 2 : 1),
									  0);

			setup.setPosition(stageStart.add(d), thetaStart);
		} else {
			double dt = drag.x * (180.0 / hookedOn.getWidth()) * (me.isShiftDown() ? 2 : 1);

			setup.setPosition(applyRotation(stageStart, dt), thetaStart + dt);
		}
	}

	@Override
	public void mouseEntered(MouseEvent me) {}

	@Override
	public void mouseExited(MouseEvent me) {}

	@Override
	public void mouseMoved(MouseEvent me) {}

}
