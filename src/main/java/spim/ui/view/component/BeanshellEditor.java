package spim.ui.view.component;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.ParseException;
import bsh.TargetError;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.micromanager.Studio;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import spim.hardware.SPIMSetup;
import spim.ui.view.component.util.SequentialWebEngineLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterReader;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: August 2021
 */
public class BeanshellEditor extends BorderPane implements SPIMSetupInjectable
{
	WebView editorView;

	private Studio studio;
	private SPIMSetup setup;

	private Interpreter beanshellREPLint_;
	private TextField commandField;
	private PipedWriter commandWriter;

	public BeanshellEditor( Stage stage, SPIMSetup setup, Studio studio ) {
		this.setup = setup;
		this.studio = studio;

		commandWriter = new PipedWriter();

		commandField = new TextField();
		commandField.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {
				try {
					commandWriter.write(commandField.getText());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		editorView = new WebView();
		editorView.setMinHeight( 180 );

		Button okBtn = new Button( "Run" );
		okBtn.setOnAction( new EventHandler<ActionEvent>()
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

		setTop( commandField );
		setCenter( editorView );
		setBottom( new HBox( 10, copyBtn, pasteBtn, okBtn )  );

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

	@Override
	public void setSetup(SPIMSetup setup, Studio studio) {
		this.setup = setup;
		this.studio = studio;

		if(setup != null) {
			createBeanshellREPL();
			initializeInterpreter();
		}
	}

	public void initialize() {
		// We need JavaScript support
		editorView.getEngine().setJavaScriptEnabled(true);

		// The build in ACE context menu does not work because
		// JavaScript Clipboard interaction is disabled by security.
		// We have to do this by ourselfs.
		editorView.setContextMenuEnabled(false);

		SequentialWebEngineLoader.load(editorView.getEngine(), getClass().getResource("ace/beanshell.html").toExternalForm(), (observable, oldValue, newValue) -> {
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

	class CommandLineReader extends FilterReader {
		static final int normal = 0;
		static final int lastCharNL = 1;
		static final int sentSemi = 2;
		int state = 1;

		public CommandLineReader(Reader in) {
			super(in);
		}

		public int read() throws IOException {
			if (this.state == 2) {
				this.state = 1;
				return 10;
			} else {
				int b;
				while((b = this.in.read()) == 13) {
				}

				if (b == 10) {
					if (this.state == 1) {
						b = 59;
						this.state = 2;
					} else {
						this.state = 1;
					}
				} else {
					this.state = 0;
				}

				return b;
			}
		}

		public int read(char[] buff, int off, int len) throws IOException {
			int b = this.read();
			if (b == -1) {
				return -1;
			} else {
				buff[off] = (char)b;
				return 1;
			}
		}
	}

	final void createBeanshellREPL() {

//		Reader in = new CommandLineReader(new InputStreamReader((InputStream)System.in));
		Reader in = null;
		try {
			in = new CommandLineReader(new PipedReader(commandWriter));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Create console and REPL interpreter:
		beanshellREPLint_ = new Interpreter(in, System.out, System.err, true);

		new Thread(beanshellREPLint_, "BeanShell interpreter").start();

		running_ = false;
		evalThd_ = new EvalThread("");
	}

	// Add methods and variables to the interpreter
	private void initializeInterpreter() {
		File tmpFile = null;
		try {
			java.io.InputStream input = getClass().
					getResourceAsStream("/org/micromanager/scriptpanel_startup.bsh");
			if (input != null) {
				tmpFile = File.createTempFile("mm_scriptpanel_startup", ".bsh");
				try (java.io.OutputStream output = new java.io.FileOutputStream(tmpFile)) {
					int read;
					byte[] bytes = new byte[4096];
					while ((read = input.read(bytes)) != -1) {
						output.write(bytes, 0, read);
					}
				}
				tmpFile.deleteOnExit();
			}
			else {
				ReportingUtils.logError("Failed to find Script Panel Beanshell startup script");
			}
		} catch (IOException e) {
			ReportingUtils.showError("Failed to read Script Panel BeanShell startup script");
		}

		if (tmpFile != null) {
			try {
				beanshellREPLint_.source(tmpFile.getAbsolutePath());
			} catch (FileNotFoundException e) {
				ReportingUtils.showError(e);
			} catch (IOException | EvalError e) {
				ReportingUtils.showError(e);
			}
		}

		// This command allows variables to be inspected in the command-line
		// (e.g., typing "x;" causes the value of x to be returned):
		beanshellREPLint_.setShowResults(true);

		insertScriptingObject("mm", studio);
		insertScriptingObject("mmc", studio.core());
	}

	public void insertScriptingObject(String varName, Object obj) {
		try {
			beanshellREPLint_.set(varName,obj);
		} catch (EvalError e) {
			handleException(e);
		}
	}

	public void handleException (Exception e) {
		ReportingUtils.showError(e);
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

		theEditorElement.setTextContent("import org.micromanager.data.Coordinates;\n"
				+ "cb = Coordinates.builder();\n"
				+ "print(cb.c(2).build());\n"
				+ "inspect(cb);\n");

		editorView.getEngine().executeScript("initeditor()");
	}

	public void joinEvalThread() throws InterruptedException {
		if (evalThd_.isAlive()) {
			evalThd_.join();
		}
	}

	public void evaluateAsync(String script) throws MMScriptException {
		if (evalThd_.isAlive())
			throw new MMScriptException("Another script execution in progress!");

		evalThd_ = new EvalThread(script);
		evalThd_.start();
	}

	public void stopRequest(boolean shouldInterrupt) {
		if (evalThd_.isAlive()) {
			if (shouldInterrupt) {
				evalThd_.interrupt();
			}
			else {
				// HACK: kill the thread.
				evalThd_.stop();
				stop_ = true;
			}
		}
	}

	private void runCode( String code )
	{
		try {
//			runButton_.setEnabled(false);
//			stopButton_.setText("Interrupt");
//			stopButton_.setEnabled(true);

			evaluateAsync(code);

			// Spawn a thread that waits for the execution thread to exit and then
			// updates the buttons as appropriate.
			Thread sentinel = new Thread(() -> {
				try {
					joinEvalThread();
				}
				catch (InterruptedException e) {} // Assume thread is done.
//				runButton_.setEnabled(true);
//				stopButton_.setEnabled(false);
			});
			sentinel.start();
		} catch (MMScriptException e) {
			ReportingUtils.logError(e);
		}

	}

	boolean running_ = false;
	boolean error_ = false;
	EvalThread evalThd_;
	boolean stop_ = false;

	public final class EvalThread extends Thread {
		String script_;
		String errorText_;

		public EvalThread(String script) {
			script_ = script;
			errorText_ = new String();
		}

		@Override
		public void run() {
			stop_ = false;
			running_ = true;
			errorText_ = new String();
			try {
				beanshellREPLint_.eval(script_);
			} catch (TargetError e){
				int lineNo = e.getErrorLineNumber();
				System.err.println(formatBeanshellError(e, lineNo));
			} catch (ParseException e) {
				// special handling of the parse errors beacuse beanshell error object
				// has bugs and does not return line numbers
				String msg = e.getMessage();
				String lineNumberTxt = msg.substring(0, msg.lastIndexOf(','));
				lineNumberTxt = lineNumberTxt.substring(lineNumberTxt.lastIndexOf(' ') + 1);
				try {
					System.err.println("Parse error: " + msg + " in " + Integer.parseInt(lineNumberTxt));
				} catch (NumberFormatException nfe) {
					System.err.println("Parse error: " + msg);
				}
			} catch (EvalError e) {
				int lineNo = e.getErrorLineNumber();
				System.err.println(formatBeanshellError(e, lineNo));
			} finally {
				running_ = false;
			}
		}

		public String getError() {
			return errorText_;
		}
	}

	private String formatBeanshellError(EvalError e, int line) {
		if (e instanceof TargetError) {
			Throwable t = ((TargetError)e).getTarget();
			if (t instanceof NullPointerException) {
				// Null Pointer Exceptions do not seem to have much more information
				// However, do make clear to the user that this is a npe
				return "Line " + line + ": Null Pointer Exception";
			}
			return "Line " + line + ": run-time error : " + (t != null ? t.getMessage() : e.getErrorText());
		} else if (e instanceof ParseException) {
			return "Line " + line + ": syntax error : " + e.getErrorText();
		} else if (e instanceof EvalError) {
			return "Line " + line + ": evaluation error : " + e.getMessage();
		} else {
			Throwable t = e.getCause();
			return "Line " + line + ": general error : " + (t != null ? t.getMessage() : e.getErrorText());
		}
	}

	public void onOk() {
		// We need to sace the edited script to the game model.
		if(setup == null) {
			System.err.println("Please load Micro-Manager first. Try it again");
			return;
		}
		String theContent = (String) editorView.getEngine().executeScript("getvalue()");

		runCode( theContent );
	}
}