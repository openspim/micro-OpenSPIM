package spim.ui.view.component.xyzr3d;

import javafx.event.EventHandler;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

/**
 * Created by moon on 3/8/16.
 */
public abstract class View3D
{
	public enum ViewPort
	{
		BOTTOM( "Bottom" ),
		CORNER( "Corner" ),
		FRONT( "Front" ),
		TOP( "Top" ),
		RIGHT( "Right" ),
		BACK( "Back" );

		ViewPort( String viewPort )
		{
			this.viewPort = viewPort;
		}

		private String viewPort;

		String getViewPort()
		{
			return viewPort;
		}
	}

	protected final Group root3D;
	protected double sceneDiameter = 0;

	protected Affine viewingRotate = new Affine();
	protected Translate viewingTranslate = new Translate();
	protected double startX = 0;
	protected double startY = 0;

	protected PerspectiveCamera perspectiveCamera = null;

	protected View3D( Group root3D )
	{
		this.root3D = root3D;

		perspectiveCamera = new PerspectiveCamera( true );
		// perspectiveCamera.setVerticalFieldOfView(false);
		perspectiveCamera.setFarClip( 100000 );
		perspectiveCamera.setNearClip( 0.1 );
		perspectiveCamera.setFieldOfView( 44 );
	}

	public void setSceneDiameter( final double sceneDiameter )
	{
		this.sceneDiameter = sceneDiameter;
	}

	public void setVantagePoint( final ViewPort viewPort )
	{

		Transform rotate = null;

		final double distance = distToSceneCenter( sceneDiameter / 2 );

		switch ( viewPort )
		{
			case BOTTOM:
				rotate = new Rotate( 90, Rotate.X_AXIS );
				break;
			case CORNER:
				Rotate rotateX = new Rotate( -20, Rotate.X_AXIS );
				Rotate rotateY = new Rotate( 40,
						new Point3D( 0, 1, 0.5 ).normalize() );
				rotate = rotateX.createConcatenation( rotateY );
				break;
			case FRONT:
				rotate = new Rotate();
				break;
			case TOP:
				rotate = new Rotate( -90, Rotate.X_AXIS );
				break;
			case RIGHT:
				rotate = new Rotate( -90, Rotate.Y_AXIS );
				break;
			case BACK:
				rotate = new Rotate( 180, Rotate.Y_AXIS );
				break;
		}

		viewingRotate.setToTransform( rotate );

		viewingTranslate.setX( 0 );
		viewingTranslate.setY( 0 );
		viewingTranslate.setZ( -distance );
	}

	abstract double getWidth();

	abstract double getHeight();

	protected EventHandler< MouseEvent > mouseHandler()
	{
		final Rotate viewingRotX = new Rotate( 0, 0, 0, 0, Rotate.X_AXIS );
		final Rotate viewingRotY = new Rotate( 0, 0, 0, 0, Rotate.Y_AXIS );

		return new EventHandler< MouseEvent >()
		{
			@Override
			public void handle( MouseEvent event )
			{
				if ( event.getButton() == MouseButton.PRIMARY )
				{
					viewingRotX.setAngle( ( startY - event.getSceneY() ) / 10 );
					viewingRotY.setAngle( ( event.getSceneX() - startX ) / 10 );
					viewingRotate.append( viewingRotX.createConcatenation( viewingRotY ) );
				}
				else if ( event.getButton() == MouseButton.SECONDARY )
				{
					viewingTranslate.setX( viewingTranslate.getX() + ( startX - event.getSceneX() )
							/ 100 );
					viewingTranslate.setY( viewingTranslate.getY() + ( startY - event.getSceneY() )
							/ 100 );
				}
				else if ( event.getButton() == MouseButton.MIDDLE )
				{
					viewingTranslate.setZ( viewingTranslate.getZ() + ( event.getSceneY() - startY )
							/ 40 );
				}

				startX = event.getSceneX();
				startY = event.getSceneY();
			}
		};
	}

	protected EventHandler< ScrollEvent > scrollHandler()
	{
		return new EventHandler< ScrollEvent >()
		{
			@Override
			public void handle( ScrollEvent event )
			{
				viewingTranslate.setZ( viewingTranslate.getZ() - event.getDeltaY()
						/ 40 );
			}
		};
	}

	protected EventHandler< MouseEvent > mousePressedHandler()
	{
		return new EventHandler< MouseEvent >()
		{
			@Override
			public void handle( MouseEvent event )
			{
				startX = event.getSceneX();
				startY = event.getSceneY();
			}
		};
	}

	double distToSceneCenter( final double sceneRadius )
	{
		// Extra space
		final double borderFactor = 1.0;

		final double fov = perspectiveCamera.getFieldOfView();

		final double c3dWidth = getWidth();
		final double c3dHeight = getHeight();
		// Consider ratio of canvas' width and height
		double ratioFactor = 1.0;
		if ( c3dWidth > c3dHeight )
		{
			ratioFactor = c3dWidth / c3dHeight;
		}

		final double distToSceneCenter = borderFactor * ratioFactor
				* sceneRadius
				/ Math.tan( Math.toRadians( fov / 2 ) );

		return distToSceneCenter;
	}

	public Group getRoot3D()
	{
		return root3D;
	}
}
