package spim.ui.view.component;

import halcyon.HalcyonFrame;
import halcyon.model.node.HalcyonNode;
import halcyon.model.node.HalcyonNodeType;
import halcyon.view.TreePanel;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import javafx.stage.WindowEvent;
import org.micromanager.Studio;

import org.micromanager.events.GUIRefreshEvent;
import spim.hardware.SPIMSetup;
import spim.hardware.VersaLase;

import spim.model.event.ControlEvent;
import spim.ui.view.component.console.StdOutCaptureConsole;
import spim.ui.view.component.util.ResourceUtil;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * Description: µOpenSPIM application main class
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class HalcyonMain extends HalcyonFrame
{
	private ObjectProperty<Studio> mmStudioProperty = new SimpleObjectProperty<>();
	private ObjectProperty<GUIRefreshEvent> mmStudioGUIRefreshEventProperty = new SimpleObjectProperty<>();
	private BooleanProperty terminated = new SimpleBooleanProperty();
	AcquisitionPanel acquisitionPanel;
	ToolbarPanel toolbarPanel;

	public HalcyonMain() {
		super("µOpenSPIM", ResourceUtil.getString("app.icon"),
				800,
				600);
	}
	@Override
	public void start( Stage primaryStage )
	{
		Platform.runLater( new Runnable()
		{
			@Override public void run()
			{
				Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
				try
				{
					Thread.sleep(1000);
				}
				catch ( InterruptedException e )
				{
					e.printStackTrace();
				}
				show( primaryStage, null );
			}
		} );
	}

	public void show( Stage primaryStage, Studio gui )
	{
		// TODO: support other type of devices
		BorderPane borderPane = createHalcyonBorderPane( primaryStage, gui );

		super.show( borderPane, false );

		Event.fireEvent( toolbarPanel, new ControlEvent( ControlEvent.MM_OPEN, this ) );
	}

	private BorderPane createHalcyonBorderPane( Stage primaryStage, Studio studio )
	{
		SPIMSetup spimSetup = null;

		if(studio != null && studio.getCMMCore() != null)
		{
			spimSetup = SPIMSetup.createDefaultSetup( studio.getCMMCore() );
		}

		final String rootIconPath =
				ResourceUtil.getString("root.icon");

		ArrayList< HalcyonNodeType > nodeTypeList =
				new ArrayList<>();

		nodeTypeList.add(SpimHalcyonNodeType.CONTROL);
		nodeTypeList.add(SpimHalcyonNodeType.SHUTTER);
		nodeTypeList.add(SpimHalcyonNodeType.CONSOLE);
		nodeTypeList.add(SpimHalcyonNodeType.EDITOR);
//		nodeTypeList.add(SpimHalcyonNodeType.CAMERA);
		nodeTypeList.add(SpimHalcyonNodeType.LASER);
		nodeTypeList.add(SpimHalcyonNodeType.STAGE);

		TreePanel lTreePanel = new TreePanel("Configurations",
				"OpenSPIM",
				getClass().getResourceAsStream(rootIconPath),
				nodeTypeList);

		setTreePanel(lTreePanel);

		final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
				SpimHalcyonNodeType.LASER,
				new LaserDevicePanel( null, 488 ) );

		final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
				SpimHalcyonNodeType.LASER,
				new LaserDevicePanel( null, 561 ));

//		CameraDevicePanel cameraDevicePanel = new CameraDevicePanel( spimSetup, null );
//		final HalcyonNode lCamera = HalcyonNode.wrap("Camera",
//				SpimHalcyonNodeType.CAMERA, cameraDevicePanel );

		StagePanel stagePanel = new StagePanel(null);
		final HalcyonNode lStage = HalcyonNode.wrap("Stage",
				SpimHalcyonNodeType.STAGE,
				stagePanel );

		addNode(lLaser1);
		addNode(lLaser2);
//		addNode(lCamera);
		addNode(lStage);

		JavaEditor javaEditor = new JavaEditor( spimSetup, studio );
		final HalcyonNode editor1 = HalcyonNode.wrap( "Java",
				SpimHalcyonNodeType.EDITOR,
				javaEditor );

		addNode(editor1);

		BeanshellEditor beanshellEditor = new BeanshellEditor( spimSetup, studio );
		final HalcyonNode editor2 = HalcyonNode.wrap( "Beanshell",
				SpimHalcyonNodeType.EDITOR,
				beanshellEditor );

		addNode(editor2);

		ArduinoPanel arduinoPanel = new ArduinoPanel( spimSetup );
		final HalcyonNode arduino = HalcyonNode.wrap( "ArduinoUno", SpimHalcyonNodeType.SHUTTER, arduinoPanel );
		addNode( arduino );

		// Custom Toolbar provided here
		toolbarPanel = new ToolbarPanel( primaryStage, studio, mmStudioProperty, mmStudioGUIRefreshEventProperty );
		toolbarPanel.setPrefSize(300, 200);
		addToolbar(toolbarPanel);

		acquisitionPanel = new AcquisitionPanel( spimSetup, studio, null, arduinoPanel.getPinItemTableView(), toolbarPanel.waitSecondsProperty(), getHostServices() );
		stagePanel.setAcquisitionPanel( acquisitionPanel );
		final HalcyonNode control1 = HalcyonNode.wrap( "Acquisition",
				SpimHalcyonNodeType.CONTROL,
				acquisitionPanel );

		addNode( control1 );

//		final HalcyonNode control2 = HalcyonNode.wrap( "3D",
//				SpimHalcyonNodeType.CONTROL,
//				new Stage3DPanel() );
//
//		addNode( control2 );
		StdOutCaptureConsole console = new StdOutCaptureConsole();
		final HalcyonNode consoleNode = HalcyonNode.wrap( "Console", SpimHalcyonNodeType.CONSOLE, console );
		addNode( consoleNode );

		final VersaLaserDevicePanel[] versaLaserDevicePanel = new VersaLaserDevicePanel[ 1 ];
		final LaserDevicePanel[] lasers = new LaserDevicePanel[ 2 ];

		mmStudioProperty.addListener( new ChangeListener< Studio >()
		{
			@Override public void changed( ObservableValue< ? extends Studio > observable, Studio oldValue, Studio studio )
			{
				if(studio != null && studio.getCMMCore() != null) {
					Platform.runLater( new Runnable()
					{
						@Override public void run()
						{
							SPIMSetup spimSetup = SPIMSetup.createDefaultSetup( studio.getCMMCore() );

							if(spimSetup.getLaser() != null)
							{
								removeAllNodes( SpimHalcyonNodeType.LASER );

								if(spimSetup.getLaser().getLabel().startsWith( "VLT_VersaLase" ))
								{
									versaLaserDevicePanel[ 0 ] = new VersaLaserDevicePanel( ( VersaLase ) spimSetup.getLaser() );
									final HalcyonNode lVersaLase = HalcyonNode.wrap( "VersaLase",
											SpimHalcyonNodeType.LASER, versaLaserDevicePanel[ 0 ] );

									addNode(lVersaLase);
								}
								else
								{
									lasers[ 0 ] = new LaserDevicePanel( spimSetup.getLaser(), 488 );
									final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
											SpimHalcyonNodeType.LASER, lasers[ 0 ] );

									addNode(lLaser1);
								}
							}

							if(spimSetup.getLaser2() != null)
							{
								lasers[ 1 ] = new LaserDevicePanel( spimSetup.getLaser2(), 594 );
								final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
										SpimHalcyonNodeType.LASER, lasers[ 1 ]);
								addNode(lLaser2);
							}

							removeNoChildNode();

							if(spimSetup.getZStage() != null)
							{
								stagePanel.setSetup(spimSetup, studio);
							}

//							cameraDevicePanel.setSetup( spimSetup, studio );
							toolbarPanel.setSetup( spimSetup, studio );
							javaEditor.setSetup(spimSetup, studio);
							beanshellEditor.setSetup(spimSetup, studio);
							arduinoPanel.setSetup(spimSetup, studio);
							acquisitionPanel.setSetup( spimSetup, studio );
							acquisitionPanel.setStagePanel( stagePanel );
						}
					} );
				} else {
					terminated.set(true);
					Platform.runLater( new Runnable()
				    {
					   @Override public void run()
					   {
					   	   if(versaLaserDevicePanel[0] != null) versaLaserDevicePanel[0].setSetup( null, studio );

					   	   if(lasers[0] != null) lasers[0].setSetup( null, studio );
					   	   if(lasers[1] != null) lasers[1].setSetup( null, studio );

						   stagePanel.setSetup( null, studio );
//						   cameraDevicePanel.setSetup( null, studio );
						   toolbarPanel.setSetup( null, studio );
						   javaEditor.setSetup( null, studio );
						   beanshellEditor.setSetup( null, studio);
						   arduinoPanel.setSetup( null, studio );
						   acquisitionPanel.setSetup( null, studio );
						   acquisitionPanel.setStagePanel( null );
						   terminated.set(false);
					   }
				    });
				}
			}
		} );

		primaryStage.getIcons()
				.add(new Image(getClass().getResourceAsStream(ResourceUtil.getString("app.icon"))));

		primaryStage.sceneProperty().addListener( new ChangeListener< Scene >()
		{
			@Override public void changed( ObservableValue< ? extends Scene > observable, Scene oldValue, Scene newValue )
			{
				if(newValue != null) {
					newValue.getStylesheets().add("/spim/ui/view/component/default.css");
					observable.removeListener( this );
				}
			}
		} );

//		String lLayoutFile = getUserDataDirectory( mWindowtitle )
//				+ "layout.pref";
		checkLayoutPref(getFileFromResourceAsStream("spim/gui/layout.pref"));
		checkOthersPref(getFileFromResourceAsStream("spim/gui/others.pref"));

		return createHalcyonFrame( primaryStage );
	}

	// get a file from the resources folder
	// works everywhere, IDEA, unit test and JAR file.
	private InputStream getFileFromResourceAsStream(String fileName) {

		// The class loader that loaded the class
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream(fileName);

		// the stream holding the file content
		if (inputStream == null) {
			throw new IllegalArgumentException("file not found! " + fileName);
		} else {
			return inputStream;
		}

	}

	@Override
	protected void callFrameClosed( WindowEvent closeEvent )
	{
		acquisitionPanel.setSetup( null, null );
		super.callFrameClosed( closeEvent );
	}

	public static void main(String[] args) throws Exception
	{
		launch(args);
	}
}
