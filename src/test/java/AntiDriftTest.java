import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import spim.algorithm.AntiDrift;
import spim.controller.AntiDriftController;
import spim.algorithm.DefaultAntiDrift;
import utils.ImageGenerator;

import java.awt.*;
import java.io.File;

import static org.junit.Assume.assumeTrue;

/**
 * The type Anti drift handler test.
 */
public class AntiDriftTest
{
	private ImagePlus impFirst;
	private ImagePlus impSecond;
	private ImagePlus impThird;


	/**
	 * Calculates the Gauss function (AKA kernel) of the given parameters.
	 *
	 * @param size the size of the kernel
	 * @param center the center of the Gaussian function
	 * @param sigma the standard deviation of the Gaussian function
	 * @return the kernel
	 */
	private static float[] gauss(final int size, final float center, final float sigma) {
		final float[] result = new float[size];
		for (int i = 0; i < size; i++) {
			final float x = (i - center) / sigma;
			result[i] = (float) Math.exp(-x * x / 2);
		}
		return result;
	}



	/**
	 * setup the images
	 */
	@Before
	public void setup()
	{
		// impFirst = generateBlob(128, 128, 128, 64, 32, 48, 50 / 8.0f , 30 / 4.0f, 40 / 4.0f);
		// impSecond = generateBlob(128, 128, 128, 64 + 16, 32 + 24, 48 + 32, 40 /8.0f, 20 / 4.0f, 30 / 4.0f);
		// The below parameters are simplified version with above ratio
		impFirst = ImageGenerator.generateFloatBlob(128, 128, 128, 64, 32, 48, 12.5f , 7.5f, 10.0f);
		impSecond = ImageGenerator.generateFloatBlob(128, 128, 128, 64 + 16, 32 + 24, 48 + 32, 5.0f, 5.0f, 7.5f);
		impThird = ImageGenerator.generateFloatBlob(128, 128, 128, 64 + 8, 32 + 32, 48 + 24, 4.0f, 4.0f, 6.5f);
	}

	/**
	 * Test anti drift handler.
	 */
	@Test
	public void testAntiDrift()
	{
		final DefaultAntiDrift proj = new DefaultAntiDrift(7);
		proj.startNewStack();

		final ImageStack stackFirst = impFirst.getImageStack();
		for(int k = 1; k <= stackFirst.getSize(); k++)
		{
			proj.addXYSlice( stackFirst.getProcessor( k ) );
		}
		proj.finishStack( );

		proj.startNewStack();
		final ImageStack stackSecond = impSecond.getImageStack();
		for(int k = 1; k <= stackSecond.getSize(); k++)
		{
			proj.addXYSlice( stackSecond.getProcessor( k ) );
		}

		final Vector3D correction = proj.finishStack( );
		final double DELTA = 1e-5;

		Assert.assertEquals(16, correction.getX(), DELTA);
		Assert.assertEquals(24, correction.getY(), DELTA);
		Assert.assertEquals(32, correction.getZ(), DELTA);
	}

	/**
	 * GUI involved test which is run in main function
	 */
	public void testAntiDriftController()
	{
		assumeTrue(!GraphicsEnvironment.isHeadless());

		final AntiDriftController ct = new AntiDriftController( new File("/Users/moon/temp/"), 2, 3, 4, 0, 1, 0.5);

		ct.setCallback(new AntiDrift.Callback() {
			public void applyOffset(Vector3D offs) {
				offs = new Vector3D(offs.getX()*-1, offs.getY()*-1, -offs.getZ());
				System.out.println(String.format("Offset: %s", offs.toString()));
			}
		});

		ct.startNewStack();

		final ImageStack stackFirst = impFirst.getImageStack();
		for(int k = 1; k <= stackFirst.getSize(); k++)
		{
			ct.addXYSlice( stackFirst.getProcessor( k ) );
		}
		ct.finishStack();

		ct.startNewStack();
		final ImageStack stackSecond = impSecond.getImageStack();
		for(int k = 1; k <= stackSecond.getSize(); k++)
		{
			ct.addXYSlice( stackSecond.getProcessor( k ) );
		}
		ct.finishStack();

//		try
//		{
//			Thread.sleep( 10000 );
//		} catch (InterruptedException e)
//		{
//			e.printStackTrace();
//		}
//
//		ct.startNewStack();
//		final ImageStack stackThird = impThird.getImageStack();
//		for(int k = 1; k <= stackThird.getSize(); k++)
//		{
//			ct.addXYSlice( stackThird.getProcessor( k ) );
//		}
//		ct.finishStack();
	}

	Vector3D updatedOffset = new Vector3D( 0, 0, 0 );

	public void testAntiDriftWithData()
	{
		final AntiDriftController ct = new AntiDriftController( new File("/Users/moon/temp/"), 2, 3, 4, 0, 1, 3);

		ct.setCallback(new AntiDrift.Callback() {
			public void applyOffset(Vector3D offs) {
				offs = updatedOffset = new Vector3D(offs.getX()*1, offs.getY()*1, offs.getZ());
				System.out.println(String.format("Offset: %s", offs.toString()));
			}
		});

		for(int i = 0; i < 5; i++)
		{
			String filename = "spim_TL0" + (i + 1) + "_Angle0.ome.tiff";
			ImagePlus ip = IJ.openImage("/Users/moon/temp/normal/" + filename);
			ct.startNewStack();

			final ImageStack stack = ip.getImageStack();
			for(int k = 1; k <= stack.getSize(); k++)
			{
				FloatProcessor fp = (FloatProcessor) stack.getProcessor( k ).convertToFloat();

				fp.translate( updatedOffset.getX(), updatedOffset.getY() );

				ct.addXYSlice( fp );
			}
			ct.finishStack();

			ip.close();

			try
			{
				Thread.sleep( 10000 );
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void testAntiDriftWithJohannesData() {
		final AntiDriftController ct = new AntiDriftController( new File("/Users/moon/temp/openspim"), 2, 3, 4, 0, 1, 0.5);
//
//		ct.setCallback(new AntiDrift.Callback() {
//			public void applyOffset(Vector3D offs) {
//				offs = updatedOffset = new Vector3D(offs.getX()*1, offs.getY()*1, offs.getZ());
//				System.out.println(String.format("Offset: %s", offs.toString()));
//			}
//		});

		final DefaultAntiDrift proj = new DefaultAntiDrift(7);

//		String filename = "Pd_100TP_ch_0-1.tif";
		String filename = "TP0-310_shift_3xds.tif";
		ImagePlus ip = IJ.openVirtual("/Users/moon/temp/openspim/" + filename);

		System.out.println(ip.getNFrames());

//		for(int t = 0; t < ip.getNFrames(); t++) {
//			proj.startNewStack();
//			System.out.println("t=" + t);
//			for(int z = 0; z < ip.getNSlices(); z++) {
//				ip.setPosition(0, z, t);
//
////				if(z > 29)
////				{
//					ImageProcessor p = ip.getProcessor();
//					proj.addXYSlice( p );
////				}
//			}
//			proj.finishStack();
//
//			final Vector3D correction = proj.finishStack( );
//			final double DELTA = 1e-5;
//
//			System.out.println(String.format( "x=%f, y=%f, z=%f", correction.getX(), correction.getY(), correction.getZ() ));
//		}
//		int t = 5;
//
//			ct.startNewStack();
//			System.out.println("t=" + t);
//			for(int z = 0; z < ip.getNSlices(); z++) {
//				ip.setPosition(0, z, t);
//
//				//				if(z > 29)
//				//				{
//				ImageProcessor p = ip.getProcessor();
//				ct.addXYSlice( p );
//				//				}
//			}
//			ct.finishStack();
//
////			Vector3D correction = proj.finishStack( );
//
////			System.out.println(String.format( "x=%f, y=%f, z=%f", correction.getX(), correction.getY(), correction.getZ() ));
//
//		t = 80;
//		ct.startNewStack();
//		System.out.println("t=" + t);
//		for(int z = 0; z < ip.getNSlices(); z++) {
//			ip.setPosition(0, z, t);
//
//			//				if(z > 29)
//			//				{
//			ImageProcessor p = ip.getProcessor();
//			ct.addXYSlice( p );
//			//				}
//		}
//		ct.finishStack();

//		correction = proj.finishStack( );

//		System.out.println(String.format( "x=%f, y=%f, z=%f", correction.getX(), correction.getY(), correction.getZ() ));


		//		ImagePlus image = IJ.openImage();
//		image.setStack( stack );
//		image.show();
		int t = ip.getNFrames();

		for(int i = 10; i < t; i+=20) {
			System.out.println(i);
//			ct.startNewStack();
			proj.startNewStack();

			double xOffset, yOffset;

			Vector3D offset = proj.getUpdatedOffset();
			xOffset = offset.getX();
			yOffset = offset.getY();
			System.out.println("Anti-Drift used offset only X,Y: " + offset);

			for(int k = 1; k <= ip.getNSlices(); k++)
			{
				ip.setPosition( 0, k, i );

//				FloatProcessor fp = ( FloatProcessor ) ip.getProcessor().convertToFloat();

				// Translate the original image according to the current offset
				// This behavior will be replaced by Stage Control Movement operation with pixelSizeUm property
//				fp.translate( xOffset, yOffset );
				// Corrected image will be used for the viewer
//				ct.addXYSlice( fp );

//				proj.addXYSlice( fp );
				proj.addXYSlice( ip.getProcessor() );
			}
//			ct.finishStack();
			proj.updateOffset(proj.finishStack());
		}
		ip.close();
		System.out.println("Final offset: " + proj.getUpdatedOffset());


//		for(int i = 0; i < 5; i++)
//		{
//			String filename = "Pd_100TP_ch_0-1.tif";
//			ImagePlus ip = IJ.openImage("/Users/moon/temp/openspim/" + filename);
//			ct.startNewStack();
//
//			final ImageStack stack = ip.getImageStack();
//			for(int k = 1; k <= stack.getSize(); k++)
//			{
//				FloatProcessor fp = (FloatProcessor) stack.getProcessor( k ).convertToFloat();
//
//				fp.translate( updatedOffset.getX(), updatedOffset.getY() );
//
//				ct.addXYSlice( fp );
//			}
//			ct.finishStack();
//
//			ip.close();
//
//			try
//			{
//				Thread.sleep( 10000 );
//			} catch (InterruptedException e)
//			{
//				e.printStackTrace();
//			}
//		}
	}

	public static void main(String[] argv)
	{
		AntiDriftTest test = new AntiDriftTest();
//		test.testAntiDriftWithData();
		test.testAntiDriftWithJohannesData();
//		test.setup();
//		test.testAntiDriftController();
	}
}
