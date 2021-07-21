package spim.ui.view.component.widgets.cube;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.chart.NumberAxis;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.RectangleBuilder;
import javafx.scene.transform.Shear;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: July 2021
 */
public class StackCube extends Group
{
	public StackCube(double size, double height, double max, Color color, double shade,
					 DoubleProperty startZ, DoubleProperty endZ,
					 DoubleProperty currentZ ) {


		Rectangle current = RectangleBuilder.create() // top face
				.width(size).height(0.25*size)
				.fill(Color.RED.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5))
				.translateX(0)
				.translateY( - 0.75 * size )
				.transforms( new Shear( -2, 0 ) )
				.build();

		current.translateYProperty().bind( currentZ.subtract( 0.75 * size ) );

		double currentStackSize = endZ.get() - startZ.get();
		double endPosition = startZ.get();

		Color posStackColor = Color.GREEN.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5);

		Rectangle currentStackTopFace = RectangleBuilder.create() // top face
				.width(size).height(0.25*size)
				.fill(posStackColor.deriveColor(0.0, 1.0, (1 - 0.1*shade), 1.0))
				.translateX(0)
				.translateY( endPosition - 0.75 * size)
				.transforms( new Shear( -2, 0 ) )
				.build();

		currentStackTopFace.translateYProperty().bind( startZ.subtract( 0.75 * size ) );

		Rectangle currentStackRightFace = RectangleBuilder.create() // right face
				.width(size/2).height(currentStackSize)
				.fill(posStackColor.deriveColor(0.0, 1.0, (1 - 0.3*shade), 1.0))
				.translateX( 0.5 * size )
				.translateY( endPosition -0.5 * size )
				.transforms( new Shear( 0, -0.5 ) )
				.build();

		currentStackRightFace.heightProperty().bind( endZ.subtract( startZ ) );
		currentStackRightFace.translateYProperty().bind( startZ.subtract( 0.5 * size ) );

		Rectangle currentStackFrontFace = RectangleBuilder.create() // front face
				.width(size).height(currentStackSize)
				.fill(posStackColor)
				.translateX( -0.5 * size )
				.translateY( endPosition -0.5 * size )
				.build();

		currentStackFrontFace.heightProperty().bind( endZ.subtract( startZ ) );
		currentStackFrontFace.translateYProperty().bind( startZ.subtract( 0.5 * size ) );

		NumberAxis axis = new NumberAxis(-max, 0, max > 100 ? 500 : 10);

		axis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(axis) {
			@Override
			public String toString(Number value) {
				// note we are printing minus value
				return String.format("%d", -value.intValue());
			}
		});

		axis.setPrefHeight(height);
		axis.setTranslateX( -70 );
		axis.setTranslateY( - 0.5 * size);
		axis.setSide(Side.LEFT);

		getChildren().addAll(
				axis,
				RectangleBuilder.create() // top face
						.width(size).height(0.25*size)
						.fill(color.deriveColor(0.0, 1.0, (1 - 0.1*shade), 1.0))
						.translateX(0)
						.translateY(-0.75*size)
						.transforms( new Shear( -2, 0 ) )
						.build(),
				RectangleBuilder.create() // right face
						.width(size/2).height(height)
						.fill(color.deriveColor(0.0, 1.0, (1 - 0.3*shade), 1.0))
						.translateX(0.5*size)
						.translateY(-0.5*size)
						.transforms( new Shear( 0, -0.5 ) )
						.build(),
				RectangleBuilder.create() // front face
						.width(size).height(height)
						.fill(color)
						.translateX(-0.5*size)
						.translateY(-0.5*size)
						.build(),
				currentStackTopFace,
				currentStackRightFace,
				currentStackFrontFace,
				current
		);
	}
}
