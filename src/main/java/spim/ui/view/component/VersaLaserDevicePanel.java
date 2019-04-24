package spim.ui.view.component;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import spim.hardware.VersaLase;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2019
 */
public class VersaLaserDevicePanel extends VBox
{
	public VersaLaserDevicePanel( VersaLase laser )
	{
		if(laser.getLaserA() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserA() ) );
			getChildren().add( createInfoPane( laser.getLaserA() ) );
		}
		if(laser.getLaserB() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserB() ) );
			getChildren().add( createInfoPane( laser.getLaserB() ) );
		}
		if(laser.getLaserC() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserC() ) );
			getChildren().add( createInfoPane( laser.getLaserC() ) );
		}
		if(laser.getLaserD() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserD() ) );
			getChildren().add( createInfoPane( laser.getLaserD() ) );
		}
	}

	private GridPane createInfoPane( VersaLase.VersaLaseLaser laser )
	{
		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		gridpane.addRow( 0, new Label( "Emission: " + laser.getLaserEmission() ) );
		gridpane.addRow( 1, new Label( "Digital Modulation: " + laser.getDigitalModulation() ) );
		gridpane.addRow( 2, new Label( "Analog Modulation(External Power Control): " + laser.getAnalogModulation() ) );

		return gridpane;
	}
}
