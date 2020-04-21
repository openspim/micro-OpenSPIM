package spim;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import loci.common.DebugTools;
import org.micromanager.Studio;
import spim.mm.MMUtils;
import spim.mm.MicroManager;
import spim.ui.view.component.HalcyonMain;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class SlimOpenSPIM extends Application
{
	@Override
	public void start( Stage stage ) {
		stage.setOnCloseRequest(event -> System.exit(0));

		DebugTools.enableLogging( "OFF" );
		Platform.setImplicitExit( false );

		MicroManager.orgUserDir = System.getProperty( "user.dir" );
		if (!MMUtils.isSystemLibrairiesLoaded())
		{
			// load micro manager libraries
			if (!MMUtils.fixSystemLibrairies( stage ))
				return;
		}

		ObjectProperty< Studio > mmStudioProperty = new SimpleObjectProperty<>();
		MicroManager.init( stage, mmStudioProperty );
	}

	public static void main( final String[] args )
	{
//		launch( args );
		launch( HalcyonMain.class );
	}
}
