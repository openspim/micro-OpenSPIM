package spim.ui.view.component;

import ij.gui.Roi;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.*;
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
import javafx.scene.Node;
import javafx.scene.control.*;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.RectangleBuilder;
import javafx.scene.transform.Shear;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import mmcorej.DeviceType;

import org.micromanager.Studio;

import org.micromanager.internal.MMStudio;
import spim.hardware.SPIMSetup;
import spim.model.data.AcquisitionSetting;
import spim.model.data.ChannelItem;
import spim.model.data.PinItem;
import spim.model.data.PositionItem;
import spim.model.data.TimePointItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.pane.CheckboxPane;
import spim.ui.view.component.pane.LabeledPane;
import spim.ui.view.component.util.TableViewUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static spim.ui.view.component.util.TableViewUtil.createTimePointItemDataView;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class AcquisitionPanel extends BorderPane implements SPIMSetupInjectable
{
	final private TableView< PositionItem > positionItemTableView;
	private TableView< ChannelItem > channelItemTableView;
	final private TableView< ChannelItem > channelItemArduinoTableView;
	final private HashMap< String, StringProperty > propertyMap;
	final private SimpleObjectProperty<PositionItem> currentPosition;

	private TableView< TimePointItem > timePointItemTableView;

	private SPIMSetup spimSetup;
	private Studio studio;
	final private TabPane channelTabPane;
	ObservableList<String> acquisitionOrderItems;
	long imageWidth, imageHeight, imageDepth, bufferSize;


	// Save/load acquisition setting purpose
	// Time points panel
	BooleanProperty enabledTimePoints;

	// Dynamic Time Points panel
	ArrayList<TimePointItem> timePointItems;

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
	ArrayList<ChannelItem> channelItemsArduino;

	// Save Image panel
	BooleanProperty enabledSaveImages;
	StringProperty directory;
	StringProperty filename;
	ObjectProperty savingFormat;
	BooleanProperty saveAsHDF5;
	BooleanProperty saveMIP;
	ObjectProperty roiRectangle;

	// Experiment note
	StringProperty experimentNote;

	// properties for progress
	LongProperty totalImages, processedImages;

	// Cameras
	int noCams = 0;
	StagePanel stagePanel;

	Thread acquisitionThread = null;
	double maxZStack = 100;

	// For StackCube sizing, zStart and zEnd are converted to the actual cube size
	SimpleDoubleProperty zStart;
	SimpleDoubleProperty zEnd;
	SimpleDoubleProperty zCurrent;
	ChangeListener< Number > currentChangeListener;

	// Holder for zStep and zEnd
	double zStackStart, zStackStepSize, zStackEnd;

	// For Smart Imaging
	SimpleDoubleProperty currentTP;
	CylinderProgress smartImagingCylinder;
	SimpleDoubleProperty cylinderSize;
	SimpleDoubleProperty waitSeconds;

	// Rotate Step Size
	SimpleDoubleProperty rotateStepSize;

	Group zStackGroup;
	GridPane zStackGridPane;
	StackCube cube;
	Slider zSlider = null;
	Tab laserTab;

	MMAcquisitionEngine engine;
	BooleanProperty antiDrift;

	// Anti-Drift log
	StringProperty antiDriftLog;
	ReadOnlyObjectProperty<Integer> antiDriftRefCh;

	public AcquisitionPanel( SPIMSetup setup, Studio studio, StagePanel stagePanel, TableView< PinItem > pinItemTableView, ObjectProperty roiRectangleProperty, SimpleDoubleProperty waitSeconds ) {
		this.spimSetup = setup;
		this.studio = studio;
		this.propertyMap = new HashMap<>();
		this.currentPosition = new SimpleObjectProperty<>();

		this.totalImages = new SimpleLongProperty();
		this.processedImages = new SimpleLongProperty();

		this.stagePanel = stagePanel;
		this.roiRectangle = roiRectangleProperty;
		this.roiRectangle.addListener( ( observable, oldValue, newValue ) -> {
			if(null != newValue) {
				java.awt.Rectangle roi = ( java.awt.Rectangle ) newValue;
				imageWidth = roi.width;
				imageHeight = roi.height;
			} else {
				if (this.studio.core() != null) {
					this.imageWidth = this.studio.core().getImageWidth();
					this.imageHeight = this.studio.core().getImageHeight();
				}
			}
			bufferSize = imageWidth * imageHeight * imageDepth / 8;
			computeTotal();
		} );

		this.zStart = new SimpleDoubleProperty( 0 );
		this.zEnd = new SimpleDoubleProperty( 0 );
		this.zCurrent = new SimpleDoubleProperty( 0 );

		this.currentTP = new SimpleDoubleProperty( 0 );
		this.cylinderSize = new SimpleDoubleProperty( 400 );

		this.rotateStepSize = new SimpleDoubleProperty(1);

		// Wait timer
		this.waitSeconds = waitSeconds;

		// 1. Property Map for summary panel
		this.propertyMap.put( "times", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "positions", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "slices", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "channels", new SimpleStringProperty( "0" ) );
		this.propertyMap.put( "cams", new SimpleStringProperty( "0" ) );
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

		if(setup != null)
		{
			if ( setup.getCamera1() != null )
				noCams++;
			if ( setup.getCamera2() != null )
				noCams++;

			maxZStack = setup.getZStage().getMaxPosition();
		}

		this.bufferSize = this.imageWidth * this.imageHeight * this.imageDepth / 8;

		positionItemTableView = TableViewUtil.createPositionItemDataView(this);

		// Buttons
		final ProgressIndicator pi = new ProgressIndicator(0);
		pi.setMinSize( 50,50 );
		pi.setMaxSize( 50,50 );

		final HBox acquireHBox = new HBox();
		acquireHBox.setSpacing(5);
		acquireHBox.setAlignment( Pos.CENTER_LEFT );
		acquireHBox.setPadding( new Insets(10) );

		Button acquireButton = new Button( "Acquire" );
		acquireButton.setMinSize( 110, 40 );
		acquireButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
		acquireButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(AcquisitionPanel.this.studio == null) {
					new Alert( Alert.AlertType.WARNING, "MM2 config is not loaded.").show();
					return;
				}

				if(studio != null)
				{
					System.out.println("Height: " + studio.core().getImageHeight());
					System.out.println("Width: " + studio.core().getImageWidth());
					System.out.println("Depth: " + studio.core().getImageBitDepth());
					System.out.println("BufferSize: " + studio.core().getImageBufferSize());
				}

				if(acquisitionThread == null) {
					if ( startAcquisition(acquireButton) )
					{
						acquireButton.setText( "Stop acquisition" );
						acquireButton.setStyle( "-fx-font: 15 arial; -fx-base: #e77d8c;" );
					}
				}
				else {
					stopAcquisition();
					acquireButton.setText( "Acquire" );
					acquireButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
				}
			}
		} );

		processedImages.addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				pi.setProgress( newValue.doubleValue() / totalImages.getValue() );
			}
		} );

		CheckBox antiDriftCheckbox = new CheckBox("Anti-Drift");
		antiDrift = antiDriftCheckbox.selectedProperty();

		Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 1));
		spinner.setPrefSize(55, 50);
		spinner.setMaxHeight(20);
		antiDriftRefCh = spinner.valueProperty();

		HBox chBox = new HBox(3, new Label("Ref Ch: "), spinner);
		chBox.setAlignment(Pos.CENTER);
		acquireHBox.getChildren().addAll(acquireButton, new VBox(2, antiDriftCheckbox, chBox), pi);
		spinner.setTooltip(new Tooltip("This channel index will be used for Anti-Drift reference"));

		BorderPane.setMargin(acquireHBox, new Insets(12,12,12,12));

		// listbox for Position list
		SplitPane timePositionSplit = new SplitPane(
				createPositionListPane(positionItemTableView),
				createTimePointsPane() );
		timePositionSplit.setOrientation( Orientation.VERTICAL );
		timePositionSplit.setDividerPositions( 0.5 );

		// acquisition order
		createAcquisitionOrderPane();
		final TitledPane acqPane = new TitledPane( "Acquisition", acquireHBox );
		acqPane.setCollapsible( false );

		VBox zstackAcquisitionOrderPane = new VBox( 20, createZStackPane(stagePanel), acqPane );

		// setup the Z-stacks
		// summary
		SplitPane positionZStackSplit = new SplitPane(timePositionSplit, zstackAcquisitionOrderPane);
		positionZStackSplit.setOrientation( Orientation.HORIZONTAL );
		positionZStackSplit.setDividerPositions( 0.6 );


		// Two tabs for "Laser Shutter" / "Arduino Shutter"
		int exposure = 20;
		if(studio != null && studio.core() != null)
		{
			try
			{
				exposure = (int) studio.core().getExposure();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}

		// Channel list
		if(setup != null) {
			Optional<String> multi = Arrays.stream( studio.core().getLoadedDevicesOfType( DeviceType.CameraDevice ).toArray() ).filter( c -> c.startsWith( "Multi" ) ).findFirst();

			if(multi.isPresent())
				channelItemTableView = TableViewUtil.createChannelItemDataView( setup, multi.get());
			else
				channelItemTableView = TableViewUtil.createChannelItemDataView( setup, null);
		}
		else
			channelItemTableView = TableViewUtil.createChannelItemDataView( setup, null);

		laserTab = new Tab( "Software Controlled" );
		Node viewContent = null;
		if(setup != null) {
			viewContent = createChannelItemTable( channelItemTableView, setup.getCamera1().getLabel(), setup.getLaser().getLabel(), exposure );
		} else {
			viewContent = createChannelItemTable( channelItemTableView, "Camera-1", "Laser-1", exposure );
		}
		laserTab.setContent( viewContent );
		laserTab.setClosable( false );

		channelItemArduinoTableView = TableViewUtil.createChannelItemArduinoDataView();
		Tab arduinoTab = new Tab( "Arduino Controlled" );
		arduinoTab.setContent( createChannelItemArduinoTable( channelItemArduinoTableView, pinItemTableView, exposure ) );
		arduinoTab.setClosable( false );

		channelTabPane = new TabPane( laserTab, arduinoTab );
		channelTabPane.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				computeTotalChannels();
			}
		} );

		CheckboxPane channelPane = new CheckboxPane( "Select Channels/Pins", channelTabPane );
		enabledChannels = channelPane.selectedProperty();

		// Acquisition Setting Buttons
		final HBox acqSettings = new HBox();
		acqSettings.setSpacing(5);
		acqSettings.setAlignment( Pos.CENTER_LEFT );

		Button saveButton = new Button( "SAVE" );
		saveButton.setMinSize( 100, 40 );
		saveButton.setStyle("-fx-font: 12 arial; -fx-base: #69e760;");
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

		Button loadButton = new Button( "LOAD" );
		loadButton.setMinSize( 100, 40 );
		loadButton.setStyle("-fx-font: 12 arial; -fx-base: #e7e45d;");
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

		Button clearButton = new Button( "CLEAR" );
		clearButton.setMinSize( 100, 40 );
		clearButton.setStyle("-fx-font: 12 arial; -fx-base: #ffbec4;");
		clearButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				Optional< ButtonType > results = new Alert( Alert.AlertType.WARNING, "Are you sure?", ButtonType.YES, ButtonType.CANCEL).showAndWait();

				if( results.isPresent() && results.get() == ButtonType.YES) {
					clearAcquisitionSetting();
				} else {
					System.err.println("Clear acquisition setting stopped by user cancellation.");
				}
			}
		} );

		acqSettings.getChildren().addAll( saveButton, loadButton, clearButton );
		BorderPane.setMargin(acqSettings, new Insets(12,12,12,12));

		final TitledPane acqSettingPane = new TitledPane( "Acquisition Setting", acqSettings );
		acqSettingPane.setCollapsible( false );

		// Laser shutter(software) / Arduino Shutter(hardware)
		// Save image options
		SplitPane channelListSaveImage = new SplitPane(
				channelPane,
				createSaveImagesPane(acqSettingPane)
		);
//		channelListSaveImage.setDividerPositions( 0.6 );

		VBox smartImagingBox = new VBox( 10, new Label( "Smart Imaging" ), smartImagingCylinder );
		smartImagingBox.setAlignment( Pos.CENTER_LEFT );
		smartImagingBox.setPadding( new Insets(10) );
		cylinderSize.bind(smartImagingBox.widthProperty());

		SplitPane content = new SplitPane( positionZStackSplit, smartImagingBox, channelListSaveImage );
		content.setOrientation( Orientation.VERTICAL );
		content.setDividerPositions( 0.5, 0.2 );

		content.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				content.setDividerPositions( 0.5, 0.2 );
			}
		});

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
				else computeTotalTimePoints();

				if( !enabledPositions.get() ) propertyMap.get("positions").set("");
				else computeTotalPositionImages();

				if( !enabledChannels.get() ) propertyMap.get("channels").set("");
				else computeTotalChannels();

				return !enabledChannels.get() && !enabledPositions.get() && !enabledZStacks.get() && !enabledTimePoints.get();
			}
		};

		disabledAcquisitionOrder.bind( bb );

		setCenter( content );

		HBox bottom = new HBox(20, createSummaryPane() );
		bottom.setAlignment( Pos.CENTER_LEFT );
		setBottom( bottom );

		addEventHandler( ControlEvent.STAGE, new EventHandler< ControlEvent >()
		{
			@Override public void handle( ControlEvent event )
			{
				if(event.getEventType().equals( ControlEvent.STAGE_MOVE )) {
					PositionItem pos = (PositionItem) event.getParam()[0];

					double zStart = pos.getZStart();
					double zEnd = pos.getZEnd();
					double zMid = zStart + (zEnd - zStart) / 2;

					if(spimSetup != null) {
//						double r = spimSetup.getThetaStage().getPosition();
//						double x = spimSetup.getXStage().getPosition();
//						double y = spimSetup.getYStage().getPosition();
//						double z = spimSetup.getZStage().getPosition();
//
//						pos.setX( x );
//						pos.setY( y );
//						pos.setR( r );
//						pos.setZStart( z );
//						pos.setZEnd( z + 10 );
						AcquisitionPanel.this.stagePanel.goToPos( pos.getX(), pos.getY(), zMid, pos.getR() );
						System.out.println(String.format( "Stage_Move: X:%.0f, Y:%.0f, Z:%.0f, R:%.0f",
								pos.getX(), pos.getY(), zMid, pos.getR() ) );
					}
					else {
//						pos.setX( 200 );
//						pos.setY( 100 );
//						pos.setR( 300 );
//						pos.setZStart( 60 );
//						pos.setZEnd( 60 + 10 );
						System.out.println(String.format( "Stage_Move: X:%.0f, Y:%.0f, Z:%.0f, R:%.0f",
								pos.getX(), pos.getY(), zMid, pos.getR() ) );
					}
					int i = positionItemTableView.getSelectionModel().getSelectedIndex();
					positionItemTableView.getSelectionModel().clearSelection();
					positionItemTableView.getSelectionModel().select( i );
					positionItemTableView.refresh();
				}
			}
		} );
	}

	Studio getStudio() {
		return this.studio;
	}

	private static String getUserDataDirectory()
	{
		String path = System.getProperty( "user.home" ) + File.separator
				+ ".openSpimAcq"
				+ File.separator;
		dirExist( path );
		return path;
	}

	private static void dirExist( String dir )
	{
		if ( !new File( dir ).exists() )
		{
			new File( dir ).mkdirs();
		}
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio )
	{
		// Automatic Acquisition Setting saving
		if(this.studio != null) {
			String acqSettingFile = (( MMStudio ) this.studio).getSysConfigFile() + ".xml";
			acqSettingFile = acqSettingFile.replace( File.separator, "_" );
			AcquisitionSetting.save( new File(getUserDataDirectory() + acqSettingFile), getAcquisitionSetting() );
		}

		this.spimSetup = setup;
		this.studio = studio;

		if(studio != null)
		{
			System.out.println("Height: " + this.studio.core().getImageHeight());
			System.out.println("Width: " + this.studio.core().getImageWidth());
			System.out.println("Depth: " + this.studio.core().getImageBitDepth());
			System.out.println("BufferSize: " + this.studio.core().getImageBufferSize());
			this.imageWidth = studio.core().getImageWidth();
			this.imageHeight = studio.core().getImageHeight();
			this.imageDepth = studio.core().getImageBitDepth();

			if(setup != null)
			{
				if ( setup.getCamera1() != null )
					noCams++;
				if ( setup.getCamera2() != null )
					noCams++;

				maxZStack = setup.getZStage().getMaxPosition();

				int exposure = 20;
				try
				{
					exposure = (int) studio.core().getExposure();
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}

				Optional<String> multi = Arrays.stream( studio.core().getLoadedDevicesOfType( DeviceType.CameraDevice ).toArray() ).filter( c -> c.startsWith( "Multi" ) ).findFirst();

				if(multi.isPresent())
					channelItemTableView = TableViewUtil.createChannelItemDataView(setup, multi.get());
				else channelItemTableView = TableViewUtil.createChannelItemDataView(setup, null);
				Node viewContent = createChannelItemTable( channelItemTableView, setup.getCamera1().getLabel(), setup.getLaser().getLabel(), exposure );
				laserTab.setContent( viewContent );

				if(spimSetup != null && spimSetup.getThetaStage() != null) {
					rotateStepSize.bindBidirectional(spimSetup.getThetaStage().stepSizeProperty());
				}
			}

			String acqSettingFile = (( MMStudio ) this.studio).getSysConfigFile() + ".xml";
			acqSettingFile = acqSettingFile.replace( File.separator, "_" );
			File acqSetting = new File(getUserDataDirectory() + acqSettingFile);
			if(acqSetting.exists()) {
				final AcquisitionSetting setting = AcquisitionSetting.load( acqSetting );
				if(setting != null) {
					// Update GUI
					updateUI( setting );
				}
			}
		} else {
			this.imageWidth = 512;
			this.imageHeight = 512;
			this.imageDepth = 8;
			this.bufferSize = 512 * 512;

			if(setup == null)
			{
				noCams = 0;
				maxZStack = 100;
				int exposure = 20;

				channelItemTableView = TableViewUtil.createChannelItemDataView(setup, null);
				Node viewContent = createChannelItemTable( channelItemTableView, "Camera-1", "Laser-1", exposure );
				laserTab.setContent( viewContent );
			}
		}
	}

	public void setStagePanel(StagePanel stagePanel) {

		double cubeHeight = 200;

		if(stagePanel != null) {
			zStackGridPane.getChildren().remove( zSlider );

			this.stagePanel = stagePanel;
			this.stagePanel.setAcquisitionPanel( this );

			currentChangeListener = ( observable, oldValue, newValue ) -> zCurrent.set( newValue.doubleValue() / maxZStack * cubeHeight );

			stagePanel.getZValueProperty().addListener( currentChangeListener);

			zStackGroup.getChildren().remove( cube );

		} else {
			this.stagePanel.setAcquisitionPanel( null );
			this.stagePanel = null;

			zStackGridPane.add( zSlider, 3, 0, 1, 2 );
		}
		cube = new StackCube(50, cubeHeight, Color.CORNFLOWERBLUE, 1, zStart, zEnd, zCurrent );

		zStackGroup.getChildren().add( 0, cube );
		cube.setTranslateX( -60 );
	}

	private void updateUI ( AcquisitionSetting setting ) {
		// 1.1 Time points panel
		enabledTimePoints.set( setting.getEnabledTimePoints() );

		// 1.2 Smart Imaging option
		timePointItems = setting.getTimePointItems();
		timePointItemTableView.getItems().setAll( timePointItems );

		// 2. Position panel
		enabledPositions.set( setting.getEnabledPositions() );
		positionItems = setting.getPositionItems();
		positionItemTableView.getItems().setAll( positionItems );

		// 3. Z-Stack panel
		enabledZStacks.set( setting.getEnabledZStacks() );

		// 4. Acquisition order panel
		acquisitionOrder.set( setting.getAcquisitionOrder() );

		// 5. Channels panel
		enabledChannels.set( setting.getEnabledChannels() );
		channelTabPane.getSelectionModel().select( setting.getSelectedTab() );
		channelItems = setting.getChannelItems();
		channelItemTableView.getItems().setAll( channelItems );

		channelItemsArduino = setting.getChannelItemsArduino();
		ObservableList<ChannelItem> arduinoItems = channelItemArduinoTableView.getItems();
		for(int i = 0; i < arduinoItems.size(); i++) {
			arduinoItems.get( i ).setValue( channelItemsArduino.get(i).getValue() );
			arduinoItems.get( i ).setSelected( channelItemsArduino.get(i).getSelected() );
		}
		channelItemArduinoTableView.refresh();

		// 6. Save Image panel
		enabledSaveImages.set( setting.getEnabledSaveImages() );
		directory.set( setting.getDirectory() );
		filename.set( setting.getFilename() );
		savingFormat.set( setting.getSavingFormat() );
		saveAsHDF5.set( setting.getSaveAsHDF5() );
		saveMIP.set( setting.getSaveMIP() );
		roiRectangle.set( setting.getRoiRectangle() );
		if(studio != null && studio.live() != null && studio.live().getDisplay() != null && studio.live().getDisplay().getImagePlus() != null && studio.live().getDisplay().getImagePlus().getRoi() != null) {
			Roi ipRoi = studio.live().getDisplay().getImagePlus().getRoi();
			studio.live().getDisplay().getImagePlus().setRoi( ( java.awt.Rectangle ) setting.getRoiRectangle() );
		}

		// Extra update
		rotateStepSize.set(setting.getRotateStepSize());

		// Experiment Note
		experimentNote.set(setting.getExperimentNote());
	}

	private AcquisitionSetting getAcquisitionSetting() {
		timePointItems = new ArrayList<>( timePointItemTableView.getItems() );
		positionItems = new ArrayList<>( positionItemTableView.getItems() );
		channelItems = new ArrayList<>( channelItemTableView.getItems() );
		channelItemsArduino = new ArrayList<>( channelItemArduinoTableView.getItems() );

		return new AcquisitionSetting( enabledTimePoints, timePointItems,
				enabledPositions, positionItems, enabledZStacks, acquisitionOrder,
				enabledChannels, channelTabPane.getSelectionModel().selectedIndexProperty().get(), channelItems,
				channelItemsArduino, enabledSaveImages, directory, filename, savingFormat, saveAsHDF5, saveMIP, roiRectangle, rotateStepSize,
				experimentNote );
	}

	private void clearAcquisitionSetting() {
		timePointItemTableView.getItems().clear();
		positionItemTableView.getItems().clear();
		channelItemTableView.getItems().clear();
		channelItemArduinoTableView.getItems().clear();

		enabledTimePoints.set(true);

		enabledPositions.set( true );
		enabledZStacks.set( true );
		acquisitionOrder.set( "Time > Position > Slice > Channel" );
		enabledChannels.set( true );
		channelTabPane.getSelectionModel().select( 0 );

		enabledSaveImages.set( true );
		directory.set( "" );
		filename.set( "Untitled" );
		savingFormat.set( null );
		saveAsHDF5.set( false );
		saveMIP.set( false );
		roiRectangle.set( null );

		rotateStepSize.set(1);
		experimentNote.set( "" );
	}

	public void stopAcquisition()
	{
		System.out.println("Stop button pressed");
		waitSeconds.set(-1);

		if ( acquisitionThread == null )
		{
			System.err.println("Acquisition thread is not running!");
			return;
		}

		if (null != engine) engine.stop();

		try
		{
			acquisitionThread.interrupt();
			acquisitionThread.join(30000);
		} catch (NullPointerException npe) {
			// Don't care.
		} catch (InterruptedException e1) {
			System.err.println("Couldn't stop the thread gracefully.");
		}

		acquisitionThread = null;
		Platform.runLater( () -> processedImages.set( 0 ) );
	}

	public boolean startAcquisition( Button acquireButton )
	{
		System.out.println("Acquire button pressed");

		if ( acquisitionThread != null )
		{
			System.err.println("Acquisition thread is running!");
			return false;
		}

		if ( !stagePanel.isOn() ) {
			new Alert( Alert.AlertType.WARNING, "The stage is offline. Please, turn on the stage first." ).showAndWait();
			return false;
		}

		// Check the folder
		if ( enabledSaveImages.get() && directory.getValue().isEmpty() )
		{
			Optional< ButtonType > results = new Alert( Alert.AlertType.WARNING, "Saving folder is not specified. \nDo you want specify it now?", ButtonType.YES, ButtonType.NO).showAndWait();

			if( results.isPresent() && results.get() == ButtonType.YES) {
				DirectoryChooser d = new DirectoryChooser();
				File f = d.showDialog(null);
				if (f != null) directory.set( f.getAbsolutePath() );
				else return false;
			} else {
				System.err.println("Acquisition stopped due to no saving directory specified.");
				return false;
			}
		}

		// Check if the specified file name exists
		if ( enabledSaveImages.get() )
		{
			File folder = new File(directory.getValue());

			if(folder != null && null != folder.listFiles()) {
				boolean found = false;

				for(File file: Objects.requireNonNull( folder.listFiles() ) ) {
					if(file.getName().startsWith( filename.getValue() )) {
						found = true;
						break;
					}
				}

				if(found) {
					Optional< ButtonType > results = new Alert( Alert.AlertType.WARNING, "The given filename exists. \nDo you want delete them?", ButtonType.YES, ButtonType.CANCEL).showAndWait();

					if( results.isPresent() && results.get() == ButtonType.YES) {
						for(File file: Objects.requireNonNull( folder.listFiles() ) ) {
							if(file.getName().startsWith( filename.getValue() )) {
								file.delete();
							}
						}
						File spim = new File(folder, filename.getValue() + "-spim");
						if(spim.exists()) {
							for(File file: Objects.requireNonNull( spim.listFiles() ) ) {
								if(file.getName().startsWith( filename.getValue() )) {
									file.delete();
								}
							}
						}
						spim.delete();
					} else {
						System.err.println("Acquisition stopped by user cancellation.");
						return false;
					}
				}
			}
		}

		if(timePointItemTableView.getItems().size() < 1) {
			new Alert( Alert.AlertType.WARNING, "Please, add timepoints for the acquisition.").showAndWait();

			System.err.println("Acquisition stopped due to no timepoints specified in Smart-Imaging panel.");
			return false;
		}

		if(positionItemTableView.getItems().size() < 1) {
			new Alert( Alert.AlertType.WARNING, "Please, add positions for the acquisition.").showAndWait();

			System.err.println("Acquisition stopped due to no positions specified.");
			return false;
		}

		double pixSizeUm = getStudio().core().getPixelSizeUm();
		if(antiDrift.getValue() && ! (pixSizeUm > 0)) {
			new Alert( Alert.AlertType.WARNING, "Please, provide pixel size calibration data before using Anti-Drift").showAndWait();

			System.err.println("Acquisition stopped due to no pixelSizeUm");
			return false;
		}

		engine = new MMAcquisitionEngine();

		engine.init();

		List<ChannelItem> channelItemList;

		final boolean arduinoSelected = channelTabPane.getSelectionModel().isSelected( 1 );

		if(arduinoSelected)
			channelItemList = channelItemArduinoTableView.getItems().stream().filter( c -> c.getSelected() ).collect( Collectors.toList() );
		else
			channelItemList = channelItemTableView.getItems().stream().filter( c -> c.getSelected() ).collect( Collectors.toList() );

		int tp = propertyMap.get("times").getValue().isEmpty() ? 1 : Integer.parseInt( propertyMap.get("times").getValue() );

		Platform.runLater( () -> processedImages.set( 0 ) );

		acquisitionThread = new Thread(() ->
		{
			Thread.currentThread().setContextClassLoader( HalcyonMain.class.getClassLoader() );

			try
			{
				engine.performAcquisition( studio, spimSetup, stagePanel, ( java.awt.Rectangle) roiRectangle.get(), tp,
						timePointItemTableView.getItems(), currentTP, waitSeconds,
						arduinoSelected, new File(directory.getValue()), filename.getValue(),
						positionItemTableView.getItems(), channelItemList, processedImages,
						enabledSaveImages.get(), savingFormat.getValue(), saveMIP.getValue(), antiDrift.getValue(), experimentNote.getValue(),
						antiDriftLog, antiDriftRefCh.get() );

//				new MMAcquisitionRunner().runAcquisition();

//				engine.performAcquisitionMM( spimSetup, stagePanel, (java.awt.Rectangle) roiRectangle.get(), tp, deltaT * unit, arduinoSelected, new File(directory.getValue()), filename.getValue(), positionItemTableView.getItems(), channelItemList, processedImages, enabledSaveImages.get());


				acquisitionThread = null;
				engine = null;
				Platform.runLater( () -> {
					acquireButton.setText( "Acquire" );
					acquireButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
				} );

//				Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		} );

		acquisitionThread.start();

		return true;
	}

	static double getUnit(String unitString) {
		double unit = 1;

		switch ( unitString ) {
			case "ms" : unit = 0.001;
				break;
			case "min" : unit = 60;
				break;
		}

		return unit;
	}

	private Node createPositionListPane( TableView< PositionItem > positionItemTableView ) {
		positionItemTableView.setEditable( true );

		EventHandler newEventHandler = ( EventHandler< ActionEvent > ) event -> {
			if(spimSetup != null ) {
				double r = spimSetup.getThetaStage().getPosition();
				double x = spimSetup.getXStage().getPosition();
				double y = spimSetup.getYStage().getPosition();
				double z = spimSetup.getZStage().getPosition();
				positionItemTableView.getItems().add( new PositionItem( x, y, r, z, z, 6 ) );
			}
			else {
				positionItemTableView.getItems().add( new PositionItem( 10, 20, 30, 20, 50, 10 ) );
			}
		};

		EventHandler deleteEventHandler = new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(positionItemTableView.getSelectionModel().getSelectedIndex() > -1)
					positionItemTableView.getItems().remove( positionItemTableView.getSelectionModel().getSelectedIndex() );
			}
		};

		Button newButton = new Button("Add current position/angle");
		newButton.setOnAction( newEventHandler );

		Button deleteButton = new Button("Delete");
		deleteButton.setOnAction( deleteEventHandler );

		MenuItem deleteItem = new MenuItem( "Delete" );
		deleteItem.setOnAction( event -> deleteButton.fire() );

		HBox hbox = new HBox( 5, newButton, deleteButton );

		positionItemTableView.getSelectionModel().selectedItemProperty().addListener( new ChangeListener< PositionItem >()
		{
			@Override public void changed( ObservableValue< ? extends PositionItem > observable, PositionItem oldValue, PositionItem newValue )
			{
				currentPosition.set( newValue );
			}
		} );

		positionItemTableView.setContextMenu( new ContextMenu( deleteItem ) );

		positionItemTableView.getItems().addListener( new InvalidationListener()
		{
			@Override public void invalidated( Observable observable )
			{
				computeTotalPositionImages();
			}
		} );

		CheckboxPane pane = new CheckboxPane( "Positions/Angles", new VBox( hbox, positionItemTableView ) );
		enabledPositions = pane.selectedProperty();

		Tab positionTab = new Tab("Position", pane);
		positionTab.setClosable(false);

		TextArea antiDriftLogArea = new TextArea();
		antiDriftLogArea.setEditable(false);
		antiDriftLog = antiDriftLogArea.textProperty();

		Tab antiDriftLogTab = new Tab("Anti-Drift logs", antiDriftLogArea);
		antiDriftLogTab.setClosable(false);

		TabPane tabPane = new TabPane(positionTab, antiDriftLogTab);

		VBox vbox = new VBox( 12, tabPane, pane );

		return vbox;
	}

	public void computeTotalPositionImages() {
		long totalImages = 0;
		for(PositionItem item : positionItemTableView.getItems())
		{
			if(item.getZEnd() > item.getZStart()) {
				totalImages += (item.getZEnd() - item.getZStart() + item.getZStep()) / item.getZStep();
			}
		}

		propertyMap.get("positions").setValue( totalImages + "" );
	}

	private Node createSaveImagesPane(Node buttonPane) {
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
				Path path = Paths.get(directory.getValue());
				if(path.getParent() != null)
					d.setInitialDirectory( new File( path.getParent().toString() ) );

				File f = d.showDialog(null);
				if (f != null) finalTextField.setText( f.getAbsolutePath() );
			}
		} );

		ImageView img = new ImageView(ResourceUtil.getString("root.icon"));
		Button openFolder = new Button();
		openFolder.setGraphic(img);
		openFolder.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				try {
					Desktop.getDesktop().open(new File(directory.get()));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		gridpane.addRow( 0, new Label( "Directory:" ), textField, selectFolder, openFolder );

		textField = new TextField("Untitled");
		filename = textField.textProperty();

		propertyMap.put( "filename", textField.textProperty() );
		gridpane.addRow( 1, new Label( "File name:" ), textField );

		ComboBox c = new ComboBox<>( FXCollections.observableArrayList(
				// "Separate all dimensions", <- not implemented yet
				"None", "Separate the channel dimension", "Image stack (include multi-channel)" ) );

		c.valueProperty().setValue("None");

		savingFormat = c.valueProperty();

		Label desc = new Label( "The default saving format is Single Plane TIFF.\nYou can have additional formats:" );
		gridpane.addRow( 2, desc);
		gridpane.setColumnSpan( desc, 2 );

		gridpane.addRow( 3, new Label( "SPIM format:" ), c );

		CheckBox ch = new CheckBox( "Save as HDF5" );
		// TODO: Use N5 format instead
//		gridpane.addRow( 3, ch );
		saveAsHDF5 = ch.selectedProperty();

		CheckBox mip = new CheckBox( "Save & show Maximum Intensity Projection in each stack" );
		gridpane.addRow( 4, mip );
		gridpane.setColumnSpan( mip, 2 );

		saveMIP = mip.selectedProperty();

		CheckboxPane pane = new CheckboxPane( "Save Images", gridpane, 12 );
		enabledSaveImages = pane.selectedProperty();

		Tab saveOptionTab = new Tab("Save option", pane);
		saveOptionTab.setClosable(false);

		TextArea textArea = new TextArea();
		textArea.setWrapText(true);
		experimentNote = textArea.textProperty();

		Tab noteTab = new Tab("Notes", textArea);
		noteTab.setClosable(false);

		TabPane tabPane = new TabPane(saveOptionTab, noteTab);

		VBox vbox = new VBox( 12, tabPane, buttonPane );

		return vbox;
	}

	private Node createChannelItemTable( TableView< ChannelItem > channelItemTableView, String camera, String laser, int exp ) {
		channelItemTableView.setEditable( true );

		InvalidationListener invalidationListener = observable -> computeTotalChannels();

		EventHandler<ActionEvent> newChannelHandler = new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				// TODO: Get the current position from the stage control and make the new position
				channelItemTableView.getItems().add( new ChannelItem( camera, laser, exp, invalidationListener ) );
			}
		};

		EventHandler<ActionEvent> deleteChannelHandler =  new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(channelItemTableView.getSelectionModel().getSelectedIndex() > -1)
					channelItemTableView.getItems().remove( channelItemTableView.getSelectionModel().getSelectedIndex() );
			}
		};

		Button newChannelButton = new Button( "Add a channel" );
		newChannelButton.setOnAction( newChannelHandler );

		Button deleteChannelButton = new Button( "Delete" );
		deleteChannelButton.setOnAction( deleteChannelHandler );

		MenuItem newItem = new MenuItem( "New" );
		newItem.setOnAction( event -> newChannelButton.fire() );

		MenuItem deleteItem = new MenuItem( "Delete" );
		deleteItem.setOnAction( event -> deleteChannelButton.fire() );

		// add context menu here
		channelItemTableView.setContextMenu( new ContextMenu( newItem, deleteItem ) );

		channelItemTableView.getItems().addListener( new InvalidationListener()
		{
			@Override public void invalidated( Observable observable )
			{
				computeTotalChannels();
			}
		} );


		HBox hbox = new HBox( 10, newChannelButton, deleteChannelButton );

		return new VBox( hbox, channelItemTableView);
	}

	private TableView< ChannelItem > createChannelItemArduinoTable( TableView< ChannelItem > channelItemTableView, TableView< PinItem > pinItemTableView, int exposure ) {
		channelItemTableView.setEditable( true );

		InvalidationListener invalidationListener = observable -> {
			computeTotalChannels();
			if(spimSetup != null && spimSetup.getArduino1() != null) {
				int sum = 0;
				for (ChannelItem item : channelItemTableView.getItems()) {
					if(item.getSelected()) {
						sum += Integer.parseInt( item.getLaser() );

						if(item.selectedProperty().equals( observable )) {
							System.out.println("[Arduino] activated switch : " + item.getLaser());
						}
					}
				}
				System.out.println("[Arduino] switch status : " + sum);
				spimSetup.getArduino1().setSwitchState( Integer.toString( sum ) );
			} else {
				int sum = 0;
				for (ChannelItem item : channelItemTableView.getItems()) {
					if(item.getSelected()) {
						sum += Integer.parseInt( item.getLaser() );

						if(item.selectedProperty().equals( observable )) {
							System.out.println("[Arduino-Demo] activated switch : " + item.getLaser());
						}
					}
				}
				System.out.println("[Arduino-Demo] switch status : " + sum);
			}
		};

		for (PinItem item : pinItemTableView.getItems()) {
			if(item.getState() > 0 && item.getState() < 63)
				channelItemTableView.getItems().add( new ChannelItem( item, exposure, invalidationListener ) );
		}

		return channelItemTableView;
	}

	private void computeTotalChannels() {
		int totalChannels;

		if(channelTabPane.getSelectionModel().isSelected( 0 )) {
			int multi = (int) channelItemTableView.getItems().stream().filter( c -> c.getSelected() && c.getName().startsWith("Multi") ).count() * 2;

			totalChannels = (int) channelItemTableView.getItems().stream().filter( c -> c.getSelected() && !c.getName().startsWith("Multi")).count() + multi;
			propertyMap.get("cams").setValue( 1 + "" );
		}
		else {
			totalChannels = (int) channelItemArduinoTableView.getItems().stream().filter( c -> c.getSelected() ).count();
			propertyMap.get("cams").setValue( 2 + "" );
		}

		propertyMap.get("channels").setValue( totalChannels + "" );
	}

	private LabeledPane createSummaryPane() {
		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 20 );

		// First row
		Label label = new Label();
		label.textProperty().bind( propertyMap.get("positions") );
		label.textProperty().addListener( observable -> computeTotal() );

		Label label2 = new Label();
		label2.textProperty().bind( propertyMap.get("totalImages") );
		gridpane.addRow( 0, new Label("No. of positions: "), label, new Label("Total images: "), label2 );

		// Second row
		label = new Label();
		label.textProperty().bind( propertyMap.get("times") );
		label.textProperty().addListener( observable -> computeTotal() );

		label2 = new Label();
		label2.textProperty().bind( propertyMap.get("totalSize") );
		gridpane.addRow( 1, new Label("No. of time points: "), label, new Label("Total size: "), label2 );

		// Third row
		label = new Label();
		label.textProperty().bind( propertyMap.get("cams") );
		label.textProperty().addListener( observable -> computeTotal() );

		label2 = new Label();
		label2.textProperty().bind( propertyMap.get("duration") );
		gridpane.addRow( 2, new Label("No. of cameras: "), label, new Label("Duration: "), label2 );

		label = new Label();
		label.textProperty().bind( propertyMap.get("channels") );
		label.textProperty().addListener( observable -> computeTotal() );

		label2 = new Label();
		label2.textProperty().bind( propertyMap.get("order") );
		gridpane.addRow( 3, new Label("No. of channels: "), label, new Label("Order: "), label2 );

		return new LabeledPane( "Summary", gridpane );
	}

	private void computeTotal() {
		long tp = propertyMap.get("times").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("times").getValue() );
		long pos = propertyMap.get("positions").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("positions").getValue() );
		long ch = propertyMap.get("channels").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("channels").getValue() );
		long cams = propertyMap.get("cams").getValue().isEmpty() ? 1 : Long.parseLong( propertyMap.get("cams").getValue() );

		tp = tp == 0 ? 1: tp;
		pos = pos == 0 ? 1: pos;
		cams = cams == 0 ? 1: cams;
		ch = ch == 0 ? 1: ch;
		long tot = tp * pos * ch * cams;

		propertyMap.get("totalImages").setValue( String.format( "%d", tot ) );
		this.totalImages.set( tot );
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

	private Button createZStackButton(String name) {
		Button button = new Button( name );
		button.setMinWidth( 70 );
		button.setMinHeight( 22 );
		button.setAlignment( Pos.BASELINE_CENTER );
		button.setStyle("-fx-text-fill: white; -fx-base: #43a5e7;");
		return button;
	}

	private CheckboxPane createZStackPane( StagePanel stagePanel ) {

		double cubeHeight = 200;

		if(stagePanel == null)
		{
			zSlider = new Slider(0, 100, 0);
			zSlider.setOrientation( Orientation.VERTICAL );

			currentChangeListener = ( observable, oldValue, newValue ) -> zCurrent.set( newValue.doubleValue() / maxZStack * cubeHeight );

			zSlider.valueProperty().addListener( currentChangeListener );
		}
		else
		{
			currentChangeListener = ( observable, oldValue, newValue ) -> zCurrent.set( newValue.doubleValue() / maxZStack * cubeHeight );

			stagePanel.getZValueProperty().addListener( currentChangeListener );
		}
		cube = new StackCube(50, cubeHeight, Color.CORNFLOWERBLUE, 1, zStart, zEnd, zCurrent );

//		cube.setRotate( 180 );
		cube.setTranslateX( -60 );

		zStackGridPane = new GridPane();
		zStackGridPane.setVgap( 50 );
		zStackGridPane.setHgap( 5 );

		Button startButton = createZStackButton( "Z-start" );
		TextField zStartField = createNumberTextField();
		zStartField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(!newValue.isEmpty()) {
					zStackStart = Double.parseDouble( newValue );
					zStart.set( zStackStart / maxZStack * cubeHeight );
				}
			}
		} );

		zStartField.setOnAction( event -> {
			if(currentPosition.get() != null) {
				currentPosition.get().setZStart( zStackStart );
				computeTotalPositionImages();
				positionItemTableView.refresh();
			}
		} );

		zStackGridPane.addRow( 0, startButton, zStartField );

		TextField zStepField = createNumberTextField();
		zStepField.setText( "1.5" );
		zStepField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(!newValue.isEmpty()) {
					zStackStepSize = Double.parseDouble( newValue );
				}
			}
		} );
		zStepField.setOnAction( event -> {
			if(currentPosition.get() != null)
			{
				currentPosition.get().setZStep( zStackStepSize );
				computeTotalPositionImages();
				positionItemTableView.refresh();
			}
		} );

		Button midButton = createZStackButton( "Go to centre" );

		zStackGridPane.addRow( 1, midButton, zStepField, new Label( "Z-step (\u03BCm)" ) );

		Button endButton = createZStackButton( "Z-end" );
		TextField zEndField = createNumberTextField();
		zEndField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				if(!newValue.isEmpty()) {
					zStackEnd = Double.parseDouble( newValue );
					zEnd.set( zStackEnd / maxZStack * cubeHeight );
				}
			}
		} );

		zEndField.setOnAction( event -> {
			if(currentPosition.get() != null) {
				currentPosition.get().setZEnd( zStackEnd );
				computeTotalPositionImages();
				positionItemTableView.refresh();
			}
		} );

		setupMouseClickedHandler(startButton, zStartField, endButton, zEndField, midButton);

		zStackGridPane.addRow( 2, endButton, zEndField );

		currentPosition.addListener( new ChangeListener< PositionItem >()
		{
			@Override public void changed( ObservableValue< ? extends PositionItem > observable, PositionItem oldValue, PositionItem newValue )
			{
				if(newValue != null) {
					zStartField.setText( (int)newValue.getZStart() + "" );
					zStepField.setText( newValue.getZStep() + "");
					zEndField.setText( (int)newValue.getZEnd() + "");

					zStart.set( newValue.getZStart() / maxZStack * cubeHeight );
					zEnd.set( newValue.getZEnd() / maxZStack * cubeHeight );
				}
			}
		} );

		if(stagePanel == null && zSlider != null)
			zStackGridPane.add( zSlider, 3, 0, 1, 2 );

		Button newButton = new Button( "Add Pos." );
		newButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(zStartField.getText().isEmpty()) {
					addNewPosition( -1, -1, Double.parseDouble( zStepField.getText() ) );
				} else {
					addNewPosition( Integer.parseInt( zStartField.getText() ),
							Integer.parseInt( zEndField.getText() ), Double.parseDouble( zStepField.getText() ) );
				}
			}
		} );

		Button updateButton = new Button("Update");
		updateButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(currentPosition.get() != null) {
					currentPosition.get().setZStart( Double.parseDouble( zStartField.getText() ) );
					currentPosition.get().setZStep( Double.parseDouble( zStepField.getText() ) );
					currentPosition.get().setZEnd( Double.parseDouble( zEndField.getText() ) );
					computeTotalPositionImages();
					positionItemTableView.refresh();
				}
			}
		} );
		zStackGridPane.addRow( 3, newButton, updateButton );

		zStackGridPane.setTranslateY( -30 );

		// create a group
		zStackGroup = new Group(cube, zStackGridPane );

		CheckboxPane pane = new CheckboxPane( "Z-stacks", zStackGroup );
		enabledZStacks = pane.selectedProperty();
		return pane;
	}

	private void addNewPosition( int zStart, int zEnd, double zStep ) {
		if(spimSetup != null ) {
			double r = spimSetup.getThetaStage().getPosition();
			double x = spimSetup.getXStage().getPosition();
			double y = spimSetup.getYStage().getPosition();

			if( zStart < 0 && zEnd < 0 ) {
				double z = spimSetup.getZStage().getPosition();
				positionItemTableView.getItems().add(new PositionItem(x, y, r, z, z, zStep));
			} else {
				positionItemTableView.getItems().add(new PositionItem(x, y, r, zStart, zEnd, zStep));
			}
		}
		else {
			positionItemTableView.getItems().add( new PositionItem( 10, 20, 30, zStart, zEnd, zStep ) );
		}
	}

	public void addNewPosition() {
		addNewPosition( (int) zStackStart, (int) zStackEnd, zStackStepSize );
	}

	private void setupMouseClickedHandler( Button startButton, TextField zStartField, Button endButton, TextField zEndField, Button midButton )
	{
		startButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(spimSetup != null && spimSetup.getZStage() != null) {
					int currPos = (int) spimSetup.getZStage().getPosition();
					if(zEndField.getText().isEmpty())
						zStartField.setText( currPos + "" );
					else {
						int zEnd = Integer.parseInt( zEndField.getText() );
						if(zEnd < currPos) {
							zEndField.setText( currPos + "" );
							zStartField.setText( zEnd + "" );
						} else {
							zStartField.setText( currPos + "" );
						}
					}
				}
			}
		} );

		endButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(spimSetup != null && spimSetup.getZStage() != null) {
					int currPos = (int) spimSetup.getZStage().getPosition();
					if(zStartField.getText().isEmpty())
						zEndField.setText( currPos + "" );
					else {
						int zStart = Integer.parseInt( zStartField.getText() );
						if(zStart < currPos) {
							zEndField.setText( currPos + "" );
						} else {
							zStartField.setText( currPos + "" );
							zEndField.setText( zStart + "" );
						}
					}
				}
			}
		} );

		midButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(spimSetup != null && spimSetup.getZStage() != null) {
					if(!zStartField.getText().isEmpty() && !zEndField.getText().isEmpty())
					{
						double zStart = Double.parseDouble( zStartField.getText() );
						double zEnd = Double.parseDouble( zEndField.getText() );

						if(zStart < zEnd) {
							stagePanel.goToZ(zStart + (zEnd - zStart) / 2);
						} else
							System.err.println("zStart is bigger than zEnd. Cannot go to the centre point.");
					}
				}
				else {
					if(!zStartField.getText().isEmpty() && !zEndField.getText().isEmpty())
					{
						double zStart = Double.parseDouble( zStartField.getText() );
						double zEnd = Double.parseDouble( zEndField.getText() );

						if(zStart < zEnd) {
							System.out.println(String.format( "Stage_Move: Z:%.0f",
									zStart + (zEnd - zStart) / 2 ) );
						} else
							System.err.println("zStart is bigger than zEnd. Cannot go to the centre point.");
					}
				}
			}
		} );
	}

	private CheckboxPane createTimePointsPane()
	{
		// Dynamic timepoints
		timePointItemTableView = createTimePointItemDataView();
		timePointItemTableView.setEditable( true );

		Button newTPButton = new Button("New TP");
		newTPButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				timePointItemTableView.getItems().add( new TimePointItem( 1 , 1, TimePointItem.IntervalUnit.Sec ) );
			}
		} );

		Button newWaitButton = new Button("New Wait");
		newWaitButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				timePointItemTableView.getItems().add( new TimePointItem( 10, TimePointItem.IntervalUnit.Sec ) );
			}
		} );

		Button deleteButton = new Button("Delete");
		deleteButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(timePointItemTableView.getSelectionModel().getSelectedIndex() > -1)
					timePointItemTableView.getItems().remove( timePointItemTableView.getSelectionModel().getSelectedIndex() );
			}
		} );

		HBox hbox = new HBox( 5, newTPButton, newWaitButton, deleteButton );

		smartImagingCylinder = new CylinderProgress( cylinderSize, timePointItemTableView.getItems(), currentTP );

		CheckboxPane pane = new CheckboxPane( "Time points", new VBox( hbox, timePointItemTableView ) );

		TimePointItem.updateTimePointItem.addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				// System.out.println("Compute: " + newValue);
			}
		} );

		StringBinding sb = new StringBinding()
		{
			{
				super.bind( TimePointItem.updateTimePointItem, timePointItemTableView.getItems() );
			}
			@Override protected String computeValue()
			{
				return computeTotalTimePoints();
			}
		};

		propertyMap.get("duration").bind( sb );

		enabledTimePoints = pane.selectedProperty();
		return pane;
	}

	private String computeTotalTimePoints() {
		String expectedTime;

		// Dynamic timeline
		double total = timePointItemTableView.getItems().stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();

		int noTimePoints = timePointItemTableView.getItems().stream().mapToInt( TimePointItem::getNoTimePoints ).sum();

		propertyMap.get("times").setValue( noTimePoints + "" );

		expectedTime = TimePointItem.toString( total );

		return expectedTime;
	}

	private TextField createNumberTextField() {
		TextField textField = new TextField() {
			@Override public void replaceText(int start, int end, String text) {
				if (text.matches("[0-9\\.]*")) {
					super.replaceText(start, end, text);
				}
			}

			@Override public void replaceSelection(String text) {
				if (text.matches("[0-9\\.]*")) {
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
		public StackCube(double size, double height, Color color, double shade,
				DoubleProperty startZ, DoubleProperty endZ,
				DoubleProperty currentZ ) {


			Rectangle current = RectangleBuilder.create() // top face
					.width(size).height(0.25*size)
					.fill(Color.RED.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5))
					.translateX(0)
					.translateY( - 0.75 * size )
					.transforms( new Shear( -2, 0 ) )
					.build();

			current.translateYProperty().bind( currentZ.subtract( 0.75 * size ) );

			double currentStackSize = endZ.get() - startZ.get();
			double endPosition = startZ.get();

			Color posStackColor = Color.GREEN.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5);

			Rectangle currentStackTopFace = RectangleBuilder.create() // top face
					.width(size).height(0.25*size)
					.fill(posStackColor.deriveColor(0.0, 1.0, (1 - 0.1*shade), 1.0))
					.translateX(0)
					.translateY( endPosition - 0.75 * size)
					.transforms( new Shear( -2, 0 ) )
					.build();

			currentStackTopFace.translateYProperty().bind( startZ.subtract( 0.75 * size ) );

			Rectangle currentStackRightFace = RectangleBuilder.create() // right face
					.width(size/2).height(currentStackSize)
					.fill(posStackColor.deriveColor(0.0, 1.0, (1 - 0.3*shade), 1.0))
					.translateX( 0.5 * size )
					.translateY( endPosition -0.5 * size )
					.transforms( new Shear( 0, -0.5 ) )
					.build();

			currentStackRightFace.heightProperty().bind( endZ.subtract( startZ ) );
			currentStackRightFace.translateYProperty().bind( startZ.subtract( 0.5 * size ) );

			Rectangle currentStackFrontFace = RectangleBuilder.create() // front face
					.width(size).height(currentStackSize)
					.fill(posStackColor)
					.translateX( -0.5 * size )
					.translateY( endPosition -0.5 * size )
					.build();

			currentStackFrontFace.heightProperty().bind( endZ.subtract( startZ ) );
			currentStackFrontFace.translateYProperty().bind( startZ.subtract( 0.5 * size ) );

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
					currentStackTopFace,
					currentStackRightFace,
					currentStackFrontFace,
					current
			);
		}
	}
}
