package spim.progacq;

import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.io.File;

/**
 * The Anti-drift controller consists of AntiDrift logic and GUI.
 */
public class AntiDriftController implements AntiDrift.Callback
{
	final private DefaultAntiDrift antiDrift;
	final private AdjustGUI gui;
	private Vector3D center;
	private double zratio;
	private long tp;
	private File outputDir;

	/**
	 * Instantiates a new Anti drift controller.
	 *
	 * @param outputDir the output dir
	 * @param acqParams the acq params
	 * @param acqRow the acq row
	 */
	public AntiDriftController(final File outputDir, final AcqParams acqParams, final AcqRow acqRow)
	{
		final Vector3D loc = new Vector3D(acqRow.getX(), acqRow.getY(), acqRow.getZStartPosition());
		final double theta = acqRow.getTheta();

		zratio = acqRow.getZStepSize() / acqParams.getCore().getPixelSizeUm();
		tp = 1;

		antiDrift = new DefaultAntiDrift();
		gui = new AdjustGUI(acqParams, acqRow);

		if(outputDir != null) {
			String xyz = String.format("XYZ%.2fx%.2fx%.2f_Theta%.2f", loc.getX(), loc.getY(), loc.getZ(), theta);
			File saveDir = new File(new File(outputDir, "diffs"), xyz);

			if(!saveDir.exists() && !saveDir.mkdirs()) {
				ij.IJ.log("Couldn't create output directory " + saveDir.getAbsolutePath());
			} else {
				this.outputDir = saveDir;
			}
		}
	}

	/**
	 * New instance.
	 *
	 * @param outputDir the output dir
	 * @param acqParams the acq params
	 * @param acqRow the acq row
	 * @return the anti drift controller
	 */
	public static AntiDriftController newInstance(final File outputDir, final AcqParams acqParams, final AcqRow acqRow)
	{
		return new AntiDriftController( outputDir, acqParams, acqRow );
	}

	/**
	 * The interface Factory.
	 */
	public interface Factory
	{
		/**
		 * New instance.
		 *
		 * @param p the p
		 * @param r the r
		 * @return the anti drift controller
		 */
		public AntiDriftController newInstance(AcqParams p, AcqRow r);
	}

	/**
	 * Sets callback.
	 *
	 * @param callback the callback
	 */
	public void setCallback(AntiDrift.Callback callback)
	{
		antiDrift.setCallback( callback );
	}

	/**
	 * Start new stack.
	 */
	public void startNewStack()
	{
		if(gui.isVisible())
		{
			antiDrift.updateOffset( gui.getOffset() );
			gui.setVisible( false );
			gui.dispose();
		}

		antiDrift.startNewStack();
	}

	/**
	 * Tally slice.
	 *
	 * @param ip the ip
	 */
	public void tallySlice(ImageProcessor ip)
	{
		antiDrift.tallySlice( ip );
	}

	/**
	 * Finish stack.
	 */
	public void finishStack()
	{
		antiDrift.finishStack();
	}

	/**
	 * Finish stack.
	 *
	 * @param initial the initial
	 */
	public void finishStack(boolean initial)
	{
		center = antiDrift.getLastCorrection().add( antiDrift.getLatest().getCenter() );

		// Before processing anti-drift
		antiDrift.writeDiff( getOutFile("initial"), antiDrift.getLastCorrection(), zratio, center );

		// Process anti-drift
		antiDrift.finishStack( initial );

		// After the anti-drift processing
		antiDrift.writeDiff( getOutFile("suggested"), antiDrift.getLastCorrection(), zratio, center );

		// Invoke GUI for fine-tuning
		gui.setCallback( this );
		gui.setZratio( zratio );
		gui.updateScale( antiDrift.getLatest().largestDimension() * 2 );
		gui.setOffset( antiDrift.getLastCorrection() );
		gui.setCenter( center );
		gui.setBefore( antiDrift.getFirst() );
		gui.setAfter( antiDrift.getLatest() );

		gui.updateDiff();

		gui.setVisible( true );

		// first = latest
		antiDrift.setFirst( antiDrift.getLatest() );

		// Increase the file index number
		++tp;
	}

	private File getOutFile(String tag) {
		if(outputDir != null)
			return new File(outputDir, String.format("diff_TL%02d_%s.tiff", tp, tag));
		else
			return null;
	}

	public void applyOffset( Vector3D offset )
	{
		offset = offset.add(antiDrift.getLastCorrection());
		antiDrift.setLastCorrection( offset );

		// TODO: Check how this code is working
//		invokeCallback(new Vector3D(-offs.getX(), -offs.getY(), -offs.getZ()*zstep));

		antiDrift.writeDiff( getOutFile("final"), antiDrift.getLastCorrection(), zratio, center );
	}
}
