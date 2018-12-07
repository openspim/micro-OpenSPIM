package spim.ui.view.component.xyzr3d;

import spim.ui.view.component.xyzr3d.controls.CircleIndicator;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

/**
 * StageControl
 */
public class StageControl
{
	private VBox panel;

	// 3D perspective view properties
	PerspectiveCamera camera = new PerspectiveCamera();

	final Cam camOffset = new Cam();
	final Cam cam = new Cam();

	final Group rectangleGroup = new Group();
	final Xform axisGroup = new Xform();
	private AnimationTimer timer;

	// Properties

	class Cam extends Group
	{
		Translate t = new Translate();
		Translate p = new Translate();
		Translate ip = new Translate();
		Rotate rx = new Rotate();

		{
			rx.setAxis( Rotate.X_AXIS );
		}

		Rotate ry = new Rotate();

		{
			ry.setAxis( Rotate.Y_AXIS );
		}

		Rotate rz = new Rotate();

		{
			rz.setAxis( Rotate.Z_AXIS );
		}

		Scale s = new Scale();

		public Cam()
		{
			super();
			getTransforms().addAll( t, p, rx, rz, ry, s, ip );
		}
	}

	public StageControl()
	{
		init();
	}

	private void buildAxes()
	{
		System.out.println( "buildAxes()" );
		final PhongMaterial redMaterial = new PhongMaterial();
		redMaterial.setDiffuseColor( Color.DARKRED );
		redMaterial.setSpecularColor( Color.RED );

		final PhongMaterial greenMaterial = new PhongMaterial();
		greenMaterial.setDiffuseColor( Color.DARKGREEN );
		greenMaterial.setSpecularColor( Color.GREEN );

		final PhongMaterial blueMaterial = new PhongMaterial();
		blueMaterial.setDiffuseColor( Color.DARKBLUE );
		blueMaterial.setSpecularColor( Color.BLUE );

		final Box xAxis = new Box( 240.0, 1, 1 );
		final Box yAxis = new Box( 1, 240.0, 1 );
		final Box zAxis = new Box( 1, 1, 240.0 );

		xAxis.setMaterial( redMaterial );
		yAxis.setMaterial( greenMaterial );
		zAxis.setMaterial( blueMaterial );

		axisGroup.getChildren().addAll( xAxis, yAxis, zAxis );
	}

	public void init()
	{
		panel = new VBox( 20 );

		// R-Stage
		final Label caption = new Label( "Sample Stage R (micro-degree)" );

		final Slider slider = new Slider();
		slider.setMin( 0 );
		slider.setMax( 360 );
		slider.setMajorTickUnit( 90 );
		slider.setShowTickMarks( true );
		slider.setShowTickLabels( true );

		final CircleIndicator pi = new CircleIndicator( 0 );

		slider.valueProperty()
				.addListener( ( ObservableValue< ? extends Number > ov,
						Number old_val,
						Number new_val ) -> pi.setProgress( new_val.doubleValue() ) );

		final HBox rStage = new HBox( 5 );
		rStage.getChildren().addAll( caption, slider, pi );
		HBox.setHgrow( rStage, Priority.ALWAYS );

		// X-Stage
		final Label xStageLabel = new Label( "Sample Stage X (microns)" );
		final Slider xStageSlider = createSlider( 100,
				"X-axis stage control" );
		final Label xStageValue = new Label( Double.toString( xStageSlider.getValue() ) );

		xStageSlider.valueProperty().addListener( ( observable,
				oldValue,
				newValue ) -> {
			xStageValue.setText( String.format( "%d", newValue.intValue() ) );
			rectangleGroup.setTranslateX( newValue.doubleValue() );
		} );

		final HBox xStage = new HBox( 5 );
		xStage.getChildren().addAll( xStageLabel,
				xStageSlider,
				xStageValue );
		HBox.setHgrow( xStage, Priority.ALWAYS );

		// Y-Stage
		final Label yStageLabel = new Label( "Sample Stage Y (microns)" );
		final Slider yStageSlider = createSlider( 100,
				"Y-axis stage control" );
		final Label yStageValue = new Label( Double.toString( yStageSlider.getValue() ) );

		yStageSlider.valueProperty()
				.addListener( ( ov, oldValue, newValue ) -> {
					yStageValue.setText( String.format( "%d",
							newValue.intValue() ) );
					rectangleGroup.setTranslateZ( newValue.doubleValue() );
				} );

		final HBox yStage = new HBox( 5 );
		yStage.getChildren().addAll( yStageLabel,
				yStageSlider,
				yStageValue );
		HBox.setHgrow( yStage, Priority.ALWAYS );

		// Z-Stage
		final Label zStageLabel = new Label( "Sample Stage Z (microns)" );
		final Slider zStageSlider = createSlider( 100,
				"Z-axis stage control" );
		final Label zStageValue = new Label( Double.toString( zStageSlider.getValue() ) );

		zStageSlider.valueProperty()
				.addListener( ( ov, oldValue, newValue ) -> {
					zStageValue.setText( String.format( "%d",
							newValue.intValue() ) );
					rectangleGroup.setTranslateY( newValue.doubleValue() );
				} );

		final HBox zStage = new HBox( 5 );
		zStage.getChildren().addAll( zStageLabel,
				zStageSlider,
				zStageValue );
		HBox.setHgrow( zStage, Priority.ALWAYS );

		final StackPane stackPane = new StackPane();
		stackPane.getChildren()
				.addAll( VBoxBuilder.create()
						.spacing( 10 )
						.alignment( Pos.TOP_CENTER )
						.children( rStage,
								xStage,
								yStage,
								zStage )
						.build() );

		VBox.setMargin( stackPane, new Insets( 30, 8, 8, 8 ) );
		panel.getChildren().add( stackPane );

		// ////////////////////////////////////////////
		// 3D perspective view
		Pane pane = new Pane();
		pane.setPrefSize( 200, 400 );
		// cam.getChildren().add(camera);
		camOffset.getChildren().add( cam );
		pane.getChildren().add( camOffset );
		VBox.setMargin( pane, new Insets( 30, 8, 8, 8 ) );

		TitledPane tp = new TitledPane();
		tp.setText( "Stage View" );
		tp.setContent( pane );
		tp.setCollapsible( false );

		panel.getChildren().add( tp );
		resetCam();

		rectangleGroup.setDepthTest( DepthTest.ENABLE );

		double xPos = 350;
		double yPos = 250;
		double zPos = 250;

		buildAxes();
		axisGroup.setTranslateX( xPos );
		axisGroup.setTranslateY( yPos );
		axisGroup.setTranslateZ( zPos );

		double barWidth = 10.0;
		double barHeight = 10.0;
		double barDepth = 10.0;

		final PhongMaterial redMaterial = new PhongMaterial();
		redMaterial.setSpecularColor( Color.YELLOW );
		redMaterial.setDiffuseColor( Color.RED );

		final Box red = new Box( barWidth, barHeight, barDepth );
		red.setTranslateX( xPos );
		red.setTranslateY( yPos );
		red.setTranslateZ( zPos );
		red.setMaterial( redMaterial );

		rectangleGroup.getChildren().add( red );

		rectangleGroup.setScaleX( 2 );
		rectangleGroup.setScaleY( 2 );
		rectangleGroup.setScaleZ( 2 );
		cam.getChildren().addAll( axisGroup, rectangleGroup );

		double halfSceneWidth = 500;
		double halfSceneHeight = 500;
		cam.p.setX( halfSceneWidth );
		cam.ip.setX( -halfSceneWidth );
		cam.p.setY( halfSceneHeight );
		cam.ip.setY( -halfSceneHeight );

		timer = new AnimationTimer()
		{
			@Override
			public void handle( long now )
			{
			}
		};
	}

	public void resetCam()
	{
		cam.t.setX( 0.0 );
		cam.t.setY( 0.0 );
		cam.t.setZ( 0.0 );
		cam.rx.setAngle( 30.0 );
		cam.ry.setAngle( -30.0 );
		cam.rz.setAngle( 0.0 );
		cam.s.setX( 1.25 );
		cam.s.setY( 1.25 );
		cam.s.setZ( 1.25 );

		cam.p.setX( 0.0 );
		cam.p.setY( 0.0 );
		cam.p.setZ( 0.0 );

		cam.ip.setX( 0.0 );
		cam.ip.setY( 0.0 );
		cam.ip.setZ( 0.0 );

		final Bounds bounds = cam.getBoundsInLocal();
		final double pivotX = bounds.getMinX() + bounds.getWidth() / 2;
		final double pivotY = bounds.getMinY() + bounds.getHeight() / 2;
		final double pivotZ = bounds.getMinZ() + bounds.getDepth() / 2;

		cam.p.setX( pivotX );
		cam.p.setY( pivotY );
		cam.p.setZ( pivotZ );

		cam.ip.setX( -pivotX );
		cam.ip.setY( -pivotY );
		cam.ip.setZ( -pivotZ );
	}

	public void start( Stage stage )
	{
		Scene scene = new Scene( panel );
		scene.setCamera( camera );

		stage.setTitle( "Stage Control" );
		stage.setScene( scene );
		stage.show();

		timer.start();
	}

	private Slider createSlider( final double value,
			final String helpText )
	{
		final Slider slider = new Slider( 0, value, 0 );
		slider.setMajorTickUnit( 20 );
		slider.setMinorTickCount( 0 );
		slider.setShowTickMarks( true );
		slider.setShowTickLabels( true );
		slider.setStyle( "-fx-text-fill: white" );
		slider.setTooltip( new Tooltip( helpText ) );
		return slider;
	}

	public void stop()
	{
	}

	public VBox getPanel()
	{
		return panel;
	}
}
