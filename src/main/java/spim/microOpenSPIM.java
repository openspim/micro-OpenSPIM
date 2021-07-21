package spim;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import loci.common.DebugTools;
import org.micromanager.Studio;
import org.micromanager.events.GUIRefreshEvent;
import spim.mm.MMUtils;
import spim.mm.MicroManager;
import spim.ui.view.component.HalcyonMain;

/**
 * Description: Entry point of µOpenSPIM
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class microOpenSPIM extends Application
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
		ObjectProperty<GUIRefreshEvent> mmStudioGUIRefreshEventProperty = new SimpleObjectProperty<>();
		MicroManager.init( stage, mmStudioProperty, mmStudioGUIRefreshEventProperty );
	}

	public static void main( final String[] args )
	{
		launch( HalcyonMain.class );
	}
}
