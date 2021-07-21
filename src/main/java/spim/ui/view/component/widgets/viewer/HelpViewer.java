package spim.ui.view.component.widgets.viewer;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class HelpViewer extends Region {
	final WebView browser = new WebView();
	final WebEngine webEngine = browser.getEngine();
	private HBox toolBar;

	public HelpViewer(HelpType type) {
		//apply the styles
		getStyleClass().add("browser");
		// load the web page
//		webEngine.load("http://www.oracle.com/products/index.html");
//		editorView.getEngine().load( getClass().getResource("ace/editor.html").toExternalForm());
		webEngine.load(type.getHtml());

		Button closeBtn = new Button("Close");
		closeBtn.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				Stage stage = (Stage) getScene().getWindow();
				// do what you have to do
				stage.close();
			}
		});
		toolBar = new HBox();
		toolBar.getChildren().addAll(closeBtn);

		//add the web view to the scene
		getChildren().add(toolBar);
		getChildren().add(browser);
	}
	private Node createSpacer() {
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		return spacer;
	}

	@Override protected void layoutChildren() {
		double w = getWidth();
		double h = getHeight();
		double tbHeight = toolBar.prefHeight(w);
		layoutInArea(browser,0,0,w,h-tbHeight,0, HPos.CENTER, VPos.CENTER);
		layoutInArea(toolBar,0,h-tbHeight,w,tbHeight,0,HPos.CENTER,VPos.CENTER);
	}

	@Override protected double computePrefWidth(double height) {
		return 750;
	}

	@Override protected double computePrefHeight(double width) {
		return 500;
	}
}
