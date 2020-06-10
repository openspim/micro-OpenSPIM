package spim.ui.view.component;

import ij.IJ;
import ij.ImageJ;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.dockfx.DockNode;
import org.micromanager.Studio;
import spim.hardware.SPIMSetup;
import spim.mm.MMUtils;
import spim.mm.MicroManager;

import java.awt.Rectangle;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode implements SPIMSetupInjectable
{
	Studio studio;

	public ToolbarPanel( Studio mmStudio, ObjectProperty roiRectangle, ObjectProperty< Studio > mmStudioObjectProperty )
	{
		super(new VBox());
		this.studio = mmStudio;
		getDockTitleBar().setVisible(false);

		setTitle("OpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		Image logoImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "logo.png" ),
				160, 100, true, true );
		ImageView iv = new ImageView(logoImg);

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

		gridpane.addRow( 1, new HBox( iv ) );

		TitledPane titledPane = new TitledPane( "Region of Interest", new VBox( 3, roiXYLabel, roiWLabel, roiHLabel ) );
		titledPane.setCollapsible( false );

		gridpane.addRow( 2, titledPane );
		gridpane.addRow( 3, new HBox( 3, setRoiButton, clearRoiButton) );

		Button ijButton = new Button( "Open IJ" );
		ijButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(null == IJ.getInstance() ) {
					ij.ImageJ ij = new ImageJ( );
					ij.show();
				} else {
					IJ.getInstance().show();
				}
			}
		} );

		Button mmButton = new Button( "START");
		mmButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(null == MicroManager.getInstance()) {
					Stage stage = new Stage();
					if (!MMUtils.isSystemLibrairiesLoaded())
					{
						// load micro manager libraries
						if (!MMUtils.fixSystemLibrairies( stage ))
							return;
					}

					MicroManager.init( stage, mmStudioObjectProperty );
				} else {
					MicroManager.getInstance().show();
				}
			}
		} );

		gridpane.addRow( 4, new HBox(3, ijButton, mmButton) );

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

	@Override public void setSetup( SPIMSetup setup, Studio studio ) {
		this.studio = studio;
	}
}