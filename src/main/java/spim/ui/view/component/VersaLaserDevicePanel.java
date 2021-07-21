package spim.ui.view.component;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.micromanager.Studio;
import spim.hardware.SPIMSetup;
import spim.hardware.VersaLase;

import java.util.Arrays;
import java.util.Objects;

/**
 * Description: VersaLase Device Panel consists of four lasers embedded in one box
 * Sub modular lasers can be accessed by "LASER_A_", "LASER_B_", "LASER_C_" and "LASER_D_".
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2019
 */
public class VersaLaserDevicePanel extends VBox implements SPIMSetupInjectable
{
	LaserDevicePanel laserPanels[] = new LaserDevicePanel[4];

	public VersaLaserDevicePanel( VersaLase laser )
	{
		if(laser.getLaserA() != null)
		{
			laserPanels[ 0 ] = new LaserDevicePanel( laser.getLaserA() );
			getChildren().add( laserPanels[ 0 ] );
			getChildren().add( createInfoPane( laser.getLaserA() ) );
		}
		if(laser.getLaserB() != null)
		{
			laserPanels[ 1 ] = new LaserDevicePanel( laser.getLaserB() );
			getChildren().add( laserPanels[ 1 ] );
			getChildren().add( createInfoPane( laser.getLaserB() ) );
		}
		if(laser.getLaserC() != null)
		{
			laserPanels[ 2 ] = new LaserDevicePanel( laser.getLaserC() );
			getChildren().add( laserPanels[ 2 ] );
			getChildren().add( createInfoPane( laser.getLaserC() ) );
		}
		if(laser.getLaserD() != null)
		{
			laserPanels[ 3 ] = new LaserDevicePanel( laser.getLaserD() );
			getChildren().add( laserPanels[ 3 ] );
			getChildren().add( createInfoPane( laser.getLaserD() ) );
		}
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio )
	{
		Arrays.stream( laserPanels ).filter( Objects::nonNull ).forEach( c -> c.setSetup( setup, studio ) );
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
