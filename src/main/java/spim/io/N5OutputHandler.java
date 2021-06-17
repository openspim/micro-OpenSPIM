package spim.io;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import loci.formats.meta.IMetadata;
import mmcorej.CMMCore;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;

import org.micromanager.internal.utils.ReportingUtils;
import org.janelia.saalfeldlab.n5.*;
import spim.acquisition.Row;
import spim.ui.view.component.MMAcquisitionEngine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class N5OutputHandler implements OutputHandler, Thread.UncaughtExceptionHandler
{
	private File outputDirectory;

	private IMetadata meta;
	private int imageCounter, sliceCounter;

	private CMMCore core;
	private int stacks, timesteps, tiles, channels;
	private Row[] acqRows;

	private double[] zStepSize;
	private Exception rethrow;

	private String currentFile;
	final private String prefix;
	final PropertyMap metadataMap;
	private int timepoint;
	final boolean isMultiPosition;

	final int width;
	final int height;
	N5FSWriter writer;
	final GzipCompression compression;
	final ExecutorService exec;
	final long bitDepth;
	final ArrayList<String> datasetList;
	ImageStack[] imageStacks;
	final String outputFile;

	public N5OutputHandler(CMMCore iCore, File outDir, String filenamePrefix, Row[] acqRows, int channels,
						  int iTimeSteps, int tileCount, SummaryMetadata metadata ) throws IOException {
		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		imageCounter = -1;
		sliceCounter = 0;

		stacks = acqRows.length;
		zStepSize = new double[stacks];
		core = iCore;
		timesteps = iTimeSteps;
		outputDirectory = outDir;
		this.acqRows = acqRows;
		tiles = tileCount;
		this.channels = channels;
		this.prefix = filenamePrefix;
		this.metadataMap = ((DefaultSummaryMetadata) metadata).toPropertyMap();
		this.isMultiPosition = ( stacks > 1 );

		List<String> multis = MMAcquisitionEngine.getMultiCams(core);

		outputFile = outputDirectory.getAbsolutePath() + File.separator + filenamePrefix + ".n5";
		writer = new N5FSWriter( outputFile );
		compression = new GzipCompression();

		bitDepth = core.getImageBitDepth();

		width = (int)core.getImageWidth();
		height = (int)core.getImageHeight();

		datasetList = new ArrayList<>();

		for (int image = 0; image < stacks; ++image) {

			Row row = acqRows[image];
			int depth = row.getDepth();
			int ch = channels == 0 ? 1 : channels;

			long[] dimensions = new long[] {width, height, ch, depth, timesteps};
			int[] blockSize = new int[] {width / 2, height / 2, 1, depth / 2, 1};

			final DatasetAttributes attributes = new DatasetAttributes(
					dimensions,
					blockSize,
					bitDepth == 8 ? DataType.UINT8 : DataType.UINT16,
					compression);

			String datasetName = makeDatasetName(image, channels, multis);
			writer.createDataset(datasetName, attributes);

			datasetList.add(datasetName);
		}

		exec = Executors.newFixedThreadPool(10);
	}

	private String makeDatasetName(int posIndex, int c, List<String> multis) {
		String posString = "";
		if (isMultiPosition)
			posString = String.format("_Pos%02d", posIndex);

		if (multis != null) {
			posString += String.format("_View%02d", c / multis.size());
		}

		return String.format("/Volumes/DataSet" + posString, timepoint);
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		return null;
	}

	@Override
	public void beginStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Beginning stack along time " + time + " / angle " + angle );

		imageStacks = new ImageStack[this.channels];
		for(int i = 0; i < this.channels; i++) {
			imageStacks[i] = new ImageStack(width, height);
		}

		++imageCounter;
		timepoint = time;

	}

	@Override
	public void processSlice(int time, int angle, int exp, int c, ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT) throws Exception {
		int image = imageCounter % stacks;
		int plane = sliceCounter;

		imageStacks[c].addSlice(ip);

		++sliceCounter;
	}

	@Override
	public void finalizeStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Finished stack along time " + time + " / angle " + angle );

		final long[] gridPosition = new long[5];
		gridPosition[4] = time;

		for(int i = 0; i < this.channels; i++) {
			RandomAccessibleInterval source;
			gridPosition[2] = i;
			if(bitDepth == 8) {
				final Img rai = ImageJFunctions.<UnsignedByteType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[i]));
				source = Views.addDimension(Views.zeroMin(rai), 0, 0);
			} else {
				final Img rai = ImageJFunctions.<UnsignedShortType>wrap(new ImagePlus("t=" + time + "/angle=" + angle, imageStacks[i]));
				source = Views.addDimension(Views.zeroMin(rai), 0, 0);
			}

			source = Views.addDimension(source, 0, 0);
			source = Views.moveAxis(source, 2, 3);

			if (writer != null) {
				N5Utils.saveBlock(source, writer, datasetList.get(angle), gridPosition, exec);
			}
		}
	}

	@Override
	public void finalizeAcquisition(boolean b) throws Exception {
		try
		{
			final N5MicroManagerMetadata metaWriter = new N5MicroManagerMetadata( outputFile );

			PropertyMap.Builder pb = PropertyMaps.builder().putAll(metadataMap)
					.putInteger("Width", width)
					.putInteger("Height", height)
					.putEnumAsString( "Storage", StorageType.N5 );

			metaWriter.readMetadata( pb.build() );

			for (int image = 0; image < stacks; ++image) {
				metaWriter.writeMetadata( metaWriter, writer, datasetList.get(image) );
			}
		}
		catch ( final Exception e ) { e.printStackTrace(); }

		imageCounter = 0;
		writer = null;
	}

	@Override public void uncaughtException( Thread thread, Throwable throwable )
	{
		if(!(throwable instanceof Exception))
		{
			ReportingUtils.logError(throwable, "Non-exception throwable " + throwable.toString() + " caught from N5 resave thread. Wrapping.");
			throwable = new Exception("Wrapped throwable; see core log for details: " + throwable.getMessage(), throwable);
		}

		rethrow = (Exception)throwable;
	}

	public static void main(final String[] args) {
	}
}
