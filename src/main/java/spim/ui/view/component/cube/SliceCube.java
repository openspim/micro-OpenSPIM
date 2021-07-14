package spim.ui.view.component.cube;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
public class SliceCube extends Group {
	public SliceCube( double size, double height, double max, Color color, double shade,
					 DoubleProperty startZ, DoubleProperty endZ,
					 DoubleProperty currentZ, DoubleProperty stepSize ) {

		DoubleProperty start = new SimpleDoubleProperty();
		start.bind(startZ.multiply( -max / height ));

		DoubleProperty end = new SimpleDoubleProperty();
		end.bind(endZ.multiply( - max / height ));

		DoubleProperty step = new SimpleDoubleProperty();
		step.bind(stepSize.multiply( - max / height ));

		DoubleProperty curr = new SimpleDoubleProperty();
		curr.bind(currentZ.multiply( - max / height ));

		final Group[] slices = {new Group()};

		Rectangle current = RectangleBuilder.create() // top face
				.width(size).height(0.25*size)
				.fill(Color.RED.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5))
				.translateX(0)
				.translateY( - 0.75 * size )
				.transforms( new Shear( -2, 0 ) )
				.build();

		current.translateYProperty().bind(Bindings.when(
				curr.lessThanOrEqualTo(start).and(curr.greaterThanOrEqualTo(end))).then(
						curr.subtract(start).divide(end.subtract(start).divide(height)).add(- 0.75 * size)
				).otherwise(
					Bindings.when(curr.greaterThan(start)).then(
							- 0.75 * size
					).otherwise(Bindings.when(curr.lessThan(end)).then(
						height - 0.75 * size)
						.otherwise(- 0.75 * size)
					)
				)
		);

		NumberAxis axis = new NumberAxis(-max, 0, max > 100 ? 500 : 10);

		axis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(axis) {
			@Override
			public String toString(Number value) {
				// note we are printing minus value
				return String.format("%d", -value.intValue());
			}
		});

		axis.setPrefHeight( height );
		axis.setTranslateX( 30 );
		axis.setTranslateY( - 0.5 * size );
		axis.setSide( Side.RIGHT );
		axis.setTickLength( 30 );

//		newValue.getZStart() / maxZStack * cubeHeight
		axis.lowerBoundProperty().bind( end );
		axis.upperBoundProperty().bind( start );
		axis.tickUnitProperty().bind(Bindings.when(
				start.subtract(end).greaterThan(100)
		).then(100).otherwise(10));

		Color posStackColor = Color.TOMATO.deriveColor(0.0, 1.0, (1 - 0.1*shade), 0.5);

		endZ.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				updateSlices(end, start, step, slices, size, posStackColor, shade, height);
			}
		});

		startZ.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				updateSlices(end, start, step, slices, size, posStackColor, shade, height);
			}
		});

		stepSize.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				updateSlices(end, start, step, slices, size, posStackColor, shade, height);
			}
		});


		getChildren().addAll(
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
				slices[0],
				current,
				axis
		);
	}

	private void updateSlices(DoubleProperty end, DoubleProperty start, DoubleProperty step, Group[] slices, double size, Color posStackColor, double shade, double height) {
		if (end.intValue() != 0 && start.intValue() != 0) {
			if(end.intValue() < start.intValue()) {
				final float num = start.floatValue() - end.floatValue() + step.floatValue() / step.floatValue();
				final double max = start.get() - end.get();

				getChildren().remove(slices[0]);
				slices[0] = new Group();

				for(float i = 0; i < num; i += -step.floatValue()) {
					slices[0].getChildren().add(
						RectangleBuilder.create() // top face
							.width(size).height(0.25*size)
							.fill(posStackColor.deriveColor(0.0, 1.0, (1 - 0.1*shade), 1.0))
							.translateX(0)
							.translateY( i / max * height - 0.75 * size)
							.transforms( new Shear( -2, 0 ) )
							.build()
					);
				}

				getChildren().add(3, slices[0]);
			}
		}
	}
}
