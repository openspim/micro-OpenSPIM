package spim.ui.view.component.widgets.viewer;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.html.HTMLAnchorElement;

import java.net.URI;

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

		webEngine.getLoadWorker().stateProperty().addListener(new HyperLinkRedirectListener(browser));

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

	public class HyperLinkRedirectListener implements ChangeListener<Worker.State>, EventListener
	{
		private static final String CLICK_EVENT = "click";
		private static final String ANCHOR_TAG = "a";

		private final WebView webView;

		public HyperLinkRedirectListener(WebView webView)
		{
			this.webView = webView;
		}

		@Override
		public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue)
		{
			if (Worker.State.SUCCEEDED.equals(newValue))
			{
				Document document = webView.getEngine().getDocument();
				NodeList anchors = document.getElementsByTagName(ANCHOR_TAG);
				for (int i = 0; i < anchors.getLength(); i++)
				{
					Node node = anchors.item(i);
					EventTarget eventTarget = (EventTarget) node;
					eventTarget.addEventListener(CLICK_EVENT, this, false);
				}
			}
		}

		@Override
		public void handleEvent(Event event)
		{
			HTMLAnchorElement anchorElement = (HTMLAnchorElement) event.getCurrentTarget();
			String href = anchorElement.getHref();

			if (java.awt.Desktop.isDesktopSupported())
			{
				openLinkInSystemBrowser(href);
			} else
			{
				// LOGGER.warn("OS does not support desktop operations like browsing. Cannot open link '{}'.", href);
			}

			event.preventDefault();
		}

		private void openLinkInSystemBrowser(String url)
		{
			// LOGGER.debug("Opening link '{}' in default system browser.", url);

			try
			{
				URI uri = new URI(url);
				java.awt.Desktop.getDesktop().browse(uri);
			} catch (Throwable e)
			{
				// LOGGER.error("Error on opening link '{}' in system browser.", url);
			}
		}
	}

	public static Button createHelpButton() {
		Button button = new Button("?");
		button.setStyle("-fx-background-radius: 5em; " +
				"-fx-font-size: 10px;" +
				"-fx-min-width: 20px; " +
				"-fx-min-height: 20px; " +
				"-fx-max-width: 20px; " +
				"-fx-max-height: 20px; " +
				"-fx-font-weight: bold; " +
				"-fx-text-fill: white; " +
				"-fx-base: #3e8cd6;");
		return button;
	}
}
