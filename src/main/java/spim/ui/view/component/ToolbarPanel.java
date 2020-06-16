package spim.ui.view.component;

import ij.IJ;
import ij.ImageJ;
import ij.gui.Roi;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
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
	final ObjectProperty<Studio> studioProperty;

	public ToolbarPanel( Studio mmStudio, ObjectProperty< Studio > mmStudioObjectProperty )
	{
		super(new VBox());
		this.studioProperty = new SimpleObjectProperty<>( mmStudio );
		getDockTitleBar().setVisible(false);

		setTitle("OpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		Image logoImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "logo.png" ),
				160, 100, true, true );
		ImageView iv = new ImageView(logoImg);

		Label liveDemoLabel = new Label( "LIVE DEMO" );
		liveDemoLabel.setStyle( "-fx-font: 18 arial; -fx-background-color: #0faff0" );

		VBox topHbox = new VBox(10);
		topHbox.setAlignment( Pos.CENTER );
		topHbox.getChildren().addAll( iv, liveDemoLabel );

		studioProperty.addListener( new ChangeListener< Studio >()
		{
			@Override public void changed( ObservableValue< ? extends Studio > observable, Studio oldValue, Studio newValue )
			{
				if(newValue != null) topHbox.getChildren().remove( liveDemoLabel );
				else topHbox.getChildren().add( liveDemoLabel );
			}
		} );

		gridpane.addRow( 1, topHbox );

		Button ijButton = new Button( "Open Fiji" );
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

		gridpane.addRow( 2, new HBox(3, ijButton, mmButton) );

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
		this.studioProperty.set(studio);
	}
}