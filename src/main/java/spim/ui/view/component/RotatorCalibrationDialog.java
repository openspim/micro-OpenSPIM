package spim.ui.view.component;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

/**
 * Description: RotatorCalibrationDialog calibrates the rotator for 360 degree.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2020
 */
public class RotatorCalibrationDialog extends Alert
{
	private double returnResult = 0;
	private StringProperty stepSizeProperty;
	ScheduledExecutorService executor;

	public RotatorCalibrationDialog( spim.hardware.Stage stage )
	{
		super(Alert.AlertType.INFORMATION);
		setTitle( "Rotator Calibration" );

		DialogPane dialogPane = getDialogPane();

		//		ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		//		dialogPane.getButtonTypes().addAll(okButtonType, cancelButtonType);
		dialogPane.getButtonTypes().addAll(cancelButtonType);

		final StageUnit unit = new StageUnit( "Rotator ", false, true, stage );

		Label stepSizeLabel = new Label( "Calculated StepSize:" );
		String stepSizeString = stage != null ? stage.getStepSize() + "" : "";
		TextField stepSize = new TextField( stepSizeString );
		stepSizeProperty = stepSize.textProperty();
		unit.deviceValueProperty().addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				if(newValue.doubleValue() > 0) {
					// theta * Math.PI / 180.0D
					returnResult = Math.round(360.0 / newValue.doubleValue() * 100d) / 100d;
					stepSize.setText( String.format("%.2f", returnResult) );
				}
			}
		} );

		unit.targetValueProperty().addListener( ( observable, oldValue, newValue ) -> {
			unit.get( StageUnit.BooleanState.Ready ).setValue( false );
			if(stage != null)
				stage.setPosition( newValue.doubleValue() );
		} );

		HBox stepBox = new HBox( 10, stepSizeLabel, stepSize );
		stepBox.setAlignment( Pos.CENTER_LEFT );

		VBox controls = new VBox( unit, stepBox );

		BorderPane main = new BorderPane();
		main.setCenter( controls );

		dialogPane.setHeaderText( "Please, move the knob at the position of 360 degree of your device." );
		dialogPane.setContent( main );
		setResizable( true );

		initExecutor();

		if(stage == null) {
			executor.scheduleAtFixedRate( () -> {
				if ( unit.get( StageUnit.BooleanState.Enable ).get()  &&
						!unit.get( StageUnit.BooleanState.Ready ).get() )
				{
					double target = unit.targetValueProperty().getValue().doubleValue();
					double device = unit.deviceValueProperty().getValue().doubleValue();
					double error = target - device;

					double granularity = 3;
					double newDevice = device + granularity * signum( error );

//					Platform.runLater( () -> {
						if ( abs( error ) > granularity )
							unit.deviceValueProperty().setValue( newDevice );
						else if ( !unit.get( StageUnit.BooleanState.Ready ).get() )
							unit.get( StageUnit.BooleanState.Ready ).set( true );
//					} );
				}
			}, 500, 500, TimeUnit.MILLISECONDS );
		} else {
			executor.scheduleAtFixedRate( () -> {
				monitorSPIM(unit);
			}, 500, 500, TimeUnit.MILLISECONDS );
		}

		this.setOnCloseRequest( new EventHandler< DialogEvent >()
		{
			@Override public void handle( DialogEvent event )
			{
				if(stage != null)
					stage.setPosition( 0d );
				shutdownExecutor();
			}
		} );
	}

	public double getReturnResult()
	{
//		return returnResult;
		return Double.parseDouble(stepSizeProperty.getValue());
	}

	private void monitorSPIM(StageUnit unit) {
		if ( unit.get( StageUnit.BooleanState.Enable ).get() &&
				!unit.get( StageUnit.BooleanState.Ready ).get() )
		{
			double target = unit.targetValueProperty().getValue().doubleValue();

			double device = unit.getStageDevice().getPosition();

			double error = target - device;
			double granularity = 1.5;

			double finalDevice = device;
//			Platform.runLater( () -> {
				unit.deviceValueProperty().setValue( finalDevice );

				if ( abs( error ) < granularity )
					unit.get( StageUnit.BooleanState.Ready ).set( true );
//			} );
		}
	}

	private void shutdownExecutor() {
		if(executor != null) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
			}
		}
		executor = null;
	}

	private void initExecutor() {
		shutdownExecutor();

		executor = Executors.newScheduledThreadPool( 5 );
	}
}
