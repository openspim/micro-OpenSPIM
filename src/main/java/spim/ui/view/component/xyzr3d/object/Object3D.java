package spim.ui.view.component.xyzr3d.object;

import javafx.scene.Group;

/**
 * Base class for 3D object
 */
public abstract class Object3D extends Group
{
	private static final double MODEL_SCALE_FACTOR = 10;

	public Group buildScene()
	{
		this.setScaleX(MODEL_SCALE_FACTOR);
		this.setScaleY(MODEL_SCALE_FACTOR);
		this.setScaleZ(MODEL_SCALE_FACTOR);

		return new Group(this);
	}
}
