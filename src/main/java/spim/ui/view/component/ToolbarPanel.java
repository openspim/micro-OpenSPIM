package spim.ui.view.component;

import ij.gui.Roi;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.dockfx.DockNode;
import org.micromanager.Studio;

import java.awt.Rectangle;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode
{

	public ToolbarPanel( Studio studio, ObjectProperty roiRectangle )
	{
		super(new VBox());
		getDockTitleBar().setVisible(false);

		setTitle("OpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		SimpleBooleanProperty liveOn = new SimpleBooleanProperty( false );
		Button liveViewButton = new Button( "LiveView Start");
		liveViewButton.setMinSize( 100, 40 );
		liveViewButton.setStyle("-fx-font: 12 arial; -fx-base: #49e7db;");
		liveViewButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				liveOn.set( !liveOn.get() );
				if(liveOn.get())
				{
					liveViewButton.setText( "LiveView Stop" );
					if(studio != null)
						studio.live().setLiveMode( true );
				} else {
					liveViewButton.setText( "LiveView Start" );
					if(studio != null)
						studio.live().setLiveMode( false );
				}
			}
		} );

		gridpane.addRow( 1, liveViewButton );

		java.awt.Rectangle roi = null;
		try
		{
			if(studio != null && studio.core() != null)
				roi = studio.core().getROI();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		if(null == roi)
			roi = new Rectangle( 0, 0, 512, 512 );

		Label roiXYLabel = new Label(String.format( "X=%d, Y=%d", roi.x, roi.y ) );
		Label roiWLabel = new Label(String.format( "Width=%d", roi.width ));
		Label roiHLabel = new Label(String.format( "Height=%d", roi.height ));

		roiRectangle.addListener( new ChangeListener()
		{
			@Override public void changed( ObservableValue observable, Object oldValue, Object newValue )
			{
				if(null != newValue) {
					java.awt.Rectangle roi = ( java.awt.Rectangle ) newValue;
					roiXYLabel.setText( String.format( "X=%d, Y=%d", roi.x, roi.y ) );
					roiWLabel.setText( String.format( "Width=%d", roi.width ) );
					roiHLabel.setText( String.format( "Height=%d", roi.height ) );
				}
			}
		} );

		Button setRoiButton = new Button( "Set ROI" );
		setRoiButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(studio != null && studio.live() != null && studio.live().getDisplay() != null && studio.live().getDisplay().getImagePlus() != null && studio.live().getDisplay().getImagePlus().getRoi() != null) {
					Roi ipRoi = studio.live().getDisplay().getImagePlus().getRoi();
					roiRectangle.setValue( ipRoi.getBounds() );
				}
			}
		} );

		Button clearRoiButton = new Button("Clear");
		clearRoiButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				try
				{
					if(studio != null && studio.core() != null)
					{
						studio.core().clearROI();
						roiRectangle.setValue( studio.core().getROI() );
						if(studio.live() != null && studio.live().getDisplay() != null && studio.live().getDisplay().getImagePlus() != null && studio.live().getDisplay().getImagePlus().getRoi() != null) {
							studio.live().getDisplay().getImagePlus().deleteRoi();
						}
					}
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		} );

		gridpane.addRow( 2, new HBox(  ) );

		TitledPane titledPane = new TitledPane( "Region of Interest", new VBox( 3, roiXYLabel, roiWLabel, roiHLabel ) );
		titledPane.setCollapsible( false );

		gridpane.addRow( 3, titledPane );
		gridpane.addRow( 4, new HBox( 3, setRoiButton, clearRoiButton) );



		//		btn = new Button("Test Std Err");
//		btn.setOnAction(e -> {
//
//			for (int i = 0; i < 2000; i++)
//			{
//				System.err.println("" + i + " " + "Console Test");
//			}
//
//		});
//
//		box.getChildren().add(btn);
//
//		// Wavelength color check
//		btn = new Button("488");
//		btn.setStyle("-fx-background-color: #0FAFF0");
//		box.getChildren().add(btn);
	}
}