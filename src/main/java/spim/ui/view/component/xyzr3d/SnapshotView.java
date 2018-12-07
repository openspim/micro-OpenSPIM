package spim.ui.view.component.xyzr3d;

import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Paint;

/**
 * SnapshotView is a copy of scene
 */
public class SnapshotView extends View3D
{

	private final SnapshotParameters params;
	private final ImageView viewPane;
	private WritableImage wImage = null;

	public SnapshotView( final Group root3D )
	{
		super( root3D );

		perspectiveCamera.getTransforms().setAll( viewingRotate,
				viewingTranslate );

		params = new SnapshotParameters();
		params.setCamera( perspectiveCamera );
		params.setDepthBuffer( true );

		// UI element
		viewPane = new ImageView();

		// Bind event handlers
		viewPane.setOnMouseDragged( mouseHandler() );
		viewPane.setOnScroll( scrollHandler() );
		viewPane.setOnMousePressed( mousePressedHandler() );

	}

	public ImageView getViewPane()
	{
		return viewPane;
	}

	public void drawView( final double width, final double height )
	{

		params.setViewport( new Rectangle2D( 0, 0, width, height ) );

		if ( wImage == null || wImage.getWidth() != width
				|| wImage.getHeight() != height )
		{
			wImage = root3D.snapshot( params, null );
		}
		else
		{
			root3D.snapshot( params, wImage );
		}

		viewPane.setImage( wImage );
	}

	public void setBackground( final Paint bg )
	{
		params.setFill( bg );
	}

	@Override
	double getWidth()
	{
		return viewPane.getBoundsInLocal().getWidth();
	}

	@Override
	double getHeight()
	{
		return viewPane.getBoundsInLocal().getHeight();
	}
}
