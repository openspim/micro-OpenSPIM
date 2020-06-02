package spim.ui.view.component;

import eu.hansolo.enzo.common.SymbolType;
import eu.hansolo.enzo.simpleindicator.SimpleIndicator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import spim.ui.view.component.iconswitch.IconSwitch;
import spim.ui.view.component.slider.StageSlider;

import java.util.HashMap;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2018
 */
public class StageUnit extends Region
{
	enum Stage
	{
		R, X, Y, Z
	}

	enum BooleanState
	{
		Enable, Ready, Homing, Stop, Reset
	}

	private final StageSlider targetSlider, deviceSlider;

	private final Property< Number > targetValueProperty, deviceValueProperty;

	private final HashMap< BooleanState, BooleanProperty > booleanStateMap;

	private final IconSwitch enableSwitch;

	private final boolean isR;

	private spim.hardware.Stage stageDevice;

	private double currentValue;

	public void setStageDevice(spim.hardware.Stage stageDevice) {
		this.stageDevice = stageDevice;

		resetDevice();
	}

	public void resetDevice() {
		final double min = ( isR ? 0.0 : ( null == stageDevice ? 0.0 : stageDevice.getMinPosition() ) );
		final double max = ( isR ? 360.0 : ( null == stageDevice ? 9000.0 : stageDevice.getMaxPosition() ) );
		final double tick = isR ? 60.0 : 1000;

		currentValue = ( null == stageDevice ? 0.0 : ( isR ? stageDevice.getPosition() + 180.0 : stageDevice.getPosition() ) );

		targetSlider.updateMinMaxTick( min, max, tick );
		deviceSlider.updateMinMaxTick( min, max, tick );
	}

	public spim.hardware.Stage getStageDevice() {
		return this.stageDevice;
	}

	public StageUnit( String labelString, boolean isR, spim.hardware.Stage stageDevice )
	{
		this(labelString, isR, false, stageDevice);
	}

	public StageUnit( String labelString, boolean isR, boolean isCalibrationMode, spim.hardware.Stage stageDevice )
	{
		final Label stageLabel = new Label( labelString );

		this.isR = isR;
		this.stageDevice = stageDevice;

		// Initial properties
		final double min = ( isR ? 0.0 : ( null == stageDevice ? 0.0 : (isCalibrationMode) ? 0 : stageDevice.getMinPosition() ) );
		final double max = ( isR ? 360.0 : ( null == stageDevice ? 9000.0 : (isCalibrationMode) ? stageDevice.getRealMaxPosition() : stageDevice.getMaxPosition() ) );
		final double tick = isR ? 60.0 : 1000;

		currentValue = ( null == stageDevice ? 0.0 : ( isR ? stageDevice.getPosition() + 180.0 : stageDevice.getPosition() ) );

		// Model
		targetValueProperty = new SimpleDoubleProperty(  );
		deviceValueProperty = new SimpleDoubleProperty(  );

		targetSlider = new StageSlider( "", targetValueProperty, false, isR, min, max, tick );
		deviceSlider = new StageSlider( "", deviceValueProperty, true, isR, min, max, tick );

		booleanStateMap = new HashMap<>();

		for ( BooleanState state : BooleanState.values() )
		{
			booleanStateMap.put( state, new SimpleBooleanProperty( false ) );
		}

		// GUI
		enableSwitch = new IconSwitch();
		enableSwitch.setSymbolType( SymbolType.POWER );
		enableSwitch.setSymbolColor( Color.web( "#ffffff" ) );
		enableSwitch.setSwitchColor( Color.web( "#34495e" ) );
		enableSwitch.setThumbColor( Color.web( "#ff495e" ) );
		enableSwitch.setMaxSize( 60, 30 );

		final SimpleIndicator indicator = new SimpleIndicator();
		indicator.setMaxSize( 50, 50 );

		targetValueProperty.addListener( ( observable, oldValue, newValue )
				-> booleanStateMap.get( BooleanState.Ready ).set( false ) );

		// Data -> GUI
		booleanStateMap.get( BooleanState.Enable ).addListener( ( observable, oldValue, newValue ) ->
		{
			targetSlider.getSlider().setDisable( !newValue );
			targetSlider.getTextField().setDisable( !newValue );
			if ( !newValue )
				indicator.setIndicatorStyle( SimpleIndicator.IndicatorStyle.GRAY );

			if ( newValue )
			{
				targetValueProperty.setValue( currentValue );
				targetSlider.getSlider().setValue( currentValue );
			} else {
				currentValue = ( null == this.stageDevice ? 0.0 : ( isR ? this.stageDevice.getPosition() + 180.0 : this.stageDevice.getPosition() ) );
			}
		} );

		// Initialize the status at startup
		enableSwitch.setSelected( booleanStateMap.get( BooleanState.Enable ).get() );
		targetSlider.getSlider()
				.setDisable( !booleanStateMap.get( BooleanState.Enable ).get() );
		targetSlider.getTextField()
				.setDisable( !booleanStateMap.get( BooleanState.Enable ).get() );

		booleanStateMap.get( BooleanState.Ready ).addListener( ( observable, oldValue, newValue ) ->
		{
			if ( newValue )
				indicator.setIndicatorStyle( SimpleIndicator.IndicatorStyle.GREEN );
			else
				indicator.setIndicatorStyle( SimpleIndicator.IndicatorStyle.GRAY );
		} );

		if ( booleanStateMap.get( BooleanState.Ready ).get() )
			indicator.setIndicatorStyle( SimpleIndicator.IndicatorStyle.GREEN );
		else
			indicator.setIndicatorStyle( SimpleIndicator.IndicatorStyle.GRAY );

		final Button homingButton = new Button( "Homing" );
		homingButton.setAlignment( Pos.BASELINE_LEFT );
		homingButton.setPrefWidth( 70 );
		homingButton.setOnAction( event -> {
			targetValueProperty.setValue( 0.0 );
			targetSlider.getSlider().setValue( 0.0 );
			booleanStateMap.get( BooleanState.Ready ).setValue( false );
		} );

		final Button stopButton = new Button( "Stop" );
		stopButton.setAlignment( Pos.BASELINE_LEFT );
		stopButton.setPrefWidth( 70 );
		stopButton.setOnAction( event -> {
			booleanStateMap.get( BooleanState.Ready ).setValue( true );
			double pos = deviceValueProperty.getValue().doubleValue();
			targetSlider.getSlider().setValue( pos );
			if(null != this.stageDevice) this.stageDevice.setPosition( pos );
		} );

		Button resetButton = new Button( "Reset" );
		resetButton.setAlignment( Pos.BASELINE_LEFT );
		resetButton.setPrefWidth( 70 );
		resetButton.setOnAction( event -> {
			targetValueProperty.setValue( currentValue );
			targetSlider.getSlider().setValue( currentValue );
			booleanStateMap.get( BooleanState.Ready ).setValue( false );
		} );

		if(isR) {
			resetButton = new Button( "Calibrate" );
			resetButton.setAlignment( Pos.BASELINE_LEFT );
			resetButton.setPrefWidth( 70 );
			resetButton.setOnAction( event -> {
				if(getStageDevice() != null) {
					System.err.println("Current Step Size: " + getStageDevice().getStepSize());
					RotatorCalibrationDialog dlg = new RotatorCalibrationDialog( getStageDevice() );

					dlg.showAndWait()
							.filter(response -> response == ButtonType.OK)
							.ifPresent(response -> {
								getStageDevice().setStepSize( dlg.getReturnResult() );
								resetDevice();
							});
				} else {
					new Alert( Alert.AlertType.ERROR, "StageDevice is not ready.");
				}
			} );
		}

		GridPane gridPane = new GridPane();
		gridPane.setAlignment( Pos.CENTER );
		gridPane.setHgap( 5 );
		gridPane.setVgap( 5 );
		gridPane.setPadding( new Insets( 10, 10, 10, 10 ) );

		gridPane.add( indicator, 0, 0 );
		GridPane.setRowSpan( indicator, 2 );
		gridPane.add( enableSwitch, 0, 2 );
		GridPane.setHalignment( enableSwitch, HPos.CENTER );

		gridPane.add( homingButton, 1, 0 );
		gridPane.add( stopButton, 1, 1 );
		gridPane.add( resetButton, 1, 2 );

		final HBox stageBox = new HBox( 5 );

		// Fine grained control buttons
		String style = "-fx-font-size: 10px; ";
		// 100 um
		final Button decreaseBtn100 = new Button( "<<" );
		decreaseBtn100.setStyle( style );
		decreaseBtn100.setTooltip( new Tooltip( isR ? "Decrease by 5" : "Decrease by 100" ) );
		decreaseBtn100.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				double unit = isR ? -5 : -100;
				double n = Math.max( ( ( SimpleDoubleProperty ) targetValueProperty ).get() + unit, 0);
				targetValueProperty.setValue( n );
				targetSlider.getSlider().setValue( n );
				booleanStateMap.get( BooleanState.Ready ).setValue( false );
			}
		} );
		// 10 um
		final Button decreaseBtn10 = new Button( "<" );
		decreaseBtn10.setStyle( style );
		decreaseBtn10.setTooltip( new Tooltip( isR ? "Decrease by 1" : "Decrease by 10" ) );
		decreaseBtn10.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				double unit = isR ? -1 : -10;
				double n = Math.max( ( ( SimpleDoubleProperty ) targetValueProperty ).get() + unit, 0);
				targetValueProperty.setValue( n );
				targetSlider.getSlider().setValue( n );
				booleanStateMap.get( BooleanState.Ready ).setValue( false );
			}
		} );

		// 100 um
		final Button increaseBtn100 = new Button( ">>" );
		increaseBtn100.setStyle( style );
		increaseBtn100.setTooltip( new Tooltip( isR ? "Increase by 5" : "Increase by 100" ) );
		increaseBtn100.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				double unit = isR ? 5 : 100;
				double n = Math.min( ( ( SimpleDoubleProperty ) targetValueProperty ).get() + unit, targetSlider.getSlider().getMax() );
				targetValueProperty.setValue( n );
				targetSlider.getSlider().setValue( n );
				booleanStateMap.get( BooleanState.Ready ).setValue( false );
			}
		} );
		// 10 um
		final Button increaseBtn10 = new Button( ">" );
		increaseBtn10.setStyle( style );
		increaseBtn10.setTooltip( new Tooltip( isR ? "Increase by 1" : "Increase by 10" ) );
		increaseBtn10.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				double unit = isR ? 1 : 10;
				double n = Math.min( ( ( SimpleDoubleProperty ) targetValueProperty ).get() + unit, targetSlider.getSlider().getMax() );
				targetValueProperty.setValue( n );
				targetSlider.getSlider().setValue( n );
				booleanStateMap.get( BooleanState.Ready ).setValue( false );
			}
		} );

		final HBox buttonsBox = new HBox( 2, decreaseBtn100, new Separator( Orientation.VERTICAL ), decreaseBtn10, new Separator( Orientation.VERTICAL ), increaseBtn10, new Separator( Orientation.VERTICAL ), increaseBtn100 );
		buttonsBox.setPadding(new Insets(0, 50, 0, 70));
		buttonsBox.setAlignment( Pos.TOP_LEFT );
		buttonsBox.setDisable( true );

		// Enable, GUI -> Data
		enableSwitch.selectedProperty().addListener( ( observable, oldValue, newValue ) ->
		{
			booleanStateMap.get( BooleanState.Enable ).set( newValue );
//			booleanStateMap.get( BooleanState.Ready ).setValue( newValue );
			buttonsBox.setDisable( !newValue );
		} );

		final VBox sliderBox = new VBox( targetSlider, buttonsBox, deviceSlider );


		stageBox.getChildren()
				.addAll( new VBox( stageLabel, gridPane ), sliderBox );

		HBox.setHgrow( stageBox, Priority.ALWAYS );

		getChildren().add( stageBox );
	}

	public BooleanProperty get( BooleanState state )
	{
		return booleanStateMap.get( state );
	}

	public Property< Number > targetValueProperty()
	{
		return targetValueProperty;
	}

	public Property< Number > deviceValueProperty()
	{
		return deviceValueProperty;
	}

	public double getCurrentValue() {
		return deviceValueProperty.getValue().doubleValue();
	}

	public void setCurrentPos(double val) {
		targetValueProperty.setValue( val );
		targetSlider.getSlider().setValue( val );
		booleanStateMap.get( BooleanState.Ready ).setValue( false );
	}

	public StageSlider getDeviceSlider()
	{
		return deviceSlider;
	}

	public BooleanProperty getEnabledProperty() {
		return enableSwitch.selectedProperty();
	}
}
