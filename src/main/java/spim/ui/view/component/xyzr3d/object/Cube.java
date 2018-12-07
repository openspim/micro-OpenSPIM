package spim.ui.view.component.xyzr3d.object;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

/**
 * 3D Cube object
 */
public class Cube extends Object3D
{
	final Rotate rx = new Rotate(0, Rotate.X_AXIS);
	final Rotate ry = new Rotate(0, Rotate.Y_AXIS);
	final Rotate rz = new Rotate(0, Rotate.Z_AXIS);

	MeshView xMinus = loadMeshView();
	MeshView xPlus = loadMeshView();
	MeshView yMinus = loadMeshView();
	MeshView yPlus = loadMeshView();
	MeshView zMinus = loadMeshView();
	MeshView zPlus = loadMeshView();

	private TriangleMesh triangleMesh;

	private MeshView loadMeshView()
	{
		float[] points =
		{ 5, 5, 0, -5, 5, 0, 5, -5, 0, -5, -5, 0 };
		float[] texCoords =
		{ 1, 1, 0, 1, 1, 0, 0, 0 };
		int[] faces =
		{ 2, 2, 1, 1, 0, 0, 2, 2, 3, 3, 1, 1 };

		if (triangleMesh == null)
		{
			triangleMesh = new TriangleMesh();
			triangleMesh.getPoints().setAll(points);
			triangleMesh.getTexCoords().setAll(texCoords);
			triangleMesh.getFaces().setAll(faces);
		}

		return new MeshView(triangleMesh);
	}

	public Cube(double size, Color color, double shade)
	{
		zPlus.setRotationAxis(Rotate.X_AXIS);
		zPlus.setRotate(180);
		zPlus.setTranslateX(-0.5 * size);
		zPlus.setTranslateY(-0.5 * size);
		zPlus.setTranslateZ(0.5 * size);

		yPlus.setTranslateX(-0.5 * size);
		yPlus.setTranslateY(0);
		yPlus.setRotationAxis(Rotate.X_AXIS);
		yPlus.setRotate(90);

		xPlus.setTranslateX(0);
		xPlus.setTranslateY(-0.5 * size);
		xPlus.setRotationAxis(Rotate.Y_AXIS);
		xPlus.setRotate(-90);

		xMinus.setTranslateX(-1 * size);
		xMinus.setTranslateY(-0.5 * size);
		xMinus.setRotationAxis(Rotate.Y_AXIS);
		xMinus.setRotate(90);

		yMinus.setTranslateX(-0.5 * size);
		yMinus.setTranslateY(-1 * size);
		yMinus.setRotationAxis(Rotate.X_AXIS);
		yMinus.setRotate(-90);

		zMinus.setTranslateX(-0.5 * size);
		zMinus.setTranslateY(-0.5 * size);
		zMinus.setTranslateZ(-0.5 * size);

		getTransforms().addAll(rz, ry, rx);
		getChildren().addAll(zMinus, yMinus, xPlus, xMinus, yPlus, zPlus);
	}

	public void setMaterials(	String xPlus,
														String xMinus,
														String yPlus,
														String yMinus,
														String zPlus,
														String zMinus)
	{
		// Setup x+ material
		PhongMaterial material = new PhongMaterial();
		Image image = new Image(xPlus);
		material.setDiffuseMap(image);
		this.xPlus.setMaterial(material);

		// Setup x- material
		material = new PhongMaterial();
		image = new Image(xMinus);
		material.setDiffuseMap(image);
		this.xMinus.setMaterial(material);

		// Setup y+ material
		material = new PhongMaterial();
		image = new Image(yPlus);
		material.setDiffuseMap(image);
		this.yPlus.setMaterial(material);

		// Setup y- material
		material = new PhongMaterial();
		image = new Image(yMinus);
		material.setDiffuseMap(image);
		this.yMinus.setMaterial(material);

		// Setup z+ material
		material = new PhongMaterial();
		image = new Image(zPlus);
		material.setDiffuseMap(image);
		this.zPlus.setMaterial(material);

		// Setup z- material
		material = new PhongMaterial();
		image = new Image(zMinus);
		material.setDiffuseMap(image);
		this.zMinus.setMaterial(material);
	}
}