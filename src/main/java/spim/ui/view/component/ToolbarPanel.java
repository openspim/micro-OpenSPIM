package spim.ui.view.component;

import ij.gui.Roi;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mmcorej.CMMCore;
import org.dockfx.DockNode;
import org.micromanager.Studio;
import spim.hardware.Camera;
import spim.hardware.SPIMSetup;
import spim.mm.MMUtils;
import spim.mm.MicroManager;


/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode implements SPIMSetupInjectable
{
	final ObjectProperty<Studio> studioProperty;
	final ObjectProperty<SPIMSetup> spimSetupObjectProperty;

	final ObjectProperty roiRectangle;

	final ObservableList<String> binningOptions =
			FXCollections.observableArrayList();

	final VBox topHbox;
	final VBox liveViewHbox;
	final HBox buttonHbox;

	final Label liveDemoLabel;
	final Button mmButton;
	final Button liveViewButton;

	final Label roiXYLabel;
	final Label roiWLabel;
	final Label roiHLabel;

	final ComboBox binningComboBox;

	public ToolbarPanel( Studio mmStudio, ObjectProperty< Studio > mmStudioObjectProperty )
	{
		super(new VBox());
		this.studioProperty = new SimpleObjectProperty<>( mmStudio );
		this.spimSetupObjectProperty = new SimpleObjectProperty<>();

		getDockTitleBar().setVisible(false);

		setTitle("OpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		Image logoImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "logo.png" ),
				160, 100, true, true );
		ImageView iv = new ImageView(logoImg);

		liveDemoLabel = new Label( "LIVE DEMO" );
		liveDemoLabel.setStyle( "-fx-font: 18 arial; -fx-background-color: #0faff0" );

		topHbox = new VBox(10);
		topHbox.setAlignment( Pos.CENTER );
		topHbox.getChildren().addAll( iv, liveDemoLabel );

		gridpane.addRow( 1, topHbox );

//		Button ijButton = new Button( "Open Fiji" );
//		ijButton.setOnAction( new EventHandler< ActionEvent >()
//		{
//			@Override public void handle( ActionEvent event )
//			{
//				if(null == IJ.getInstance() ) {
//					ij.ImageJ ij = new ImageJ( );
//					ij.show();
//				} else {
//					IJ.getInstance().show();
//				}
//			}
//		} );

		mmButton = new Button( "START");
		mmButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
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

		buttonHbox = new HBox(3, mmButton);
		buttonHbox.setAlignment( Pos.CENTER );
		gridpane.addRow( 2, buttonHbox );


		SimpleBooleanProperty liveOn = new SimpleBooleanProperty( false );
		liveViewButton = new Button( "LiveView");
		liveViewButton.setMinSize( 100, 40 );
		liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
		liveViewButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(studioProperty.get() == null) {
					new Alert( Alert.AlertType.WARNING, "MM2 config is not loaded.").show();
					return;
				}

				liveOn.set( !liveOn.get() );
				if(liveOn.get())
				{
					liveViewButton.setText( "Stop LiveView" );
					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #e77d8c;");
					if(studioProperty.get() != null)
						studioProperty.get().live().setLiveMode( true );
				} else {
					liveViewButton.setText( "LiveView" );
					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
					if(studioProperty.get() != null)
						studioProperty.get().live().setLiveMode( false );
				}
			}
		} );

		liveViewHbox = new VBox(3);
		liveViewHbox.setAlignment( Pos.CENTER );

		// Region Of Interest
		roiRectangle = new SimpleObjectProperty();

		java.awt.Rectangle roi = new java.awt.Rectangle( 0, 0, 0, 0 );

		roiXYLabel = new Label(String.format( "X=%d, Y=%d", roi.x, roi.y ) );
		roiWLabel = new Label(String.format( "Width=%d", roi.width ));
		roiHLabel = new Label(String.format( "Height=%d", roi.height ));

		roiRectangle.addListener( new ChangeListener()
		{
			@Override public void changed( ObservableValue observable, Object oldValue, Object newValue )
			{
				java.awt.Rectangle roi;
				if(null != newValue) {
					roi = ( java.awt.Rectangle ) newValue;
				} else {
					roi = new java.awt.Rectangle( 0, 0, 0, 0 );
				}

				roiXYLabel.setText( String.format( "X=%d, Y=%d", roi.x, roi.y ) );
				roiWLabel.setText( String.format( "Width=%d", roi.width ) );
				roiHLabel.setText( String.format( "Height=%d", roi.height ) );
			}
		} );

		Button setRoiButton = new Button( "Set ROI" );
		setRoiButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				Studio studio = studioProperty.get();
				if( studio != null && studio.live() != null && studio.live().getDisplay() != null && studio.live().getDisplay().getImagePlus() != null && studio.live().getDisplay().getImagePlus().getRoi() != null) {
					Roi ipRoi = studio.live().getDisplay().getImagePlus().getRoi();
					roiRectangle.setValue( ipRoi.getBounds() );
				}
			}
		} );

		Button clearRoiButton = new Button("Reset");
		clearRoiButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				Studio studio = studioProperty.get();
				try
				{
					if( studio != null && studio.core() != null)
					{
						studio.core().clearROI();
						roiRectangle.setValue( new java.awt.Rectangle(0, 0, (int) studio.core().getImageWidth(), (int) studio.core().getImageHeight()) );
						if( studio.live() != null && studio.live().getDisplay() != null && studio.live().getDisplay().getImagePlus() != null && studio.live().getDisplay().getImagePlus().getRoi() != null) {
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

		VBox roiInfo = new VBox(3);
		roiInfo.setStyle("-fx-border-color: gray");
		roiInfo.getChildren().addAll( roiXYLabel, roiWLabel, roiHLabel );
		roiInfo.setPadding( new Insets(3, 3, 3, 3) );

		TitledPane roiPane = new TitledPane( "ROI Setting", new HBox( 3, roiInfo, new VBox( 3, setRoiButton, clearRoiButton ) ) );
//		roiPane.setExpanded( false );
		roiPane.setMinWidth(200);

		liveViewHbox.getChildren().add( roiPane );

		// Setup binning
		binningComboBox = new ComboBox( binningOptions );
		binningComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener() {
			@Override
			public void changed(ObservableValue observable, Object oldValue, Object newValue) {
				if(newValue != null)
					binningItemChanged(newValue.toString());
			}
		});

		HBox binningHBox = new HBox(3, new Label("Binning: "), binningComboBox);
		binningHBox.setAlignment( Pos.CENTER_LEFT );
		binningHBox.setPadding(new Insets(5));
		liveViewHbox.getChildren().add( binningHBox );

		gridpane.addRow( 3, liveViewHbox );

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

	void binningItemChanged(String item) {
//		System.out.println(item);

		if (studioProperty.get() != null) {
			Studio studio = studioProperty.get();
			CMMCore core = studio.core();

			SPIMSetup spimSetup = spimSetupObjectProperty.get();
			String currentCamera = core.getCameraDevice();

			if(currentCamera.startsWith("Multi")) {
				if(spimSetup.getCamera1() != null) {
					spimSetup.getCamera1().setBinning(item);
				}

				if(spimSetup.getCamera2() != null) {
					spimSetup.getCamera2().setBinning(item);
				}
			} else {
				if(spimSetup.getCamera1() != null && currentCamera.equals(spimSetup.getCamera1().getLabel()))
					spimSetup.getCamera1().setBinning(item);

				if(spimSetup.getCamera2() != null && currentCamera.equals(spimSetup.getCamera2().getLabel()))
					spimSetup.getCamera2().setBinning(item);
			}
		}
	}

	public ObjectProperty roiRectangleProperty() {
		return roiRectangle;
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio ) {
		this.studioProperty.set(studio);
		this.spimSetupObjectProperty.set(setup);

		java.awt.Rectangle roi;
		if(null != studio) {
			topHbox.getChildren().remove( liveDemoLabel );
			buttonHbox.getChildren().remove( mmButton );
			liveViewHbox.getChildren().add( 0, liveViewButton );
			roi = new java.awt.Rectangle(0, 0, (int) studio.core().getImageWidth(), (int) studio.core().getImageHeight());
		} else {
			topHbox.getChildren().add( liveDemoLabel );
			buttonHbox.getChildren().add( mmButton );
			liveViewHbox.getChildren().remove( liveViewButton );
			roi = new java.awt.Rectangle( 0, 0, 0, 0 );
		}

		roiXYLabel.setText( String.format( "X=%d, Y=%d", roi.x, roi.y ) );
		roiWLabel.setText( String.format( "Width=%d", roi.width ) );
		roiHLabel.setText( String.format( "Height=%d", roi.height ) );

		if(null != setup) {
			try {
				Camera camera = setup.getCamera1();
				camera.getAvailableBinningValues().forEach(binningOptions::add);
				binningComboBox.getSelectionModel().select(camera.getBinningAsString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			binningOptions.clear();
		}
	}
}