package spim.ui.view.component;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;

import mmcorej.TaggedImage;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import spim.hardware.SPIMSetup;
import spim.model.event.ControlEvent;
import spim.plugin.compile.PluginRuntime;

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
public class JavaEditor extends Editor
{
	private Class plugin;
	private Method compiledMethod;

	static String scriptExample = "import org.micromanager.data.Coords;\n" +
			"import org.micromanager.data.Image;\n" +
			"import org.micromanager.data.Datastore;\n" +
			"import org.micromanager.data.Coords;\n" +
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
			"/***\n" +
			" * \"Ctrl+R\" on Windows and \"Meta+R\" on Mac runs your code.\n" +
			" * You can design any processes based on the images.\n" +
			" */\n" +
			"public class Script {\n" +
			"    /***\n" +
			"     * This main method runs by clicking \"Run\" button.\n" +
			"     */\n" +
			"    public static void main(String[] args, SPIMSetup setup, Studio mm)\n" +
			"    {\n" +
			"        // Check clijx instance\n" +
			"        System.out.println(\"Test\");\n" +
			"        CLIJx clijx = CLIJx.getInstance();\n" +
			"        System.out.println(clijx.clinfo());\n" +
			"    }\n" +
			"    \n" +
			"    /***\n" +
			"     * process method runs whenever the new image is received during acquisition.\n" +
			"     */\n" +
			"    static ImageStack[] stacks = new ImageStack[2];    \n" +
			"    public static void process(Coords coords, mmcorej.TaggedImage tagged)\n" +
			"    {\n" +
			"        // On receiving TaggedImage during acquisition\n" +
			"        if(tagged != null) {\n" +
			"            try {\n" +
			"                System.out.println( coords );\n" +
			"                // To check all the tags in the image, uncomment the below\n" +
			"                // System.out.println(tagged.tags.toString( 2 ));\n" +
			"                // int slice = tagged.tags.getInt(\"SliceIndex\");\n" +
			"                // double exp = tagged.tags.getInt( \"Exposure-ms\" );\n" +
			"                // double zPos = tagged.tags.getDouble( \"ZPositionUm\" );\n" +
			"                // double xPos = tagged.tags.getDouble( \"XPositionUm\" );\n" +
			"                // double yPos = tagged.tags.getDouble( \"YPositionUm\" );\n" +
			"                // int ch = tagged.tags.getInt( \"ChannelIndex\" );\n" +
			"                // String cam = tagged.tags.getString( \"Camera\" );\n" +
			"                int slices = tagged.tags.getJSONObject( \"Summary\" ).getInt( \"Slices\" );\n" +
			"                // int channels = tagged.tags.getJSONObject( \"Summary\" ).getInt( \"Channels\" );\n" +
			"                System.out.println( coords.getZ() + \"/\" + slices );\n" +
			"\n" +
			"                ImageProcessor image = ImageUtils.makeProcessor(tagged);\n" +
			"                int c = coords.getC();\n" +
			"                int z = coords.getZ();\n" +
			"                if(z == 0) {\n" +
			"                    stacks[c] = new ImageStack(image.getWidth(), image.getHeight());\n" +
			"                }\n" +
			"                \n" +
			"                stacks[c].addSlice(image);\n" +
			"\n" +
			"                if(z == slices - 1) {\n" +
			"                    new ImagePlus(c + \"\", stacks[c]).show();\n" +
			"                }\n" +
			"            } catch(JSONException es) {\n" +
			"            }\n" +
			"        }\n" +
			"    }\n" +
			"}\n";

	static String pluginExample = "import com.google.common.eventbus.Subscribe;\n" +
			"\n" +
			"import java.awt.*;\n" +
			"import java.awt.event.*;\n" +
			"import javax.swing.*;\n" +
			"import org.micromanager.data.*;\n" +
			"import org.micromanager.data.Image;\n" +
			"\n" +
			"import org.micromanager.*;\n" +
			"import org.micromanager.data.ProcessorConfigurator;\n" +
			"import org.micromanager.propertymap.MutablePropertyMapView;\n" +
			"\n" +
			"import org.scijava.plugin.Plugin;\n" +
			"import org.scijava.plugin.SciJavaPlugin;\n" +
			"\n" +
			"/***\n" +
			" * \"Ctrl+R\" on Windows and \"Meta+R\" on Mac runs your code.\n" +
			" * This example shows how to implement on-the-fly Processor in Micro-Manager.\n" +
			" * Please, refer https://github.com/micro-manager/micro-manager/tree/master/plugins/ImageFlipper\n" +
			" * if you want to see the actual example.\n" +
			" * \n" +
			" * Clicking \"Generate an on-the-fly Plugin\" button generates \"MoonPlugin.jar\" in the plugin folder.\n" +
			" */\n" +
			"@Plugin(type = ProcessorPlugin.class)\n" +
			"public class MoonPlugin implements ProcessorPlugin, SciJavaPlugin {\n" +
			"    private Studio studio_;\n" +
			"\n" +
			"    @Override\n" +
			"    public void setContext(Studio studio) {\n" +
			"        studio_ = studio;\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public ProcessorConfigurator createConfigurator(PropertyMap settings) {\n" +
			"        return new MoonConfigurator(settings, studio_);\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public ProcessorFactory createFactory(PropertyMap settings) {\n" +
			"        return new MoonFactory(settings, studio_);\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public String getName() {\n" +
			"        return \"Moon Plugin\";\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public String getHelpText() {\n" +
			"        return \"An example on-the-fly processor\";\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public String getVersion() {\n" +
			"        return \"Version 1.0\";\n" +
			"    }\n" +
			"\n" +
			"    @Override\n" +
			"    public String getCopyright() {\n" +
			"        return \"Copyright (c) <year> <copyright holder>. All rights reserved.\";\n" +
			"    }\n" +
			"\n" +
			"    public class MoonConfigurator extends JFrame implements ProcessorConfigurator {\n" +
			"        private final Studio studio_;\n" +
			"        private final MutablePropertyMapView defaults_;\n" +
			"\n" +
			"        public MoonConfigurator(PropertyMap settings, Studio studio) {\n" +
			"           studio_ = studio;\n" +
			"           defaults_ = studio_.profile().getSettings(this.getClass());\n" +
			"        }\n" +
			"        \n" +
			"        @Override\n" +
			"        public void showGUI() {\n" +
			"            Dialog d = new Dialog(this, \"Dialog Example\", true);  \n" +
			"            d.setLayout( new FlowLayout() );  \n" +
			"            Button b = new Button (\"OK\");  \n" +
			"            b.addActionListener ( new ActionListener()  \n" +
			"            {  \n" +
			"                public void actionPerformed( ActionEvent e )  \n" +
			"                {  \n" +
			"                    d.setVisible(false);  \n" +
			"                }  \n" +
			"            });  \n" +
			"            d.add( new Label (\"Click button to continue.\"));\n" +
			"            d.add(b);   \n" +
			"            d.setSize(300,300);    \n" +
			"            d.setVisible(true);  \n" +
			"        }\n" +
			"        \n" +
			"        @Override\n" +
			"        public void cleanup() {\n" +
			"        }\n" +
			"       \n" +
			"        @Override\n" +
			"        public PropertyMap getSettings() {\n" +
			"            PropertyMap.Builder builder = PropertyMaps.builder(); \n" +
			"            builder.putString(\"shape\", \"full\");\n" +
			"            builder.putInteger(\"radian-integer\", 6);\n" +
			"            builder.putDouble(\"radian-double\", 6.283);\n" +
			"            builder.putBoolean(\"full\", true);\n" +
			"            return builder.build();\n" +
			"        }\n" +
			"    }\n" +
			"\n" +
			"    public class MoonFactory implements ProcessorFactory {\n" +
			"        private PropertyMap settings_;\n" +
			"        private Studio studio_;\n" +
			"    \n" +
			"        public MoonFactory(PropertyMap settings, Studio studio) {\n" +
			"            settings_ = settings;\n" +
			"            studio_ = studio;\n" +
			"        }\n" +
			"    \n" +
			"        @Override\n" +
			"        public Processor createProcessor() {\n" +
			"            return new MoonProcessor(studio_);\n" +
			"        }\n" +
			"    }\n" +
			"\n" +
			"    public class MoonProcessor implements Processor {\n" +
			"        private Studio studio_;\n" +
			"\n" +
			"        public MoonProcessor(Studio studio) {\n" +
			"            studio_ = studio;\n" +
			"        }\n" +
			"\n" +
			"        @Override\n" +
			"        public void processImage(Image image, ProcessorContext context) {\n" +
			"            context.outputImage(image);\n" +
			"        }\n" +
			"    }\n" +
			"}";

	public JavaEditor( SPIMSetup setup, Studio studio ) {
		super(setup, studio);

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

		MenuButton loadExamples = new MenuButton("Load Examples");
		loadExamples.setStyle("-fx-base: #8bb9e7;");

		MenuItem loadScriptExample = new MenuItem("Load a Script example");
		loadScriptExample.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onLoadScriptExample();
			}
		});

		MenuItem loadPluginExample = new MenuItem("Load a Plugin example");
		loadPluginExample.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onLoadPluginExample();
			}
		});

		loadExamples.getItems().addAll( loadScriptExample, loadPluginExample );

		Button generatePlugin = new Button("Generate an on-the-fly Plugin");
		generatePlugin.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				onPluginGenerated();
			}
		});

		setBottom( new HBox( 10, copyBtn, pasteBtn, okBtn, loadExamples, generatePlugin )  );
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio ) {
		this.setup = setup;
		this.studio = studio;

		if(this.setup != null) {
			this.setup.addEventHandler(ControlEvent.MM, new EventHandler<ControlEvent>() {
				@Override
				public void handle(ControlEvent event) {
					if(event.getEventType().equals( ControlEvent.MM_IMAGE_CAPTURED )) {
						Coords coord = (Coords) event.getParam()[0];
						TaggedImage tagged = (TaggedImage) event.getParam()[1];

						if(compiledMethod != null) {
							try {
								compiledMethod.invoke(null, coord, tagged);
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

	protected String getEditorHtml() {
		return "ace/editor.html";
	}

	protected void initializeHTML() {
		// Initialize the editor
		Document theDocument = editorView.getEngine().getDocument();
		Element theEditorElement = theDocument.getElementById("editor");
		theEditorElement.setTextContent(scriptExample);

		// and fill it with the LUA script taken from our editing action
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
	}

	protected void load(Class clazz)
	{
		Method method = null;

		try {
			method = clazz.getMethod("main", String[].class, SPIMSetup.class, Studio.class);
			method.invoke(null, new String[1], setup, studio);

			if(method != null) {
				compiledMethod = clazz.getMethod("process", Coords.class, TaggedImage.class);
			}
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch ( InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onOk() {
		// We need to compile and run the edited script.
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");
		compileRun( theContent );
	}

	public void onPluginGenerated() {
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