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

	public CylinderProgress( double size, ObservableList<TimePointItem> list, DoubleProperty current ) {
		col = new CylinderCollection( list, size );
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
		progress.heightProperty().bind( current );
		progress.translateXProperty().bind( current.multiply( 0.5 ).subtract( size * 0.5 ) );
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
		setRotate( 20 );
	}

	public void refresh() {
		col.refresh();
	}



	public class CylinderCollection extends Group {
		final private ObservableList< TimePointItem > list;
		final private double size;
		public CylinderCollection( ObservableList<TimePointItem> list, double size ) {
			this.list = list;
			this.size = size;
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
				cylinderMat.setDiffuseColor( color.deriveColor( 0.0, 1.0, ( 1 - 0.2 ), 0.5 ) );
				cylinder.setMaterial( cylinderMat );
				cylinder.setRotate( 90 );
				cylinder.setTranslateX( cylOffset + offset * 0.5 );

				Label label = new Label( item.toString() );
				label.setTranslateX( cylOffset );
				label.setTranslateY( -10 );
				label.setAlignment( Pos.CENTER_LEFT );
				getChildren().addAll( cylinder, label );
				cylOffset += offset;
			}
		}
	}
}
