import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.apache.commons.io.FileUtils;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.ij.N5IJUtils;
import org.janelia.saalfeldlab.n5.imglib2.N5CellLoader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import spim.util.NativeLoader;
import utils.ImageGenerator;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class N5Test {

	static private String testDirPath = System.getProperty("user.home") + "/tmp/n5-test";

	/**
	 * setup the images
	 */
	@Before
	public void setup()
	{
//		final String osName = System.getProperty( "os.name" ).toLowerCase();
//
//		String libPrefix = "lib";
//		final String libSuffix;
//		if ( osName.startsWith( "mac" ) ) {
//			// MacOSX library naming convention
//			libSuffix = ".jnilib";
//		} else if ( osName.startsWith( "win" ) ) {
//			// Windows library naming convention
//			libPrefix = "";
//			libSuffix = ".dll";
//		} else {
//			// Linux library naming convention
//			libSuffix = ".so";
//		}
//
//		System.load( libPath );
		NativeLoader.loadLibrary(NativeLoader.class);
	}

	@After
	public void tearDown() throws IOException {
		if(new File(testDirPath + ".n5").exists()) {
			FileUtils.cleanDirectory(new File(testDirPath + ".n5"));
		}

	}

	static void cropBlockDimensions(
			final long[] max,
			final long[] offset,
			final int[] blockDimensions,
			final long[] croppedBlockDimensions,
			final int[] intCroppedBlockDimensions,
			final long[] gridPosition) {

		for (int d = 0; d < max.length; ++d) {
			croppedBlockDimensions[d] = Math.min(blockDimensions[d], max[d] - offset[d] + 1);
			intCroppedBlockDimensions[d] = (int)croppedBlockDimensions[d];
			gridPosition[d] = offset[d] / blockDimensions[d];
		}
	}

	@Test
	public void testSaveN5() {
		System.out.println(System.getProperty("java.library.path"));

		N5FSWriter writer = null;
		try {
			writer = new N5FSWriter(testDirPath + ".n5");
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}

		int[] blockSize = new int[] {64, 64, 64, 64};
		GzipCompression compression = new GzipCompression();
//		RawCompression compression = new RawCompression();


		final long[] dimensions = new long[] {128, 128, 128, 128};
		final DatasetAttributes attributes = new DatasetAttributes(
				dimensions,
				blockSize,
				DataType.FLOAT32,
				compression);

		final String dataset = "/volumes/raw";
		try {
			writer.createDataset(dataset, attributes);
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}


		for(int i = 0; i < 128; i++) {
			ImagePlus imp = ImageGenerator.generateFloatBlob(128, 128, 128, 24, 32, 48,
					12.5f , 7.5f, 10.0f);
			imp.setDimensions(128, 1, 1);

			final long[] gridPosition = new long[4];
			gridPosition[3] = i;

			final Img rai = ImageJFunctions.<FloatType>wrap(imp);

			RandomAccessibleInterval<FloatType> source = Views.zeroMin(rai);
//			RandomAccessibleInterval<FloatType> source = Views.addDimension(Views.zeroMin(rai), 0, 0);
//			source = Views.addDimension(Views.zeroMin(source), 0, 0);

//			final long[] dims = Intervals.dimensionsAsLongArray(source);
//			System.out.println(Arrays.toString(dims));
//			final long[] dimensions = Intervals.dimensionsAsLongArray(source);

			final int n = dimensions.length;
			final long[] max = Intervals.maxAsLongArray(source);
			final long[] offset = new long[n];
//			final long[] gridPosition = new long[n];
			final int[] intCroppedBlockSize = new int[n];
			final long[] longCroppedBlockSize = new long[n];

			for (int d = 0; d < n;) {
				cropBlockDimensions(max, offset, blockSize, longCroppedBlockSize, intCroppedBlockSize, gridPosition);
				final RandomAccessibleInterval<FloatType> sourceBlock = Views.offsetInterval(source, offset, longCroppedBlockSize);
				final DataBlock<?> dataBlock = attributes.getDataType().createDataBlock(intCroppedBlockSize, gridPosition);
				N5CellLoader.burnIn(
						(RandomAccessibleInterval<FloatType>)sourceBlock,
						ArrayImgs.floats((float[])dataBlock.getData(), longCroppedBlockSize));

				try {
					writer.writeBlock(dataset, attributes, dataBlock);
				} catch (IOException e) {
					Assert.fail( "Could not write blocks." );
				}
//				final DataBlock<?> dataBlock = createDataBlock(
//						sourceBlock,
//						attributes.getDataType(),
//						intCroppedBlockSize,
//						longCroppedBlockSize,
//						gridPosition);
//
//				n5.writeBlock(dataset, attributes, dataBlock);

				for (d = 0; d < n; ++d) {
					offset[d] += blockSize[d];
					if (offset[d] <= max[d])
						break;
					else
						offset[d] = 0;
				}
			}

//			N5Utils.saveBlock(rai, writer, dataset, gridPosition);
		}
	}

	@Test
	public void testSaveN5Single() {
		ImageStack is = new ImageStack(128, 128);
		for(int i = 0; i < 128; i++) {
			ImagePlus imp = ImageGenerator.generateFloatBlob(128, 128, 128, i, 32, 48,
					12.5f , 7.5f, 10.0f);

			for(int j = 0; j < 128; j++) {
				imp.setZ(j);
				is.addSlice(imp.getProcessor());
			}
		}

		ImagePlus im = new ImagePlus("image", is);
		im.setDimensions(128, 128, 1);
//		im.show();


		N5FSWriter writer = null;
		try {
			writer = new N5FSWriter(testDirPath + ".n5");
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}
		int[] blockSize = new int[] {64, 64, 64, 64};
		GzipCompression compression = new GzipCompression();


		final long[] dimensions = new long[] {128, 128, 128, 128};
		final DatasetAttributes attributes = new DatasetAttributes(
				dimensions,
				blockSize,
				DataType.FLOAT32,
				compression);

		final String dataset = "/volumes/raw";
		try {
			writer.createDataset(dataset, attributes);
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}

		try {
			N5IJUtils.save(im, writer, dataset, blockSize, compression);
		} catch (IOException e) {
			Assert.fail( "Could not save the dataset." );
		}

		Assert.assertTrue( new File(testDirPath + ".n5").exists() );
	}

	@Test
	public void testSaveN5WithStack() {
		N5FSWriter writer = null;
		try {
			writer = new N5FSWriter(testDirPath + ".n5");
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}
		GzipCompression compression = new GzipCompression();

		final long[] dimensions = new long[] {128, 128, 1, 128, 128};
		int[] blockSize = new int[] {64, 64, 1, 64, 1};
		final long[] gridPosition = new long[5];

		final DatasetAttributes attributes = new DatasetAttributes(
				dimensions,
				blockSize,
				DataType.FLOAT32,
				compression);

		final String dataset = "/volumes/raw";
		try {
			writer.createDataset(dataset, attributes);
		} catch (IOException e) {
			Assert.fail( "Could not create writer or dataset." );
		}

		final ExecutorService exec = Executors.newFixedThreadPool(10);

		for(int t = 0; t < 128; t++) {
			ImagePlus imp = ImageGenerator.generateFloatBlob(128, 128, 128, t, 32, 48,
					12.5f , 7.5f, 10.0f);

			final Img rai = ImageJFunctions.<FloatType>wrap(imp);

			gridPosition[4] = t;

			RandomAccessibleInterval<FloatType> source = Views.addDimension(Views.zeroMin(rai), 0, 0);
			source = Views.addDimension(source, 0, 0);
			source = Views.moveAxis(source, 2, 3);

			try {
				N5Utils.saveBlock(source, writer, dataset, gridPosition, exec);
			} catch (IOException e) {
				Assert.fail( "Could not write blocks." );
			} catch (InterruptedException e) {
				Assert.fail( "Could not write blocks." );
			} catch (ExecutionException e) {
				Assert.fail( "Could not write blocks." );
			}
		}
	}
}
