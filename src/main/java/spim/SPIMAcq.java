package spim;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import loci.common.DebugTools;
import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;

import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.plugin.Plugin;
import spim.ui.view.component.HalcyonMain;
import spim.ui.view.component.StagePanel;
import spim.hardware.SPIMSetup;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2018
 */
//@Plugin(type = MenuPlugin.class)
public class SPIMAcq extends Application
//		implements MenuPlugin
{
	private final static String mTitle = "SPIM Acquisition Version 2.0 beta";
	private CMMCore mmc;
	private Studio gui;
	static Stage mStage;

	protected SPIMSetup setup;
	HalcyonMain halcyonMain;

//	@Override public void setContext( Studio studio )
//	{
//		gui = studio;
//		mmc = studio.core();
//	}
//
//	@Override public String getName()
//	{
//		return "OpenSPIM v2";
//	}
//
//	@Override public String getHelpText()
//	{
//		return "Open Source SPIM acquisition v2.0";
//	}
//
//	@Override public String getVersion()
//	{
//		return "2.0";
//	}
//
//	@Override public String getCopyright()
//	{
//		return "Copyright 2018 HongKee Moon GPLv2 or later";
//	}
//
//	@Override public String getSubMenu()
//	{
//		return "";
//	}
//
//	@Override public void onPluginSelected()
//	{
//		run(null, mmc);
//	}

	@Override
	public void start( Stage primaryStage )
	{
		StagePanel stageDevicePanel =
				new StagePanel();

		Scene scene = new Scene( stageDevicePanel,
				javafx.scene.paint.Color.WHITE );

		primaryStage.setOnCloseRequest( new EventHandler< WindowEvent >()
		{
			@Override public void handle( WindowEvent event )
			{

			}
		} );

		primaryStage.setTitle( mTitle );
		primaryStage.setScene( scene );
		primaryStage.show();

		mStage = primaryStage;
	}

	public void run( Stage primaryStage, CMMCore core )
	{
		DebugTools.enableLogging( "OFF" );
		Platform.setImplicitExit( false );

		if(null == setup)
			setup = SPIMSetup.createDefaultSetup( core );

		if(null == setup)
			System.err.println("SPIM setup is null after call");

		if( null != halcyonMain )
		{
//			Platform.runLater( new Runnable()
//			{
//				@Override public void run()
//				{
//					halcyonMain.show();
//				}
//			} );
			com.sun.javafx.application.PlatformImpl.startup( new Runnable()
			{
				@Override public void run()
				{
					halcyonMain = new HalcyonMain();
					halcyonMain.show( mStage, gui );
				}
			} );
		}
		else
		{
			com.sun.javafx.application.PlatformImpl.startup( new Runnable()
			{
				@Override public void run()
				{
					if ( null == primaryStage && mStage == null )
						mStage = new Stage();
					else
						mStage = primaryStage;

//					if ( !( setup.is3DMicroscope() && setup.hasAngle() ) )
//					{
//						Alert alert = new Alert( Alert.AlertType.WARNING );
//						alert.setTitle( "Information Dialog" );
//						alert.setHeaderText( null );
//						alert.setContentText( "Your setup appears to be invalid. Please make sure you have a camera and 4D stage set up.\nYou may need to restart Micro-Manager for the OpenSPIM plugin to detect a correct setup." );
//
//						alert.show();
//					}

					Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
					halcyonMain = new HalcyonMain();
					halcyonMain.show( mStage, gui );
				}
			} );
		}
	}

	public void show(CMMCore core)
	{
//		if ( !( setup.is3DMicroscope() && setup.hasAngle() ) )
//		{
//			Alert alert = new Alert( Alert.AlertType.WARNING );
//			alert.setTitle( "Information Dialog" );
//			alert.setHeaderText( null );
//			alert.setContentText( "Your setup appears to be invalid. Please make sure you have a camera and 4D stage set up.\nYou may need to restart Micro-Manager for the OpenSPIM plugin to detect a correct setup." );
//
//			alert.showAndWait();
//		}
	}

	public static void main( final String[] args )
	{
		MMStudio app = MMStudio.getInstance();

		if (app == null) {
			app = new MMStudio(true);
			MMStudio.getInstance().uiManager().frame().setVisible(true);
			ReportingUtils.SetContainingFrame(MMStudio.getInstance().uiManager().frame());
		}
		SPIMAcq plugin = new SPIMAcq();
//		plugin.setContext( app );
//		plugin.getSubMenu();
	}
}
