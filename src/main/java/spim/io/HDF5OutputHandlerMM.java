package spim.io;

import mmcorej.CMMCore;
import spim.acquisition.Row;

import java.io.File;

/**
 * HDF5OutputHandler for MicroManager version
 */
public class HDF5OutputHandlerMM extends HDF5OutputHandler
{
	public HDF5OutputHandlerMM( CMMCore iCore, File outDir, String filename, Row[] acqRows, int iTimeSteps, int tileCount )
	{
		super(outDir, (int) iCore.getImageWidth(), (int) iCore.getImageHeight(), iTimeSteps, acqRows.length, tileCount );

		setPixelDepth( iCore.getImageBitDepth() );
		setPixelSizeUm( iCore.getPixelSizeUm() );

		int[] zSizes = new int[getRowSize()];
		double[] zStepSize = new double[getRowSize()];
		double[][] tileTransformationMatrix = new double[getRowSize()][3];

		// Setup ViewSetup
		for(int i = 0; i < getRowSize(); i++)
		{
			Row row = acqRows[i];
			zSizes[i] = row.getDepth();

			if(tileCount > 1)
			{
				zStepSize[ i ] = 3.9D;

				// Calculate actual pixel sizes
				tileTransformationMatrix[ i ][ 0 ] = row.getX() / iCore.getPixelSizeUm();
				tileTransformationMatrix[ i ][ 1 ] = row.getY() / iCore.getPixelSizeUm();
				tileTransformationMatrix[ i ][ 2 ] = row.getZStartPosition() / iCore.getPixelSizeUm();

//				ij.IJ.log( "GetX: " + row.getX() );
//				ij.IJ.log( "GetY: " + row.getY() );
//				ij.IJ.log( "GetZStart: " + row.getZStartPosition() );
//
			}
			else
				zStepSize[i] = Math.max(row.getZStepSize(), 1.0D);

//			ij.IJ.log( "Row: " + i );
//			ij.IJ.log( "    Depth: " + zSizes[ i ] );
//			ij.IJ.log( "    Step: " + zStepSize[ i ] );
		}

		setzSizes( zSizes );
		setzStepSize( zStepSize );
		setTileTransformMatrix( tileTransformationMatrix );

		init( filename );
	}
}
