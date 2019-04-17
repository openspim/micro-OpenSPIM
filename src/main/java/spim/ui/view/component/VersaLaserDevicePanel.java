package spim.ui.view.component;

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
		}
		if(laser.getLaserB() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserB() ) );
		}
		if(laser.getLaserC() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserC() ) );
		}
		if(laser.getLaserD() != null)
		{
			getChildren().add( new LaserDevicePanel( laser.getLaserD() ) );
		}
	}
}
