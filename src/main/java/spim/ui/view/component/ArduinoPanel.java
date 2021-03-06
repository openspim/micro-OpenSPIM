package spim.ui.view.component;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import spim.ui.view.component.widgets.slider.StageSlider;
import spim.ui.view.component.util.TableViewUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

/**
 * Description: Arduino panel shows all the properties regarding Arduino in Micro-Manager.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: May 2019
 */
public class ArduinoPanel extends BorderPane implements SPIMSetupInjectable
{
	final HashMap< Property, ChangeListener > listenerMap = new HashMap<>(  );

	final TableView< PinItem > pinItemTableView;
	final ComboBox<String> blankOnComboBox;
	final ComboBox<String> blankingModeComboBox;
	final TextField labelTextField;
	final ComboBox<String> sequenceComboBox;
	final IntegerProperty stateProperty;
	final StageSlider stateSlider;

	public ArduinoPanel( SPIMSetup setup ) {

		GridPane gridPane = new GridPane();
		GridPane.setMargin( gridPane, new Insets( 20 ) );
		gridPane.setPadding( new Insets( 20 ) );
		gridPane.setHgap( 10 );
		gridPane.setVgap( 10 );

		// "Blank On"
		Label label = new Label( "Blank On" );
		blankOnComboBox = new ComboBox<>( FXCollections.observableArrayList("High", "Low") );
		blankOnComboBox.getSelectionModel().select( 0 );
		gridPane.addRow( 0, label, blankOnComboBox );

		// "Blanking Mode"
		label = new Label( "Blanking Mode" );
		blankingModeComboBox = new ComboBox<>( FXCollections.observableArrayList("On", "Off") );
		blankingModeComboBox.getSelectionModel().select( 0 );
		gridPane.addRow( 1, label, blankingModeComboBox );

		// "Label"
		label = new Label( "Label" );
		labelTextField = new TextField( "16" );
		gridPane.addRow( 2, label, labelTextField );

		// "Sequence"
		label = new Label( "Sequence" );
		sequenceComboBox = new ComboBox<>( FXCollections.observableArrayList("On", "Off") );
		sequenceComboBox.getSelectionModel().select( 1 );
		gridPane.addRow( 3, label, sequenceComboBox );

		// "State"
		label = new Label( "State" );
		stateProperty = new SimpleIntegerProperty( 16 );
		stateSlider = new StageSlider( "", stateProperty, false, false, 0, 64, 2 );
		gridPane.addRow( 4, label, stateSlider );

		setSetup( setup, null );

		Image image = new Image( getClass().getResourceAsStream( "arduino-uno.png" ) );

		// simple displays ImageView the image as is
		ImageView iv1 = new ImageView();
		iv1.setImage(image);

		pinItemTableView = TableViewUtil.createPinItemArduinoDataView();
//		pinItemTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		pinItemTableView.setEditable( true );
//		pinItemTableView.getItems().addAll( new PinItem[]{
//				new PinItem( "13", 32, "VersaLase 561" ),
//				new PinItem( "12", 16, "VersaLase 488" ),
//				new PinItem( "11", 8 ),
//				new PinItem( "10", 4 ),
//				new PinItem( "9", 2 ),
//				new PinItem( "8", 1 ),
//				new PinItem( "ALL OFF", 0 ),
//				new PinItem( "ALL ON", 63 )
//		} );

		PinList list = new PinList();
		pinItemTableView.getItems().addAll(list.list);

		GridPane.setMargin(pinItemTableView, new Insets(205, 0, 173, 0));

		gridPane.addRow( 5, iv1, pinItemTableView );

		setCenter( gridPane );
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio ) {

		// Remove all the listeners
		listenerMap.keySet().stream().forEach( c -> c.removeListener( listenerMap.get(c) ) );

		if(setup != null && setup.getArduino1() != null)
		{
			Arduino arduino = setup.getArduino1();
			blankOnComboBox.getSelectionModel().select( arduino.getBlankOn() );
			blankingModeComboBox.getSelectionModel().select( arduino.getBlankingMode() );
			labelTextField.setText( arduino.getSwitchLabel() );
			sequenceComboBox.getSelectionModel().select( arduino.getSequence() );
			stateProperty.set( Integer.parseInt( arduino.getSwitchState() ) );

			ChangeListener listener = ( ChangeListener< String > ) ( observable, oldValue, newValue ) -> arduino.setBlankOn( newValue );
			blankOnComboBox.valueProperty().addListener( listener );
			listenerMap.put( blankOnComboBox.valueProperty(), listener );

			listener = ( ChangeListener< String > ) ( observable, oldValue, newValue ) -> arduino.setBlankingMode( newValue );
			blankingModeComboBox.valueProperty().addListener( listener );
			listenerMap.put( blankingModeComboBox.valueProperty(), listener);

			listener = ( ChangeListener< String > ) ( observable, oldValue, newValue ) -> arduino.setSwitchLabel( newValue );
			labelTextField.textProperty().addListener(listener);
			listenerMap.put( labelTextField.textProperty(), listener);

			listener =  new ChangeListener< String >()
			{
				@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
				{
					arduino.setSequence( newValue );
				}
			};
			sequenceComboBox.valueProperty().addListener(listener);
			listenerMap.put( sequenceComboBox.valueProperty(), listener );

			listener = ( ChangeListener< Number > ) ( observable, oldValue, newValue ) -> arduino.setSwitchState( newValue.toString() );
			stateProperty.addListener(listener);
			listenerMap.put(stateProperty, listener);
		}
	}

	TableView< PinItem > getPinItemTableView() {
		return pinItemTableView;
	}

	public class PinList
	{
		final String filename = "arduino.properties";
		final Properties prop = new Properties();
		private ObservableList<PinItem> list;

		public PinList() {
			if(isExist( filename ))
			{
				try
				{
					FileInputStream f = new FileInputStream( getUserDataDirectory() + filename );
					prop.load( f );
				}
				catch ( FileNotFoundException e )
				{
					e.printStackTrace();
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}
				this.list = load( prop );
			}
			else
			{
				InputStream in = getClass().getResourceAsStream( filename );
				try
				{
					prop.load( in );
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}
				this.list = load( prop );

				store();
			}
		}

		public ObservableList<PinItem> getList() {
			return list;
		}

		public boolean isExist(String filename)
		{
			String path = getUserDataDirectory();
			if(!new File(path).exists())
				new File(path).mkdirs();

			String file = getUserDataDirectory() + filename;
			return new File(file).exists();
		}

		public ObservableList<PinItem> load( Properties prop ) {
			ObservableList<PinItem> loadedList = FXCollections.observableArrayList();

//			for(Object key : prop.keySet()){
//				System.out.println(key + ":" + prop.getProperty( key.toString() ));
//			}

			loadedList.add( PinItem.createPinItem( "13", prop, this ) );
			loadedList.add( PinItem.createPinItem( "12", prop, this ) );
			loadedList.add( PinItem.createPinItem( "11", prop, this ) );
			loadedList.add( PinItem.createPinItem( "10", prop, this ) );
			loadedList.add( PinItem.createPinItem( "9", prop, this ) );
			loadedList.add( PinItem.createPinItem( "8", prop, this ) );
			loadedList.add( PinItem.createPinItem( "ALL_OFF", prop, this ) );
			loadedList.add( PinItem.createPinItem( "ALL_ON", prop, this ) );

			return loadedList;
		}

		public void store() {
			try
			{
				prop.store( new FileOutputStream( getUserDataDirectory() + filename ), null );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}

		String getUserDataDirectory() {
			return System.getProperty("user.home") + File.separator + ".openspim" + File.separator + getApplicationVersionString() + File.separator;
		}

		String getApplicationVersionString() {
			return "1.0";
		}
	}
}
