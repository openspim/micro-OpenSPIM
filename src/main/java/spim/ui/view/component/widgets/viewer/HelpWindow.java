package spim.ui.view.component.widgets.viewer;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class HelpWindow extends Application {

	private Scene scene;

	public void show(HelpType type) {
		this.show(null, type);
	}

	public void show(Stage stage, HelpType type) {
		if(stage == null)
			stage = new Stage();

		stage.setTitle("Help View");
		scene = new Scene(new HelpViewer(type), 750, 500, Color.web("#666970"));

		Stage finalStage = stage;
		scene.addEventFilter(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			public void handle(KeyEvent ke) {
				if (ke.getCode() == KeyCode.ESCAPE) {
					finalStage.close();
					ke.consume(); // <-- stops passing the event to next node
				}
			}
		});

		stage.setScene(scene);
		// apply CSS style
//		scene.getStylesheets().add("webviewsample/BrowserToolbar.css");
		// show stage
		stage.show();
	}

	@Override
	public void start(Stage stage) {
		// ACQUISITION, ZSTACK, POSITION, IMAGING, SAVEIMAGE, CHANNEL, TIMEPOINT;
		// create scene
		show(stage, HelpType.TIMEPOINT);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
