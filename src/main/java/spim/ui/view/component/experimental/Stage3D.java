package spim.ui.view.component.experimental;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: July 2021
 */
public class Stage3D extends Application {
	@Override
	public void start( Stage stage ) {
		stage.setOnCloseRequest(event -> System.exit(0));

		Stage3DPanel panel = new Stage3DPanel();

		Scene scene = new Scene(panel,
				javafx.scene.paint.Color.WHITE);

		stage.setTitle(this.getClass().getSimpleName());
		stage.setScene(scene);
		stage.show();
	}

	public static void main( final String[] args )
	{
		launch( args );
	}
}
