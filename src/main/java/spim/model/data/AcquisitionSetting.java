package spim.model.data;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class AcquisitionSetting
{
	final static String appName = "OpenSPIM AcquisitionSetting";
	final static String readerVersion = appName + " : 1.0";

	// Time points panel
	Boolean enabledTimePoints;
	String numTimePoints;
	String intervalTimePoints;
	Object intervalUnitTimePoints;

	// Position panel
	Boolean enabledPositions;
	ArrayList<PositionItem> positionItems;

	// Z-Stack panel
	Boolean enabledZStacks;

	// Acquisition order panel
	Object acquisitionOrder;

	// Channels panel
	Boolean enabledChannels;
	ArrayList<ChannelItem> channelItems;

	// Save Image panel
	Boolean enabledSaveImages;
	String directory;
	String filename;
	Object savingFormat;
	Boolean saveAsHDF5;

	public Boolean getEnabledTimePoints()
	{
		return enabledTimePoints;
	}

	public void setEnabledTimePoints( Boolean enabledTimePoints )
	{
		this.enabledTimePoints = enabledTimePoints;
	}

	public String getNumTimePoints()
	{
		return numTimePoints;
	}

	public void setNumTimePoints( String numTimePoints )
	{
		this.numTimePoints = numTimePoints;
	}

	public String getIntervalTimePoints()
	{
		return intervalTimePoints;
	}

	public void setIntervalTimePoints( String intervalTimePoints )
	{
		this.intervalTimePoints = intervalTimePoints;
	}

	public Object getIntervalUnitTimePoints()
	{
		return intervalUnitTimePoints;
	}

	public void setIntervalUnitTimePoints( String intervalUnitTimePoints )
	{
		this.intervalUnitTimePoints = intervalUnitTimePoints;
	}

	public Boolean getEnabledPositions()
	{
		return enabledPositions;
	}

	public void setEnabledPositions( Boolean enabledPositions )
	{
		this.enabledPositions = enabledPositions;
	}

	public ArrayList< PositionItem > getPositionItems()
	{
		return positionItems;
	}

	public void setPositionItems( ArrayList< PositionItem > positionItems )
	{
		this.positionItems = positionItems;
	}

	public Boolean getEnabledZStacks()
	{
		return enabledZStacks;
	}

	public void setEnabledZStacks( Boolean enabledZStacks )
	{
		this.enabledZStacks = enabledZStacks;
	}

	public Object getAcquisitionOrder()
	{
		return acquisitionOrder;
	}

	public void setAcquisitionOrder( String acquisitionOrder )
	{
		this.acquisitionOrder = acquisitionOrder;
	}

	public Boolean getEnabledChannels()
	{
		return enabledChannels;
	}

	public void setEnabledChannels( Boolean enabledChannels )
	{
		this.enabledChannels = enabledChannels;
	}

	public ArrayList< ChannelItem > getChannelItems()
	{
		return channelItems;
	}

	public void setChannelItems( ArrayList< ChannelItem > channelItems )
	{
		this.channelItems = channelItems;
	}

	public Boolean getEnabledSaveImages()
	{
		return enabledSaveImages;
	}

	public void setEnabledSaveImages( Boolean enabledSaveImages )
	{
		this.enabledSaveImages = enabledSaveImages;
	}

	public String getDirectory()
	{
		return directory;
	}

	public void setDirectory( String directory )
	{
		this.directory = directory;
	}

	public String getFilename()
	{
		return filename;
	}

	public void setFilename( String filename )
	{
		this.filename = filename;
	}

	public Object getSavingFormat()
	{
		return savingFormat;
	}

	public void setSavingFormat( String savingFormat )
	{
		this.savingFormat = savingFormat;
	}

	public Boolean getSaveAsHDF5()
	{
		return saveAsHDF5;
	}

	public void setSaveAsHDF5( Boolean saveAsHDF5 )
	{
		this.saveAsHDF5 = saveAsHDF5;
	}

	public AcquisitionSetting()
	{
	}

	public AcquisitionSetting( BooleanProperty enabledTimePoints, StringProperty numTimePoints, StringProperty intervalTimePoints, ObjectProperty intervalUnitTimePoints, BooleanProperty enabledPositions, ArrayList< PositionItem > positionItems, BooleanProperty enabledZStacks, ObjectProperty acquisitionOrder, BooleanProperty enabledChannels, ArrayList< ChannelItem > channelItems, BooleanProperty enabledSaveImages, StringProperty directory, StringProperty filename, ObjectProperty savingFormat, BooleanProperty saveAsHDF5 )
	{
		// Time points panel
		this.enabledTimePoints = enabledTimePoints.get();
		this.numTimePoints = numTimePoints.get();
		this.intervalTimePoints = intervalTimePoints.get();
		this.intervalUnitTimePoints = intervalUnitTimePoints.get();

		// Position panel
		this.enabledPositions = enabledPositions.get();
		this.positionItems = positionItems;

		// Z-Stack panel
		this.enabledZStacks = enabledZStacks.get();

		// Acquisition order
		this.acquisitionOrder = acquisitionOrder.get();

		// Channels panel
		this.enabledChannels = enabledChannels.get();
		this.channelItems = channelItems;

		// Save Image panel
		this.enabledSaveImages = enabledSaveImages.get();
		this.directory = directory.get();
		this.filename = filename.get();
		this.savingFormat = savingFormat.get();
		this.saveAsHDF5 = saveAsHDF5.get();
	}

	public static AcquisitionSetting load( File file ) {
		XMLDecoder e;
		try
		{
			e = new XMLDecoder(
					new BufferedInputStream(
							new FileInputStream( file ) ) );
		}
		catch ( FileNotFoundException e1 )
		{
			System.err.println( e1.getMessage() );
			return null;
		}

		String version = (String) e.readObject();

		if( null == version || !readerVersion.equals( version ) )
		{
			throw new IllegalArgumentException( "OpenSPIM AcquisitionSetting version mismatch" );
		}

		AcquisitionSetting setting = (AcquisitionSetting) e.readObject();

		e.close();

		return setting;
	}

	public static boolean save( File file, AcquisitionSetting setting ) {
		XMLEncoder e = null;
		try
		{
			e = new XMLEncoder(
					new BufferedOutputStream(
							new FileOutputStream( file ) ) );
		}
		catch ( FileNotFoundException e1 )
		{
			e1.printStackTrace();
			return false;
		}

		assert e != null;
		e.writeObject( readerVersion );

		e.writeObject( setting );

//		// Time points panel
//		e.writeObject( enabledPositions );
//		e.writeObject( numTimePoints );
//		e.writeObject( intervalTimePoints );
//		e.writeObject( intervalUnitTimePoints );
//
//		// Position panel
//		e.writeObject( enabledPositions );
//		e.writeObject( positionItems );
//
//		// Z-Stack panel
//		e.writeObject( enabledZStacks );
//
//		// Acquisition order panel
//		e.writeObject( acquisitionOrder );
//
//		// Channels panel
//		e.writeObject( enabledChannels );
//		e.writeObject( channelItems );
//
//		// Save Image panel
//		e.writeObject( enabledSaveImages );
//		e.writeObject( directory );
//		e.writeObject( filename );
//		e.writeObject( savingFormat );
//		e.writeObject( saveAsHDF5 );

		e.close();
		return true;
	}
}