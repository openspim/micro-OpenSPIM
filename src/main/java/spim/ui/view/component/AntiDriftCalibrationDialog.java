package spim.ui.view.component;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class AntiDriftCalibrationDialog extends Alert {

	private StringProperty stepSizeProperty;

	public AntiDriftCalibrationDialog( spim.hardware.Stage stage ) {
		super(Alert.AlertType.INFORMATION);
		setTitle( "Rotator Calibration" );

		DialogPane dialogPane = getDialogPane();

		Label stepSizeLabel = new Label( "X:" );
		String stepSizeString = stage != null ? stage.getStepSize() + "" : "";
		TextField stepSize = new TextField( stepSizeString );
		stepSizeProperty = stepSize.textProperty();


		HBox stepBox = new HBox( 10, stepSizeLabel, stepSize );
		stepBox.setAlignment( Pos.CENTER_LEFT );

		VBox controls = new VBox(  stepBox );

		BorderPane main = new BorderPane();
		main.setCenter( controls );

		dialogPane.setHeaderText( "Please, move the X,Y,Z devices and check +/- of each axes." );
		dialogPane.setContent( main );
		setResizable( true );

		//		ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		//		dialogPane.getButtonTypes().addAll(okButtonType, cancelButtonType);
		dialogPane.getButtonTypes().addAll(cancelButtonType);
	}

	public double getReturnResult()
	{
//		return returnResult;
		return Double.parseDouble(stepSizeProperty.getValue());
	}
}
