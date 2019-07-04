package spim.ui.view.component;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.dockfx.DockNode;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode
{

	public ToolbarPanel()
	{
		super(new VBox());
		getDockTitleBar().setVisible(false);

		setTitle("OpenSPIM");

		final VBox box = (VBox) getContents();
		box.setSpacing( 20 );

		Button btn = new Button("Test Std Out");
		btn.setOnAction(e -> {

			for (int i = 0; i < 2000; i++)
			{
				System.out.println("" + i + " " + "Console Test");
			}

		});

		box.getChildren().add(btn);

		btn = new Button("Test Std Err");
		btn.setOnAction(e -> {

			for (int i = 0; i < 2000; i++)
			{
				System.err.println("" + i + " " + "Console Test");
			}

		});

		box.getChildren().add(btn);

		// Wavelength color check
		btn = new Button("488");
		btn.setStyle("-fx-background-color: #0FAFF0");
		box.getChildren().add(btn);
	}
}