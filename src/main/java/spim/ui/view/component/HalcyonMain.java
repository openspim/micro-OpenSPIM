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
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import org.micromanager.Studio;

import spim.hardware.SPIMSetup;
import spim.hardware.VersaLase;

import java.util.ArrayList;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class HalcyonMain extends HalcyonFrame
{
	ObjectProperty<Studio> mmStudioProperty = new SimpleObjectProperty<>();
	BooleanProperty terminated = new SimpleBooleanProperty();

	public HalcyonMain() {
		super("OpenSPIM v2.0",
				ResourceUtil.getString("app.icon"),
				800,
				600);
	}
	@Override
	public void start( Stage primaryStage )
	{
		primaryStage.setOnCloseRequest(event -> System.exit(0));
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

		super.show( borderPane );
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
		for ( HalcyonNodeType lHalcyonNodeType : SpimHalcyonNodeType.values())
			nodeTypeList.add(lHalcyonNodeType);

		TreePanel lTreePanel = new TreePanel("Config",
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

		CameraDevicePanel cameraDevicePanel = new CameraDevicePanel( spimSetup, null );
		final HalcyonNode lCamera = HalcyonNode.wrap("Camera",
				SpimHalcyonNodeType.CAMERA, cameraDevicePanel );

		StagePanel stagePanel = new StagePanel(null);
		final HalcyonNode lStage1 = HalcyonNode.wrap("Stage-1",
				SpimHalcyonNodeType.STAGE,
				stagePanel );

		addNode(lLaser1);
		addNode(lLaser2);
		addNode(lCamera);
		addNode(lStage1);

		JavaEditor javaEditor = new JavaEditor( primaryStage, spimSetup, studio );
		final HalcyonNode editor1 = HalcyonNode.wrap( "Java",
				SpimHalcyonNodeType.EDITOR,
				javaEditor );

		addNode(editor1);

		ArduinoPanel arduinoPanel = new ArduinoPanel( spimSetup );
		final HalcyonNode arduino = HalcyonNode.wrap( "ArduinoUno", SpimHalcyonNodeType.SHUTTER, arduinoPanel );
		addNode( arduino );

		AcquisitionPanel acquisitionPanel = new AcquisitionPanel( primaryStage, spimSetup, studio, null, arduinoPanel.getPinItemTableView() );
		final HalcyonNode control1 = HalcyonNode.wrap( "Acquisition",
				SpimHalcyonNodeType.CONTROL,
				acquisitionPanel );

		addNode( control1 );

//		final HalcyonNode control2 = HalcyonNode.wrap( "3D",
//				SpimHalcyonNodeType.CONTROL,
//				new Stage3DPanel() );
//
//		addNode( control2 );

		// Custom DemoToolbar provided here
		ToolbarPanel lToolbar = new ToolbarPanel( studio, acquisitionPanel.roiRectangleProperty(), mmStudioProperty );
		lToolbar.setPrefSize(300, 200);
		addToolbar(lToolbar);

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

							cameraDevicePanel.setSetup( spimSetup, studio );
							lToolbar.setSetup( spimSetup, studio );
							javaEditor.setSetup(spimSetup, studio);
							arduinoPanel.setSetup(spimSetup, studio);
							acquisitionPanel.setStagePanel( stagePanel );
							acquisitionPanel.setSetup( spimSetup, studio );
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
						   cameraDevicePanel.setSetup( null, studio );
						   lToolbar.setSetup( null, studio );
						   javaEditor.setSetup( null, studio );
						   arduinoPanel.setSetup( null, studio );
						   acquisitionPanel.setStagePanel( null );
						   acquisitionPanel.setSetup( null, studio );
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

		return createHalcyonFrame( primaryStage );
	}

//	@Override
//	protected void callFrameClosed( WindowEvent closeEvent )
//	{
//		hide();
//	}

	public static void main(String[] args) throws Exception
	{
		launch(args);
	}
}
