package spim.ui.view.component;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.micromanager.Studio;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import spim.plugin.compile.PluginRuntime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class JavaEditor extends BorderPane
{
	WebView editorView;

	private Stage modalStage;
	final private Studio studio;

	protected Class plugin;

	public JavaEditor(Stage stage, Studio studio) {

		this.studio = studio;

		editorView = new WebView();
		editorView.setMinHeight( 180 );

		Button okBtn = new Button( "Run" );
		okBtn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				onOk();
			}
		} );

		setCenter( editorView );
		setBottom( new HBox( okBtn )  );

		sceneProperty().addListener( new ChangeListener< Scene >()
		{
			@Override public void changed( ObservableValue< ? extends Scene > observable, Scene oldValue, Scene newValue )
			{
				if(newValue != null) {
					initialize( stage );
					observable.removeListener( this );
				}
			}
		} );
	}

	public void initialize(Stage aModalStage) {
		modalStage = aModalStage;

		// We need JavaScript support
		editorView.getEngine().setJavaScriptEnabled(true);
		editorView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue == Worker.State.SUCCEEDED) {
				initializeHTML();
			}
		});
		// The build in ACE context menu does not work because
		// JavaScript Clipboard interaction is disabled by security.
		// We have to do this by ourselfs.
		editorView.setContextMenuEnabled(false);

		// Load the bootstrap html
		// It will trigger the initializeHTML() method by the above registered state change listener
		// after the everything was loaded
		editorView.getEngine().load( getClass().getResource("ace/editor.html").toExternalForm());

		// Copy & Paste Clipboard support
		final KeyCombination theCombinationCopy = new KeyCodeCombination( KeyCode.C, KeyCombination.CONTROL_DOWN);
		final KeyCombination theCombinationPaste = new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN);

		aModalStage.getScene().addEventFilter( KeyEvent.KEY_PRESSED, aEvent -> {
			if (theCombinationCopy.match(aEvent)) {
				onCopy();
			}
			if (theCombinationPaste.match(aEvent)) {
				onPaste();
			}
		});
	}

	private void onCopy() {

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

	private void onPaste() {

		// Get the content from the clipboard
		Clipboard theClipboard = Clipboard.getSystemClipboard();
		String theContent = (String) theClipboard.getContent( DataFormat.PLAIN_TEXT);
		if (theContent != null) {
			// And put it in the editor
			// We do a Java2JavaScript downcall here
			// For details, take a look at the function declaration in editor.html
			JSObject theWindow = (JSObject) editorView.getEngine().executeScript("window");
			theWindow.call("pastevalue", theContent);
		}
	}

	private void initializeHTML() {
		// Initialize the editor
		// and fill it with the LUA script taken from our editing action
		Document theDocument = editorView.getEngine().getDocument();
		Element theEditorElement = theDocument.getElementById("editor");

//		theEditorElement.setTextContent(action.scriptProperty().get().script);
		theEditorElement.setTextContent("import org.micromanager.data.Coords;\n"
				+ "import org.micromanager.data.Image;\n"
				+ "import org.micromanager.data.Datastore;\n"
				+ "import org.micromanager.display.DisplayWindow;\n"
				+ "import org.micromanager.Studio;\n"
				+ "import mmcorej.TaggedImage;\n"
				+ "import mmcorej.CMMCore;\n"
				+ "import javax.swing.SwingUtilities;\n"
				+ "\n"
				+ "public class Script {\n"
				+ "        public static void main(String[] args, Studio mm)\n"
				+ "        {\n"
				+ "            SwingUtilities.invokeLater(() -> {\n"
				+ "            try {\n"
				+ "                System.out.println(\"Test\");\n"
				+ "                Datastore store = mm.data().createRAMDatastore();\n"
				+ "                DisplayWindow display = mm.displays().createDisplay(store);\n"
				+ "                mm.core().snapImage();\n"
				+ "                TaggedImage tmp = mm.core().getTaggedImage();\n"
				+ "                Image image1 = mm.data().convertTaggedImage(tmp);\n"
				+ "                image1 = image1.copyAtCoords(image1.getCoords().copy().channel(0).build());\n"
				+ "                mm.core().snapImage();\n"
				+ "                tmp = mm.core().getTaggedImage();\n"
				+ "                Image image2 = mm.data().convertTaggedImage(tmp);\n"
				+ "                image2 = image2.copyAtCoords(image1.getCoords().copy().channel(1).build());\n"
				+ "                store.putImage(image1);\n"
				+ "                store.putImage(image2);\n"
				+ "                //store.save(display.getAsWindow());\n"
				+ "            } catch(Exception e)\n"
				+ "            {\n"
				+ "                e.printStackTrace();\n"
				+ "            }\n"
				+ "            });\n"
				+ "        }\n"
				+ "}\n");

		editorView.getEngine().executeScript("initeditor()");
	}

	private void compileRun( String code ) {
		if(plugin != null)
		{
			//unload();
		}

		PluginRuntime runtime = new PluginRuntime();

		if(code.trim().isEmpty()) {
			System.out.println("No code is provided.");
			return;
		}

		// Remove package declaration
		Pattern pkg = Pattern.compile("[\\s]*package (.*?);");
		Matcher pkgMatcher = pkg.matcher(code);
		boolean isPkg = pkgMatcher.find();
		String pkgName = "";

		if(isPkg)
			pkgName = pkgMatcher.group(1);

		// Find a plugin class name
		Pattern pattern = Pattern.compile("[\\s]*public class (.*?) ");
		Matcher m = pattern.matcher(code);

		m.find();
		String className = m.group(1);

		if(isPkg)
			className = pkgName + "." + className;

		if(runtime.compile(className, code))
		{
			try {
				plugin = runtime.instanciate(className, code);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			} catch (IllegalAccessException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			} catch (InstantiationException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}

			if(null != plugin)
				load(plugin);
		}


//		try {
//
//			// We only want to test on a clone
			//// so the test does not change enything
//			GameScene theClone = persistenceManager.cloneSceneForPreview(gameScene);
//
//			// Execute a single run for verification
//			GameObject theObject = new GameObject(theClone, "dummy");
//			GameObjectInstance theInstance = theClone.createFrom(theObject);
//			theEngine = theClone.getRuntime().getScriptEngineFactory().createNewEngine(theClone, aScript);
//			theEngine.registerObject("instance", theInstance);
//			theEngine.registerObject("scene", theClone);
//			theEngine.registerObject("game", theClone.getGame());
//
//			Object theResult = theEngine.proceedGame(100, 16);
//			if (theResult == null) {
//				throw new RuntimeException("Got NULL as a response, expected " + GameProcess.ProceedResult.STOPPED+" or " + GameProcess.ProceedResult.CONTINUE_RUNNING);
//			}
//
//			GameProcess.ProceedResult theResultAsEnum = GameProcess.ProceedResult.valueOf(theResult.toString());
//
//			theEngine.shutdown();
//
//			System.err.println("Got response : " + "blah~");
//
//			return true;
//		} catch (Exception e) {
//
//			StringWriter theWriter = new StringWriter();
//			e.printStackTrace(new PrintWriter(theWriter));
//
//			System.err.println("Exception : " + theWriter);
//		} finally {
//			if (theEngine != null) {
//				theEngine.shutdown();
//			}
//		}
//		return false;
	}

	protected void load(Class clazz)
	{
		Method method = null;
		try {
			method = clazz.getMethod("main", String[].class, Studio.class);
			method.invoke(null, new String[1], studio);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch ( InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public void onOk() {
		// We need to sace the edited script to the game model.
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");
//		Script theNewScript = new Script(theContent);
//
//		action.scriptProperty().set(theNewScript);
//		modalStage.close();
		compileRun( theContent );
	}

	public void onTest() {
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");
//		Script theNewScript = new Script(theContent);
//		test(theContent);
	}

	public void performEditing() {
		modalStage.show();
	}
}