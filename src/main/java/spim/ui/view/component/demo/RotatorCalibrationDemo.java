package spim.ui.view.component.demo;

import javafx.application.Application;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import spim.ui.view.component.RotatorCalibrationDialog;

import java.util.Optional;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2020
 */
public class RotatorCalibrationDemo extends Application
{

	@Override public void start( Stage primaryStage ) throws Exception
	{
		RotatorCalibrationDialog dlg = new RotatorCalibrationDialog( null );
		dlg.showAndWait()
			.filter(response -> response == ButtonType.OK)
			.ifPresent(response -> System.out.println( dlg.getReturnResult() ));
//		Optional< ButtonType > result = dlg.showAndWait();
//
//		if(result.isPresent() && result.get().getButtonData() == ButtonType.OK.getButtonData()) {
//			System.out.println(dlg.getReturnResult());
//		} else {
//			System.out.println(false);
//		}
	}

	public static void main( final String[] args )
	{
		launch( args );
	}
}
