package spim.ui.view.component;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.SubScene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Screen;
import spim.ui.view.component.slider.StageSlider;
import spim.ui.view.component.xyzr3d.CubeScene;
import spim.ui.view.component.xyzr3d.SnapshotView;
import spim.ui.view.component.xyzr3d.View3D;
import spim.hardware.SPIMSetup;
import spim.hardware.Stage;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class Stage3DPanel  extends BorderPane
{
	private SPIMSetup spimSetup;
	private Pane background1Pane = null;
	private CubeScene cubeScene = null;
	private SubScene subScene = null;
	private SnapshotView secondView = null;
	private SnapshotView thirdView = null;
	private SnapshotView fourthView = null;
	private AnimationTimer viewTimer = null;

	private HashMap< StageUnit.Stage, StageUnit > stageMap = null;

	public Stage3DPanel()
	{
		init();
	}

	public Stage3DPanel( SPIMSetup setup )
	{
		this();
		this.spimSetup = setup;
	}

	public void init()
	{
		//		System.out.println("3D supported? " +
		//		Platform.isSupported( ConditionalFeature.SCENE3D));
		stageMap = new HashMap<>();

		cubeScene = new CubeScene();
		subScene = cubeScene.getSubScene();

		secondView = new SnapshotView( cubeScene.getRoot3D() );
		final ImageView secondViewPane = secondView.getViewPane();
		secondView.setSceneDiameter( cubeScene.getSceneDiameter() );

		thirdView = new SnapshotView( cubeScene.getRoot3D() );
		final ImageView thirdViewPane = thirdView.getViewPane();
		thirdView.setSceneDiameter( cubeScene.getSceneDiameter() );

		fourthView = new SnapshotView( cubeScene.getRoot3D() );
		final ImageView fourthViewPane = fourthView.getViewPane();
		fourthView.setSceneDiameter( cubeScene.getSceneDiameter() );

		cubeScene.setVantagePoint( View3D.ViewPort.CORNER );
		secondView.setVantagePoint( View3D.ViewPort.BOTTOM );
		thirdView.setVantagePoint( View3D.ViewPort.RIGHT );
		fourthView.setVantagePoint( View3D.ViewPort.FRONT );

		final Rectangle2D screenRect = Screen.getPrimary().getBounds();
		final double screenWidth = screenRect.getWidth();
		final double screenHeight = screenRect.getHeight();

		final double startWidth = screenWidth * 0.7;
		final double startHeight = screenHeight * 0.7;
		subScene.setWidth( startWidth / 2 );
		subScene.setHeight( startHeight / 2 );

		background1Pane = new Pane();
		background1Pane.setPrefSize( startWidth / 2, startHeight / 2 );

		final Pane layeredPane = new Pane()
		{
			@Override
			protected void layoutChildren()
			{

				final double sceneWidth = getWidth();

				// Main view
				final double width = sceneWidth / 2;
				final double height = getHeight() / 2;

				background1Pane.setPrefSize( width, height );
				background1Pane.autosize();
				background1Pane.relocate( 0, 0 );

				// Second view
				secondViewPane.relocate( width, 0 );
				thirdViewPane.relocate( 0, height );
				fourthViewPane.relocate( width, height );
			}
		};

		final ChangeListener sceneBoundsListener = new ChangeListener()
		{
			@Override
			public void changed( ObservableValue observable,
					Object oldXY,
					Object newXY )
			{
				subScene.setWidth( layeredPane.getWidth() / 2 );
				subScene.setHeight( layeredPane.getHeight() / 2 );
			}
		};
		layeredPane.widthProperty().addListener( sceneBoundsListener );
		layeredPane.heightProperty().addListener( sceneBoundsListener );

		viewTimer = new AnimationTimer()
		{
			@Override
			public void handle( long now )
			{
				secondView.drawView( layeredPane.getWidth() / 2,
						layeredPane.getHeight() / 2 );
				thirdView.drawView( layeredPane.getWidth() / 2,
						layeredPane.getHeight() / 2 );
				fourthView.drawView( layeredPane.getWidth() / 2,
						layeredPane.getHeight() / 2 );
			}
		};

		layeredPane.getChildren().addAll( background1Pane,
				subScene,
				secondViewPane,
				thirdViewPane,
				fourthViewPane );

		Background black = new Background( new BackgroundFill( Color.BLACK,
				null,
				null ) );

		background1Pane.setBackground( black ); // initial background
		secondView.setBackground( Paint.valueOf( "#000000" ) );
		thirdView.setBackground( Paint.valueOf( "#000000" ) );
		fourthView.setBackground( Paint.valueOf( "#000000" ) );

		setTop( createControls() );
		setCenter( layeredPane );

		ScheduledExecutorService executor = Executors.newScheduledThreadPool( 5 );

		if(spimSetup == null) {
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
							case R: device = spimSetup.getThetaStage().getPosition();
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

						Platform.runLater( () -> {
							if ( abs( error ) < 0.05 )
								stageMap.get( stage ).get( StageUnit.BooleanState.Ready ).set( true );
						} );
					}
				}
			}, 500, 5, TimeUnit.MILLISECONDS );
		}

		viewTimer.start();
	}

	private StageUnit createStageControl( String labelString, StageUnit.Stage stage )
	{
		BoundingBox cubeBB = cubeScene.getCubeBoundingBox();

		StageUnit unit;

		if( null == spimSetup ) {
			unit = new StageUnit( labelString, stage == StageUnit.Stage.R, null );
		}
		else {
			Stage stageDevice = null;

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

			unit = new StageUnit( labelString, stage == StageUnit.Stage.R, stageDevice );

			final Property< Number > targetProperty = unit.targetValueProperty();

			Stage finalStageDevice = stageDevice;
			targetProperty.addListener( ( observable, oldValue, newValue ) ->
					finalStageDevice.setPosition( newValue.doubleValue() ) );
		}

		final StageSlider deviceSlider = unit.getDeviceSlider();

		double offset = deviceSlider.getSlider().getMin() * signum( deviceSlider.getSlider()
				.getMin() );

		switch ( stage )
		{
			case R:
			{
				cubeScene.getCubeCenterGroup()
						.rotateProperty().bind( deviceSlider.getSlider()
						.valueProperty() );
			}
			break;
			case X:
			{
				double min = cubeScene.getCubeCenterGroup().getTranslateX();
				double max = cubeScene.getCubeCenterGroup().getTranslateX() + CubeScene.VIEWPORT_SIZE
						- cubeBB.getMaxX()
						* 2.2;

				deviceSlider.getSlider()
						.valueProperty()
						.addListener( ( observable, oldValue, newValue ) -> cubeScene.getCubeCenterGroup()
								.setTranslateX( ( newValue.doubleValue() + offset ) * ( max - min )
										/ ( deviceSlider.getSlider()
										.getMax() + offset )
										+ min ) );

				cubeScene.getCubeCenterGroup()
						.setTranslateX( ( deviceSlider.getSlider().getValue() + offset ) * ( max - min )
								/ ( deviceSlider.getSlider()
								.getMax() + offset )
								+ min );
			}
			break;
			case Y:
			{
				double min = cubeScene.getCubeCenterGroup().getTranslateY();
				double max = cubeScene.getCubeCenterGroup().getTranslateY() + CubeScene.VIEWPORT_SIZE
						- cubeBB.getMaxY()
						* 2.2;

				deviceSlider.getSlider()
						.valueProperty()
						.addListener( ( observable, oldValue, newValue ) -> cubeScene.getCubeCenterGroup()
								.setTranslateY( ( newValue.doubleValue() + offset ) * ( max - min )
										/ ( deviceSlider.getSlider()
										.getMax() + offset )
										+ min ) );

				cubeScene.getCubeCenterGroup()
						.setTranslateY( ( deviceSlider.getSlider().getValue() + offset ) * ( max - min )
								/ ( deviceSlider.getSlider()
								.getMax() + offset )
								+ min );
			}
			break;
			case Z:
			{
				double min = cubeScene.getCubeCenterGroup().getTranslateZ();
				double max = cubeScene.getCubeCenterGroup().getTranslateZ() + CubeScene.VIEWPORT_SIZE
						- cubeBB.getMaxZ()
						* 2.2;

				deviceSlider.getSlider()
						.valueProperty()
						.addListener( ( observable, oldValue, newValue ) -> cubeScene.getCubeCenterGroup()
								.setTranslateZ( ( newValue.doubleValue() + offset ) * ( max - min )
										/ ( deviceSlider.getSlider()
										.getMax() + offset )
										+ min ) );

				cubeScene.getCubeCenterGroup()
						.setTranslateZ( ( deviceSlider.getSlider().getValue() + offset ) * ( max - min )
								/ ( deviceSlider.getSlider()
								.getMax() + offset )
								+ min );

			}
			break;
		}

		stageMap.put( stage, unit );

		return unit;
	}

	private VBox createControls()
	{
		// CheckBox rotate = new CheckBox("Rotate");
		// rotate.selectedProperty().addListener(observable -> {
		// if (rotate.isSelected())
		// {
		// cubeScene.setRotationSpeed(20);
		// }
		// else
		// {
		// cubeScene.stopCubeRotation();
		// }
		// });

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

