package spim.ui.view.component;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.micromanager.Studio;

import spim.hardware.SPIMSetup;
import spim.ui.view.component.util.SequentialWebEngineLoader;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: August 2021
 */
public abstract class Editor extends BorderPane implements SPIMSetupInjectable {
	WebView editorView;

	protected Studio studio;
	protected SPIMSetup setup;

	protected Editor( SPIMSetup setup, Studio studio ) {
		this.setup = setup;
		this.studio = studio;

		editorView = new WebView();
		editorView.setMinHeight( 180 );

		setCenter( editorView );

		sceneProperty().addListener( new ChangeListener<Scene>()
		{
			@Override public void changed(ObservableValue< ? extends Scene > observable, Scene oldValue, Scene newValue )
			{
				if(newValue != null) {
					initialize();
					sceneProperty().removeListener( this );
				}
			}
		} );
	}

	protected abstract void initializeHTML();
	protected abstract String getEditorHtml();

	private void initialize() {
		// We need JavaScript support
		editorView.getEngine().setJavaScriptEnabled(true);

		// The build in ACE context menu does not work because
		// JavaScript Clipboard interaction is disabled by security.
		// We have to do this by ourselfs.
		editorView.setContextMenuEnabled(false);

		SequentialWebEngineLoader.load(editorView.getEngine(), getClass().getResource( getEditorHtml() ).toExternalForm(), (observable, oldValue, newValue) -> {
			if (newValue == Worker.State.SUCCEEDED) {
				initializeHTML();
			}
		});

		// Copy & Paste Clipboard support
		final KeyCombination theCombinationCopy = new KeyCodeCombination( KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		final KeyCombination theCombinationCut = new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN);
		final KeyCombination theCombinationPaste = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
		final KeyCombination theCombinationRun = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);

		editorView.addEventFilter( KeyEvent.KEY_PRESSED, aEvent -> {
			if (theCombinationCopy.match(aEvent)) {
				onCopy();
			}
			if (theCombinationCut.match(aEvent)) {
				onCut();
			}
			if (theCombinationPaste.match(aEvent)) {
				onPaste();
			}
			if (theCombinationRun.match(aEvent)) {
				onOk();
			}
		});
	}

	protected void onCopy() {

		// Get the selected content from the editor
		// We to a Java2JavaScript downcall here
		// For details, take a look at the function declaration in editor.html
		String theContentAsText = (String) editorView.getEngine().executeScript("copyselection()");

		// And put it to the clipboard
		Clipboard theClipboard = Clipboard.getSystemClipboard();
		ClipboardContent theContent = new ClipboardContent();
		theContent.putString(theContentAsText);
		theClipboard.setContent(theContent);
	}

	protected void onCut() {

		String theContentAsText = (String) editorView.getEngine().executeScript("cutselection()");

		// And put it to the clipboard
		Clipboard theClipboard = Clipboard.getSystemClipboard();
		ClipboardContent theContent = new ClipboardContent();
		theContent.putString(theContentAsText);
		theClipboard.setContent(theContent);
	}


	protected void onPaste() {

		// Get the content from the clipboard
		Clipboard theClipboard = Clipboard.getSystemClipboard();
		String theContent = (String) theClipboard.getContent( DataFormat.PLAIN_TEXT );
		if (theContent != null) {
			// And put it in the editor
			// We do a Java2JavaScript downcall here
			// For details, take a look at the function declaration in editor.html
			JSObject theWindow = (JSObject) editorView.getEngine().executeScript("window");
			theWindow.call("pastevalue", theContent);
		}
	}

	protected void setTextContent(String content) {
		JSObject theWindow = (JSObject) editorView.getEngine().executeScript("window");
		theWindow.call("setContent", content);
	}

	protected void onOk() {
		// We need to sace the edited script to the game model.
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");
		System.out.println( theContent );
	}
}
