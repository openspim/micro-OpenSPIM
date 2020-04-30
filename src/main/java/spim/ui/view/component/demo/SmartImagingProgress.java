package spim.ui.view.component.demo;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import spim.model.data.TimePointItem;
import spim.ui.view.component.CylinderProgress;

import static spim.ui.view.component.util.TableViewUtil.createTimePointItemDataView;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2020
 */
public class SmartImagingProgress extends Application
{
	private TableView< TimePointItem > timePointItemTableView;

	@Override
	public void start( Stage stage)
	{
		Group root = new Group();
		Scene scene = new Scene( root );
		stage.setScene( scene );
		stage.setTitle( "Smart Imaging Progress Component" );

		timePointItemTableView = createTimePointItemDataView();
		timePointItemTableView.setEditable( true );

		Button newTPButton = new Button("New TP");
		newTPButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				timePointItemTableView.getItems().add( new TimePointItem( 1 , 1, TimePointItem.IntervalUnit.Sec ) );
			}
		} );

		Button newWaitButton = new Button("New Wait");
		newWaitButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				timePointItemTableView.getItems().add( new TimePointItem( 10, TimePointItem.IntervalUnit.Sec ) );
			}
		} );

		Button deleteButton = new Button("Delete");
		deleteButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(timePointItemTableView.getSelectionModel().getSelectedIndex() > -1)
					timePointItemTableView.getItems().remove( timePointItemTableView.getSelectionModel().getSelectedIndex() );
			}
		} );

		int size = 400;
		Slider slider = new Slider(0, size, 0);
		CylinderProgress cylinder = new CylinderProgress(size, timePointItemTableView.getItems(), slider.valueProperty() );

		HBox hbox = new HBox( 5, newTPButton, newWaitButton, deleteButton, slider );

		scene.setRoot( new VBox( hbox, timePointItemTableView, cylinder ) );
		scene.getStylesheets().add("/spim/ui/view/component/default.css");

		stage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
