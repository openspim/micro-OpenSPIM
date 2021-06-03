package spim.ui.view.component.pane;

import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class CheckboxPane extends BorderPane
{
	final CheckBox topic;
	public CheckboxPane( String title, Node content, Node help )
	{
		this(title, content, help, 0);
	}

	public CheckboxPane( String title, Node content, Node help, int margin )
	{
		topic = new CheckBox( title );
		topic.setPadding( new Insets(5 ) );
		topic.setSelected( true );
		topic.setFont( Font.font("Verdana", FontWeight.BOLD, 13) );

		if(help != null)
			setTop( new HBox(5, topic, help) );
		else
			setTop( topic );

		if(margin > 0) {
			BorderPane.setAlignment(content, Pos.TOP_LEFT);
			BorderPane.setMargin(content, new Insets(12,12,12,12));
		}

		setCenter( content );

		content.disableProperty().bind( topic.selectedProperty().not() );
	}

	public void setSelected(boolean b) {
		topic.setSelected( b );
	}

	public BooleanProperty selectedProperty() {
		return topic.selectedProperty();
	}
}