package spim.progacq;

import java.io.File;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import org.micromanager.utils.ReportingUtils;

import loci.common.DataTools;
import loci.common.services.ServiceFactory;

import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mmcorej.CMMCore;

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.PositiveInteger;

public class OMETIFFHandler implements AcqOutputHandler {
	private File outputDirectory;

	private IMetadata meta;
	private int imageCounter, sliceCounter;
	private IFormatWriter writer;

	private CMMCore core;
	private int stacks, timesteps;
	private AcqRow[] acqRows;
	private double deltat;
	
	public OMETIFFHandler(CMMCore iCore, File outDir, String xyDev,
			String cDev, String zDev, String tDev, AcqRow[] acqRows,
			int iTimeSteps, double iDeltaT) {

		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		imageCounter = -1;
		sliceCounter = 0;

		stacks = acqRows.length;
		core = iCore;
		timesteps = iTimeSteps;
		deltat = iDeltaT;
		outputDirectory = outDir;
		this.acqRows = acqRows;

		try {
			meta = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata();

			meta.createRoot();

			meta.setDatasetID(MetadataTools.createLSID("Dataset", 0), 0);

			for (int image = 0; image < stacks; ++image) {
				meta.setImageID(MetadataTools.createLSID("Image", image), image);

				AcqRow row = acqRows[image];
				int depth = row.getDepth();

				meta.setPixelsID(MetadataTools.createLSID("Pixels", 0), image);
				meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, image);
				meta.setPixelsBinDataBigEndian(Boolean.FALSE, image, 0);
				meta.setPixelsType(core.getImageBitDepth() == 8 ? PixelType.UINT8 : PixelType.UINT16, image);
				meta.setChannelID(MetadataTools.createLSID("Channel", 0), image, 0);
				meta.setChannelSamplesPerPixel(new PositiveInteger(1), image, 0);

				for (int t = 0; t < timesteps; ++t) {
					String fileName = makeFilename(image, t);
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

				meta.setPixelsPhysicalSizeX(new PositiveFloat(core.getPixelSizeUm()*core.getImageWidth()), image);
				meta.setPixelsPhysicalSizeY(new PositiveFloat(core.getPixelSizeUm()*core.getImageHeight()), image);
				meta.setPixelsPhysicalSizeZ(new PositiveFloat(1d), image);
				meta.setPixelsTimeIncrement(new Double(deltat), image);
			}

			writer = new ImageWriter().getWriter(makeFilename(0, 0));

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

	private static String makeFilename(int angleIndex, int timepoint) {
		return String.format("spim_TL%02d_Angle%01d.ome.tiff", (timepoint + 1), angleIndex);
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
	public void beginStack(int axis) throws Exception {
		ReportingUtils.logMessage("Beginning stack along dimension " + axis);

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
	public void processSlice(ImageProcessor ip, double X, double Y, double Z, double theta, double deltaT)
			throws Exception {
		long bitDepth = core.getImageBitDepth();
		byte[] data = bitDepth == 8 ?
			(byte[])ip.getPixels() :
			DataTools.shortsToBytes((short[])ip.getPixels(), true);

		int image = imageCounter % stacks;
		int timePoint = imageCounter / stacks;
		int plane = timePoint*acqRows[image].getDepth() + sliceCounter;

		meta.setPlanePositionX(X, image, plane);
		meta.setPlanePositionY(Y, image, plane);
		meta.setPlanePositionZ(Z, image, plane);
		meta.setPlaneTheZ(new NonNegativeInteger(sliceCounter), image, plane);
		meta.setPlaneTheT(new NonNegativeInteger(timePoint), image, plane);
		meta.setPlaneDeltaT(deltaT, image, plane);

		storeDouble(image, plane, 0, "Theta", theta);

		try {
			writer.saveBytes(plane, data);
		} catch(java.io.IOException ioe) {
			finalizeStack(0);
			if(writer != null)
				writer.close();
			throw new Exception("Error writing OME-TIFF.", ioe);
		}

		++sliceCounter;
	}

	@Override
	public void finalizeStack(int depth) throws Exception {
		ReportingUtils.logMessage("Finished stack along dimension " + depth);
	}

	@Override
	public void finalizeAcquisition() throws Exception {
		if(writer != null)
			writer.close();

		imageCounter = 0;

		writer = null;
	}


	private static long WIDTH = 256;
	private static long HEIGHT = 256;
	private static long BITDEPTH = 16;
	private static double UMPERPIX = (10D / 23D);

	public static void main(String[] args) {
		if(args.length < 5) {
			args = new String[] {
				"OMETIFFHandler.java",
				"2",
				"5",
				"2",
				"C:\\Documents and Settings\\LOCI\\Desktop\\blehtest\\"
			};
		}

		// Hopefully this doesn't do anything horrible.
		// EDIT: Well, this does something horrible. :(
		class mockCore extends CMMCore {
			mockCore() {
			}
			public long getImageBitDepth() {
				return BITDEPTH;
			}

			public long getImageWidth() {
				return WIDTH;
			}

			public long getImageHeight() {
				return HEIGHT;
			}

			public double getPixelSizeUm() {
				return UMPERPIX;
			}
		}

		int stacks = Integer.parseInt(args[1]);
		int depth = Integer.parseInt(args[2]);
		int timePoints = Integer.parseInt(args[3]);

		File outDir = new File(args[4]);

		AcqRow[] rows = new AcqRow[stacks];
		for(int xyt=0; xyt < stacks; ++xyt)
			rows[xyt] = new AcqRow(new String[] {"Picard XY Stage", "Picard Twister", "t"},
					"Picard Stage", "1:1:" + depth);

		mockCore core = new mockCore();

		OMETIFFHandler handler = new OMETIFFHandler(core, outDir, "Picard XY Stage", "Picard Twister", "Picard Stage", "t", rows, timePoints, 13);

		for(int t=0; t < timePoints; ++t) {
			for(int xyt=0; xyt < stacks; ++xyt) {
				try {
					handler.beginStack(0);
				} catch(Exception e) {
					ReportingUtils.logException("Error beginning stack: " + t + " " + xyt, e);
					return;
				}

				for(int z=1; z <= depth; ++z) {
					short[] pix = new short[(int) (WIDTH*HEIGHT)];

					for(int y=0; y < HEIGHT; ++y)
						for(int x=0; x < WIDTH; ++x)
							pix[(int) (y*WIDTH + x)] = (short) (Math.pow(2,BITDEPTH-2)*(Math.sin((x - xyt*8D)*Math.PI*8/WIDTH)+1)*(Math.sin((y - t*8D)*Math.PI*8/HEIGHT)+1)*z/depth);

					try {
						handler.processSlice(new ShortProcessor((int) WIDTH, (int) HEIGHT, pix, null), 0, 0, z, xyt, t);
					} catch(Exception e) {
						ReportingUtils.logException("Error processing slice: " + t + " " + xyt + " " + z, e);
						return;
					}
				}

				try {
					handler.finalizeStack(0);
				} catch(Exception e) {
					ReportingUtils.logException("Error finalizing stack: " + t + " " + xyt, e);
					return;
				}
			}
		}

		try {
			handler.finalizeAcquisition();
		} catch (Exception e) {
			ReportingUtils.logException("Error finalizing acquisition.", e);
		}

		core.delete();
	}
}
