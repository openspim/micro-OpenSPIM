package spim.ui.view.component.util;

import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Button;
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
import javafx.util.Callback;
import javafx.util.converter.NumberStringConverter;
import spim.model.data.ChannelItem;
import spim.model.data.PositionItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.AcquisitionPanel;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class TableViewUtil
{
	private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

	public static TableView< PositionItem > createPositionItemDataView( AcquisitionPanel acquisitionPanel )
	{
		TableView<PositionItem> tv = new TableView<>();

		TableColumn<PositionItem, Number> numberColumn = new TableColumn<>("X");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getX() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setX( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("Y");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getY() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setY( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("R");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getR() )
		);
		numberColumn.setCellFactory( TextFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setR( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		TableColumn<PositionItem, String> column = new TableColumn<>("Z");
		column.setPrefWidth(90);
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


		TableColumn<PositionItem, Void> btnColumn = new TableColumn<>("");
		btnColumn.setPrefWidth(100);
		Callback<TableColumn<PositionItem, Void>, TableCell<PositionItem, Void>> cellFactory = new Callback<TableColumn<PositionItem, Void>, TableCell<PositionItem, Void>>() {
			@Override
			public TableCell<PositionItem, Void> call(final TableColumn<PositionItem, Void> param) {
				final TableCell<PositionItem, Void> cell = new TableCell<PositionItem, Void>() {

					private final Button btn = new Button("Set Pos");

					{
						btn.setOnAction(( ActionEvent event) -> {
							PositionItem data = getTableView().getItems().get( getIndex() );
//							System.out.println( ControlEvent.STAGE_MOVE + " fired. Data: " + data);
							Event.fireEvent( acquisitionPanel, new ControlEvent( ControlEvent.STAGE_MOVE, data ) );
						});
					}

					@Override
					public void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							setGraphic(btn);
						}
					}
				};
				return cell;
			}
		};

		btnColumn.setCellFactory(cellFactory);

		tv.getColumns().add(btnColumn);

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

		TableColumn<ChannelItem, ChannelItem.Type> typeColumn = new TableColumn<>("Shutter");
		typeColumn.setPrefWidth(80);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList(ChannelItem.Type.Laser) ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );
		} );
		tv.getColumns().add(typeColumn);

		TableColumn<ChannelItem, String> column = new TableColumn<>("Name");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getName() )
		);
		column.setCellFactory( ChoiceBoxTableCell.forTableColumn(
				FXCollections.observableArrayList("Camera-1", "Camera-2") ) );
		column.setOnEditCommit( event -> event.getRowValue().setName( event.getNewValue() ) );
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

	public static TableView< ChannelItem > createChannelItemArduinoDataView()
	{
		TableView<ChannelItem> tv = new TableView<>();

		TableColumn<ChannelItem, Boolean> booleanColumn = new TableColumn<>("");
		booleanColumn.setPrefWidth( 50 );
		booleanColumn.setCellValueFactory( new PropertyValueFactory<>( "selected" ) );
		booleanColumn.setCellFactory( tc -> new CheckBoxTableCell<>() );
		booleanColumn.setOnEditCommit( event -> event.getRowValue().setSelected( event.getNewValue() ) );
		tv.getColumns().add(booleanColumn);

		TableColumn<ChannelItem, ChannelItem.Type> typeColumn = new TableColumn<>("Shutter");
		typeColumn.setPrefWidth(80);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList(ChannelItem.Type.Arduino) ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );
		} );
		tv.getColumns().add(typeColumn);

		TableColumn<ChannelItem, String> column = new TableColumn<>("Name");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getName() )
		);
		column.setCellFactory( ChoiceBoxTableCell.forTableColumn(
				FXCollections.observableArrayList( "Pin-13", "Pin-12", "Pin-11", "Pin-10", "Pin-9", "Pin-8") ) );
		column.setOnEditCommit( event -> event.getRowValue().setName( event.getNewValue() ) );
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
