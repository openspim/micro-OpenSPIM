package spim.progacq;

import ij.process.ColorProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import javax.swing.JFrame;
import javax.swing.JPanel;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * The Adjust GUI for Anti-drift tunning.
 */
public class AdjustGUI extends JFrame implements KeyListener
{
	private Dimension preferredImageSize;
	private Image diff;
	private JPanel panel;

	private long tp;
	private Vector3D loc;

	private Projections before, after;

	/**
	 * Sets before.
	 *
	 * @param before the before
	 */
	public void setBefore( Projections before )
	{
		this.before = before;
	}

	/**
	 * Sets after.
	 *
	 * @param after the after
	 */
	public void setAfter( Projections after )
	{
		this.after = after;
	}

	private AbstractAntiDrift.Callback callback;

	/**
	 * Sets callback.
	 *
	 * @param callback the callback
	 */
	public void setCallback( AbstractAntiDrift.Callback callback )
	{
		this.callback = callback;
	}

	private Vector3D offset;

	/**
	 * Gets offset.
	 *
	 * @return the offset
	 */
	public Vector3D getOffset()
	{
		return offset;
	}

	/**
	 * Sets offset.
	 *
	 * @param offset the offset
	 */
	public void setOffset(Vector3D offset)
	{
		this.offset = offset;
	}

	private Vector3D center;

	/**
	 * Sets center.
	 *
	 * @param center the center
	 */
	public void setCenter( Vector3D center )
	{
		this.center = center;
	}

	private double zratio;

	/**
	 * Sets zratio.
	 *
	 * @param zratio the zratio
	 */
	public void setZratio( double zratio )
	{
		this.zratio = zratio;
	}

	private double scale;

	/**
	 * Update scale.
	 *
	 * @param base the base
	 */
	public void updateScale( double base )
	{
		this.scale = getToolkit().getScreenSize().height / base * 0.9;
	}

	/**
	 * Instantiates a new AdjustGUI using primitive parameters
	 *
	 * @param x the x
	 * @param y the y
	 * @param z the z
	 * @param theta the theta
	 * @param zratio the zratio
	 */
	public AdjustGUI(final double x, final double y, final double z, final double theta, final double zratio)
	{
		this.loc = new Vector3D(x, y, z);
		this.tp = 1;
		this.zratio = zratio;

		addKeyListener(this);
		setTitle(String.format("xyz: %.2f x %.2f x %.2f, theta: %.2f, timepoint %02d", loc.getX(), loc.getY(), loc.getZ(), theta, tp));
	}

	public void keyPressed(final KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_ENTER:
				dispose();
				callback.applyOffset(offset);
				break;
			case KeyEvent.VK_ESCAPE:
				dispose();
				callback.applyOffset(Vector3D.ZERO);
				break;
			case KeyEvent.VK_UP:
				offset = offset.add(new Vector3D(0, (e.isShiftDown() ? 10 : 1), 0));
				updateDiff();
				break;
			case KeyEvent.VK_DOWN:
				offset = offset.subtract(new Vector3D(0, (e.isShiftDown() ? 10 : 1), 0));
				updateDiff();
				break;
			case KeyEvent.VK_LEFT:
				offset = offset.add(new Vector3D((e.isShiftDown() ? 10 : 1), 0, 0));
				updateDiff();
				break;
			case KeyEvent.VK_RIGHT:
				offset = offset.subtract(new Vector3D((e.isShiftDown() ? 10 : 1), 0, 0));
				updateDiff();
				break;
			case KeyEvent.VK_PAGE_UP:
				offset = offset.subtract(new Vector3D(0, 0, (e.isShiftDown() ? 10 : 1)));
				updateDiff();
				break;
			case KeyEvent.VK_PAGE_DOWN:
				offset = offset.add(new Vector3D(0, 0, (e.isShiftDown() ? 10 : 1)));
				updateDiff();
				break;
			case KeyEvent.VK_MINUS:
			case KeyEvent.VK_END:
				scale -= 0.1;
				updateDiff();
				break;
			case KeyEvent.VK_EQUALS:
			case KeyEvent.VK_HOME:
				scale += 0.1;
				updateDiff();
				break;
		}
	}

	public void keyReleased(final KeyEvent e) {
		// do nothing
	}

	public void keyTyped(final KeyEvent e) {
		// do nothing
	}

	/**
	 * Update diff between before and after.
	 */
	public void updateDiff() {
		final ColorProcessor cp = before.getDiff(after, scale, zratio, offset, center);
		preferredImageSize = new Dimension(cp.getWidth(), cp.getHeight());
		if(diff != null) {
			diff.flush();
			diff = null;
		}
		diff = cp.createImage();

		if (panel == null) {
			panel = new JPanel() {
				@Override
				public void paintComponent(final Graphics g) {
					super.paintComponent(g);
					g.drawImage(diff, 0, 0, null);
				}
			};
		}

		getContentPane().add(panel);
		panel.setPreferredSize(preferredImageSize);
		pack();
		panel.repaint();
	}

	@Override
	public void dispose() {
		diff.flush();
		diff = null;
		before = after = null;
		setVisible( false );
	}
}
