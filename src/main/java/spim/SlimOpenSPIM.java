package spim;

import ij.ImageJ;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import loci.common.DebugTools;
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
	static Stage mStage;
	public static void main( final String[] args )
	{

//		MMStudio app = MMStudio.getInstance();
//
//		if (app == null) {
//			app = new MMStudio(true);
//			MMStudio.getFrame().setVisible(true);
//			ReportingUtils.SetContainingFrame(MMStudio.getFrame());
//		}
//		SPIMAcq plugin = new SPIMAcq();
//		plugin.setContext( app );
//		plugin.getSubMenu();
		// verify that system libraries are loaded
//		if (!MMUtils.isSystemLibrairiesLoaded())
//		{
//			// load micro manager libraries
//			if (!MMUtils.fixSystemLibrairies())
//				return;
//		}

//		MicroManager.init();
		SlimOpenSPIM plugin = new SlimOpenSPIM();
		com.sun.javafx.application.PlatformImpl.startup( new Runnable()
		{
			@Override public void run()
			{
				DebugTools.enableLogging( "OFF" );
				Platform.setImplicitExit( false );

				MicroManager.orgUserDir = System.getProperty( "user.dir" );
				MMUtils.host = plugin.getHostServices();
				if (!MMUtils.isSystemLibrairiesLoaded())
				{
					// load micro manager libraries
					if (!MMUtils.fixSystemLibrairies( plugin.getStage() ))
						return;
				}

				MicroManager.init( plugin.getStage(), null );
			}
		} );

	}

	Stage getStage() {
		if ( mStage == null )
			mStage = new Stage();
		return mStage;
	}

	@Override
	public void start( Stage arg0 ) throws Exception {
		MMUtils.host = getHostServices();
		mStage = arg0;

		if (!MMUtils.isSystemLibrairiesLoaded())
		{
			// load micro manager libraries
			if (!MMUtils.fixSystemLibrairies(arg0))
				return;
		}

//		MicroManager.init();
	}
}
