package spim.ui.view.component;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.micromanager.Studio;
import spim.hardware.Arduino;
import spim.hardware.SPIMSetup;
import spim.model.data.PinItem;
import spim.ui.view.component.slider.StageSlider;
import spim.ui.view.component.util.TableViewUtil;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2019
 */
public class ArduinoPanel extends BorderPane
{
	final TableView< PinItem > pinItemTableView;

	public ArduinoPanel( SPIMSetup setup, Studio studio ) {

		GridPane gridPane = new GridPane();
		GridPane.setMargin( gridPane, new Insets( 20 ) );
		gridPane.setPadding( new Insets( 20 ) );
		gridPane.setHgap( 10 );
		gridPane.setVgap( 10 );

		// "Blank On"
		Label label = new Label( "Blank On" );
		ComboBox<String> blankOnComboBox = new ComboBox<>( FXCollections.observableArrayList("High", "Low") );
		blankOnComboBox.getSelectionModel().select( 0 );
		gridPane.addRow( 0, label, blankOnComboBox );

		// "Blanking Mode"
		label = new Label( "Blanking Mode" );
		ComboBox<String> blankingModeComboBox = new ComboBox<>( FXCollections.observableArrayList("On", "Off") );
		blankingModeComboBox.getSelectionModel().select( 0 );
		gridPane.addRow( 1, label, blankingModeComboBox );

		// "Label"
		label = new Label( "Label" );
		TextField labelTextField = new TextField( "16" );
		gridPane.addRow( 2, label, labelTextField );

		// "Sequence"
		label = new Label( "Sequence" );
		ComboBox<String> sequenceComboBox = new ComboBox<>( FXCollections.observableArrayList("On", "Off") );
		sequenceComboBox.getSelectionModel().select( 1 );
		gridPane.addRow( 3, label, sequenceComboBox );

		// "State"
		label = new Label( "State" );
		IntegerProperty stateProperty = new SimpleIntegerProperty( 16 );
		StageSlider stateSlider = new StageSlider( "", stateProperty, false, false, 0, 64, 2 );
		gridPane.addRow( 4, label, stateSlider );

		if(setup != null && setup.getArduino1() != null) {
			Arduino arduino = setup.getArduino1();
			blankOnComboBox.getSelectionModel().select( arduino.getBlankOn() );
			blankingModeComboBox.getSelectionModel().select( arduino.getBlankingMode() );
			labelTextField.setText( arduino.getSwitchLabel() );
			sequenceComboBox.getSelectionModel().select( arduino.getSequence() );
			stateProperty.set( Integer.parseInt( arduino.getSwitchState() ) );

			blankOnComboBox.valueProperty().addListener( new ChangeListener< String >()
			{
				@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
				{
					arduino.setBlankOn( newValue );
				}
			} );

			blankingModeComboBox.valueProperty().addListener( new ChangeListener< String >()
			{
				@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
				{
					arduino.setBlankingMode( newValue );
				}
			} );

			labelTextField.textProperty().addListener( new ChangeListener< String >()
			{
				@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
				{
					arduino.setSwitchLabel( newValue );
				}
			} );

			sequenceComboBox.valueProperty().addListener( new ChangeListener< String >()
			{
				@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
				{
					arduino.setSequence( newValue );
				}
			} );

			stateProperty.addListener( new ChangeListener< Number >()
			{
				@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
				{
					arduino.setSwitchState( newValue.toString() );
				}
			} );
		}

		Image image = new Image( getClass().getResourceAsStream( "arduino-uno.png" ) );

		// simple displays ImageView the image as is
		ImageView iv1 = new ImageView();
		iv1.setImage(image);

		pinItemTableView = TableViewUtil.createPinItemArduinoDataView();
//		pinItemTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		pinItemTableView.setEditable( true );
		pinItemTableView.getItems().addAll( new PinItem[]{
				new PinItem( "13", 32, "VersaLase 561" ),
				new PinItem( "12", 16, "VersaLase 488" ),
				new PinItem( "11", 8 ),
				new PinItem( "10", 4 ),
				new PinItem( "9", 2 ),
				new PinItem( "8", 1 ),
				new PinItem( "ALL OFF", 0 ),
				new PinItem( "ALL ON", 63 )
		} );

		GridPane.setMargin(pinItemTableView, new Insets(205, 0, 173, 0));

		gridPane.addRow( 5, iv1, pinItemTableView );

		setCenter( gridPane );
	}

	TableView< PinItem > getPinItemTableView() {
		return pinItemTableView;
	}
}
