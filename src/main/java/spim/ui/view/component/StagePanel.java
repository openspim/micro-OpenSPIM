package spim.ui.view.component;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;
import spim.ui.view.component.slider.StageSlider;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2018
 */
public class StagePanel extends BorderPane
{
	private SPIMSetup spimSetup;

	private HashMap< StageUnit.Stage, StageUnit > stageMap = null;

	public StagePanel()
	{
		init();
	}

	public StagePanel( SPIMSetup setup )
	{
		this.spimSetup = setup;
		init();
	}

	public void init()
	{
		stageMap = new HashMap<>();

		setCenter( createControls() );

		ScheduledExecutorService executor = Executors.newScheduledThreadPool( 5 );

		if(spimSetup == null) {
			System.out.println("SPIM setup is null");
			executor.scheduleAtFixedRate( () -> {

				for ( StageUnit.Stage stage : stageMap.keySet() )
				{
					if ( stageMap.get( stage ).get( StageUnit.BooleanState.Enable ).get()  &&
							!stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).get() )
					{
						double target = stageMap.get( stage ).targetValueProperty().getValue().doubleValue();
						double device = stageMap.get( stage ).deviceValueProperty().getValue().doubleValue();
						double error = target - device;

						double granularity = stage == StageUnit.Stage.R ? 0.5 : 3;
						double newDevice = device + granularity * signum( error );

						Platform.runLater( () -> {
							if ( abs( error ) > granularity )
								stageMap.get( stage ).deviceValueProperty().setValue( newDevice );
							else if ( !stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).get() )
								stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).set( true );
						} );
					}
				}
			}, 500, 5, TimeUnit.MILLISECONDS );
		}
		else
		{
			executor.scheduleAtFixedRate( () -> {

				for ( StageUnit.Stage stage : stageMap.keySet() )
				{
					if ( stageMap.get( stage ).get( StageUnit.BooleanState.Enable ).get() &&
							!stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).get() )
					{
						double target = stageMap.get( stage ).targetValueProperty().getValue().doubleValue();

						double device = 0d;
						// read the value from the device
						switch ( stage ) {
							case R: device = spimSetup.getThetaStage().getPosition() + 180.0;
								break;
							case X: device = spimSetup.getXStage().getPosition();
								break;
							case Y: device = spimSetup.getYStage().getPosition();
								break;
							case Z: device = spimSetup.getZStage().getPosition();
								break;
						}

						stageMap.get( stage ).deviceValueProperty().setValue( device );

						double error = target - device;
						double granularity = stage == StageUnit.Stage.R ? 2.5 : 1.5;

						Platform.runLater( () -> {
							if ( abs( error ) < granularity )
								stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).set( true );
						} );
					}
				}
			}, 500, 5, TimeUnit.MILLISECONDS );
		}
	}

	public DoubleProperty getZValueProperty() {
		SimpleDoubleProperty property = new SimpleDoubleProperty();

		double max = spimSetup.getZStage().getMaxPosition();
		double min = spimSetup.getZStage().getMinPosition();

		stageMap.get( StageUnit.Stage.Z ).deviceValueProperty().addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				property.set( min / max * newValue.doubleValue() );
			}
		} );

		return property;
	}

	private StageUnit createStageControl( String labelString, StageUnit.Stage stage )
	{
		StageUnit unit;

		if( null == spimSetup ) {
			unit = new StageUnit( labelString, stage == StageUnit.Stage.R, null );
		}
		else {
			Stage stageDevice = null;

			switch ( stage ) {
				case R: stageDevice = spimSetup.getThetaStage();
					spimSetup.isConnected( SPIMSetup.SPIMDevice.STAGE_THETA );
					break;
				case X: stageDevice = spimSetup.getXStage();
					spimSetup.isConnected( SPIMSetup.SPIMDevice.STAGE_X );
					break;
				case Y: stageDevice = spimSetup.getYStage();
					spimSetup.isConnected( SPIMSetup.SPIMDevice.STAGE_Y );
					break;
				case Z: stageDevice = spimSetup.getZStage();
					spimSetup.isConnected( SPIMSetup.SPIMDevice.STAGE_Z );
					break;
			}

			unit = new StageUnit( labelString, stage == StageUnit.Stage.R, stageDevice );

			final Property< Number > targetProperty = unit.targetValueProperty();

			targetProperty.addListener( ( observable, oldValue, newValue ) ->
			{
				switch ( stage ) {
					case R:
						spimSetup.getThetaStage().setPosition( newValue.doubleValue() - 180.0 );
						break;
					case X: spimSetup.getXStage().setPosition( newValue.doubleValue() );
						break;
					case Y: spimSetup.getYStage().setPosition( newValue.doubleValue() );
						break;
					case Z: spimSetup.getZStage().setPosition( newValue.doubleValue() );
						break;
				}
			} );
		}

		stageMap.put( stage, unit );

		return unit;
	}

	private VBox createControls()
	{
		VBox controls = new VBox( 10,
				// rotate,
				createStageControl( "Stage R (\u00b5-degree)",
						StageUnit.Stage.R ),
				createStageControl( "Stage X (\u00b5m)",
						StageUnit.Stage.X ),
				createStageControl( "Stage Y (\u00b5m)",
						StageUnit.Stage.Y ),
				createStageControl( "Stage Z (\u00b5m)",
						StageUnit.Stage.Z ) );
		controls.setPadding( new Insets( 10 ) );
		return controls;
	}
}
