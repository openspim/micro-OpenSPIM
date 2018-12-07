package spim.ui.view.component.xyzr3d.object;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;

/**
 * X,Y,Z Axes
 */
public class Axes extends Object3D
{
	final Rotate rx = new Rotate(0, Rotate.X_AXIS);
	final Rotate ry = new Rotate(0, Rotate.Y_AXIS);
	final Rotate rz = new Rotate(0, Rotate.Z_AXIS);

	Cylinder x = new Cylinder(1, 15);
	Cylinder y = new Cylinder(1, 15);
	Cylinder z = new Cylinder(1, 15);

	public Axes()
	{
		z.setMaterial(new PhongMaterial(Color.BLUE));
		z.setRotationAxis(Rotate.X_AXIS);
		z.setRotate(90);
		z.setTranslateZ(0.5 * 15);
		z.setTranslateX(-0.5 * 15);
		z.setTranslateY(-0.5 * 15);

		y.setMaterial(new PhongMaterial(Color.GREEN));
		y.setTranslateX(-0.5 * 15);

		x.setMaterial(new PhongMaterial(Color.RED));
		x.setRotationAxis(Rotate.Z_AXIS);
		x.setRotate(90);
		x.setTranslateY(-0.5 * 15);

		getTransforms().addAll(rz, ry, rx);
		getChildren().addAll(x, y, z);
	}
}