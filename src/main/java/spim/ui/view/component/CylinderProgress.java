package spim.ui.view.component;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import spim.model.data.TimePointItem;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2020
 */
public class CylinderProgress extends Group
{
	private CylinderCollection col;

	public CylinderProgress( DoubleProperty sizeProperty, ObservableList<TimePointItem> list, DoubleProperty current ) {
		col = new CylinderCollection( list, sizeProperty );
		list.addListener( new ListChangeListener< TimePointItem >()
		{
			@Override public void onChanged( Change< ? extends TimePointItem > c )
			{
				col.refresh();
			}
		} );

		TimePointItem.updateTimePointItem.addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				col.refresh();
			}
		} );

		Cylinder progress = new Cylinder();
		progress.heightProperty().bind( current.multiply(sizeProperty) );
		progress.translateXProperty().bind( current.multiply(sizeProperty).multiply( 0.5 ).subtract( sizeProperty.multiply(0.5 ) ) );
		progress.setRadius( 20 );
		progress.setRotate( 90 );
		progress.heightProperty().addListener( new ChangeListener< Number >()
		{
			@Override public void changed( ObservableValue< ? extends Number > observable, Number oldValue, Number newValue )
			{
				Platform.runLater( new Runnable()
				{
					@Override public void run()
					{
						col.refresh();
					}
				} );
			}
		} );

		PhongMaterial progressMat = new PhongMaterial();
		progressMat.setDiffuseColor( Color.CORNFLOWERBLUE );
		progress.setMaterial( progressMat );

		getChildren().addAll(
				progress,
				col
		);

		setRotationAxis( Rotate.Y_AXIS );
		setRotate( -5 );
	}

	public void refresh() {
		col.refresh();
	}


	@Override
	public double minWidth(double height) {
		return 500;
	}

	public class CylinderCollection extends Group {
		final private ObservableList< TimePointItem > list;
		private double size;
		public CylinderCollection( ObservableList<TimePointItem> list, DoubleProperty sizeProperty ) {
			super();
			this.list = list;
			this.size = sizeProperty.get();

			sizeProperty.addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
					CylinderCollection.this.size = newValue.doubleValue();
					refresh();
				}
			});
		}

		public void refresh() {
			getChildren().clear();
			double unit = size / list.stream().mapToDouble( TimePointItem::getTotalSeconds ).sum();
			double offset;
			double cylOffset = -size * 0.5;

			for(TimePointItem item : list)
			{
				Color color = null;
				if ( item.getType().equals( TimePointItem.Type.Acq ) )
				{
					color = Color.LIMEGREEN;
				}
				else if ( item.getType().equals( TimePointItem.Type.Wait ) )
				{
					color = Color.ORANGERED;
				}

				Cylinder cylinder = new Cylinder();
				offset = unit * item.getTotalSeconds();
				cylinder.setHeight( offset );
				cylinder.setRadius( 20 );

				PhongMaterial cylinderMat = new PhongMaterial();
				cylinderMat.setDiffuseColor( color.deriveColor( 0.0, 1.0, 0.8, 0.5 ) );
//				cylinderMat.setSpecularColor( Color.BLACK );
				cylinder.setMaterial( cylinderMat );
				cylinder.setRotate( 90 );
				cylinder.setTranslateX( cylOffset + offset * 0.5 );

				String n = item.getNoTimePoints() + "";

				Label label = new Label( n );
				label.setTranslateX( cylOffset + offset * 0.5 - 1.5 * n.length());
				label.setTranslateY( -10 );
				label.setAlignment( Pos.CENTER );

				String intervalString = item.toIntervalString();
				Label tp = new Label( intervalString );
				tp.setTranslateX( cylOffset + offset * 0.5 - 2 * intervalString.length());
				if (list.indexOf(item) % 2 == 0)
					tp.setTranslateY(-40);
				else tp.setTranslateY(20);
				tp.setAlignment( Pos.CENTER );
				getChildren().addAll( cylinder, label, tp );

				cylOffset += offset;
			}
		}
	}
}
