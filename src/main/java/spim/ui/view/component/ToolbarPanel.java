package spim.ui.view.component;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.dockfx.DockNode;
import org.micromanager.Studio;

import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.events.GUIRefreshEvent;
import spim.hardware.SPIMSetup;
import spim.io.*;
import spim.mm.MMUtils;
import spim.mm.MicroManager;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;


/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode implements SPIMSetupInjectable
{
	final ObjectProperty<Studio> studioProperty;
	final ObjectProperty<SPIMSetup> spimSetupObjectProperty;

	final VBox topHbox;
	final VBox liveViewHbox;
	final HBox buttonHbox;

	final Label liveDemoLabel;
	final Button mmButton;
//	final Button liveViewButton;
	final Button openDatasetButton;

	final SimpleDoubleProperty waitSeconds;

	final Text pixelSizeValue;
	final Text rotatorStepSizeValue;
	final Text zStageStepSizeValue;

	public ToolbarPanel( Studio mmStudio, ObjectProperty< Studio > mmStudioObjectProperty, ObjectProperty<GUIRefreshEvent> refreshEventProperty )
	{
		super(new VBox());
		this.studioProperty = new SimpleObjectProperty<>( mmStudio );
		this.spimSetupObjectProperty = new SimpleObjectProperty<>();
		this.waitSeconds =  new SimpleDoubleProperty(-1);

		getDockTitleBar().setVisible(false);

		setTitle("µOpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		Image logoImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "uOS-logo.png" ) );
		ImageView iv = new ImageView(logoImg);
		iv.setPreserveRatio(true);
		iv.setFitWidth(160);

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

		mmButton = new Button( "µManager");
		mmButton.setStyle("-fx-font: 18 arial; -fx-base: #e7e45d;");
		mmButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(null == MicroManager.getInstance()) {
					Stage stage = new Stage();
					MMUtils.resetCancelled();

					if (!MMUtils.isSystemLibrairiesLoaded())
					{
						// load micro manager libraries
						if (!MMUtils.fixSystemLibrairies( stage ))
							return;
					}

					MicroManager.init( stage, mmStudioObjectProperty, refreshEventProperty );

					while(MMUtils.invalidMMPath() && !MMUtils.cancelled())
					{
						if (!MMUtils.isSystemLibrairiesLoaded())
						{
							// load micro manager libraries
							if (!MMUtils.fixSystemLibrairies( stage ))
								return;
						}

						MicroManager.init( stage, mmStudioObjectProperty, refreshEventProperty );
					}
				} else {
					MicroManager.getInstance().show();
				}
			}
		} );

		buttonHbox = new HBox(3, mmButton);
		buttonHbox.setAlignment( Pos.CENTER );
		gridpane.addRow( 2, buttonHbox );


//		SimpleBooleanProperty liveOn = new SimpleBooleanProperty( false );
//		liveViewButton = new Button( "LiveView");
//		liveViewButton.setMinSize( 100, 40 );
//		liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
//		liveViewButton.setOnAction( new EventHandler< ActionEvent >()
//		{
//			@Override public void handle( ActionEvent event )
//			{
//				if(studioProperty.get() == null) {
//					new Alert( Alert.AlertType.WARNING, "MM2 config is not loaded.").show();
//					return;
//				}
//
//				liveOn.set( !liveOn.get() );
//				if(liveOn.get())
//				{
//					liveViewButton.setText( "Stop LiveView" );
//					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #e77d8c;");
//					if(studioProperty.get() != null)
//						studioProperty.get().live().setLiveMode( true );
//				} else {
//					liveViewButton.setText( "LiveView" );
//					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
//					if(studioProperty.get() != null)
//						studioProperty.get().live().setLiveMode( false );
//				}
//			}
//		} );

		openDatasetButton = new Button("Open Dataset");
		openDatasetButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				try {
					loadData();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		liveViewHbox = new VBox(3);
		liveViewHbox.setAlignment( Pos.CENTER );

		gridpane.addRow( 3, liveViewHbox );

		VBox timerBox = new VBox(3);
		timerBox.setAlignment( Pos.CENTER );

		final Label timerLabel = new Label();
		timerLabel.setStyle( "-fx-font: 16 arial;" );
		final ProgressBar pb = new ProgressBar(-1);

		waitSeconds.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				Platform.runLater(() -> {
					if (newValue.doubleValue() > -1) {
						if(timerBox.getChildren().size() == 0) {
							timerBox.getChildren().addAll(timerLabel, pb);
						}
						timerLabel.setText(String.format("Acquisition in:\n\t%.0f sec", newValue.doubleValue()));
					} else {
						timerBox.getChildren().clear();
					}
				});
			}
		});

		gridpane.addRow( 4, timerBox );

		Supplier<Text> nl = () -> new Text("\n");

		Font helveticaBold = Font.font("Helvetica", FontWeight.BOLD, 12);
		Text pixelSizeLabel = new Text("Pixel Size μm: ");
		pixelSizeLabel.setFont(helveticaBold);
		pixelSizeValue = new Text("N.A.");
		pixelSizeValue.setFont(Font.font("Helvetica", 12));

		Text rotatorStepSizeLabel = new Text("Rotator Step Size μm: ");
		rotatorStepSizeLabel.setFont(helveticaBold);
		rotatorStepSizeValue = new Text("N.A.");
		rotatorStepSizeValue.setFont(Font.font("Helvetica", 12));

		Text zStageStepSizeLabel = new Text("Z-Stage Step Size μm: ");
		zStageStepSizeLabel.setFont(helveticaBold);
		zStageStepSizeValue = new Text("N.A.");
		zStageStepSizeValue.setFont(Font.font("Helvetica", 12));

		refreshEventProperty.addListener((observable, oldValue, newValue) -> {
			if(studioProperty.get() != null) {
				pixelSizeValue.setText(studioProperty.get().core().getPixelSizeUm() + "");
				rotatorStepSizeValue.setText(spimSetupObjectProperty.get().getThetaStage().getStepSize() + "");
				zStageStepSizeValue.setText(spimSetupObjectProperty.get().getZStage().getStepSize() + "");
			}
		});

		TextFlow textFlow = new TextFlow(pixelSizeLabel, pixelSizeValue, nl.get(),
				rotatorStepSizeLabel, rotatorStepSizeValue, nl.get(),
				zStageStepSizeLabel, zStageStepSizeValue, nl.get());

		gridpane.addRow( 5, textFlow );

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

	public void loadData() throws IOException {

		DirectoryChooser d = new DirectoryChooser();
		File f = d.showDialog(null);

		if(f == null) return;

		File[] list = f.listFiles((file, s) -> s.endsWith("_metadata.txt"));

		if (list.length == 0) return;

		StorageType storageType = null;
		String prefix = "";

		if (list.length == 1) {
			prefix = list[0].getName().replaceFirst("_metadata.txt", "");
			String directory = f.getAbsolutePath();
			storageType = StorageOpener.checkStorageType(directory, prefix);
		} else {
			// List up all the prefix candidates and let the user choose one
		}

		String directory = f.getAbsolutePath();
		DefaultDatastore result = new DefaultDatastore(studioProperty.get());

		switch (storageType) {
			case SinglePlaneTiff: result.setStorage(new OpenSPIMSinglePlaneTiffSeries(result, directory, prefix, false));
			break;
			case OMETiff: result.setStorage(new OMETIFFStorage(result, directory, prefix, false));
			break;
			case N5: result.setStorage(new N5MicroManagerStorage(result, directory, prefix, 1, false));
			break;
		}

		result.setSavePath(directory);
		result.freeze();

		studioProperty.get().displays().manage(result);
		studioProperty.get().displays().loadDisplays(result);
	}

	public SimpleDoubleProperty waitSecondsProperty() {
		return waitSeconds;
	}

	@Override public void setSetup(SPIMSetup setup, Studio studio ) {
		this.studioProperty.set(studio);
		this.spimSetupObjectProperty.set(setup);

//		java.awt.Rectangle roi;
		if(null != studio) {
			topHbox.getChildren().remove( liveDemoLabel );
			buttonHbox.getChildren().remove( mmButton );
//			liveViewHbox.getChildren().add( 0, liveViewButton);
			liveViewHbox.getChildren().add( openDatasetButton );
			pixelSizeValue.setText(studio.core().getPixelSizeUm() + "");
			rotatorStepSizeValue.setText(setup.getThetaStage().getStepSize() + "");
			zStageStepSizeValue.setText(setup.getZStage().getStepSize() + "");
//			roi = new java.awt.Rectangle(0, 0, 0, 0);
		} else {
			topHbox.getChildren().add( liveDemoLabel );
			buttonHbox.getChildren().add( mmButton );
//			liveViewHbox.getChildren().remove( liveViewButton );
			liveViewHbox.getChildren().remove( openDatasetButton );
			pixelSizeValue.setText("N.A.");
			rotatorStepSizeValue.setText("N.A.");
			zStageStepSizeValue.setText("N.A.");
//			roi = new java.awt.Rectangle( 0, 0, 0, 0 );
		}
	}
}