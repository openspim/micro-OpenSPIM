package spim.ui.view.component.xyzr3d;

import spim.ui.view.component.xyzr3d.object.Axes;
import spim.ui.view.component.xyzr3d.object.Cube;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.BoundingBox;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * CubeScene contains a scene of CUBE
 */
public class CubeScene extends View3D
{
	public static final int VIEWPORT_SIZE = 800;

	private static final String xPlus = CubeScene.class.getResource( "images/Xplus.png" )
			.toString();
	private static final String xMinus = CubeScene.class.getResource( "images/XMinus.png" )
			.toString();

	private static final String yPlus = CubeScene.class.getResource( "images/Yplus.png" )
			.toString();
	private static final String yMinus = CubeScene.class.getResource( "images/YMinus.png" )
			.toString();

	private static final String zPlus = CubeScene.class.getResource( "images/Zplus.png" )
			.toString();
	private static final String zMinus = CubeScene.class.getResource( "images/ZMinus.png" )
			.toString();

	private SubScene subScene = null;
	private Group viewingGroup = null;

	private Group cubeCenterGroup = null;

	private Rotate cubeRotate = null;
	private Timeline cubeRotTimeline = null;

	public CubeScene()
	{
		super( new Group() );
		createBaseScene();
		createSubScene( 800, 800, SceneAntialiasing.BALANCED );
	}

	public void createBaseScene()
	{
		// SubScene's lights
		PointLight pointLight = new PointLight( Color.WHITE );
		pointLight.setTranslateZ( -20000 );

		// Viewing group: camera and headlight
		viewingGroup = new Group( perspectiveCamera, pointLight );
		viewingGroup.getTransforms().setAll( viewingRotate,
				viewingTranslate );

		//
		// Group hierarchy of the cube
		//
		Cube meshView = new Cube( 10, Color.RED, 1.0 );
		meshView.setMaterials( xPlus, xMinus, yPlus, yMinus, zPlus, zMinus );
		cubeCenterGroup = meshView.buildScene();
		cubeCenterGroup.setRotationAxis( Rotate.Y_AXIS );
		translateGroup( cubeCenterGroup, 60.0 );

		// Create an Axis
		Axes axes = new Axes();
		Group axisGroup = axes.buildScene();
		translateGroup( axisGroup, 60.0 );

		Box stageBox = new Box( VIEWPORT_SIZE,
				VIEWPORT_SIZE,
				VIEWPORT_SIZE );

		stageBox.setMaterial( new PhongMaterial( Color.color( 0.1,
				0.4,
				0.4,
				0.02 ) ) );
		// stageBox.setDrawMode( DrawMode.LINE );

		Group stageGroup = new Group( cubeCenterGroup, axisGroup, stageBox );

		root3D.getChildren().add( stageGroup );
		cubeRotate = new Rotate( 0, 0, 0, 0, Rotate.Z_AXIS );

		final KeyValue kv0 = new KeyValue( cubeRotate.angleProperty(),
				0,
				Interpolator.LINEAR );
		final KeyValue kv1 = new KeyValue( cubeRotate.angleProperty(),
				360,
				Interpolator.LINEAR );
		final KeyFrame kf0 = new KeyFrame( Duration.millis( 0 ), kv0 );
		final KeyFrame kf1 = new KeyFrame( Duration.millis( 50000 ), kv1 ); // min
		// speed,
		// max
		// duration

		cubeRotTimeline = new Timeline();
		cubeRotTimeline.setCycleCount( Animation.INDEFINITE );
		cubeRotTimeline.getKeyFrames().setAll( kf0, kf1 );

		root3D.getTransforms().setAll( cubeRotate );

		BoundingBox cubeBinL = ( BoundingBox ) stageGroup.getBoundsInLocal();
		stageGroup.setTranslateX( -( cubeBinL.getMinX() + cubeBinL.getMaxX() ) / 2.0 );
		stageGroup.setTranslateY( -( cubeBinL.getMinY() + cubeBinL.getMaxY() ) / 2.0 );
		stageGroup.setTranslateZ( -( cubeBinL.getMinZ() + cubeBinL.getMaxZ() ) / 2.0 );

		sceneDiameter = Math.sqrt( Math.pow( cubeBinL.getWidth(), 2 ) + Math.pow( cubeBinL.getHeight(),
				2 )
				+ Math.pow( cubeBinL.getDepth(), 2 ) );
	}

	public BoundingBox getCubeBoundingBox()
	{
		return ( BoundingBox ) cubeCenterGroup.getBoundsInLocal();
	}

	static void translateGroup( Group group, double offset )
	{
		BoundingBox groupBB = ( BoundingBox ) group.getBoundsInLocal();
		group.setTranslateX( -VIEWPORT_SIZE / 2.0
				+ ( groupBB.getMinX() + groupBB.getMaxX() )
				/ 2.0
				+ offset );
		group.setTranslateY( -VIEWPORT_SIZE / 2.0
				+ ( groupBB.getMinY() + groupBB.getMaxY() )
				/ 2.0
				+ offset );
		group.setTranslateZ( -VIEWPORT_SIZE / 2.0
				+ ( groupBB.getMinZ() + groupBB.getMaxZ() )
				/ 2.0
				+ offset );
	}

	public Group getCubeCenterGroup()
	{
		return cubeCenterGroup;
	}

	public double getSceneDiameter()
	{
		return sceneDiameter;
	}

	public void createSubScene( final double width,
			final double height,
			final SceneAntialiasing sceneAA )
	{
		final Group subSceneRoot = new Group();

		subScene = new SubScene( subSceneRoot,
				width,
				height,
				true,
				sceneAA );

		subScene.setFill( Color.TRANSPARENT );
		subScene.setCamera( perspectiveCamera );

		// Add all to SubScene
		subSceneRoot.getChildren().addAll( root3D, viewingGroup ); // , ambLight

		// Bind event handlers
		subScene.setOnMouseDragged( mouseHandler() );
		subScene.setOnScroll( scrollHandler() );
		subScene.setOnMousePressed( mousePressedHandler() );
	}

	public SubScene getSubScene()
	{
		return subScene;
	}

	@Override
	double getWidth()
	{
		return subScene.getWidth();
	}

	@Override
	double getHeight()
	{
		return subScene.getHeight();
	}

	public void stopCubeRotation()
	{
		cubeRotTimeline.stop();
		cubeRotate.setAngle( 0 );
	}

	public void setRotationSpeed( final float speed )
	{
		cubeRotTimeline.play();
		cubeRotTimeline.setRate( speed );
	}
}
