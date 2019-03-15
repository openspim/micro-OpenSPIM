package spim.ui.view.component.pane;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2017
 */
public class LabeledPane extends BorderPane
{
	public LabeledPane( String title, Node content )
	{
		Label topic = new Label( title );
		topic.setPadding( new Insets(5 ) );
		topic.setLabelFor( this );
		topic.setFont( Font.font("Verdana", FontWeight.BOLD, 13) );

		setTop( topic );
		BorderPane.setAlignment(content, Pos.TOP_LEFT);
		BorderPane.setMargin(content, new Insets(12,12,12,12));
		setCenter( content );
	}
}