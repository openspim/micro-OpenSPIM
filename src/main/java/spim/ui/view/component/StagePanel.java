package spim.ui.view.component;

import eu.hansolo.enzo.common.SymbolType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.micromanager.Studio;
import spim.hardware.SPIMSetup;
import spim.ui.view.component.iconswitch.IconSwitch;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
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
public class StagePanel extends BorderPane implements SPIMSetupInjectable
{
	private SPIMSetup spimSetup;

	private HashMap< StageUnit.Stage, StageUnit > stageMap = null;
	private HashMap< StageUnit.Stage, ChangeListener > changeListenerMap = null;

	private StageUnit stageUnitR;
	private StageUnit stageUnitX;
	private StageUnit stageUnitY;
	private StageUnit stageUnitZ;

	ScheduledExecutorService executor;

	// For undo task
	StageUnit lastUsedStageUnit;
	double lastUsedLocation;

	public StagePanel()
	{
		init();
	}

	public StagePanel( SPIMSetup setup )
	{
		this.spimSetup = setup;
		init();
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio )
	{
		this.spimSetup = setup;

		initExecutor();

		// rebound the control
		for ( StageUnit.Stage stage : stageMap.keySet() )
		{
			spim.hardware.Stage stageDevice = null;

			StageUnit unit = stageMap.get(stage);

			if(setup != null) {
				switch ( stage ) {
					case R: stageDevice = spimSetup.getThetaStage();
						break;
					case X: stageDevice = spimSetup.getXStage();
						break;
					case Y: stageDevice = spimSetup.getYStage();
						break;
					case Z: stageDevice = spimSetup.getZStage();
						break;
				}
			}

			unit.setStageDevice( stageDevice );

			final Property< Number > targetProperty = unit.targetValueProperty();

			if(changeListenerMap.get(stage) != null)
				targetProperty.removeListener( changeListenerMap.get(stage) );

			if(setup != null) {
				ChangeListener< Number > targetPropertyChangeListener = ( observable, oldValue, newValue ) -> {
					switch ( stage ) {
						case R:
							spimSetup.getThetaStage().setPosition( newValue.doubleValue() );
							break;
						case X: spimSetup.getXStage().setPosition( newValue.doubleValue() );
							break;
						case Y: spimSetup.getYStage().setPosition( newValue.doubleValue() );
							break;
						case Z: spimSetup.getZStage().setPosition( newValue.doubleValue() );
							break;
					}
				};
				changeListenerMap.put(stage, targetPropertyChangeListener);
				targetProperty.addListener( targetPropertyChangeListener );
			}
		}

		if(setup != null) {
			// start executor for monitoring values
			executor.scheduleAtFixedRate( () -> {
				monitorSPIM();
			}, 500, 10, TimeUnit.MILLISECONDS );
		}
	}

	private void initExecutor() {
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

		executor = Executors.newScheduledThreadPool( 5 );
	}

	private void monitorSPIM() {
		for ( StageUnit.Stage stage : stageMap.keySet() )
		{
			if ( stageMap.get( stage ).get( StageUnit.BooleanState.Enable ).get() &&
					!stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).get() )
			{
				double target = stageMap.get( stage ).targetValueProperty().getValue().doubleValue();

				double device = 0d;
				// read the value from the device
				switch ( stage ) {
					case R: device = spimSetup.getThetaStage().getPosition();
						break;
					case X: device = spimSetup.getXStage().getPosition();
						break;
					case Y: device = spimSetup.getYStage().getPosition();
						break;
					case Z: device = spimSetup.getZStage().getPosition();
						break;
				}

				double error = target - device;
				double granularity = stage == StageUnit.Stage.R ? 2.5 : 1.5;

				double finalDevice = device;
				Platform.runLater( () -> {
					stageMap.get( stage ).deviceValueProperty().setValue( finalDevice );

					if ( abs( error ) < granularity )
						stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).set( true );
				} );
			}
		}
	}

	public void init()
	{
		stageMap = new HashMap<>();
		changeListenerMap = new HashMap<>();

		setCenter( createControls() );

		initExecutor();

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
			}, 500, 10, TimeUnit.MILLISECONDS );
		}
		else
		{
			executor.scheduleAtFixedRate( () -> {
				monitorSPIM();
			}, 500, 10, TimeUnit.MILLISECONDS );
		}
	}

	public DoubleProperty getZValueProperty() {
		SimpleDoubleProperty property = new SimpleDoubleProperty();

		double max = spimSetup.getZStage().getMaxPosition();

		stageMap.get( StageUnit.Stage.Z ).deviceValueProperty().addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				property.set( newValue.doubleValue() / max * 200 );
			}
		} );

		return property;
	}

	private StageUnit createStageControl( String labelString, StageUnit.Stage stage )
	{
		final StageUnit unit = new StageUnit( labelString, stage == StageUnit.Stage.R, null );

		stageMap.put( stage, unit );

		return unit;
	}

	public void goToPos(double x, double y, double z, double r)
	{
		stageUnitR.setCurrentPos(r);
		stageUnitX.setCurrentPos(x);
		stageUnitY.setCurrentPos(y);
		stageUnitZ.setCurrentPos(z);
	}

	public void goToPos(double x, double y, double r) {
		stageUnitR.setCurrentPos(r);
		stageUnitX.setCurrentPos(x);
		stageUnitY.setCurrentPos(y);
	}

	public void goToZ(double z) {
		stageUnitZ.setCurrentPos(z);
	}

	private VBox createControls()
	{
		final IconSwitch switchAll = new IconSwitch();
		switchAll.setSymbolType( SymbolType.THUNDERSTORM );
		switchAll.setSymbolColor( Color.web( "#ffffff" ) );
		switchAll.setSwitchColor( Color.web( "#34495e" ) );
		switchAll.setThumbColor( Color.web( "#ff4922" ) );
		switchAll.setMaxSize( 60, 30 );

		stageUnitR = createStageControl( "Stage R (\u00b5-degree)",
				StageUnit.Stage.R );

		stageUnitR.targetValueProperty().addListener( ( observable, oldValue, newValue ) -> {
			lastUsedStageUnit = stageUnitR;
			lastUsedLocation = lastUsedStageUnit.deviceValueProperty().getValue().doubleValue();
		} );

		stageUnitX = createStageControl( "Stage X (\u00b5m)",
				StageUnit.Stage.X );

		stageUnitX.targetValueProperty().addListener( ( observable, oldValue, newValue ) -> {
			lastUsedStageUnit = stageUnitX;
			lastUsedLocation = lastUsedStageUnit.deviceValueProperty().getValue().doubleValue();
		} );

		stageUnitY = createStageControl( "Stage Y (\u00b5m)",
				StageUnit.Stage.Y );

		stageUnitY.targetValueProperty().addListener( ( observable, oldValue, newValue ) -> {
			lastUsedStageUnit = stageUnitY;
			lastUsedLocation = lastUsedStageUnit.deviceValueProperty().getValue().doubleValue();
		} );

		stageUnitZ = createStageControl( "Stage Z (\u00b5m)",
				StageUnit.Stage.Z );

		stageUnitZ.targetValueProperty().addListener( ( observable, oldValue, newValue ) -> {
			lastUsedStageUnit = stageUnitZ;
			lastUsedLocation = lastUsedStageUnit.deviceValueProperty().getValue().doubleValue();
		} );

		switchAll.selectedProperty().addListener( new ChangeListener< Boolean >()
		{
			@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
			{
				stageUnitR.getEnabledProperty().setValue( newValue );
				stageUnitX.getEnabledProperty().setValue( newValue );
				stageUnitY.getEnabledProperty().setValue( newValue );
				stageUnitZ.getEnabledProperty().setValue( newValue );
			}
		} );

		SizedStack<String> stack = new SizedStack<>( 5 );

		MenuButton loadLocation = new MenuButton("Load location");
		loadLocation.setStyle("-fx-base: #8bb9e7;");

//		stack.getList().addListener( new ListChangeListener< MenuItem >()
//		{
//			@Override public void onChanged( Change< ? extends String > c )
//			{
//				loadLocation.getItems().setAll( stack.getList() );
//			}
//		} );

		EventHandler stackChanged = new EventHandler()
		{
			@Override public void handle( Event evt )
			{
				for(String str : stack.getList()) {
					MenuItem newItem = new MenuItem( str );
					String[] tokens = str.split( ":" );
					double r = Double.parseDouble( tokens[0] );
					double x = Double.parseDouble( tokens[1] );
					double y = Double.parseDouble( tokens[2] );
					double z = Double.parseDouble( tokens[3] );

					newItem.setOnAction( event -> {
						stageUnitR.setCurrentPos(r);
						stageUnitX.setCurrentPos(x);
						stageUnitY.setCurrentPos(y);
						stageUnitZ.setCurrentPos(z);

						System.out.println(str + " is loaded.");
					} );

					loadLocation.getItems().add( newItem );
				}
			}
		};

		stackChanged.handle( null );

		stack.getList().addListener( new ListChangeListener< String >()
		{
			@Override public void onChanged( Change< ? extends String > c )
			{
				loadLocation.getItems().clear();
				stackChanged.handle( null );
			}
		} );


		Button saveCurrentLocation = new Button( "Save current positions" );
		saveCurrentLocation.setStyle("-fx-base: #e77d8c;");
		saveCurrentLocation.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				double r = stageUnitR.getCurrentValue();
				double x = stageUnitX.getCurrentValue();
				double y = stageUnitY.getCurrentValue();
				double z = stageUnitZ.getCurrentValue();

				System.out.println(String.format( "Saved location - R: %.1f, X: %.1f, Y: %.1f, Z: %.1f",
						r, x, y, z));

				String newLocation = String.format( "%.1f:%.1f:%.1f:%.1f",
						r, x, y, z);

				stack.add( newLocation );
			}
		} );


		HBox topHbox = new HBox( 10, new Label( "All On/Off: " ), switchAll, saveCurrentLocation, loadLocation );
		topHbox.setAlignment( Pos.CENTER_LEFT );

		Button undoBtn = new Button( "Go back to the last used position" );
		undoBtn.setStyle("-fx-base: #c1e796;");
		undoBtn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(lastUsedStageUnit != null) {
					lastUsedStageUnit.setCurrentPos( lastUsedLocation );
				}
			}
		} );

		VBox controls = new VBox( 10, topHbox, stageUnitR, stageUnitX, stageUnitY, stageUnitZ, undoBtn );
		controls.setPadding( new Insets( 10 ) );
		return controls;
	}

	public class SizedStack<T>
	{
		final String filename = "savedLocations.txt";
		private int maxSize;
		private ObservableList<T> list;

		public SizedStack(int size) {
			this.maxSize = size;
			if(isExist( filename ))
				this.list = load( new File(getUserDataDirectory() + filename) );
			else
				this.list = FXCollections.observableArrayList();
		}

		public void add(T object) {
			//If the stack is too big, remove elements until it's the right size.
			while (this.list.size() >= maxSize) {
				this.list.remove( this.list.size() - 1 );
			}
			this.list.add(0, object);
			save( new File(getUserDataDirectory() + filename), list );
		}

		public ObservableList<T> getList() {
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

		public ObservableList<T> load( File file ) {
			XMLDecoder e;
			try
			{
				e = new XMLDecoder(
						new BufferedInputStream(
								new FileInputStream( file ) ) );
			}
			catch ( FileNotFoundException e1 )
			{
				System.err.println( e1.getMessage() );
				return null;
			}

			ArrayList array = (ArrayList) e.readObject();
			ObservableList<T> loadedList = FXCollections.observableArrayList(array);

			e.close();

			return loadedList;
		}

		public boolean save( File file, ObservableList<T> saveList ) {
			XMLEncoder e = null;
			try
			{
				e = new XMLEncoder(
						new BufferedOutputStream(
								new FileOutputStream( file ) ) );
			}
			catch ( FileNotFoundException e1 )
			{
				e1.printStackTrace();
				return false;
			}

			assert e != null;
			e.writeObject( new ArrayList<>(saveList) );

			e.close();
			return true;
		}

		String getUserDataDirectory() {
			return System.getProperty("user.home") + File.separator + ".openspim" + File.separator + getApplicationVersionString() + File.separator;
		}

		String getApplicationVersionString() {
			return "1.0";
		}
	}
}
