package spim.ui.view.component.demo;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class ProgressComponents extends Application
{
	@Override
	public void start( Stage stage) {
		Group root = new Group();
		Scene scene = new Scene(root);
		stage.setScene(scene);
		stage.setTitle("Progress Controls");

		final Slider slider = new Slider();
		slider.setMin(-1);
		slider.setMax(50);

		final ProgressBar pb = new ProgressBar(-1);
		final ProgressIndicator pi = new ProgressIndicator(-1);

		slider.valueProperty().addListener(new ChangeListener<Number>() {
			public void changed( ObservableValue<? extends Number> ov,
					Number old_val, Number new_val) {
				pb.setProgress(new_val.doubleValue()/50);
				pi.setProgress(new_val.doubleValue()/50);
			}
		});

		final HBox hb = new HBox();
		hb.setSpacing(5);
		hb.setAlignment( Pos.CENTER );
		hb.getChildren().addAll(slider, pb, pi);
		scene.setRoot(hb);
		stage.show();
	}
	public static void main(String[] args) {
		launch(args);
	}
}
