package spim.ui.view.component;

import ij.ImageStack;
import ij.process.ImageProcessor;
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

import mmcorej.TaggedImage;
import netscape.javascript.JSObject;
import org.micromanager.Studio;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import spim.hardware.SPIMSetup;
import spim.model.event.ControlEvent;
import spim.plugin.compile.PluginRuntime;
import spim.ui.view.component.util.SequentialWebEngineLoader;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Description: Basic java editor for testing during run-time.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class JavaEditor extends BorderPane implements SPIMSetupInjectable
{
	WebView editorView;

	private Studio studio;
	private SPIMSetup setup;

	protected Class plugin;
	protected Method compiledMethod;

	static String scriptExample = "import org.micromanager.data.Coords;\n" +
			"import org.micromanager.data.Image;\n" +
			"import org.micromanager.data.Datastore;\n" +
			"import org.micromanager.display.DisplayWindow;\n" +
			"import org.micromanager.Studio;\n" +
			"import org.micromanager.internal.utils.imageanalysis.ImageUtils;\n" +
			"import mmcorej.TaggedImage;\n" +
			"import ij.IJ;\n" +
			"import ij.ImageJ;\n" +
			"import ij.process.ImageProcessor;\n" +
			"import ij.ImageStack;\n" +
			"import ij.ImagePlus;\n" +
			"import mmcorej.TaggedImage;\n" +
			"import mmcorej.CMMCore;\n" +
			"import spim.hardware.SPIMSetup;\n" +
			"import javax.swing.SwingUtilities;\n" +
			"import net.haesleinhuepf.clijx.CLIJx;\n" +
			"import mmcorej.org.json.JSONException;\n" +
			"\n" +
			"public class Script {\n" +
			"    static ImageStack stack1;\n" +
			"    static ImageStack stack2;\n" +
			"    public static void main(String[] args, SPIMSetup setup, Studio mm, mmcorej.TaggedImage tagged)\n" +
			"    {\n" +
			"        // Check clijx instance\n" +
			"        System.out.println(\"Test\");\n" +
			"        CLIJx clijx = CLIJx.getInstance();\n" +
			"        System.out.println(clijx.clinfo());\n" +
			"\n" +
			"        // On receiving TaggedImage during acquisition\n" +
			"        if(tagged != null) {\n" +
			"            try {\n" +
			"                // To check all the tags in the image, uncomment the below\n" +
			"                // System.out.println(tagged.tags.toString( 2 ));\n" +
			"                int slice = tagged.tags.getInt(\"SliceIndex\");\n" +
			"                double exp = tagged.tags.getInt( \"Exposure-ms\" );\n" +
			"                double zPos = tagged.tags.getDouble( \"ZPositionUm\" );\n" +
			"                double xPos = tagged.tags.getDouble( \"XPositionUm\" );\n" +
			"                double yPos = tagged.tags.getDouble( \"YPositionUm\" );\n" +
			"                int ch = tagged.tags.getInt( \"ChannelIndex\" );\n" +
			"                String cam = tagged.tags.getString( \"Camera\" );\n" +
			"                int slices = tagged.tags.getJSONObject( \"Summary\" ).getInt( \"Slices\" );\n" +
			"                int channels = tagged.tags.getJSONObject( \"Summary\" ).getInt( \"Channels\" );\n" +
			"                System.out.println( slice + \"/\" + slices);\n" +
			"\n" +
			"                ImageProcessor image = ImageUtils.makeProcessor(tagged);\n" +
			"                if(slice == 0) {\n" +
			"                    if(ch == 0) stack1 = new ImageStack(image.getWidth(), image.getHeight());\n" +
			"                    else stack2 = new ImageStack(image.getWidth(), image.getHeight());\n" +
			"                }\n" +
			"                if(ch == 0) stack1.addSlice(image);\n" +
			"                else stack2.addSlice(image);\n" +
			"                \n" +
			"                if(slice == slices - 1) {\n" +
			"                    if(ch == 0) new ImagePlus(\"0\", stack1).show();\n" +
			"                    else new ImagePlus(\"1\", stack2).show();\n" +
			"                }\n" +
			"            } catch(JSONException es) {\n" +
			"            }\n" +
			"        }\n" +
			"    }\n" +
			"}\n";

	static String pluginExample = "import com.google.common.eventbus.Subscribe;\n" +
			"\n" +
			"import org.micromanager.data.ProcessorConfigurator;\n" +
			"import org.micromanager.data.ProcessorPlugin;\n" +
			"import org.micromanager.data.ProcessorFactory;\n" +
			"\n" +
			"import org.micromanager.data.Image;\n" +
			"import org.micromanager.data.Metadata;\n" +
			"import org.micromanager.data.Processor;\n" +
			"import org.micromanager.data.ProcessorContext;\n" +
			"\n" +
			"import org.micromanager.PropertyMap;\n" +
			"import org.micromanager.Studio;\n" +
			"\n" +
			"import org.scijava.plugin.Plugin;\n" +
			"import org.scijava.plugin.SciJavaPlugin;\n" +
			"\n" +
			"@Plugin(type = ProcessorPlugin.class)\n" +
			"public class MoonPlugin implements ProcessorPlugin, SciJavaPlugin {\n" +
			"   private Studio studio_;\n" +
			"\n" +
			"   @Override\n" +
			"   public void setContext(Studio studio) {\n" +
			"      studio_ = studio;\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public ProcessorConfigurator createConfigurator(PropertyMap settings) {\n" +
			"      return null;\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public ProcessorFactory createFactory(PropertyMap settings) {\n" +
			"      return new MoonFactory(settings, studio_);\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public String getName() {\n" +
			"      return \"Moon Plugin\";\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public String getHelpText() {\n" +
			"      return \"Rotates and/or mirrors images coming from the selected camera\";\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public String getVersion() {\n" +
			"      return \"Version 1.0\";\n" +
			"   }\n" +
			"\n" +
			"   @Override\n" +
			"   public String getCopyright() {\n" +
			"      return \"Copyright University of California San Francisco, 2015\";\n" +
			"   }\n" +
			"\n" +
			"    public class MoonFactory implements ProcessorFactory {\n" +
			"       private PropertyMap settings_;\n" +
			"       private Studio studio_;\n" +
			"    \n" +
			"       public MoonFactory(PropertyMap settings, Studio studio) {\n" +
			"          settings_ = settings;\n" +
			"          studio_ = studio;\n" +
			"       }\n" +
			"    \n" +
			"       @Override\n" +
			"       public Processor createProcessor() {\n" +
			"          return new MoonProcessor(studio_);\n" +
			"       }\n" +
			"    }\n" +
			"    \n" +
			"    public class MoonProcessor implements Processor {\n" +
			"       private Studio studio_;\n" +
			"\n" +
			"       public MoonProcessor(Studio studio) {\n" +
			"          studio_ = studio;\n" +
			"       }\n" +
			"\n" +
			"       @Override\n" +
			"       public void processImage(Image image, ProcessorContext context) {\n" +
			"          context.outputImage(image);\n" +
			"       }\n" +
			"    }\n" +
			"}";

	public JavaEditor( SPIMSetup setup, Studio studio ) {
		this.setup = setup;
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

		Button copyBtn = new Button("Copy");
		copyBtn.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				onCopy();
			}
		});

		Button pasteBtn = new Button("Paste");
		pasteBtn.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				onPaste();
			}
		});

		Button loadScriptExample = new Button("Load a Script example");
		loadScriptExample.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onLoadScriptExample();
			}
		});

		Button loadPluginExample = new Button("Load a Plugin example");
		loadPluginExample.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onLoadPluginExample();
			}
		});

		Button generatePlugin = new Button("Generate on-the-fly Plugin");
		generatePlugin.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onPlugin();
			}
		});

		setCenter( editorView );
		setBottom( new HBox( 10, copyBtn, pasteBtn, okBtn, loadScriptExample, loadPluginExample, generatePlugin )  );

		sceneProperty().addListener( new ChangeListener< Scene >()
		{
			@Override public void changed( ObservableValue< ? extends Scene > observable, Scene oldValue, Scene newValue )
			{
				if(newValue != null) {
					initialize();
					sceneProperty().removeListener( this );
				}
			}
		} );
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio ) {
		this.setup = setup;
		this.studio = studio;

		if(this.setup != null) {
			this.setup.addEventHandler(ControlEvent.MM, new EventHandler<ControlEvent>() {
				@Override
				public void handle(ControlEvent event) {
					if(event.getEventType().equals( ControlEvent.MM_IMAGE_CAPTURED )) {
						TaggedImage tagged = (TaggedImage) event.getParam()[0];

						if(compiledMethod != null) {
							try {
								compiledMethod.invoke(null, new String[1], setup, studio, tagged);
							} catch (IllegalAccessException e) {
								e.printStackTrace();
							} catch (InvocationTargetException e) {
								e.printStackTrace();
							}
						}
					}
				}
			});
		}
	}

	public void initialize() {
		// We need JavaScript support
		editorView.getEngine().setJavaScriptEnabled(true);

		// The build in ACE context menu does not work because
		// JavaScript Clipboard interaction is disabled by security.
		// We have to do this by ourselfs.
		editorView.setContextMenuEnabled(false);

		SequentialWebEngineLoader.load(editorView.getEngine(), getClass().getResource("ace/editor.html").toExternalForm(), (observable, oldValue, newValue) -> {
			if (newValue == Worker.State.SUCCEEDED) {
				initializeHTML();
			}
		});

		// Copy & Paste Clipboard support
		final KeyCombination theCombinationCopy = new KeyCodeCombination( KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		final KeyCombination theCombinationPaste = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
		final KeyCombination theCombinationRun = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);

		editorView.addEventFilter( KeyEvent.KEY_PRESSED, aEvent -> {
			if (theCombinationCopy.match(aEvent)) {
				onCopy();
			}
			if (theCombinationPaste.match(aEvent)) {
				onPaste();
			}
			if (theCombinationRun.match(aEvent)) {
				onOk();
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
		String theContent = (String) theClipboard.getContent( DataFormat.PLAIN_TEXT );
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
		Document theDocument = editorView.getEngine().getDocument();
		Element theEditorElement = theDocument.getElementById("editor");
		theEditorElement.setTextContent(scriptExample);

		// and fill it with the LUA script taken from our editing action
		editorView.getEngine().executeScript("initeditor()");
	}

	private void setTextContent(String content) {
		JSObject theWindow = (JSObject) editorView.getEngine().executeScript("window");
		theWindow.call("setContent", content);
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
			method = clazz.getMethod("main", String[].class, SPIMSetup.class, Studio.class, TaggedImage.class);
			method.invoke(null, new String[1], setup, studio, null);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch ( InvocationTargetException e) {
			e.printStackTrace();
		}

		if(method != null) compiledMethod = method;
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

	public void onPlugin() {
		String code = (String) editorView.getEngine().executeScript("getvalue()");

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

		if(runtime.compile(className, code, new File(System.getProperty( "mmcorej.library.path" ) + "mmplugins")))
		{
			try {
				runtime.saveClass(className, code, new File(System.getProperty( "mmcorej.library.path" ) + "mmplugins"));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			} catch (IllegalAccessException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			} catch (InstantiationException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}
		}
	}


	private void onLoadPluginExample() {
		setTextContent(pluginExample);
	}

	private void onLoadScriptExample() {
		setTextContent(scriptExample);
	}

	public void onTest() {
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");
//		Script theNewScript = new Script(theContent);
//		test(theContent);
	}
}