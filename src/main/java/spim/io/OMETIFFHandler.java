package spim.io;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.IOException;

import loci.common.DataTools;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.services.OMEXMLService;
import mmcorej.CMMCore;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import spim.acquisition.AcquisitionStatus;
import spim.acquisition.Program;
import spim.acquisition.Row;

import org.micromanager.utils.ReportingUtils;

public class OMETIFFHandler implements OutputHandler, Thread.UncaughtExceptionHandler
{
	private File outputDirectory;

	private IMetadata meta;
	private int imageCounter, sliceCounter;
	private IFormatWriter writer;

	private CMMCore core;
	private int stacks, timesteps, tiles;
	private Row[] acqRows;
	private double deltat;
	private boolean exportToHDF5;
	private double[] zStepSize;
	private Thread hdf5ResaveThread;
	private Exception rethrow;
	
	public OMETIFFHandler(CMMCore iCore, File outDir, String filenamePrefix, String xyDev,
			String cDev, String zDev, String tDev, Row[] acqRows,
			int iTimeSteps, double iDeltaT, int tileCount, boolean exportToHDF5) {

		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		this.exportToHDF5 = exportToHDF5;
		imageCounter = -1;
		sliceCounter = 0;

		stacks = acqRows.length;
		zStepSize = new double[stacks];
		core = iCore;
		timesteps = iTimeSteps;
		deltat = iDeltaT;
		outputDirectory = outDir;
		this.acqRows = acqRows;
		tiles = tileCount;
		int angles = (int) (stacks / tiles);
		
		try {
			meta = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();

			meta.createRoot();

			meta.setDatasetID(MetadataTools.createLSID("Dataset", 0), 0);

			for (int image = 0; image < stacks; ++image) {
				meta.setImageID(MetadataTools.createLSID("Image", image), image);

				Row row = acqRows[image];
				int depth = row.getDepth();

				meta.setPixelsID(MetadataTools.createLSID("Pixels", 0), image);
				meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, image);
				meta.setPixelsBinDataBigEndian(Boolean.FALSE, image, 0);
				meta.setPixelsType(core.getImageBitDepth() == 8 ? PixelType.UINT8 : PixelType.UINT16, image);
				meta.setChannelID(MetadataTools.createLSID("Channel", 0), image, 0);
				meta.setChannelSamplesPerPixel(new PositiveInteger(1), image, 0);

				int positionIndex = (int) (image/angles);
				
				for (int t = 0; t < timesteps; ++t) {
					String fileName = makeFilename(filenamePrefix, image % angles, t, positionIndex, (tiles>1));
					for(int z = 0; z < depth; ++z) {
						int td = depth*t + z;

						meta.setUUIDFileName(fileName, image, td);
//						meta.setUUIDValue("urn:uuid:" + (String)UUID.nameUUIDFromBytes(fileName.getBytes()).toString(), image, td);

						meta.setTiffDataPlaneCount(new NonNegativeInteger(1), image, td);
						meta.setTiffDataFirstT(new NonNegativeInteger(t), image, td);
						meta.setTiffDataFirstC(new NonNegativeInteger(0), image, td);
						meta.setTiffDataFirstZ(new NonNegativeInteger(z), image, td);
					};
				};

				meta.setPixelsSizeX(new PositiveInteger((int)core.getImageWidth()), image);
				meta.setPixelsSizeY(new PositiveInteger((int)core.getImageHeight()), image);
				meta.setPixelsSizeZ(new PositiveInteger(depth), image);
				meta.setPixelsSizeC(new PositiveInteger(1), image);
				meta.setPixelsSizeT(new PositiveInteger(timesteps), image);

				meta.setPixelsPhysicalSizeX(FormatTools.getPhysicalSizeX(core.getPixelSizeUm()), image);
				meta.setPixelsPhysicalSizeY(FormatTools.getPhysicalSizeX(core.getPixelSizeUm()), image);
				meta.setPixelsPhysicalSizeZ(FormatTools.getPhysicalSizeX(Math.max(row.getZStepSize(), 1.0D)), image);
				zStepSize[image] = Math.max(row.getZStepSize(), 1.0D);

				meta.setPixelsTimeIncrement(new Time(new Double(deltat), UNITS.S), image);
			}

			writer = new ImageWriter().getWriter(makeFilename(filenamePrefix, 0, 0, 0, false));
			if (writer instanceof OMETiffWriter ) {
				ij.IJ.log("calling setBigTiff(true)");
				((OMETiffWriter) writer).setBigTiff(true);
			}

			writer.setWriteSequentially(true);
			writer.setMetadataRetrieve(meta);
			writer.setInterleaved(false);
			writer.setValidBitsPerPixel((int) core.getImageBitDepth());
			writer.setCompression("Uncompressed");
		} catch(Throwable t) {
			t.printStackTrace();
			throw new IllegalArgumentException(t);
		}
	}

	private static String makeFilename(String filenamePrefix, int angleIndex, int timepoint, int posIndex, boolean multiplePos) {
		String posString = new String();
		if (multiplePos)
			posString=String.format("Pos%02d_", posIndex);
		else
			posString="";
		return String.format(filenamePrefix+"TL%02d_"+posString+"Angle%01d.ome.tiff", timepoint, angleIndex);
	}

	private void openWriter(int angleIndex, int timepoint) throws Exception {
		writer.changeOutputFile(new File(outputDirectory, meta.getUUIDFileName(angleIndex, acqRows[angleIndex].getDepth()*timepoint)).getAbsolutePath());
		writer.setSeries(angleIndex);
		meta.setUUID(meta.getUUIDValue(angleIndex, acqRows[angleIndex].getDepth()*timepoint));

		sliceCounter = 0;
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void beginStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Beginning stack along time " + time + " / angle " + angle );

		if(++imageCounter < stacks * timesteps)
			openWriter(imageCounter % stacks, imageCounter / stacks);
	}

	private int doubleAnnotations = 0;
	private int storeDouble(int image, int plane, int n, String name, double val) {
		String key = String.format("%d/%d/%d: %s", image, plane, n, name);

		meta.setDoubleAnnotationID(key, doubleAnnotations);
		meta.setDoubleAnnotationValue(val, doubleAnnotations);
		meta.setPlaneAnnotationRef(key, image, plane, n);

		return doubleAnnotations++;
	}

	@Override
	public void processSlice(int time, int angle, ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT)
			throws Exception {
		long bitDepth = core.getImageBitDepth();
		byte[] data = bitDepth == 8 ?
			(byte[])ip.getPixels() :
			DataTools.shortsToBytes((short[])ip.getPixels(), true);

		int image = imageCounter % stacks;
		int timePoint = imageCounter / stacks;
		int plane = timePoint*acqRows[image].getDepth() + sliceCounter;

		meta.setPlanePositionX(new Length(X, UNITS.REFERENCEFRAME), image, plane);
		meta.setPlanePositionY(new Length(Y, UNITS.REFERENCEFRAME), image, plane);
		meta.setPlanePositionZ(new Length(Z, UNITS.REFERENCEFRAME), image, plane);
		meta.setPlaneTheZ(new NonNegativeInteger(sliceCounter), image, plane);
		meta.setPlaneTheT(new NonNegativeInteger(timePoint), image, plane);
		meta.setPlaneDeltaT(new Time(deltaT, UNITS.S), image, plane);

		storeDouble(image, plane, 0, "Theta", theta);

		try {
			writer.saveBytes(plane, data);
		} catch(java.io.IOException ioe) {
			finalizeStack(0, 0);
			if(writer != null)
				writer.close();
			throw new Exception("Error writing OME-TIFF.", ioe);
		}

		++sliceCounter;
	}

	@Override
	public void finalizeStack(int time, int angle) throws Exception {
		ReportingUtils.logMessage("Finished stack along time " + time + " / angle " + angle );
	}

	@Override
	public void finalizeAcquisition(boolean bSuccess) throws Exception {

		final File firstFile = new File(outputDirectory, meta.getUUIDFileName(0, 0));

		if(writer != null)
			writer.close();

		imageCounter = 0;

		writer = null;

		if (bSuccess && exportToHDF5 && Program.getStatus().equals( AcquisitionStatus.DONE ))
		{
			hdf5ResaveThread = new Thread( new Runnable() {
				@Override
				public void run() {
					try
					{
						new HDF5Generator( outputDirectory, firstFile, stacks, timesteps,
								core.getPixelSizeUm(), zStepSize );
					}
					catch ( IOException e )
					{
						e.printStackTrace();
					}
					catch ( FormatException e )
					{
						e.printStackTrace();
					}
				}
			}, "HDF5 Resave Thread");
			hdf5ResaveThread.setPriority( Thread.MAX_PRIORITY );
			hdf5ResaveThread.setUncaughtExceptionHandler(this);
			hdf5ResaveThread.start();
		}
	}

	@Override public void uncaughtException( Thread thread, Throwable throwable )
	{
		if(thread != hdf5ResaveThread)
			throw new Error("Unexpected exception mis-caught.", throwable);

		if(!(throwable instanceof Exception))
		{
			ReportingUtils.logError(throwable, "Non-exception throwable " + throwable.toString() + " caught from hdf5 resave thread. Wrapping.");
			throwable = new Exception("Wrapped throwable; see core log for details: " + throwable.getMessage(), throwable);
		}

		rethrow = (Exception)throwable;
	}
}