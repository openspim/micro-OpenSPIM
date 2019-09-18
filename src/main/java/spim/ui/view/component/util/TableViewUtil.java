package spim.ui.view.component.util;

import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;
import javafx.util.converter.NumberStringConverter;
import spim.hardware.SPIMSetup;
import spim.model.data.ChannelItem;
import spim.model.data.PinItem;
import spim.model.data.PositionItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.AcquisitionPanel;
import spim.ui.view.component.StagePanel;

import java.util.ArrayList;
import java.util.List;

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

		Callback<TableColumn<PositionItem, Number>, TableCell<PositionItem, Number>> factory =
				new Callback<TableColumn<PositionItem, Number>, TableCell<PositionItem, Number>>() {
					public TableCell call(TableColumn p) {
						TableCell cell = new TableCell<PositionItem, Number>() {
							@Override
							public void updateItem(Number item, boolean empty) {
								super.updateItem(item, empty);
								setText(empty ? null : getString());
								setGraphic(null);
							}

							private String getString() {
								return getItem() == null ? "" : getItem().toString();
							}
//							/** {@inheritDoc} */
//							@Override public void startEdit() {
//								if (! isEditable()
//										|| ! getTableView().isEditable()
//										|| ! getTableColumn().isEditable()) {
//									return;
//								}
//								super.startEdit();
//
//								if (isEditing()) {
//									if (textField == null) {
//										textField = CellUtils.createTextField(this, getConverter());
//									}
//
//									CellUtils.startEdit(this, getConverter(), null, null, textField);
//								}
//							}
//
//							/** {@inheritDoc} */
//							@Override public void cancelEdit() {
//								super.cancelEdit();
//								CellUtils.cancelEdit(this, getConverter(), null);
//							}
//
//							/** {@inheritDoc} */
//							@Override public void updateItem(T item, boolean empty) {
//								super.updateItem(item, empty);
//								CellUtils.updateItem(this, getConverter(), null, null, textField);
//							}
						};

						cell.addEventFilter( MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
							@Override
							public void handle(MouseEvent event) {
								if (event.getClickCount() > 1) {
									System.out.println("double clicked!");
									TableCell c = (TableCell) event.getSource();
									c.startEdit();
									System.out.println("Cell text: " + c.getText());
								}
							}
						});
						return cell;
					}
				};

		TableColumn<PositionItem, Number> numberColumn = new TableColumn<>("X");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getX() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter( ) ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setX( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("Y");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getY() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setY( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("R");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getR() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setR( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		TableColumn<PositionItem, String> column = new TableColumn<>("Z");
		column.setPrefWidth(90);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getZString() )
		);
		column.setCellFactory( NumberFieldTableCell.forTableColumn() );
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

					private final Button btn = new Button("Go To");

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

	public static TableView< ChannelItem > createChannelItemDataView( SPIMSetup setup )
	{
		ObservableList<String> cameras = FXCollections.observableArrayList();
		ObservableList<String> lasers = FXCollections.observableArrayList();

		if(setup == null) {
			cameras.addAll( "Camera-1", "Camera-2" );
			lasers.addAll( "Laser-1", "Laser-2" );
		} else {
			if ( setup.getCamera1() != null )
			{
				cameras.add( setup.getCamera1().getLabel() );
			}

			if ( setup.getCamera2() != null )
			{
				cameras.add( setup.getCamera2().getLabel() );
			}

			if ( setup.getLaser() != null )
			{
				lasers.add( setup.getLaser().getLabel() );
			}

			if ( setup.getLaser2() != null )
			{
				lasers.add( setup.getLaser2().getLabel() );
			}
		}

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
		column.setCellFactory( ChoiceBoxTableCell.forTableColumn( cameras ) );
		column.setOnEditCommit( event -> event.getRowValue().setName( event.getNewValue() ) );
		tv.getColumns().add(column);

		column = new TableColumn<>("Laser");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getLaser() )
		);
		column.setCellFactory( ChoiceBoxTableCell.forTableColumn( lasers ) );
		column.setOnEditCommit( event -> event.getRowValue().setLaser( event.getNewValue() ) );
		tv.getColumns().add(column);

		TableColumn<ChannelItem, Number> numberColumn = new TableColumn<>("Exposure");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getValue().doubleValue() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
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
		column.setPrefWidth(150);
		column.setCellValueFactory( (param) ->
				param.getValue().nameProperty()
		);
		tv.getColumns().add(column);

		TableColumn<ChannelItem, Number> numberColumn = new TableColumn<>("Exposure");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getValue().doubleValue() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setValue( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		return tv;
	}

	public static TableView< PinItem > createPinItemArduinoDataView()
	{
		TableView<PinItem> tv = new TableView<>();

		TableColumn<PinItem, String> column = new TableColumn<>("Pin");
		column.setPrefWidth(100);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getPin() )
		);
		tv.getColumns().add(column);

		TableColumn<PinItem, Number> numberColumn = new TableColumn<>("Switch-State");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getState() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setState( event.getNewValue().intValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);

		column = new TableColumn<>("Hardware component");
		column.setPrefWidth(170);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getPinName() )
		);
		column.setCellFactory( NumberFieldTableCell.forTableColumn() );
		column.setOnEditCommit( event -> event.getRowValue().setPinName( event.getNewValue() ) );
		column.setEditable( true );

		tv.getColumns().add(column);
		tv.setId( "arduino" );

		return tv;
	}
}
