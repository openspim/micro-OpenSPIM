package spim.mm;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.micromanager.internal.MMStudio;
import spim.ui.view.component.HalcyonMain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class LoadConfig extends Application
{
	private static final String FILE = "cfgfile";
	private static final String NO_FILES = "no_files";

	private TextArea devicesTextArea;
	private TextArea configTextArea;
	private TextArea summaryText;
	private File selectedFile;
	private ListView<String> fileList;

	private Preferences preferences;
	private String sysConfigFile;
	private Stage stage;
	private boolean returnResult = false;

	@Override public void start( Stage stage ) throws Exception
	{
		Dialog dialog = new Dialog();
		dialog.setTitle( "Please, choose your configuration file" );

		DialogPane dialogPane = dialog.getDialogPane();

		preferences = Preferences.userNodeForPackage( LoadConfig.class );
		this.stage = stage;

		ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialogPane.getButtonTypes().addAll(okButtonType, cancelButtonType);
		//		dialogPane.lookupButton(cancelButtonType).setVisible(false);


		Group root = new Group();
		Scene scene = new Scene(root);
		stage.setScene(scene);

		SplitPane detailSplitPane = new SplitPane();
		detailSplitPane.setMaxWidth( 300 );
		BorderPane devicePane = new BorderPane();
		devicesTextArea = new TextArea();
		devicesTextArea.setEditable( false );
		devicePane.setTop( new Label( "Devices" ) );
		devicePane.setCenter( devicesTextArea );

		BorderPane configPane = new BorderPane();
		configTextArea = new TextArea();
		configTextArea.setEditable( false );
		configPane.setTop( new Label( "Config/Main Presets" ) );
		configPane.setCenter( configTextArea );

		detailSplitPane.getItems().addAll( devicePane, configPane );

		BorderPane filesPane = new BorderPane();
		//		filesPane.prefWidth( 200 );
		fileList = new ListView<>();
		fileList.setPrefWidth( 400 );
		fileList.setMinWidth( 400 );
		fileList.setPrefHeight( 200 );
		fileList.getSelectionModel().selectedItemProperty().addListener( new ChangeListener< String >()
		{
			@Override public void changed( ObservableValue< ? extends String > observable, String oldValue, String newValue )
			{
				sysConfigFile = newValue;
				selectedFile = new File(newValue);
				try
				{
					loadFileAttribs();
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		} );
		filesPane.setTop( new Label("Files") );
		filesPane.setCenter( fileList );

		summaryText = new TextArea( "No. Devices: \nNo. Groups: \nNo. Presets:" );
		summaryText.setEditable( false );
		summaryText.setPrefRowCount( 3 );

		Button addFileBtn = new Button( "+" );
		addFileBtn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				loadConfig();
			}
		} );

		Button deleteFileBtn = new Button( "-" );
		deleteFileBtn.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				int idx = fileList.getSelectionModel().getSelectedIndex();
				if(idx >= 0) {
					fileList.getItems().remove( idx );
					savePrefs();
				}
			}
		} );


		VBox summaryBox = new VBox();
		summaryBox.getChildren().addAll( new Label( "Summary" ), summaryText, new HBox( addFileBtn, deleteFileBtn ) );
		summaryBox.setPrefWidth( 400 );
		filesPane.setBottom( summaryBox );

		BorderPane main = new BorderPane();
		main.setCenter( filesPane );
		main.setRight( detailSplitPane );

		dialogPane.setContent( main );
		dialog.setResizable( true );

		loadPrefs();

		Optional<ButtonType> result = dialog.showAndWait();

		if(result.isPresent() && result.get().getButtonData() == ButtonType.OK.getButtonData()) {
			returnResult = true;
		} else {
			returnResult = false;
		}
	}

	public boolean getReturnResult() {
		return returnResult;
	}

	public String getConfigFilePath()
	{
		return sysConfigFile;
	}

	void savePrefs()
	{
		preferences.putInt(NO_FILES, fileList.getItems().size());
		for (int i = 0; i < fileList.getItems().size(); ++i)
			preferences.put( FILE + i, fileList.getItems().get( i ) );
	}

	private void loadPrefs()
	{
		fileList.getItems().clear();

		for (int i = 0; i < preferences.getInt(NO_FILES, 0); ++i)
		{
			String file = preferences.get(FILE + i, "");
			if (new File(file).exists())
				fileList.getItems().add( file );
		}

		if (fileList.getItems().isEmpty() && MMUtils.demoConfigFile != null)
			loadFile(MMUtils.demoConfigFile.getAbsolutePath());
	}

	void loadConfig()
	{
		FileChooser fileChooser = new FileChooser();

		fileChooser.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Micro-Manager config Files", "*.cfg")
		);

		selectedFile = fileChooser.showOpenDialog( stage );

		if (selectedFile != null)
		{
			sysConfigFile = selectedFile.getAbsolutePath();
			loadFile(sysConfigFile);
		}
	}

	private void loadFile(String path)
	{
		if (!fileList.getItems().contains(path) && new File(path).exists())
			fileList.getItems().add(path);
		else
			fileList.getSelectionModel().select( path );

		savePrefs();
	}

	void loadFileAttribs() throws IOException
	{
		int noDevices = 0;
		int noGroups = 0;
		int noPresets = 0;

		String textDevices = "";
		String textConfigs = "";

		BufferedReader in = new BufferedReader(new FileReader(selectedFile));
		if (selectedFile != null)
		{
			String actualLine = "";
			while ((actualLine = in.readLine()) != null)
			{
				if (actualLine.isEmpty())
					continue;
				if (actualLine.charAt(0) == '#')
				{
					if (actualLine.contains("Group:"))
					{
						textConfigs = textConfigs + actualLine.substring(9) + "\n";
						noGroups++;
					}
					else if (actualLine.contains("Preset:"))
					{
						textConfigs = textConfigs + "   " + actualLine.substring(10) + "\n";
						noPresets++;
					}
				}
				else
				{
					if (actualLine.startsWith("Device"))
					{
						actualLine = actualLine.substring(7);
						int coma_index;
						while ((coma_index = actualLine.indexOf(',')) != -1)
							actualLine = actualLine.substring(coma_index + 1);
						textDevices = textDevices + actualLine + "\n";
						noDevices++;
					}
				}
			}
		}
		in.close();

		String summary = "No. of Devices: " + noDevices;
		summary = summary + "\nNo. of Groups: " + noGroups;
		summary = summary + "\nNo. of Presets: " + noPresets;
		devicesTextArea.setText(textDevices);
		configTextArea.setText(textConfigs);
		summaryText.setText(summary);
		devicesTextArea.positionCaret(0);
		configTextArea.positionCaret(0);
		summaryText.positionCaret(0);
	}

	public static void main( final String[] args )
	{
		Application.launch( LoadConfig.class );
	}
}
