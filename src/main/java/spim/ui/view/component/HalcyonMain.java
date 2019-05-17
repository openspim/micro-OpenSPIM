package spim.ui.view.component;

import com.sun.javafx.css.StyleManager;
import halcyon.HalcyonFrame;
import halcyon.model.node.HalcyonNode;
import halcyon.model.node.HalcyonNodeType;
import halcyon.view.TreePanel;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.dockfx.DockNode;
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
				try
				{
					Thread.sleep(1000);
				}
				catch ( InterruptedException e )
				{
					e.printStackTrace();
				}
				show( primaryStage, null, null );
			}
		} );
	}

	public void show( Stage primaryStage, SPIMSetup setup, Studio gui )
	{
		// TODO: support other type of devices
		BorderPane borderPane = createHalcyonBorderPane( primaryStage, setup, gui );

		super.show( borderPane );
	}

	public BorderPane createHalcyonBorderPane( Stage primaryStage, SPIMSetup spimSetup, Studio studio )
	{
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

		StagePanel stagePanel = null;

		if(null != spimSetup)
		{
			if(spimSetup.getLaser() != null)
			{
				System.out.println(spimSetup.getLaser().getLabel());
				if(spimSetup.getLaser().getLabel().startsWith( "VLT_VersaLase" ))
				{
					final HalcyonNode lVersaLase = HalcyonNode.wrap( "VersaLase",
							SpimHalcyonNodeType.LASER,
							new VersaLaserDevicePanel( ( VersaLase ) spimSetup.getLaser() ) );

					addNode(lVersaLase);
				}
				else
				{
					final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
							SpimHalcyonNodeType.LASER,
							new LaserDevicePanel( spimSetup.getLaser(), 488 ) );

					addNode(lLaser1);
				}
			}

			if(spimSetup.getLaser2() != null)
			{
				final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
						SpimHalcyonNodeType.LASER,
						new LaserDevicePanel( spimSetup.getLaser2(), 594 ));
				addNode(lLaser2);
			}

			if(spimSetup.getCamera1() != null)
			{
				final HalcyonNode lCamera = HalcyonNode.wrap("Camera",
						SpimHalcyonNodeType.CAMERA,
						new CameraDevicePanel( spimSetup, studio ) );
				addNode(lCamera);
			}

			if(spimSetup.getZStage() != null)
			{
				stagePanel = new StagePanel(spimSetup);
				final HalcyonNode lStage1 = HalcyonNode.wrap("Stage-1",
						SpimHalcyonNodeType.STAGE,
						stagePanel );
				addNode(lStage1);
			}
		}
		else
		{
			final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
					SpimHalcyonNodeType.LASER,
					new LaserDevicePanel( null, 488 ) );

			final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
					SpimHalcyonNodeType.LASER,
					new LaserDevicePanel( null, 561 ));

			final HalcyonNode lCamera = HalcyonNode.wrap("Camera-1",
					SpimHalcyonNodeType.CAMERA,
					new CameraDevicePanel( spimSetup, null ) );

			final HalcyonNode lStage1 = HalcyonNode.wrap("Stage-1",
					SpimHalcyonNodeType.STAGE,
					new StagePanel(null) );

			addNode(lLaser1);
			addNode(lLaser2);
			addNode(lCamera);
			addNode(lStage1);
		}

		final HalcyonNode editor1 = HalcyonNode.wrap( "Java",
				SpimHalcyonNodeType.EDITOR,
				new JavaEditor( primaryStage, spimSetup, studio ) );

		addNode(editor1);

		AcquisitionPanel acquisitionPanel = new AcquisitionPanel( primaryStage, spimSetup, studio, stagePanel );
		final HalcyonNode control1 = HalcyonNode.wrap( "Acquisition",
				SpimHalcyonNodeType.CONTROL,
				acquisitionPanel );

		addNode( control1 );
//		final HalcyonNode control2 = HalcyonNode.wrap( "3D",
//				SpimHalcyonNodeType.CONTROL,
//				new Stage3DPanel() );
//
//		addNode( control2 );

		ArduinoPanel arduinoPanel = new ArduinoPanel( spimSetup, studio );
		final HalcyonNode arduino = HalcyonNode.wrap( "ArduinoUno", SpimHalcyonNodeType.SHUTTER, arduinoPanel );
		addNode( arduino );

		// Custom DemoToolbar provided here
		DockNode lToolbar = new ToolbarPanel();
		lToolbar.setPrefSize(300, 200);
		addToolbar(lToolbar);

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

	@Override
	protected void callFrameClosed( WindowEvent closeEvent )
	{
		hide();
	}

	public static void main(String[] args) throws Exception
	{
		launch(args);
	}
}
