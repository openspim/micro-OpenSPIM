package spim.ui.view.component;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.RectangleBuilder;
import javafx.scene.transform.Shear;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.micromanager.Studio;
import spim.hardware.SPIMSetup;
import spim.model.data.AcquisitionSetting;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.pane.CheckboxPane;
import spim.ui.view.component.pane.LabeledPane;
import spim.ui.view.component.util.TableViewUtil;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class AcquisitionPanel extends BorderPane
{
	final private TableView< PositionItem > positionItemTableView;
	final private TableView< ChannelItem > channelItemTableView;
	final private HashMap< String, StringProperty > propertyMap;
	final private SimpleObjectProperty<PositionItem> currentPosition;
	final private SPIMSetup spimSetup;
	final private Studio studio;
	ObservableList<String> acquisitionOrderItems;
	long imageWidth, imageHeight, imageDepth, bufferSize;


	// Save/load acquisition setting purpose
	// Time points panel
	BooleanProperty enabledTimePoints;
	StringProperty numTimePoints;
	StringProperty intervalTimePoints;
	ObjectProperty intervalUnitTimePoints;

	// Position panel
	BooleanProperty enabledPositions;
	ArrayList<PositionItem> positionItems;

	// Z-Stack panel
	BooleanProperty enabledZStacks;

	// Acquisition order panel
	BooleanProperty disabledAcquisitionOrder;
	ObjectProperty acquisitionOrder;

	// Channels panel
	BooleanProperty enabledChannels;
	ArrayList<ChannelItem> channelItems;

	// Save Image panel
	BooleanProperty enabledSaveImages;
	StringProperty directory;
	StringProperty filename;
	ObjectProperty savingFormat;
	BooleanProperty saveAsHDF5;

	public AcquisitionPanel( Stage stage, SPIMSetup setup, Studio studio, StagePanel stagePanel ) {
		this.spimSetup = setup;
		this.studio = studio;
		this.propertyMap = new HashMap<>();
		this.currentPosition = new SimpleObjectProperty<>();

		// 1. Property Map for summary panel
		this.propertyMap.put( "times", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "positions", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "slices", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "channels", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "totalImages", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "totalSize", new SimpleStringProperty( "0 MB" ) );
		this.propertyMap.put( "duration", new SimpleStringProperty( "0h 0m 0s" ) );
		this.propertyMap.put( "order", new SimpleStringProperty( "Position > Slice > Channel" ) );
		this.propertyMap.put( "filename", new SimpleStringProperty( "" ) );

		if(this.studio != null)
		{
			System.out.println("Height: " + this.studio.core().getImageHeight());
			System.out.println("Width: " + this.studio.core().getImageWidth());
			System.out.println("Depth: " + this.studio.core().getImageBitDepth());
			System.out.println("BufferSize: " + this.studio.core().getImageBufferSize());
			this.imageWidth = this.studio.core().getImageWidth();
			this.imageHeight = this.studio.core().getImageHeight();
			this.imageDepth = this.studio.core().getImageBitDepth();
		}
		else
		{
			this.imageWidth = 512;
			this.imageHeight = 512;
			this.imageDepth = 8;
			this.bufferSize = 512 * 512;
		}

		positionItemTableView = TableViewUtil.createPositionItemDataView(this);

		// listbox for Position list
		SplitPane timePositionSplit = new SplitPane(
				createTimePointsPane(),
				createPositionListPane(positionItemTableView) );
		timePositionSplit.setOrientation( Orientation.VERTICAL );
		timePositionSplit.setDividerPositions( 0.1 );

		// acquisition order
		SplitPane zstackAcquisitionOrderPane = new SplitPane(
				createZStackPane(stagePanel),
				createAcquisitionOrderPane()
		);
		zstackAcquisitionOrderPane.setOrientation( Orientation.VERTICAL );
		zstackAcquisitionOrderPane.setDividerPositions( 0.7 );

		// setup the Z-stacks
		// summary
		SplitPane positionZStackSplit = new SplitPane(timePositionSplit, zstackAcquisitionOrderPane, createSummaryPane());
		positionZStackSplit.setOrientation( Orientation.HORIZONTAL );
		positionZStackSplit.setDividerPositions( 0.3, 0.6 );

		// Channel list
		channelItemTableView = TableViewUtil.createChannelItemDataView();

		// Laser shutter(software) / Arduino Shutter(hardware)
		// Save image options
		SplitPane channelListSaveImage = new SplitPane(
				createChannelItemTable( channelItemTableView ),
				createSaveImagesPane()
		);
//		channelListSaveImage.setDividerPositions( 0.6 );

		SplitPane content = new SplitPane( positionZStackSplit, channelListSaveImage );
		content.setOrientation( Orientation.VERTICAL );


		// Compute acquisition order logic
		BooleanBinding bb = new BooleanBinding()
		{
			{
				super.bind(enabledChannels, enabledPositions, enabledZStacks, enabledTimePoints);
			}
			@Override protected boolean computeValue()
			{
				acquisitionOrderItems.clear();

				ArrayList<String> tp = new ArrayList<>(  );
				if (enabledTimePoints.get() && enabledPositions.get() ) {
					tp.addAll( Arrays.asList( "Time > Position", "Position > Time" ) );
				} else if (enabledTimePoints.get()) {
					tp.add( "Time");
				} else if (enabledPositions.get()) {
					tp.add( "Position");
				}

				if (enabledZStacks.get() && enabledChannels.get()) {
					if(tp.size() > 0)
						for(String former: tp) {
							for(String latter : Arrays.asList( "Slice > Channel", "Channel > Slice" ) ) {
								acquisitionOrderItems.add( former + " > " + latter );
							}
						}
					else
						acquisitionOrderItems.addAll( "Slice > Channel", "Channel > Slice" );
				} else if (enabledZStacks.get()) {
					if(tp.size() > 0)
						for(String former: tp) {
							acquisitionOrderItems.add( former + " > Slice" );
						}
					else
						acquisitionOrderItems.add( "Slice" );
				} else if (enabledChannels.get()) {
					if(tp.size() > 0)
						for(String former: tp) {
							acquisitionOrderItems.add( former + " > Channel" );
						}
					else
						acquisitionOrderItems.add( "Channel" );
				}

				if( !enabledTimePoints.get() ) propertyMap.get("times").set("");
				else propertyMap.get("times").setValue( numTimePoints.getValue() );

				if( !enabledPositions.get() ) propertyMap.get("positions").set("");
				else computeTotalPositionImages();

				if( !enabledChannels.get() ) propertyMap.get("channels").set("");
				else computeTotalChannels();

				return !enabledChannels.get() && !enabledPositions.get() && !enabledZStacks.get() && !enabledTimePoints.get();
			}
		};

		disabledAcquisitionOrder.bind( bb );

		final Slider slider = new Slider();
		slider.setMin(-1);
		slider.setMax(50);

		final ProgressBar pb = new ProgressBar(-1);
		final ProgressIndicator pi = new ProgressIndicator(-1);
		pi.setMinSize( 50,50 );
		pi.setMaxSize( 50,50 );

		slider.valueProperty().addListener(new ChangeListener<Number>() {
			public void changed( ObservableValue<? extends Number> ov,
					Number old_val, Number new_val) {
				pb.setProgress(new_val.doubleValue()/50);
				pi.setProgress(new_val.doubleValue()/50);
			}
		});

		final HBox hb = new HBox();
		hb.setSpacing(5);
		hb.setAlignment( Pos.CENTER_LEFT );

		Button acquireButton = new Button( "Acquire" );
		acquireButton.setMinSize( 120, 40 );
		acquireButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
		acquireButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(studio != null)
				{
					System.out.println("Height: " + studio.core().getImageHeight());
					System.out.println("Width: " + studio.core().getImageWidth());
					System.out.println("Depth: " + studio.core().getImageBitDepth());
					System.out.println("BufferSize: " + studio.core().getImageBufferSize());
				}

				startAcquisition();
			}
		} );

		Button stopButton = new Button( "Stop" );
		stopButton.setMinSize( 120, 40 );
		stopButton.setStyle("-fx-font: 18 arial; -fx-base: #e77d8c;");
		stopButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				stopAcquisition();
			}
		} );

		hb.getChildren().addAll(acquireButton, stopButton, slider, pb, pi);

		Button saveButton = new Button( "Save" );
		saveButton.setMinSize( 120, 40 );
		saveButton.setStyle("-fx-font: 18 arial; -fx-base: #69e760;");
		saveButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				final FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle( "OpenSPIM AcquisitionSetting file" );
				fileChooser.getExtensionFilters().addAll(
						new FileChooser.ExtensionFilter( "OpenSPIM AcquisitionSetting file", "*.xml" )
				);

				File file = fileChooser.showSaveDialog( getScene().getWindow() );
				if ( file != null )
				{
					AcquisitionSetting.save( file, getAcquisitionSetting() );
				}
			}
		} );

		Button loadButton = new Button( "Load" );
		loadButton.setMinSize( 120, 40 );
		loadButton.setStyle("-fx-font: 18 arial; -fx-base: #e7e45d;");
		loadButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				final FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle( "OpenSPIM AcquisitionSetting file" );
				fileChooser.getExtensionFilters().addAll(
						new FileChooser.ExtensionFilter( "OpenSPIM AcquisitionSetting file", "*.xml" )
				);

				File file = fileChooser.showOpenDialog( getScene().getWindow() );
				if ( file != null )
				{
					AcquisitionSetting setting = AcquisitionSetting.load( file );
					if(setting != null) {
						// Update GUI
						updateUI( setting );
					}
				}
			}
		} );

		loadButton.setOnDragOver( new EventHandler< DragEvent >()
		{
			@Override public void handle( DragEvent event )
			{
				Dragboard db = event.getDragboard();
				if ( db.hasFiles() && db.getFiles().size() == 1 )
				{
					event.acceptTransferModes( TransferMode.COPY );
				}
				else
				{
					event.consume();
				}
			}
		} );

		loadButton.setOnDragDropped( new EventHandler< DragEvent >()
		{
			@Override public void handle( DragEvent event )
			{
				Dragboard db = event.getDragboard();
				boolean success = false;
				if ( db.hasFiles() && db.getFiles().size() == 1 )
				{
					if(db.getFiles().get(0).isFile()) {
						final File file = db.getFiles().get(0);
						final AcquisitionSetting setting = AcquisitionSetting.load( file );
						if(setting != null) {
							// Update GUI
							updateUI( setting );
							success = true;
						}
					}
				}

				event.setDropCompleted( success );
				event.consume();
			}
		} );

		hb.getChildren().addAll( saveButton, loadButton );

		setCenter( content );
		BorderPane.setMargin(hb, new Insets(12,12,12,12));
		setBottom( hb );

		addEventHandler( ControlEvent.STAGE, new EventHandler< ControlEvent >()
		{
			@Override public void handle( ControlEvent event )
			{
				if(event.getEventType().equals( ControlEvent.STAGE_MOVE )) {
					PositionItem pos = (PositionItem) event.getParam()[0];

					if(setup != null) {
						double r = spimSetup.getThetaStage().getPosition() + 180.0;
						double x = spimSetup.getXStage().getPosition();
						double y = spimSetup.getYStage().getPosition();
						double z = spimSetup.getZStage().getPosition();

						pos.setX( x );
						pos.setY( y );
						pos.setR( r );
						pos.setZStart( z );
						pos.setZEnd( z + 10 );
//						setup.setPosition( pos.getX(), pos.getY(), pos.getZStart(), pos.getR() - 180.0 );
//						System.out.println("Stage_Move: " + pos);
					}
					else {
						pos.setX( 200 );
						pos.setY( 100 );
						pos.setR( 300 );
						pos.setZStart( 60 );
						pos.setZEnd( 60 + 10 );
					}
					int i = positionItemTableView.getSelectionModel().getSelectedIndex();
					positionItemTableView.getSelectionModel().clearSelection();
					positionItemTableView.getSelectionModel().select( i );
					positionItemTableView.refresh();
				}
			}
		} );
	}

	private void updateUI ( AcquisitionSetting setting ) {
		// Time points panel
		enabledTimePoints.set( setting.getEnabledTimePoints() );
		numTimePoints.set( setting.getNumTimePoints() );
		intervalTimePoints.set( setting.getIntervalTimePoints() );
		intervalUnitTimePoints.set( setting.getIntervalUnitTimePoints() );

		// Position panel
		enabledPositions.set( setting.getEnabledPositions() );
		positionItems = setting.getPositionItems();
		positionItemTableView.getItems().setAll( positionItems );

		// Z-Stack panel
		enabledZStacks.set( setting.getEnabledZStacks() );

		// Acquisition order panel
		acquisitionOrder.set( setting.getAcquisitionOrder() );

		// Channels panel
		enabledChannels.set( setting.getEnabledChannels() );
		channelItems = setting.getChannelItems();
		channelItemTableView.getItems().setAll( channelItems );

		// Save Image panel
		enabledSaveImages.set( setting.getEnabledSaveImages() );
		directory.set( setting.getDirectory() );
		filename.set( setting.getFilename() );
		savingFormat.set( setting.getSavingFormat() );
		saveAsHDF5.set( setting.getSaveAsHDF5() );
	}

	private AcquisitionSetting getAcquisitionSetting() {
		positionItems = new ArrayList<>( positionItemTableView.getItems() );
		channelItems = new ArrayList<>( channelItemTableView.getItems() );

		return new AcquisitionSetting( enabledTimePoints, numTimePoints, intervalTimePoints, intervalUnitTimePoints,
				enabledPositions, positionItems, enabledZStacks, acquisitionOrder,
				enabledChannels, channelItems, enabledSaveImages, directory, filename, savingFormat, saveAsHDF5 );
	}

	public void stopAcquisition()
	{
		System.out.println("Stop button pressed");
	}

	public void startAcquisition()
	{
		System.out.println("Acquire button pressed");
	}

	private CheckboxPane createPositionListPane(TableView< PositionItem > positionItemTableView) {
		positionItemTableView.setEditable( true );

		MenuItem newItem = new MenuItem( "New" );
		newItem.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(spimSetup != null ) {
					double r = spimSetup.getThetaStage().getPosition() + 180.0;
					double x = spimSetup.getXStage().getPosition();
					double y = spimSetup.getYStage().getPosition();
					double z = spimSetup.getZStage().getPosition();
					positionItemTableView.getItems().add( new PositionItem( x, y, r, z, 10, 5 ) );
				}
				else {
					positionItemTableView.getItems().add( new PositionItem( 10, 20, 30, 20, 50, 10 ) );
				}
			}
		} );

		MenuItem deleteItem = new MenuItem( "Delete" );
		deleteItem.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(positionItemTableView.getSelectionModel().getSelectedIndex() > -1)
					positionItemTableView.getItems().remove( positionItemTableView.getSelectionModel().getSelectedIndex() );
			}
		} );

		positionItemTableView.getSelectionModel().selectedItemProperty().addListener( new ChangeListener< PositionItem >()
		{
			@Override public void changed( ObservableValue< ? extends PositionItem > observable, PositionItem oldValue, PositionItem newValue )
			{
				currentPosition.set( newValue );
			}
		} );

		positionItemTableView.setContextMenu( new ContextMenu( newItem, deleteItem ) );

		positionItemTableView.getItems().addListener( new InvalidationListener()
		{
			@Override public void invalidated( Observable observable )
			{
				computeTotalPositionImages();
			}
		} );

		CheckboxPane pane = new CheckboxPane( "Positions", positionItemTableView );
		enabledPositions = pane.selectedProperty();
		return pane;
	}

	private void computeTotalPositionImages() {
		long totalImages = 0;
		for(PositionItem item : positionItemTableView.getItems())
		{
			if(item.getZEnd() > item.getZStart()) {
				totalImages += (item.getZEnd() - item.getZStart()) / item.getZStep();
			}
		}

		propertyMap.get("positions").setValue( totalImages + "" );
	}

	private CheckboxPane createSaveImagesPane() {
		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		TextField textField = new TextField();
		propertyMap.put( "folder", textField.textProperty() );

		directory = textField.textProperty();

		Button selectFolder = new Button( "..." );
		TextField finalTextField = textField;
		selectFolder.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				DirectoryChooser d = new DirectoryChooser();
				File f = d.showDialog(null);
				if (f != null) finalTextField.setText( f.getAbsolutePath() );
			}
		} );

		gridpane.addRow( 0, new Label( "Directory:" ), textField, selectFolder );

		textField = new TextField("Untitled");
		filename = textField.textProperty();

		propertyMap.put( "filename", textField.textProperty() );
		gridpane.addRow( 1, new Label( "File name:" ), textField );

		ComboBox c = new ComboBox<>( FXCollections.observableArrayList(
				"Separate all dimension",
				"Separate the first dimension", "Image stack" ) );

		savingFormat = c.valueProperty();

		gridpane.addRow( 2, new Label( "Saving format:" ), c );

		CheckBox ch = new CheckBox( "Save as HDF5" );
		gridpane.addRow( 3, ch );
		saveAsHDF5 = ch.selectedProperty();

		CheckboxPane pane = new CheckboxPane( "Save Images", gridpane, 12 );
		enabledSaveImages = pane.selectedProperty();
		return pane;
	}

	private CheckboxPane createChannelItemTable(TableView< ChannelItem > channelItemTableView) {
		channelItemTableView.setEditable( true );

		MenuItem newItem = new MenuItem( "New" );
		newItem.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				// TODO: Get the current position from the stage control and make the new position
				channelItemTableView.getItems().add( new ChannelItem( "Camera-1", "Laser-1", 20 ) );
			}
		} );

		MenuItem deleteItem = new MenuItem( "Delete" );
		deleteItem.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(channelItemTableView.getSelectionModel().getSelectedIndex() > -1)
					channelItemTableView.getItems().remove( channelItemTableView.getSelectionModel().getSelectedIndex() );
			}
		} );

		// add context menu here
		channelItemTableView.setContextMenu( new ContextMenu( newItem, deleteItem ) );

		channelItemTableView.getItems().addListener( new InvalidationListener()
		{
			@Override public void invalidated( Observable observable )
			{
				computeTotalChannels();
			}
		} );

		CheckboxPane pane = new CheckboxPane( "Channels", channelItemTableView );
		enabledChannels = pane.selectedProperty();
		return pane;
	}

	private void computeTotalChannels() {
		int totalChannels = channelItemTableView.getItems().size();

		propertyMap.get("channels").setValue( totalChannels + "" );
	}

	private LabeledPane createSummaryPane() {
		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		Label label = new Label();
		label.textProperty().bind( propertyMap.get("times") );
		label.textProperty().addListener( observable -> computeTotal() );
		gridpane.addRow( 0, new Label("No. of time points: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("positions") );
		label.textProperty().addListener( observable -> computeTotal() );
		gridpane.addRow( 1, new Label("Images in positions: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("channels") );
		label.textProperty().addListener( observable -> computeTotal() );
		gridpane.addRow( 2, new Label("No. of channels: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("totalImages") );
		gridpane.addRow( 3, new Label("Total images: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("totalSize") );
		gridpane.addRow( 4, new Label("Total size: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("duration") );
		gridpane.addRow( 5, new Label("Duration: "), label );

		label = new Label();
		label.textProperty().bind( propertyMap.get("order") );
		gridpane.addRow( 6, new Label("Order: "), label );

		return new LabeledPane( "Summary", gridpane );
	}

	private void computeTotal() {
		long tp = propertyMap.get("times").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("times").getValue() );
		long pos = propertyMap.get("positions").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("positions").getValue() );
		long ch = propertyMap.get("channels").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("channels").getValue() );

		tp = tp == 0 ? 1: tp;
		pos = pos == 0 ? 1: pos;
		ch = ch == 0 ? 1: ch;
		long tot = tp * pos * ch;

		propertyMap.get("totalImages").setValue( String.format( "%d", tot ) );
		propertyMap.get("totalSize").setValue( formatFileSize( tot * bufferSize ) );
	}

	private LabeledPane createAcquisitionOrderPane() {
		acquisitionOrderItems = FXCollections.observableArrayList(
				"Position > Slice > Channel",
				"Position > Channel > Slice" );

		ComboBox orderComboBox = new ComboBox<>( acquisitionOrderItems );

		acquisitionOrderItems.addListener( new ListChangeListener< String >()
		{
			@Override public void onChanged( Change< ? extends String > c )
			{
				if(orderComboBox.getSelectionModel().isEmpty())
					orderComboBox.getSelectionModel().select( 0 );
			}
		} );

		acquisitionOrder = orderComboBox.valueProperty();

		propertyMap.get("order").bind( acquisitionOrder );

		LabeledPane pane = new LabeledPane( "Acquisition Order", orderComboBox );
		disabledAcquisitionOrder = pane.disableProperty();

		return pane;
	}

	private Label createZStackLabel(String name) {
		Label label = new Label( name );
		label.setMinWidth( 70 );
		label.setMinHeight( 22 );
		label.setAlignment( Pos.BASELINE_CENTER );
		label.setStyle("-fx-text-fill: white; -fx-background-color: #43a5e7");
		return label;
	}

	private CheckboxPane createZStackPane( StagePanel stagePanel ) {

		StackCube cube;
		Slider zSlider = null;
		if(stagePanel == null)
		{
			zSlider = new Slider(0, 100, 0);
			zSlider.setOrientation( Orientation.VERTICAL );
			cube = new StackCube(50, 100, Color.CORNFLOWERBLUE, 1, zSlider.valueProperty() );
		}
		else
			cube = new StackCube(50, 100, Color.CORNFLOWERBLUE, 1, stagePanel.getZValueProperty() );

		cube.setTranslateX( -60 );

		GridPane gridpane = new GridPane();
		gridpane.setVgap( 8 );
		gridpane.setHgap( 5 );

		Label label = createZStackLabel( "Z-start" );
		TextField zStartField = createNumberTextField();
		zStartField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(currentPosition.get() != null && !newValue.isEmpty()) {
					currentPosition.get().setZStart( Double.parseDouble( newValue ) );
					computeTotalPositionImages();
					positionItemTableView.refresh();
				}
			}
		} );
		gridpane.addRow( 0, label, zStartField );

		TextField zStepField = createNumberTextField();
		zStepField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(currentPosition.get() != null && !newValue.isEmpty())
				{
					currentPosition.get().setZStep( Double.parseDouble( newValue ) );
					computeTotalPositionImages();
					positionItemTableView.refresh();
				}
			}
		} );
		gridpane.addRow( 1, new Label( "Z-step (\u03BCm)" ), zStepField );

		label = createZStackLabel( "Z-end" );
		TextField zEndField = createNumberTextField();
		zEndField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(currentPosition.get() != null && !newValue.isEmpty()) {
					currentPosition.get().setZEnd( Double.parseDouble( newValue ) );
					computeTotalPositionImages();
					positionItemTableView.refresh();
				}
			}
		} );
		gridpane.addRow( 2, label, zEndField );

		currentPosition.addListener( new ChangeListener< PositionItem >()
		{
			@Override public void changed( ObservableValue< ? extends PositionItem > observable, PositionItem oldValue, PositionItem newValue )
			{
				if(newValue != null) {
					zStartField.setText( (int)newValue.getZStart() + "" );
					zStepField.setText( (int)newValue.getZStep() + "");
					zEndField.setText( (int)newValue.getZEnd() + "");
				}
			}
		} );

		if(stagePanel == null && zSlider != null)
			gridpane.add( zSlider, 3, 0, 1, 2 );

		gridpane.setTranslateY( -30 );

		// create a group
		Group group = new Group(cube, gridpane );

		CheckboxPane pane = new CheckboxPane( "Z-stacks", group );
		enabledZStacks = pane.selectedProperty();
		return pane;
	}

	private CheckboxPane createTimePointsPane()
	{
		GridPane gridpane = new GridPane();
		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		// No. of time points
		Label numTimepoints = new Label("No. of time points:");
		GridPane.setConstraints(numTimepoints, 1, 0); // column=1 row=0

		TextField numTimepointsField = createNumberTextField();
		numTimePoints = numTimepointsField.textProperty();

		numTimePoints.addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				propertyMap.get("times").setValue( newValue );
			}
		} );

		GridPane.setConstraints(numTimepointsField, 2, 0); // column=2 row=0

		// Interval
		Label numInterval = new Label( "Interval" );
		GridPane.setConstraints(numInterval, 1, 1); // column=1 row=1
		TextField numIntervalField = createNumberTextField();
		intervalTimePoints = numIntervalField.textProperty();

		GridPane.setConstraints(numIntervalField, 2, 1); // column=2 row=1
		ComboBox unitComboBox = new ComboBox<>( FXCollections.observableArrayList( "ms", "sec", "min" ) );
		unitComboBox.getSelectionModel().select( 0 );
		intervalUnitTimePoints = unitComboBox.valueProperty();

		GridPane.setConstraints(unitComboBox, 3, 1); // column=3 row=1

		StringBinding sb = new StringBinding()
		{
			{
				super.bind( numTimePoints, intervalTimePoints, intervalUnitTimePoints );
			}
			@Override protected String computeValue()
			{
				if( intervalTimePoints.get().isEmpty() || intervalTimePoints.get().isEmpty() ) return "0h 0m 0.00s";
				double unit = 1;

				switch ( intervalUnitTimePoints.getValue().toString() ) {
					case "ms" : unit = 0.001;
						break;
					case "min" : unit = 60;
						break;
				}

				double total = unit * Double.parseDouble( numTimePoints.getValue() ) * Double.parseDouble( intervalTimePoints.getValue() );

				int h = (int) total / 3600;

				int m = (int) (total - (h * 3600)) / 60;

				double s = total - (h * 3600) - (m * 60);

				return String.format( "%dh %dm %.02fs", h, m, s );
			}
		};

		propertyMap.get("duration").bind( sb );

		gridpane.getChildren().addAll(numTimepoints, numTimepointsField, numInterval, numIntervalField, unitComboBox);

		CheckboxPane pane = new CheckboxPane( "Time points", gridpane );
		enabledTimePoints = pane.selectedProperty();
		return pane;
	}

	private TextField createNumberTextField() {
		TextField textField = new TextField() {
			@Override public void replaceText(int start, int end, String text) {
				if (text.matches("[0-9]*")) {
					super.replaceText(start, end, text);
				}
			}

			@Override public void replaceSelection(String text) {
				if (text.matches("[0-9]*")) {
					super.replaceSelection(text);
				}
			}
		};
		textField.setPrefWidth( 80 );
		return textField;
	}

	// https://stackoverflow.com/questions/13539871/converting-kb-to-mb-gb-tb-dynamically
	public static String formatFileSize(long size) {
		String hrSize = null;

		double b = size;
		double k = size/1024.0;
		double m = ((size/1024.0)/1024.0);
		double g = (((size/1024.0)/1024.0)/1024.0);
		double t = ((((size/1024.0)/1024.0)/1024.0)/1024.0);

		DecimalFormat dec = new DecimalFormat("0.00");

		if ( t>1 ) {
			hrSize = dec.format(t).concat(" TB");
		} else if ( g>1 ) {
			hrSize = dec.format(g).concat(" GB");
		} else if ( m>1 ) {
			hrSize = dec.format(m).concat(" MB");
		} else if ( k>1 ) {
			hrSize = dec.format(k).concat(" KB");
		} else {
			hrSize = dec.format(b).concat(" Bytes");
		}

		return hrSize;
	}

	public class StackCube extends Group
	{
		public StackCube(double size, double height, Color color, double shade, DoubleProperty currentZ ) {

			Rectangle current = RectangleBuilder.create() // top face
					.width(size).height(0.25*size)
					.fill(Color.RED.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5))
					.translateX(0)
					//							.translateY(-0.75 * size)
					.translateY( 100 - 0.75 * size )
					.transforms( new Shear( -2, 0 ) )
					.build();

			current.translateYProperty().bind( currentZ.multiply( -1 ).add( 100 ).subtract( 0.75 * size ) );

			getChildren().addAll(
					RectangleBuilder.create() // top face
							.width(size).height(0.25*size)
							.fill(color.deriveColor(0.0, 1.0, (1 - 0.1*shade), 1.0))
							.translateX(0)
							.translateY(-0.75*size)
							.transforms( new Shear( -2, 0 ) )
							.build(),
					RectangleBuilder.create() // right face
							.width(size/2).height(height)
							.fill(color.deriveColor(0.0, 1.0, (1 - 0.3*shade), 1.0))
							.translateX(0.5*size)
							.translateY(-0.5*size)
							.transforms( new Shear( 0, -0.5 ) )
							.build(),
					RectangleBuilder.create() // front face
							.width(size).height(height)
							.fill(color)
							.translateX(-0.5*size)
							.translateY(-0.5*size)
							.build(),
					current
			);
		}
	}
}
