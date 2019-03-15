package spim.ui.view.component.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class TableViewUtil
{
	private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

	public static TableView< PositionItem > createPositionItemDataView()
	{
		TableView<PositionItem> tv = new TableView<>();

		TableColumn<PositionItem, Number> numberColumn = new TableColumn<>("X");
		numberColumn.setPrefWidth(40);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getX() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setX( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("Y");
		numberColumn.setPrefWidth(40);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getX() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setY( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("R");
		numberColumn.setPrefWidth(40);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getX() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setR( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		TableColumn<PositionItem, String> column = new TableColumn<>("Z");
		column.setPrefWidth(120);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getZString() )
		);
		column.setCellFactory( TextFieldTableCell.forTableColumn() );
		column.setOnEditCommit( event -> {
			String zString = event.getNewValue();
			String[] tokens = zString.split( ":" );

			if(tokens.length == 3) {
				event.getRowValue().setZStart( Double.parseDouble( tokens[0] ) );
				event.getRowValue().setZStep( Double.parseDouble( tokens[1] ) );
				event.getRowValue().setZEnd( Double.parseDouble( tokens[2] ) );
			} else if(tokens.length == 1) {
				event.getRowValue().setZStart( Double.parseDouble( tokens[0] ) );
				event.getRowValue().setZStep( 1 );
				event.getRowValue().setZEnd( Double.parseDouble( tokens[0] ) );
			}

			// invoke selected item changed!
			int i = tv.getSelectionModel().getSelectedIndex();
			tv.getSelectionModel().clearSelection();
			tv.getSelectionModel().select( i );
			tv.refresh();
		} );
		column.setEditable( true );
		tv.getColumns().add(column);

		// Drag and drop
		tv.setRowFactory(ev -> {
			TableRow<PositionItem> row = new TableRow<>();

			row.setOnDragDetected(event -> {
				if (! row.isEmpty()) {
					Integer index = row.getIndex();
					Dragboard db = row.startDragAndDrop( TransferMode.MOVE);
					db.setDragView(row.snapshot(null, null));
					ClipboardContent cc = new ClipboardContent();
					cc.put(SERIALIZED_MIME_TYPE, index);
					db.setContent(cc);
					event.consume();
				}
			});

			row.setOnDragOver(event -> {
				Dragboard db = event.getDragboard();
				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					if (row.getIndex() != ((Integer)db.getContent(SERIALIZED_MIME_TYPE)).intValue()) {
						event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
						event.consume();
					}
				}
			});

			row.setOnDragDropped(event -> {
				Dragboard db = event.getDragboard();
				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
					PositionItem draggedPerson = tv.getItems().remove(draggedIndex);

					int dropIndex ;

					if (row.isEmpty()) {
						dropIndex = tv.getItems().size() ;
					} else {
						dropIndex = row.getIndex();
					}

					tv.getItems().add(dropIndex, draggedPerson);

					event.setDropCompleted(true);
					tv.getSelectionModel().select(dropIndex);
					event.consume();
				}
			});

			return row ;
		});

		return tv;
	}

	public static TableView< ChannelItem > createChannelItemDataView()
	{
		TableView<ChannelItem> tv = new TableView<>();

		TableColumn<ChannelItem, Boolean> booleanColumn = new TableColumn<>("");
		booleanColumn.setPrefWidth( 50 );
		booleanColumn.setCellValueFactory( new PropertyValueFactory<>( "selected" ) );
		booleanColumn.setCellFactory( tc -> new CheckBoxTableCell<>() );
		booleanColumn.setOnEditCommit( event -> event.getRowValue().setSelected( event.getNewValue() ) );
		tv.getColumns().add(booleanColumn);

		TableColumn<ChannelItem, ChannelItem.Type> typeColumn = new TableColumn<>("Type");
		typeColumn.setPrefWidth(80);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList(ChannelItem.Type.values()) ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );
			switch ( event.getNewValue() ) {
				case Arduino:
					event.getRowValue().setName( "Pin-13" );
					event.getRowValue().setLaser( "" );
					break;
				case Laser:
				default:
					event.getRowValue().setName( "Camera-1" );
					event.getRowValue().setName( "Laser-1" );
					break;
			}
			event.getTableView().refresh();
		} );
		tv.getColumns().add(typeColumn);

		TableColumn<ChannelItem, String> column = new TableColumn<>("Name");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getName() )
		);
		column.setCellFactory( SelectiveChoiceBoxTableCell.forTableColumn(
				FXCollections.observableArrayList("Camera-1", "Camera-2"),
				FXCollections.observableArrayList( "Pin-13", "Pin-12", "Pin-11", "Pin-10", "Pin-9", "Pin-8") ) );
		tv.getColumns().add(column);

		column = new TableColumn<>("Laser");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getLaser() )
		);
		column.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList("Laser-1", "Laser-2") ) );
		column.setOnEditCommit( event -> event.getRowValue().setLaser( event.getNewValue() ) );
		tv.getColumns().add(column);

		TableColumn<ChannelItem, Number> numberColumn = new TableColumn<>("Exposure");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getValue().doubleValue() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setValue( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		// Drag and drop
		tv.setRowFactory(ev -> {
			TableRow<ChannelItem> row = new TableRow<>();

			row.setOnDragDetected(event -> {
				if (! row.isEmpty()) {
					Integer index = row.getIndex();
					Dragboard db = row.startDragAndDrop( TransferMode.MOVE);
					db.setDragView(row.snapshot(null, null));
					ClipboardContent cc = new ClipboardContent();
					cc.put(SERIALIZED_MIME_TYPE, index);
					db.setContent(cc);
					event.consume();
				}
			});

			row.setOnDragOver(event -> {
				Dragboard db = event.getDragboard();
				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					if (row.getIndex() != ((Integer)db.getContent(SERIALIZED_MIME_TYPE)).intValue()) {
						event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
						event.consume();
					}
				}
			});

			row.setOnDragDropped(event -> {
				Dragboard db = event.getDragboard();
				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
					ChannelItem draggedItem = tv.getItems().remove(draggedIndex);

					int dropIndex ;

					if (row.isEmpty()) {
						dropIndex = tv.getItems().size() ;
					} else {
						dropIndex = row.getIndex();
					}

					tv.getItems().add(dropIndex, draggedItem);

					event.setDropCompleted(true);
					tv.getSelectionModel().select(dropIndex);
					event.consume();
				}
			});

			return row ;
		});

		return tv;
	}
}
