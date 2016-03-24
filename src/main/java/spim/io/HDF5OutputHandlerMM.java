package spim.io;

import mmcorej.CMMCore;
import spim.acquisition.Row;

import java.io.File;

/**
 * HDF5OutputHandler for MicroManager version
 */
public class HDF5OutputHandlerMM extends HDF5OutputHandler
{
	public HDF5OutputHandlerMM( CMMCore iCore, File outDir, String filename, Row[] acqRows, int iTimeSteps )
	{
		super(outDir, (int) iCore.getImageWidth(), (int) iCore.getImageHeight(), iTimeSteps, acqRows.length);

		setPixelDepth( iCore.getImageBitDepth() );
		setPixelSizeUm( iCore.getPixelSizeUm() );

		int[] zSizes = new int[getAngleSize()];
		double[] zStepSize = new double[getAngleSize()];

		// Setup ViewSetup
		for(int i = 0; i < getAngleSize(); i++)
		{
			Row row = acqRows[i];
			zSizes[i] = row.getDepth();
			zStepSize[i] = Math.max(row.getZStepSize(), 1.0D);

			ij.IJ.log("Row: "+ i);
			ij.IJ.log("    Depth: "+ zSizes[i]);
			ij.IJ.log("    Step: "+ zStepSize[i]);
		}

		setzSizes( zSizes );
		setzStepSize( zStepSize );

		init( filename );
	}
}
