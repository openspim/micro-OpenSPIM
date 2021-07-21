package spim.ui.view.component.widgets.slider.demo;

import javafx.application.Application;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import spim.ui.view.component.widgets.slider.StageSlider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2018
 */
public class StageSliderDemo extends Application
{

	@Override
	public void start( Stage stage )
	{
		GridPane root = new GridPane();
		root.setMaxWidth( Double.MAX_VALUE );

		Scene scene = new Scene( root, 600, 400 );
		stage.setScene( scene );
		stage.setTitle( "Stage Slider Sample" );

		// Slider 1.
		Property< Number > property1 = new SimpleDoubleProperty( 0.0f );

		StageSlider slider = new StageSlider( "First", property1, false, false, 0.0, 100.0, 1.0 );

		root.add( slider, 0, 1 );

		// Slider 2.
		Property< Number > property2 = new SimpleDoubleProperty( 0.0f );

		slider = new StageSlider( "Second", property2, true, false, 0.0, 200.0, 10.0 );

		ExecutorService executor = Executors.newSingleThreadExecutor();

		executor.submit( () -> {
			while ( true )
			{
				property2.setValue( ( int ) ( Math.random() * 100 ) % 200 );
				Thread.sleep( 1000 );
			}
		} );

		root.add( slider, 0, 2 );

		Property< Number > property3 = new SimpleDoubleProperty( 0.0f );
		slider = new StageSlider( "Third", property3, false, true, 0.0, 360.0, 3.0 );

		root.add( slider, 0, 3 );

		Button btn = new Button( "Test" );
		StageSlider finalSlider = slider;
		btn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				finalSlider.getSlider().setUpTickUpperValue( 10 );
			}
		} );

		root.add( btn, 1, 3 );

		stage.show();
	}

	/**
	 * Main
	 */
	public static void main( String[] args )
	{
		launch( args );
	}
}
