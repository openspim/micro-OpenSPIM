package spim.ui.view.component;

import halcyon.HalcyonFrame;
import halcyon.model.node.HalcyonNode;
import halcyon.model.node.HalcyonNodeType;
import halcyon.view.TreePanel;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.dockfx.DockNode;
import org.micromanager.Studio;
import spim.hardware.SPIMSetup;

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
		show( primaryStage, null, null );
	}

	public void show( Stage primaryStage, SPIMSetup setup, Studio gui )
	{
		// TODO: support other type of devices
		BorderPane borderPane = createHalcyonBorderPane( primaryStage, setup, gui );

		super.show( borderPane );
	}

	public BorderPane createHalcyonBorderPane( Stage primaryStage, SPIMSetup setup, Studio gui )
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

		if(null != setup)
		{
			if(setup.getLaser() != null)
			{
				final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
						SpimHalcyonNodeType.LASER,
						new LaserDevicePanel( setup.getLaser(), 488 ) );

				addNode(lLaser1);
			}

			if(setup.getLaser2() != null)
			{
				final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
						SpimHalcyonNodeType.LASER,
						new LaserDevicePanel( setup.getLaser2(), 594 ));
				addNode(lLaser2);
			}

			if(setup.getCamera() != null)
			{
				final HalcyonNode lCamera = HalcyonNode.wrap("Camera",
						SpimHalcyonNodeType.CAMERA,
						new CameraDevicePanel( gui ) );
				addNode(lCamera);
			}

			if(setup.getZStage() != null)
			{
				final HalcyonNode lStage1 = HalcyonNode.wrap("Stage-1",
						SpimHalcyonNodeType.STAGE,
						new StagePanel(setup) );
				addNode(lStage1);
			}
		}
		else
		{
			final HalcyonNode lLaser1 = HalcyonNode.wrap( "Laser-1",
					SpimHalcyonNodeType.LASER,
					new LaserDevicePanel( null, 594 ) );

			final HalcyonNode lLaser2 = HalcyonNode.wrap("Laser-2",
					SpimHalcyonNodeType.LASER,
					new LaserDevicePanel( null, 488 ));

			final HalcyonNode lCamera = HalcyonNode.wrap("Camera-1",
					SpimHalcyonNodeType.CAMERA,
					new CameraDevicePanel( null ) );

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
				new JavaEditor(primaryStage, gui) );

		addNode(editor1);

		// Custom DemoToolbar provided here
		DockNode lToolbar = new ToolbarPanel();
		lToolbar.setPrefSize(300, 200);
		addToolbar(lToolbar);

		primaryStage.getIcons()
				.add(new Image(getClass().getResourceAsStream(ResourceUtil.getString("app.icon"))));

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
