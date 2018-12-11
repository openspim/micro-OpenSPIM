package spim.ui.view.component;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import eu.hansolo.enzo.common.SymbolType;
import eu.hansolo.enzo.onoffswitch.IconSwitch;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

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
import org.micromanager.Studio;
import org.micromanager.data.Image;
import spim.ui.view.component.slider.StageSlider;
import spim.ui.view.component.slider.customslider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: December 2018
 */
public class CameraDevicePanel extends ScrollPane
{
	private IconSwitch cameraOnSwitch;

	private ImageView displayR, displayG, displayB;
	private volatile int alphaR = 0, alphaG = 0, alphaB = 0;
	private ToggleGroup group;
	private SimpleIntegerProperty integerProperty;
	private Slider slider;
	private double scaleFactor = 1;
	private MultiAcquisition acquisition;

//	private InteractiveDisplayPaneComponent< AffineTransform3D > display;

	public CameraDevicePanel( Studio gui ) {
		int width = gui == null? 512 : (int) gui.core().getImageWidth();
		int height = gui == null? 512 : (int) gui.core().getImageHeight();
		init(width, height);

		// data -> GUI
		// CurrentPower update (data -> GUI)

//		if(null == gui)
//		{
//			final Affine reflectTransform = new Affine();
//
//			reflectTransform.setMxx(-1);
//			reflectTransform.setTx(displayG.getBoundsInLocal().getWidth());
//			reflectTransform.appendTranslation( 200, 100 );
//
//			displayG.getTransforms().add(reflectTransform);
//		}

		if(gui != null) {
			List<String> cameras = new ArrayList<>();
			cameras.add( "Andor sCMOS Camera-1" );
			cameras.add( "Andor sCMOS Camera-2" );

			acquisition = new MultiAcquisition( gui.core(), cameras );

			cameraOnSwitch.selectedProperty().addListener( new ChangeListener< Boolean >()
			{
				@Override public void changed( ObservableValue< ? extends Boolean > observable, Boolean oldValue, Boolean newValue )
				{
					if(newValue)
					{
						try
						{
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

		Random ran = new Random( 42 );

		ScheduledExecutorService executor = Executors.newScheduledThreadPool( 5 );
		executor.scheduleAtFixedRate( () -> {

			if( cameraOnSwitch.isSelected() )
			{
//				System.out.println( String.format( "%f, %f, %f", ran.nextDouble(), ran.nextDouble(), ran.nextDouble() ) );

				if(null == gui) {
					Color c = new Color( ran.nextDouble(), ran.nextDouble(), ran.nextDouble(), 1 - alphaR * 0.01 );

					WritableImage newImage = new WritableImage( width, height );
					PixelWriter writer = newImage.getPixelWriter();

					for ( int x = 0; x < width; x++ )
					{
						for ( int y = 0; y < height; y++ )
						{
							writer.setColor( x, y, c );
						}
					}

					displayR.setImage( newImage );

					javafx.scene.image.Image img = new javafx.scene.image.Image(getClass().getResourceAsStream("dresden_evening.png"), 512, 512, false, true);
					displayG.setImage( img );

				} else {
					List< Image > images = acquisition.getImages();

					WritableImage newImageR = new WritableImage( width, height );
					WritableImage newImageG = new WritableImage( width, height );
					WritableImage newImageB = new WritableImage( width, height );

					double[] maxs = new double[images.size()];

					for ( int x = 0; x < width; x++ )
					{
						for ( int y = 0; y < height; y++ )
						{
							for (int i = 0; i < images.size(); i++ ) {
								double max = maxs[i];
								maxs[i] = max < images.get(i).getIntensityAt( x, y ) ? images.get(i).getIntensityAt( x, y ) : max;
							}
						}
					}

					double r, g, b;

					for ( int x = 0; x < width; x++ )
					{
						for ( int y = 0; y < height; y++ )
						{
							r = g = b = 0;

							for (int i = 0; i < images.size(); i++ )
							{
								switch ( i % 3 )
								{
									case 0:
										r = (images.get( i ).getIntensityAt( x, y ) / maxs[ i ] + r) / (r == 0 ? 1.0 : 2.0);
										break;
									case 1:
										g = (images.get( i ).getIntensityAt( x, y ) / maxs[ i ] + g) / (g == 0 ? 1.0 : 2.0);
										break;
									case 2:
										b = (images.get( i ).getIntensityAt( x, y ) / maxs[ i ] + b) / (b == 0 ? 1.0 : 2.0);
										break;
								}
							}

							newImageR.getPixelWriter().setColor( x, y, new Color( r, 0, 0, 1 - alphaR * 0.01 ) );
							newImageG.getPixelWriter().setColor( x, y, new Color( 0, g, 0, 1 - alphaG * 0.01) );
							newImageB.getPixelWriter().setColor( x, y, new Color( 0, 0, b, 1 - alphaB * 0.01) );
						}
					}

					displayR.setImage( newImageR );
					displayG.setImage( newImageG );
					displayB.setImage( newImageB );
				}
			}

		}, 500, 200, TimeUnit.MILLISECONDS );

	}

	private void init(int width, int height)
	{
		cameraOnSwitch = new IconSwitch();

		cameraOnSwitch.setSymbolType( SymbolType.POWER );
		cameraOnSwitch.setSymbolColor( Color.web( "#ffffff" ) );
		cameraOnSwitch.setSwitchColor( Color.web( "#34495e" ) );
		cameraOnSwitch.setThumbColor( Color.web( "#ff495e" ) );
		cameraOnSwitch.setPadding( new Insets( 0,0,0,0 ) );
		HBox box = new HBox();

		WritableImage writableImage = new WritableImage( width, height );
		Color c = new Color( 0, 0, 0, 1 );
		PixelWriter imageWriter = writableImage.getPixelWriter();
		for ( int x = 0; x < width; x++ )
		{
			for ( int y = 0; y < height; y++ )
			{
				imageWriter.setColor( x, y, c );
			}
		}

		displayR = new ImageView( writableImage );
		displayR.setSmooth( false );
		displayR.setPreserveRatio( false );
		displayR.setBlendMode( BlendMode.RED );

		displayG = new ImageView( writableImage );
		displayG.setSmooth( false );
		displayG.setPreserveRatio( false );
		displayG.setBlendMode( BlendMode.GREEN );

		displayB = new ImageView( writableImage );
		displayB.setSmooth( false );
		displayB.setPreserveRatio( false );
		displayB.setBlendMode( BlendMode.BLUE );

		// Grid lines 3 x 3
//		Line l1 = new Line( 0, height / 3, width, height / 3 );
//		l1.setStyle( "-fx-stroke: white" );
//		l1.setBlendMode( BlendMode.ADD );
//
//		Line l2 = new Line( 0, height / 3 * 2, width, height / 3 * 2 );
//		l2.setStyle( "-fx-stroke: white" );
//		l2.setBlendMode( BlendMode.ADD );
//
//		Line l3 = new Line( width / 3, 0, width / 3, height );
//		l3.setStyle( "-fx-stroke: white" );
//		l3.setBlendMode( BlendMode.ADD );
//
//		Line l4 = new Line( width / 3 * 2, 0, width / 3 * 2, height );
//		l4.setStyle( "-fx-stroke: white" );
//		l4.setBlendMode( BlendMode.ADD );

		// Grid lines 2 x 2
		Line l1 = new Line( 0, height / 2, width, height / 2 );
		l1.setStyle( "-fx-stroke: white" );
		l1.setBlendMode( BlendMode.ADD );

		Line l2 = new Line( width / 2, 0, width / 2, height );
		l2.setStyle( "-fx-stroke: white" );
		l2.setBlendMode( BlendMode.ADD );


		// Clipping rectangle
		final Rectangle outputClip = new Rectangle(width, height);
		StackPane sp = new StackPane( displayR, displayG, displayB );
		sp.setPrefSize( width, height );
		sp.setClip( outputClip );

		Group g = new Group();

		g.getChildren().add(sp);

		g.getChildren().add(l1);
		g.getChildren().add(l2);
//		g.getChildren().add(l3);
//		g.getChildren().add(l4);

		VBox toolBox = new VBox( 20 );

		group = new ToggleGroup();

		RadioButton button1 = new RadioButton("R");
		button1.setToggleGroup(group);
		button1.setSelected(true);
		RadioButton button2 = new RadioButton("G");
		button2.setToggleGroup(group);
		RadioButton button3 = new RadioButton("B");
		button3.setToggleGroup(group);

		GridPane gridpane = getControlBox(width, height);

		slider.setValue( alphaR );
		group.selectedToggleProperty().addListener( new ChangeListener< Toggle >()
		{
			@Override public void changed( ObservableValue< ? extends Toggle > observable, Toggle oldValue, Toggle newValue )
			{
				RadioButton selectedRadioButton = (RadioButton) newValue;

				switch ( selectedRadioButton.getText() ) {
					case "R":
						slider.setValue( alphaR );
						break;
					case "G":
						slider.setValue( alphaG );
						break;
					case "B":
						slider.setValue( alphaB );
						break;
				}

			}
		} );

		toolBox.getChildren().addAll( cameraOnSwitch, new HBox( 10, button1, button2, button3 ), gridpane );
		toolBox.setAlignment( Pos.CENTER );

		box.setSpacing( 10 );
		Group g2 = new Group( g );
		box.getChildren().addAll( toolBox, g2 );
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
									g.setScaleX( scaleFactor );
									g.setScaleY( scaleFactor );

									break;
								case MINUS:
									scaleFactor -= 0.1;
									g.setScaleX( scaleFactor );
									g.setScaleY( scaleFactor );

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
		ImageView iv = null;

		switch ( selectedRadioButton.getText() ) {
			case "R":
				iv = displayR;
				break;
			case "G":
				iv = displayG;
				break;
			case "B":
				iv = displayB;
				break;
		}

		return iv;
	}

	private GridPane getControlBox( int width, int height )
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

				switch ( selectedRadioButton.getText() ) {
					case "R":
						alphaR = newValue.intValue();
						break;
					case "G":
						alphaG = newValue.intValue();
						break;
					case "B":
						alphaB = newValue.intValue();
						break;
				}

			}
		} );

		StageSlider targetSlider = new StageSlider( "", integerProperty, false, false, 0, 100, 5 );

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
}
