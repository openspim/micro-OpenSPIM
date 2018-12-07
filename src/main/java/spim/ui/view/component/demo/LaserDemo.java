package spim.ui.view.component.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import spim.ui.view.component.LaserDevicePanel;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class LaserDemo extends Application
{
	@Override public void start( Stage primaryStage ) throws Exception
	{
		LaserDevicePanel panel = new LaserDevicePanel( null, 594 );

		Scene scene = new Scene(panel,
				javafx.scene.paint.Color.WHITE);

		primaryStage.setTitle(this.getClass().getSimpleName());
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	public static void main( final String[] args )
	{
		Application.launch( LaserDemo.class );
	}
}
