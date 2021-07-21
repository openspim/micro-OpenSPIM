package spim.ui.view.component;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import eu.hansolo.enzo.common.SymbolType;
import eu.hansolo.enzo.onoffswitch.IconSwitch;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.controlsfx.control.RangeSlider;

import org.micromanager.Studio;
import org.micromanager.internal.utils.MDUtils;
import spim.hardware.Camera;
import spim.hardware.SPIMSetup;
import spim.model.data.DeviceItem;
import spim.ui.view.component.widgets.pane.CheckboxPane;
import spim.ui.view.component.widgets.slider.StageSlider;
import spim.ui.view.component.widgets.slider.customslider.Slider;
import spim.ui.view.component.util.TableViewUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Description: Camera device panel shows the live image with aligning tools.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: December 2018
 */
public class CameraDevicePanel extends ScrollPane implements SPIMSetupInjectable
{
	private IconSwitch cameraOnSwitch;

	final private TreeMap<String, Band> bandMap;

	private ToggleGroup group;
	private SimpleIntegerProperty integerProperty;
	private Slider slider;
	private double scaleFactor = 1;
	private MultiAcquisition acquisition;
	private WritablePixelFormat< IntBuffer > format = WritablePixelFormat.getIntArgbInstance();
	private int[] buf;
	private int[] rgbBuffer;
	private int width, height;
	private double exposure = 33;
	private volatile boolean isShownFirst = true;
	private volatile boolean isBandTableDirty = false;
	ScheduledExecutorService executor;
	Studio studio;
	StackPane imageStackPane;
	VBox bandsVbox;
	Line l1, l2;
	HBox buttonBox;
	Group clipGroup, clipBox;
	ObservableList<DeviceItem> properties;
	FilteredList<DeviceItem> filteredProperties;
	private String firstCamera = "", secondCamera = "", thirdCamera = "";
	private boolean isMulticameraPresent = false;

	public CameraDevicePanel( SPIMSetup setup, Studio gui ) {
		studio = gui;
		bandMap = new TreeMap<>();

		width = gui == null? 512 : (int) gui.core().getImageWidth();
		height = gui == null? 512 : (int) gui.core().getImageHeight();

		buf = new int[width * height];

		cameraOnSwitch = new IconSwitch();

		cameraOnSwitch.setSymbolType( SymbolType.POWER );
		cameraOnSwitch.setSymbolColor( Color.web( "#ffffff" ) );
		cameraOnSwitch.setSwitchColor( Color.web( "#34495e" ) );
		cameraOnSwitch.setThumbColor( Color.web( "#ff495e" ) );
		cameraOnSwitch.setPadding( new Insets( 0,0,0,0 ) );

		rgbBuffer = new int[width * height];

		Image orgImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "dresden_evening.png" ),
				width, height, false, true );
		PixelReader pixelReader = orgImg.getPixelReader();
		pixelReader.getPixels( 0, 0, width, height, format, rgbBuffer, 0, width );

		if(gui != null) {
			System.out.println("Number of channels: " + gui.core().getNumberOfCameraChannels());

			List<String> cameras = new ArrayList<>();

			//			cameras.add( "Andor sCMOS Camera-1" );
			//			cameras.add( "Andor sCMOS Camera-2" );


			if(setup.getCamera1() != null)
			{
				firstCamera = setup.getCamera1().getLabel();
				cameras.add( firstCamera );

				int size = 1 << gui.core().getImageBitDepth();
				int bin = setup.getCamera1().getBinning();

				width /= bin;
				height /= bin;
				buf = new int[width * height];

				bandMap.put( firstCamera, new Band( new WritableImage( width, height ), new int[ size ], "" ) );
				bandMap.get(firstCamera).setCamera( setup.getCamera1() );
				updateBandTable( firstCamera, 0, size);
				resetRgbBuffer( "R", bandMap.get(firstCamera).image.getPixelWriter() );
				resetBuffer( bandMap.get(firstCamera).image.getPixelWriter() );

				if(firstCamera.equals( "DCam" ) && setup.getCamera2() != null) {
					bandMap.get( firstCamera ).setBandColor( "R" );
					bandMap.put( firstCamera + 1, new Band( new WritableImage( width, height ), new int[ size ], "G" ) );
					bandMap.get( firstCamera + 1).setCamera( setup.getCamera1() );
					updateBandTable( firstCamera + 1, 0, size);
					resetRgbBuffer( "G", bandMap.get(firstCamera + 1).image.getPixelWriter() );
					resetBuffer( bandMap.get(firstCamera + 1).image.getPixelWriter());

					bandMap.put( "hB", new Band( new WritableImage( width, height ), new int[ size ], "B" ) );
					updateBandTable("hB", 0, size);
					resetBuffer( bandMap.get("hB").image.getPixelWriter());
				}

				if(setup.getCamera2() != null)
				{
					secondCamera = setup.getCamera2().getLabel();
					bandMap.get( firstCamera ).setBandColor( "R" );
					cameras.add( secondCamera );

					bandMap.put( secondCamera, new Band( new WritableImage( width, height), new int[ size ], "G" ) );
					bandMap.get( secondCamera ).setCamera( setup.getCamera2() );
					updateBandTable(secondCamera, 0, size);
					resetRgbBuffer( "G", bandMap.get(secondCamera).image.getPixelWriter() );
					resetBuffer( bandMap.get(secondCamera).image.getPixelWriter());

					bandMap.put( "hB", new Band( new WritableImage( width, height ), new int[ size ], "B" ) );
					updateBandTable("hB", 0, size);
					resetBuffer( bandMap.get("hB").image.getPixelWriter());
				}
			}

			acquisition = new MultiAcquisition( gui.core(), cameras );

			cameraOnSwitch.selectedProperty().addListener( new ChangeListener< Boolean >()
			{
				@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
				{
					if(isShownFirst) isShownFirst = false;

					if(newValue)
					{
						try
						{
							if(!gui.core().isSequenceRunning())
								acquisition.start();
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
					else
					{
						try
						{
							if(gui.core().isSequenceRunning())
								acquisition.stop();
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
				}
			} );
		}
		else
		{
			isShownFirst = false;
			bandMap.put( "R", new Band( new WritableImage( width, height ), new int[ 256 ], "R" ) );
			bandMap.put( "G", new Band( new WritableImage( width, height ), new int[ 256 ], "G" ) );
			bandMap.put( "B", new Band( new WritableImage( width, height ), new int[ 256 ], "B" ) );

			updateBandTable("R", 0, 255);
			updateBandTable("G", 0, 255);
			updateBandTable("B", 0, 255);

			handleBuffer(rgbBuffer, "");
		}

		init(width, height);

		startMonitor();
	}

	private void resetRgbBuffer( String rgb, PixelWriter writer )
	{
		final int size = width * height;
		for (int pixel = 0, row = 0, col = 0; pixel < size; pixel++) {
			int argb = 0xff << 24;

			if(rgb.equals( "R" )) {
				argb |= rgbBuffer[row * width + col] & 0xff0000;
			}
			if(rgb.equals( "G" )) {
				argb |= rgbBuffer[row * width + col] & 0x00ff00;
			}
			if(rgb.equals( "B" )) {
				argb |= rgbBuffer[row * width + col] & 0x0000ff;
			}

			buf[row * width + col] = argb;
			col++;
			if (col == width) {
				col = 0;
				row++;
			}
		}
		writer.setPixels( 0, 0, width, height, format, buf, 0, width );
	}

	private void resetBuffer( PixelWriter writer )
	{
		final int size = width * height;
		for (int pixel = 0, row = 0, col = 0; pixel < size; pixel++) {
			int argb = 0xff << 24;

			buf[row * width + col] = argb;
			col++;
			if (col == width) {
				col = 0;
				row++;
			}
		}
		writer.setPixels( 0, 0, width, height, format, buf, 0, width );
	}

	public Object getPixelsCopy(Object original) {
		Object copy;
		int length;
		if (original instanceof byte[]) {
			byte[] tmp = (byte[])((byte[])original);
			length = tmp.length;
			copy = new byte[length];
		} else if (original instanceof short[]) {
			short[] tmp = (short[])((short[])original);
			length = tmp.length;
			copy = new short[length];
		} else {
			if (!(original instanceof int[])) {
				throw new RuntimeException("Unrecognized pixel type " + original.getClass());
			}

			int[] tmp = (int[])((int[])original);
			length = tmp.length;
			copy = new int[length];
		}

		System.arraycopy(original, 0, copy, 0, length);
		return copy;
	}

	private void handleBuffer( int[] buffer, String exclude )
	{
		for(String key : bandMap.keySet()) {
			WritableImage writableImage = bandMap.get(key).image;
			String bandColor = bandMap.get(key).getBandColor();

			int opacity = bandMap.get(key).getOpacity();
			int[] colors = bandMap.get(key).colors;
			for(int i = 0; i < buffer.length; i++) {
				int a = 0xff - opacity;

				if(bandColor.equals( "R" ) && !exclude.equals( "R" ) ) {
					int r = (int) (colors[buffer[i] >> 16 & 0xff]);
					buf[i] = (a << 24) | (r & 0xff) << 16;
				}
				if(bandColor.equals( "G" ) && !exclude.equals( "G" ) ) {
					int g = (int) (colors[buffer[i] >> 8 & 0xff]);
					buf[i] = (a << 24) | (g & 0xff) << 8;
				}
				if(bandColor.equals( "B" ) && !exclude.equals( "B" ) ) {
					int b = (int) (colors[buffer[i] & 0xff]);
					buf[i] = (a << 24) | b & 0xff;
				}
//				int r = (colors[buffer[i] >> 16 & 0xff]);
//				int g = (colors[buffer[i] >> 8 & 0xff]);
//				int b = (colors[buffer[i] & 0xff]);

//				buf[i] = (a << 24) | (r & 0xff) << 16 | (g & 0xff) << 8 | b & 0xff;
			}

			writableImage.getPixelWriter().setPixels( 0, 0, width, height, format, buf, 0, width );
		}
	}

	private void handleBuffer( byte[] pixels, PixelWriter writer, int[] colors, int bytesPerPixel, String bandColor, int opacity )
	{
		final int size = buf.length * bytesPerPixel;

		for (int pixel = 0, row = 0, col = 0; pixel < size; pixel += bytesPerPixel) {
			int argb = 0;
			if(bytesPerPixel == 4) {
				argb += (pixels[pixel] & 0xff) << 24; 		// alpha
				argb += colors[pixels[pixel + 1] & 0xff]; 			// blue
				argb += colors[pixels[pixel + 2] & 0xff] << 8;		// green
				argb += colors[pixels[pixel + 3] & 0xff] << 16;	// red
			}
			else
			{
				argb += ( 0xff - opacity ) << 24; // 255 alpha
//				argb += colors[pixels[pixel]]; 		// blue
//				argb += colors[pixels[pixel]] << 8; // green
//				argb += colors[pixels[pixel]] << 16; // red
				if(bandColor.isEmpty()) {
					argb += colors[Byte.toUnsignedInt(pixels[pixel]) & 0xff]; 		// blue
					argb += colors[Byte.toUnsignedInt(pixels[pixel]) & 0xff] << 8; // green
					argb += colors[Byte.toUnsignedInt(pixels[pixel]) & 0xff] << 16; // red
				}
				if(bandColor.equals( "R" )) {
					int r = colors[Byte.toUnsignedInt(pixels[pixel])];
					argb |= (r & 0xff) << 16;
				}
				if(bandColor.equals( "G" )) {
					int g = colors[Byte.toUnsignedInt(pixels[pixel])];
					argb |= (g & 0xff) << 8;
				}
				if(bandColor.equals( "B" )) {
					int b = colors[Byte.toUnsignedInt(pixels[pixel])];
					argb |= b & 0xff;
				}
			}
			buf[row * width + col] = argb;
			col++;
			if (col == width) {
				col = 0;
				row++;
			}
		}

		writer.setPixels( 0, 0, width, height, format, buf, 0, width );
	}

	private void handleBuffer( short[] pixels, PixelWriter writer, int[] colors, int bytesPerPixel, String bandColor, int opacity )
	{
		final int size = buf.length * bytesPerPixel / 2;

		for (int pixel = 0, row = 0, col = 0; pixel < size; pixel += bytesPerPixel / 2) {
			int argb = 0;
			if(bytesPerPixel == 8) {
				argb += (pixels[pixel] & 0xffff) << 24; // alpha
				argb += (colors[pixels[pixel + 1] & 0xffff]); // blue
				argb += ((colors[pixels[pixel + 2] & 0xffff]) << 8); // green
				argb += ((colors[pixels[pixel + 3] & 0xffff]) << 16); // red
			}
			else
			{
				argb += ( 0xff - opacity ) << 24; // 255 alpha
//				argb += colors[pixels[pixel]]; 		// blue
//				argb += colors[pixels[pixel]] << 8; // green
//				argb += colors[pixels[pixel]] << 16; // red
				if(bandColor.isEmpty()) {
					argb += colors[pixels[pixel] & 0xffff]; 		// blue
					argb += colors[pixels[pixel] & 0xffff] << 8; // green
					argb += colors[pixels[pixel] & 0xffff] << 16; // red
				}
				if(bandColor.equals( "R" )) {
					int r = colors[pixels[pixel] & 0xffff];
					argb |= (r & 0xff) << 16;
				}
				if(bandColor.equals( "G" )) {
					int g = colors[pixels[pixel] & 0xffff];
					argb |= (g & 0xff) << 8;
				}
				if(bandColor.equals( "B" )) {
					int b = colors[pixels[pixel] & 0xffff];
					argb |= b & 0xff;
				}
			}
			buf[row * width + col] = argb;
			col++;
			if (col == width) {
				col = 0;
				row++;
			}
		}

		writer.setPixels( 0, 0, width, height, format, buf, 0, width );
	}

	private void updateBandTable( String key, int low, int high ) {
//		System.out.println(low + ":" + high);

		int[] colors = bandMap.get(key).getColors();

		double multiplier = 255.0 / (high - low);

		for(int i = 0; i < colors.length; i++) {
			if(i <= low)
				colors[ i ] = 0;
			else if(i >= high)
				colors[ i ] = -1;
			else
				colors[ i ] = (int) (((double) (i - low) * multiplier));
		}
	}

	private void init(int width, int height)
	{
		HBox box = new HBox();

		bandsVbox = new VBox();

		addBandControls();

		Spinner<Integer> spinner = new Spinner<>( 1, 50, 1, 1 );

		final ColorPicker colorPicker = new ColorPicker();
		colorPicker.setOnAction(new EventHandler() {
			public void handle( Event t) {
				l1.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
				l2.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
			}
		});

		spinner.valueProperty().addListener( new ChangeListener< Integer >()
		{
			@Override public void changed( ObservableValue< ? extends Integer > observable, Integer oldValue, Integer newValue )
			{
				l1.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
				l2.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
			}
		} );


		CheckboxPane visibleGuideLine = new CheckboxPane( "Grid line" , new VBox( spinner, colorPicker ), null);
		visibleGuideLine.setSelected( true );
		visibleGuideLine.selectedProperty().addListener( new ChangeListener< Boolean >()
		{
			@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
			{
				if(newValue) {
					l1.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
					l2.setStyle( String.format( "-fx-stroke: #%s; -fx-stroke-width: %dpx;", Integer.toHexString(colorPicker.getValue().hashCode()), spinner.getValue() ) );
				}
				else
				{
					l1.setStyle( "-fx-stroke: transparent;" );
					l2.setStyle( "-fx-stroke: transparent;" );
				}
			}
		} );

		// Clipping rectangle
		imageStackPane = new StackPane();
		imageStackPane.getChildren().addAll( bandMap.values().stream().map( c -> c.imageView).collect( Collectors.toList() ) );
		imageStackPane.setPrefSize( width, height );

		VBox toolBox = new VBox( 20 );

		group = new ToggleGroup();
		buttonBox = new HBox( 10 );

		buildButtonBox();

		GridPane gridpane = getControlBox();

		slider.setValue( 0 );
		group.selectedToggleProperty().addListener( new ChangeListener< Toggle >()
		{
			@Override public void changed( ObservableValue< ? extends Toggle > observable, Toggle oldValue, Toggle newValue )
			{
				RadioButton selectedRadioButton = (RadioButton) newValue;

				slider.setValue( bandMap.get( selectedRadioButton.getText() ).opacityProperty.get() );
			}
		} );

		toolBox.getChildren().addAll( cameraOnSwitch, bandsVbox, visibleGuideLine, buttonBox, gridpane );
		toolBox.setAlignment( Pos.CENTER );

		box.setSpacing( 10 );

		clipGroup = new Group();
		buildClipBox();

		// Camera information
		properties = FXCollections.observableArrayList();
		filteredProperties = new FilteredList<>( properties, p -> true );
		TableView< DeviceItem > tv = TableViewUtil.createDeviceItemTableView();
		tv.setItems( filteredProperties );

		Button refreshBtn = new Button( "Refresh" );
		refreshBtn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(studio != null)
					populateProperties(studio.core());
			}
		} );

		TextField filterField = new TextField();
		filterField.textProperty().addListener((observable, oldValue, newValue) -> {
			filteredProperties.setPredicate(deviceItem -> {
				// If filter text is empty, display all persons.
				if (newValue == null || newValue.isEmpty()) {
					return true;
				}

				// Compare first name and last name of every person with filter text.
				String lowerCaseFilter = newValue.toLowerCase();

				if (deviceItem.getName().toLowerCase().contains(lowerCaseFilter)) {
					return true; // Filter matches name.
				} else if (deviceItem.getValue().toLowerCase().contains(lowerCaseFilter)) {
					return true; // Filter matches value.
				}
				return false; // Does not match.
			});
		});

		HBox filterBox = new HBox(10, refreshBtn, new Label("Filter:"), filterField);
		filterBox.setAlignment( Pos.CENTER_LEFT );

		TitledPane cameraInfo = new TitledPane("Camera Properties", new VBox( 10, filterBox, tv ) );
		cameraInfo.setAnimated( false );
		cameraInfo.setExpanded( false );
		box.getChildren().addAll( toolBox, new VBox(10, clipGroup, cameraInfo ) );
		setContent( box );

		sceneProperty().addListener( new ChangeListener< Scene >()
		{
			@Override public void changed( ObservableValue< ? extends Scene > observable, Scene oldValue, Scene newValue )
			{
				newValue.setOnKeyPressed( new EventHandler< KeyEvent >()
				{
					@Override public void handle( KeyEvent event )
					{
						if (event.isControlDown()) {
							switch ( event.getCode() ) {
								case EQUALS:
									scaleFactor += 0.1;
									clipBox.setScaleX( scaleFactor );
									clipBox.setScaleY( scaleFactor );

									break;
								case MINUS:
									scaleFactor -= 0.1;
									clipBox.setScaleX( scaleFactor );
									clipBox.setScaleY( scaleFactor );

									break;
							}
						}
					}
				} );

				observable.removeListener( this );
			}
		} );

		HBox.setHgrow( box, Priority.ALWAYS );

		//		getChildren().addAll( sp );
		setStyle( "-fx-border-style: solid;" + "-fx-border-width: 1;"
				+ "-fx-border-color: black" );
	}

	private ImageView getSelectedImageView()
	{
		RadioButton selectedRadioButton = (RadioButton) group.getSelectedToggle();

		return bandMap.get( selectedRadioButton.getText() ).imageView;
	}

	private GridPane getControlBox()
	{
		GridPane gridpane = new GridPane();

		Button doubleUp = new Button();
		FontAwesomeIconView icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOUBLE_UP, "20px" );
		doubleUp.setTooltip( new Tooltip( "Move up by 10" ) );
		doubleUp.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		doubleUp.setGraphic(icon);
		doubleUp.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(0, -10) );
		} );
		GridPane.setConstraints(doubleUp, 2, 0);

		Button up = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_UP );
		up.setTooltip( new Tooltip( "Move up by 1" ) );
		up.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		up.setGraphic(icon);
		up.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(0, -1) );
		} );
		GridPane.setConstraints(up, 2, 1);
		GridPane.setHalignment(up, HPos.CENTER);

		Button doubleDown = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOUBLE_DOWN, "20px" );
		doubleDown.setTooltip( new Tooltip( "Move down by 10" ) );
		doubleDown.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		doubleDown.setGraphic(icon);
		doubleDown.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(0, 10) );
		} );
		GridPane.setConstraints(doubleDown, 2, 4);

		Button down = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOWN );
		down.setTooltip( new Tooltip( "Move down by 1" ) );
		down.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		down.setGraphic(icon);
		down.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(0, 1) );
		} );
		GridPane.setConstraints(down, 2, 3);
		GridPane.setHalignment(down, HPos.CENTER);

		Button doubleLeft = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOUBLE_LEFT, "20px" );
		doubleLeft.setTooltip( new Tooltip( "Move left by 10" ) );
		doubleLeft.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		doubleLeft.setGraphic(icon);
		doubleLeft.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(-10, 0) );
		} );
		GridPane.setConstraints(doubleLeft, 0, 2);

		Button left = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_LEFT );
		left.setTooltip( new Tooltip( "Move left by 1" ) );
		left.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		left.setGraphic(icon);
		left.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(-1, 0) );
		} );
		GridPane.setConstraints(left, 1, 2);

		Button doubleRight = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOUBLE_RIGHT, "20px" );
		doubleRight.setTooltip( new Tooltip( "Move right by 10" ) );
		doubleRight.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		doubleRight.setGraphic(icon);
		doubleRight.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(10, 0) );
		} );
		GridPane.setConstraints(doubleRight, 4, 2);

		Button right = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_RIGHT );
		right.setTooltip( new Tooltip( "Move right by 10" ) );
		right.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		right.setGraphic(icon);
		right.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Translate(1, 0) );
		} );
		GridPane.setConstraints(right, 3, 2);

		Button rotateLeft = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ROTATE_LEFT );
		rotateLeft.setTooltip( new Tooltip( "Rotate left by 90 degree" ) );
		rotateLeft.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		rotateLeft.setGraphic(icon);
		rotateLeft.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Rotate(-90, width / 2, height / 2) );
		} );
		GridPane.setConstraints(rotateLeft, 0, 0);

		Button rotateRight = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.ROTATE_RIGHT );
		rotateRight.setTooltip( new Tooltip( "Rotate Right by 90 degree" ) );
		rotateRight.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		rotateRight.setGraphic(icon);
		rotateRight.setOnAction( event -> {
			getSelectedImageView().getTransforms().add( new Rotate(90, width / 2, height / 2) );
		} );
		GridPane.setConstraints(rotateRight, 4, 0);

		Button mirrorY = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.TOGGLE_DOWN );
		mirrorY.setTooltip( new Tooltip( "Mirror by X axis" ) );
		mirrorY.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		mirrorY.setGraphic(icon);
		mirrorY.setOnAction( event -> {
			final Affine reflectTransform = new Affine();
			reflectTransform.setMxx(-1);
			reflectTransform.setMyy(-1);
			reflectTransform.setTx(getSelectedImageView().getBoundsInLocal().getWidth());
			reflectTransform.setTy(getSelectedImageView().getBoundsInLocal().getHeight());
			getSelectedImageView().getTransforms().add( reflectTransform );
		} );
		GridPane.setConstraints(mirrorY, 0, 4);

		Button mirrorX = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.TOGGLE_RIGHT );
		mirrorX.setTooltip( new Tooltip( "Mirror by Y axis" ) );
		mirrorX.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		mirrorX.setGraphic(icon);
		mirrorX.setOnAction( event -> {
			final Affine reflectTransform = new Affine();
			reflectTransform.setMxx(-1);
			reflectTransform.setTx(getSelectedImageView().getBoundsInLocal().getWidth());
			getSelectedImageView().getTransforms().add( reflectTransform );
		} );
		GridPane.setConstraints(mirrorX, 4, 4);

		Button reset = new Button();
		icon = new FontAwesomeIconView( FontAwesomeIcon.REGISTERED );
		reset.setTooltip( new Tooltip( "Reset" ) );
		reset.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
		reset.setGraphic(icon);
		reset.setOnAction( event -> {
			getSelectedImageView().getTransforms().clear();
		} );
		GridPane.setConstraints(reset, 2, 2);

		// Opacity
		Label opacityLabel = new Label( "Opacity" );
		opacityLabel.setPadding(new Insets(10, 0, 0, 0));
		GridPane.setConstraints(opacityLabel, 0, 5, 2, 1);

		integerProperty = new SimpleIntegerProperty(  );
		integerProperty.addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				RadioButton selectedRadioButton = (RadioButton) group.getSelectedToggle();

				bandMap.get( selectedRadioButton.getText() ).opacityProperty.setValue( newValue );
			}
		} );

		StageSlider targetSlider = new StageSlider( "", integerProperty, false, false, 0, 255, 5 );

		TextField textField = targetSlider.getTextField();
		GridPane.setConstraints(textField, 2, 5, 3, 1);
		GridPane.setMargin(textField, new Insets(10, 0, 0, 0));

		slider = targetSlider.getSlider();
		slider.setPadding( new Insets( 10, 0, 0, 0 ) );
		slider.setPrefSize( 150, 0 );
		GridPane.setConstraints(slider, 0, 6, 6, 1);

		gridpane.getChildren().addAll( doubleUp, up, doubleDown, down, doubleLeft, left, doubleRight, right, rotateLeft, rotateRight, mirrorX, mirrorY, reset, opacityLabel, textField, slider);
		return gridpane;
	}

	void buildButtonBox() {
		boolean isFirst = true;
		buttonBox.getChildren().clear();

		for(String key : bandMap.keySet()) {
			if(key.equals( "hB" )) continue;
			RadioButton button = new RadioButton(key);
			button.setToggleGroup(group);
			if(isFirst)
			{
				button.setSelected( true );
				isFirst = false;
			}
			buttonBox.getChildren().add(button);
		}
	}

	void buildClipBox() {
		final Rectangle outputClip = new Rectangle(width, height);
		clipBox = new Group();

		clipBox.getChildren().add(imageStackPane);

		l1 = new Line( 0, height / 2, width, height / 2 );
		l1.setStyle( "-fx-stroke: white" );
		l1.setBlendMode( BlendMode.SRC_ATOP );

		l2 = new Line( width / 2, 0, width / 2, height );
		l2.setStyle( "-fx-stroke: white" );
		l2.setBlendMode( BlendMode.SRC_ATOP );

		clipBox.getChildren().add(l1);
		clipBox.getChildren().add(l2);
		clipBox.setClip( outputClip );
		//		g.getChildren().add(l3);
		//		g.getChildren().add(l4);

		//		double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();
		scaleFactor = height > 512 ? (double) 512 / height : 1;
		clipBox.setScaleX( scaleFactor );
		clipBox.setScaleY( scaleFactor );

		clipGroup.getChildren().clear();
		clipGroup.getChildren().add( clipBox );
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio )
	{
		this.studio = studio;

		width = setup == null? 512 : (int) studio.core().getImageWidth();
		height = setup == null? 512 : (int) studio.core().getImageHeight();

		rgbBuffer = new int[width * height];
		buf = new int[width * height];

		imageStackPane.getChildren().clear();
		bandMap.clear();

		bandsVbox.getChildren().clear();

		if(setup != null) {
			System.out.println("Number of channels: " + studio.core().getNumberOfCameraChannels());

			Optional<String> multi = Arrays.stream( studio.core().getLoadedDevicesOfType( DeviceType.CameraDevice ).toArray() ).filter( c -> c.startsWith( "Multi" ) ).findFirst();

			if(multi.isPresent()) {
				isMulticameraPresent = true;
			}

			List<String> cameras = new ArrayList<>();

			populateProperties(studio.core());

			if(setup.getCamera1() != null)
			{
				firstCamera = setup.getCamera1().getLabel();
				cameras.add( firstCamera );

				int size = 1 << studio.core().getImageBitDepth();
				int bin = setup.getCamera1().getBinning();

				width /= bin;
				height /= bin;
				buf = new int[width * height];

				bandMap.put( firstCamera, new Band( new WritableImage( width, height ), new int[ size ], "" ) );
				bandMap.get(firstCamera).setCamera( setup.getCamera1() );
				updateBandTable( firstCamera, 0, size);
				resetRgbBuffer( "R", bandMap.get(firstCamera).image.getPixelWriter() );
				resetBuffer( bandMap.get(firstCamera).image.getPixelWriter() );

				if(firstCamera.equals( "DCam" ) && setup.getCamera2() != null) {
					bandMap.get( firstCamera ).setBandColor( "R" );
					bandMap.put( firstCamera + 1, new Band( new WritableImage( width, height ), new int[ size ], "G" ) );
					bandMap.get( firstCamera + 1).setCamera( setup.getCamera1() );
					updateBandTable( firstCamera + 1, 0, size);
					resetRgbBuffer( "G", bandMap.get(firstCamera + 1).image.getPixelWriter() );
					resetBuffer( bandMap.get(firstCamera + 1).image.getPixelWriter());

					bandMap.put( "hB", new Band( new WritableImage( width, height ), new int[ size ], "B" ) );
					updateBandTable("hB", 0, size);
					resetBuffer( bandMap.get("hB").image.getPixelWriter());
				}

				if(setup.getCamera2() != null)
				{
					secondCamera = setup.getCamera2().getLabel();
					bandMap.get( firstCamera ).setBandColor( "R" );
					cameras.add( secondCamera );
					System.out.println( setup.getCamera1().getDeviceName() );
					System.out.println( setup.getCamera2().getLabel() );
					bandMap.put( secondCamera, new Band( new WritableImage( width, height), new int[ size ], "G" ) );
					bandMap.get( secondCamera ).setCamera( setup.getCamera2() );
					updateBandTable(secondCamera, 0, size);
					resetRgbBuffer( "G", bandMap.get(secondCamera).image.getPixelWriter() );
					resetBuffer( bandMap.get(secondCamera).image.getPixelWriter());

					bandMap.put( "hB", new Band( new WritableImage( width, height ), new int[ size ], "B" ) );
					updateBandTable("hB", 0, size);
					resetBuffer( bandMap.get("hB").image.getPixelWriter());
				}
			}

			acquisition = new MultiAcquisition( studio.core(), cameras );

			cameraOnSwitch.selectedProperty().addListener( new ChangeListener< Boolean >()
			{
				@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
				{
					if(isShownFirst) isShownFirst = false;

					if(newValue)
					{
						try
						{
							startMonitor();

							if(studio.core() != null)
								acquisition.start();
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
					else
					{
						try
						{
							stopMonitor();

							if(studio.core() != null)
								acquisition.stop();
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
				}
			} );
		}
		else
		{
			properties.clear();
			acquisition.setSetup( null, null );

			stopMonitor();
			try
			{
				acquisition.stop();
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}

			isShownFirst = false;
			bandMap.put( "R", new Band( new WritableImage( width, height ), new int[ 256 ], "R" ) );
			bandMap.put( "G", new Band( new WritableImage( width, height ), new int[ 256 ], "G" ) );
			bandMap.put( "B", new Band( new WritableImage( width, height ), new int[ 256 ], "B" ) );

			updateBandTable("R", 0, 255);
			updateBandTable("G", 0, 255);
			updateBandTable("B", 0, 255);

			handleBuffer(rgbBuffer, "");

			isMulticameraPresent = false;
		}

		imageStackPane.getChildren().addAll( bandMap.values().stream().map( c -> c.imageView).collect( Collectors.toList() ) );
		imageStackPane.setPrefSize( width, height );

		addBandControls();

		buildClipBox();

		buildButtonBox();
	}

	private void populateProperties( CMMCore core )
	{
		if(properties != null) {
			properties.clear();
			StrVector vector = core.getLoadedDevicesOfType( DeviceType.CameraDevice );
			for(String cam : vector.toArray()) {
				try
				{
					StrVector props = core.getDevicePropertyNames( cam );
					for(String prop : props.toArray()) {
						String name = cam + '-' + prop;
						properties.add( new DeviceItem( name, core.getProperty( cam, prop ) ) );
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		}
	}

	@SuppressWarnings("Duplicates")
	void stopMonitor() {
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

	void startMonitor() {
		stopMonitor();

		executor = Executors.newScheduledThreadPool( 5 );
		executor.scheduleAtFixedRate( () -> {

			if( this.studio == null ) return;

			if( cameraOnSwitch.isSelected() )
			{
				if(null != studio) {
					HashMap<String, TaggedImage> map = acquisition.getImages(exposure);
					//					HashMap<String, ImageProcessor > map = acquisition.getDefaultImages();
					for( String key : map.keySet() ) {
						TaggedImage img = map.get(key);
						JSONObject tags = img.tags;

						try
						{
							int bytesPerPixel = MDUtils.getBytesPerPixel(tags);
							//							int bytesPerPixel = img.getBitDepth() / 8;

							//							System.out.println(bytesPerPixel + ":" + MDUtils.getNumberOfComponents( tags ));
							//							System.out.println(img.pix.getClass().getCanonicalName());
							//							System.out.println(new Date());

							if( bytesPerPixel == 2 || bytesPerPixel == 8 ) {
								// case of 16bit and 64bit RGB
								short[] buffer = (short[]) img.pix;
								handleBuffer( buffer, bandMap.get(key).image.getPixelWriter(), bandMap.get(key).colors, bytesPerPixel, bandMap.get(key).getBandColor(), bandMap.get(key).getOpacity() );
							}
							else {
								// case of 8bit and 32bit RGB
								byte[] buffer = (byte[]) img.pix;
								handleBuffer( buffer, bandMap.get(key).image.getPixelWriter(), bandMap.get(key).colors, bytesPerPixel, bandMap.get(key).getBandColor(), bandMap.get(key).getOpacity() );
							}
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
				}
				else {
					if(isBandTableDirty) {
						handleBuffer(rgbBuffer, "");
						isBandTableDirty = false;
					}
				}
			}

		}, 500, 100, TimeUnit.MILLISECONDS );
	}

	private void addBandControls() {
		for(String key : bandMap.keySet()) {
			ImageView iv = bandMap.get(key).imageView;
			iv.setSmooth( false );
			iv.setPreserveRatio( false );
			if(key.equals( firstCamera )) {
				iv.setBlendMode( BlendMode.RED );
			} else if(key.equals( secondCamera )) {
				iv.setBlendMode( BlendMode.GREEN );
			} else if(key.equals( thirdCamera )) {
				iv.setBlendMode( BlendMode.BLUE );
			}
			else
			{
				switch ( key )
				{
					case "R":
						iv.setBlendMode( BlendMode.RED );
						break;
					case "G":
						iv.setBlendMode( BlendMode.GREEN );
						break;
					case "hB":
					case "B":
						iv.setBlendMode( BlendMode.BLUE );
						break;
					default:
						iv.setBlendMode( BlendMode.MULTIPLY );
						break;
				}
			}

			if(key.equals( "hB" )) continue;

			Button acq = new Button( "Start Acq." );
			acq.setStyle("-fx-base: #9ed3ff;");
			acq.setOnAction( new EventHandler< ActionEvent >()
			{
				@Override public void handle( ActionEvent event )
				{
					if(null != studio)
					{
						if(!cameraOnSwitch.isSelected()) {
							new Alert( Alert.AlertType.ERROR, "Please, turn on the switch first.").show();
							return;
						}
						try
						{
							if(!acquisition.isAcqRunning( key )) {
								acquisition.startAcq( key );
								acq.setText( "Stop Acq." );
								acq.setStyle("-fx-base: #ffabb7;");
							} else {
								acquisition.stopAcq( key );
								acq.setText( "Start Acq." );
								acq.setStyle("-fx-base: #9ed3ff;");
							}
						} catch ( Exception e ) {
							e.printStackTrace();
						}
					}
				}
			} );

			Button snap = new Button( "Snap" );
			snap.setOnAction( new EventHandler< ActionEvent >()
			{
				@Override public void handle( ActionEvent event )
				{
					if(null != studio)
					{
						if(!cameraOnSwitch.isSelected()) {
							new Alert( Alert.AlertType.ERROR, "Please, turn on the switch first.").show();
							return;
						}
						try
						{
							String currentCamera = studio.core().getCameraDevice();
							if(currentCamera.startsWith( "Multi" )) {
								studio.core().snapImage();
								for(int i = 0; i < studio.core().getNumberOfCameraChannels(); i++) {
									TaggedImage ti = studio.core().getTaggedImage( i );
									String camera = ( String ) ti.tags.get( "Camera" );
									if(camera.equals( key )) {
										acquisition.setStaticImage( key, ti );
									}
								}
							}
							else
							{
								if ( !key.equals( currentCamera ) )
								{
									studio.core().setCameraDevice( key );
									studio.core().waitForDevice( key );
								}
								studio.core().snapImage();
								TaggedImage ti = studio.core().getTaggedImage( 0 );
								acquisition.setStaticImage( key, ti );

								if ( !key.equals( currentCamera ) )
								{
									studio.core().setCameraDevice( currentCamera );
									studio.core().waitForDevice( currentCamera );
								}
							}
						}
						catch ( Exception e )
						{
							e.printStackTrace();
						}
					}
				}
			} );

			HBox buttons = new HBox( 10, acq, snap );

			final RangeSlider slider = new RangeSlider(0, bandMap.get(key).colors.length - 1, 0, bandMap.get(key).colors.length - 1);
			slider.setShowTickLabels(true);
			slider.setMajorTickUnit( bandMap.get(key).colors.length / 5.0 );
			slider.setShowTickMarks(true);
			slider.lowValueProperty().addListener( new ChangeListener< Number >()
			{
				@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
				{
					updateBandTable( key, newValue.intValue(), (int) slider.getHighValue() );

					if(isShownFirst)
						handleBuffer(rgbBuffer, "B");
				}
			} );

			slider.highValueProperty().addListener( new ChangeListener< Number >()
			{
				@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
				{
					updateBandTable( key, (int) slider.getLowValue(), newValue.intValue() );

					if(isShownFirst)
						handleBuffer(rgbBuffer, "B");
				}
			} );

			CheckboxPane checkboxPane;
			if(null != bandMap.get(key).camera) {
				TextField exp = new TextField( bandMap.get(key).camera.getExposure() + "" );
				exp.prefHeight( 50 );
				exp.textProperty().addListener( new ChangeListener< String >()
				{
					@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
					{
						try
						{
							double val = Double.parseDouble( newValue );
							bandMap.get(key).camera.setExposure( val );
							exposure = Math.max( exposure, val );
						} catch ( NumberFormatException e ) {
							System.err.println(e);
						}
					}
				} );
				exp.disableProperty().bind( cameraOnSwitch.selectedProperty() );
				HBox expBox = new HBox( 5, new Label( "Exposure:" ), exp );
				expBox.setAlignment( Pos.BASELINE_LEFT );

				checkboxPane = new CheckboxPane( key, new VBox( 8, buttons, slider, expBox ), null );
			} else {
				checkboxPane = new CheckboxPane( key, slider, null );
			}
			bandMap.get(key).enabledProperty.bind( checkboxPane.selectedProperty() );
			bandMap.get(key).enabledProperty.addListener( new ChangeListener< Boolean >()
			{
				@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
				{
//					resetRgbBuffer( key, bandMap.get(key).image.getPixelWriter() );

					if(newValue) {
						updateBandTable( key, (int) slider.getLowValue(), (int) slider.getHighValue() );
					} else {
						updateBandTable( key, (int) slider.getHighValue(), (int) slider.getHighValue() );
					}

					if(isShownFirst)
						handleBuffer(rgbBuffer, "B");
				}
			} );
			bandsVbox.getChildren().add(checkboxPane);
		}
	}

	class Band {
		final private ImageView imageView;
		final private WritableImage image;
		final private int[] colors;
		final private IntegerProperty opacityProperty;
		final private BooleanProperty enabledProperty;
		private Camera camera;
		private String bandColor;

		public Band( WritableImage image, int[] colors, String bandColor )
		{
			this.imageView = new ImageView( image );
			this.image = image;
			this.colors = colors;
			this.opacityProperty = new SimpleIntegerProperty();
			this.enabledProperty = new SimpleBooleanProperty(true);
			this.bandColor = bandColor;
		}

		public ImageView getImageView()
		{
			return imageView;
		}

		public WritableImage getImage()
		{
			return image;
		}

		public int[] getColors()
		{
			return colors;
		}

		public int getOpacity()
		{
			return opacityProperty.get();
		}

		public IntegerProperty opacityProperty()
		{
			return opacityProperty;
		}

		public boolean isEnabled()
		{
			return enabledProperty.get();
		}

		public BooleanProperty enabledProperty()
		{
			return enabledProperty;
		}

		public String getBandColor()
		{
			return bandColor;
		}

		public void setBandColor( String bandColor )
		{
			this.bandColor = bandColor;
		}

		public Camera getCamera()
		{
			return camera;
		}

		public void setCamera( Camera camera )
		{
			this.camera = camera;
		}
	}
}
