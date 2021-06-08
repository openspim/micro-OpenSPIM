package spim.ui.view.component.util;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.converter.NumberStringConverter;
import spim.hardware.SPIMSetup;
import spim.model.data.ChannelItem;
import spim.model.data.DeviceItem;
import spim.model.data.PinItem;
import spim.model.data.PositionItem;
import spim.model.data.TimePointItem;
import spim.model.event.ControlEvent;
import spim.ui.view.component.AcquisitionPanel;

import java.util.Arrays;

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

		TableColumn<PositionItem, Number> numberColumn = new TableColumn<>( "#" );
		numberColumn.setPrefWidth( 20 );
		numberColumn.setCellValueFactory(p -> new ReadOnlyIntegerWrapper(tv.getItems().indexOf(p.getValue()) + 1));
		numberColumn.setSortable(false);
		tv.getColumns().add(numberColumn);

		numberColumn = new TableColumn<>("X");
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

		TableColumn<PositionItem, String> column = new TableColumn<>("Z");
		column.setPrefWidth(90);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getZString() )
		);
		column.setCellFactory( NumberFieldTableCell.forTableColumn() );
		column.setOnEditCommit( event -> {
			String zString = event.getNewValue();
			String[] tokens = zString.split( "-" );

			if(tokens.length == 2) {
				event.getRowValue().setZStart( Double.parseDouble( tokens[0] ) );
				event.getRowValue().setZEnd( Double.parseDouble( tokens[1] ) );
			} else if(tokens.length == 1) {
				event.getRowValue().setZStart( Double.parseDouble( tokens[0] ) );
				event.getRowValue().setZStep( 1 );
				event.getRowValue().setZEnd( Double.parseDouble( tokens[0] ) );
			}

			// invoke selected item changed!
			cascadeUpdatedValues( acquisitionPanel, tv );
		} );
		column.setEditable( true );
		tv.getColumns().add(column);

		numberColumn = new TableColumn<>("Z-Step");
		numberColumn.setPrefWidth(50);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getZStep() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> {
			event.getRowValue().setZStep( event.getNewValue().doubleValue() );
			cascadeUpdatedValues( acquisitionPanel, tv );
		});
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

		TableColumn<PositionItem, Void> btnColumn = new TableColumn<>("");
		btnColumn.setPrefWidth(130);
		Callback<TableColumn<PositionItem, Void>, TableCell<PositionItem, Void>> cellFactory = new Callback<TableColumn<PositionItem, Void>, TableCell<PositionItem, Void>>() {
			@Override
			public TableCell<PositionItem, Void> call(final TableColumn<PositionItem, Void> param) {
				final TableCell<PositionItem, Void> cell = new TableCell<PositionItem, Void>() {

					private final Button goTo = new Button("Go To");
					private final Button up = new Button();
					private final Button down = new Button();

					{
						goTo.setOnAction(( ActionEvent event) -> {
							PositionItem data = getTableView().getItems().get( getIndex() );
//							System.out.println( ControlEvent.STAGE_MOVE + " fired. Data: " + data);
							Event.fireEvent( acquisitionPanel, new ControlEvent( ControlEvent.STAGE_MOVE, data ) );
						});

						FontAwesomeIconView icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_UP );
						up.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						up.setGraphic(icon);
						up.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							PositionItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == 0) getTableView().getItems().add( 0, data );
							else getTableView().getItems().add( idx - 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );

						icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOWN );
						down.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						down.setGraphic(icon);
						down.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							PositionItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == getTableView().getItems().size()) getTableView().getItems().add( data );
							else if(idx < getTableView().getItems().size()) getTableView().getItems().add( idx + 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );
					}


					@Override
					public void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							setGraphic(new HBox( 5, goTo, up, down ));
						}
					}
				};
				return cell;
			}
		};

		btnColumn.setCellFactory(cellFactory);

		tv.getColumns().add(btnColumn);

		// Drag and drop
		tv.setRowFactory(createDragNDropRowFactory(tv));

		return tv;
	}

	public static TableView< ChannelItem > createChannelItemDataView( SPIMSetup setup, String currentCamera )
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

			if ( currentCamera != null && !cameras.contains( currentCamera ) )
				cameras.add(currentCamera);

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

		TableColumn<ChannelItem, Number> numberColumn = new TableColumn<>( "#" );
		numberColumn.setPrefWidth( 20 );
		numberColumn.setCellValueFactory(p -> new ReadOnlyIntegerWrapper(tv.getItems().indexOf(p.getValue()) + 1));
		numberColumn.setSortable(false);
		tv.getColumns().add(numberColumn);

		TableColumn<ChannelItem, ChannelItem.Type> typeColumn = new TableColumn<>("Shutter");
		typeColumn.setPrefWidth(80);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList(ChannelItem.Type.Laser) ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );
		} );
		tv.getColumns().add(typeColumn);

		TableColumn<ChannelItem, String> column = new TableColumn<>("Channel Name");
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

		numberColumn = new TableColumn<>("Exposure");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getValue().doubleValue() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setValue( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);


		TableColumn<ChannelItem, Void> btnColumn = new TableColumn<>("");
		btnColumn.setPrefWidth(130);
		Callback<TableColumn<ChannelItem, Void>, TableCell<ChannelItem, Void>> cellFactory = new Callback<TableColumn<ChannelItem, Void>, TableCell<ChannelItem, Void>>() {
			@Override
			public TableCell<ChannelItem, Void> call(final TableColumn<ChannelItem, Void> param) {
				final TableCell<ChannelItem, Void> cell = new TableCell<ChannelItem, Void>() {

					private final Button up = new Button();
					private final Button down = new Button();

					{
						FontAwesomeIconView icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_UP );
						up.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						up.setGraphic(icon);
						up.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							ChannelItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == 0) getTableView().getItems().add( 0, data );
							else getTableView().getItems().add( idx - 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );

						icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOWN );
						down.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						down.setGraphic(icon);
						down.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							ChannelItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == getTableView().getItems().size()) getTableView().getItems().add( data );
							else if(idx < getTableView().getItems().size()) getTableView().getItems().add( idx + 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );
					}


					@Override
					public void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							setGraphic(new HBox( 5, up, down ));
						}
					}
				};
				return cell;
			}
		};

		btnColumn.setCellFactory(cellFactory);

		tv.getColumns().add(btnColumn);

		// Drag and drop
		tv.setRowFactory(createDragNDropRowFactory(tv));

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

		TableColumn<ChannelItem, Number> numberColumn = new TableColumn<>( "#" );
		numberColumn.setPrefWidth( 20 );
		numberColumn.setCellValueFactory(p -> new ReadOnlyIntegerWrapper(tv.getItems().indexOf(p.getValue()) + 1));
		numberColumn.setSortable(false);
		tv.getColumns().add(numberColumn);

		TableColumn<ChannelItem, ChannelItem.Type> typeColumn = new TableColumn<>("Shutter");
		typeColumn.setPrefWidth(80);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( FXCollections.observableArrayList(ChannelItem.Type.Arduino) ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );
		} );
		tv.getColumns().add(typeColumn);

		TableColumn<ChannelItem, String> column = new TableColumn<>("Channel Name");
		column.setPrefWidth(150);
		column.setCellValueFactory( (param) ->
				param.getValue().nameProperty()
		);
		tv.getColumns().add(column);

		numberColumn = new TableColumn<>("Exposure");
		numberColumn.setPrefWidth(100);
		numberColumn.setCellValueFactory( (param) ->
				new ReadOnlyDoubleWrapper( param.getValue().getValue().doubleValue() )
		);
		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
		numberColumn.setOnEditCommit( event -> event.getRowValue().setValue( event.getNewValue().doubleValue() ) );
		numberColumn.setEditable( true );
		tv.getColumns().add(numberColumn);


		TableColumn<ChannelItem, Void> btnColumn = new TableColumn<>("");
		btnColumn.setPrefWidth(130);
		Callback<TableColumn<ChannelItem, Void>, TableCell<ChannelItem, Void>> cellFactory = new Callback<TableColumn<ChannelItem, Void>, TableCell<ChannelItem, Void>>() {
			@Override
			public TableCell<ChannelItem, Void> call(final TableColumn<ChannelItem, Void> param) {
				final TableCell<ChannelItem, Void> cell = new TableCell<ChannelItem, Void>() {

					private final Button up = new Button();
					private final Button down = new Button();

					{
						FontAwesomeIconView icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_UP );
						up.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						up.setGraphic(icon);
						up.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							ChannelItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == 0) getTableView().getItems().add( 0, data );
							else getTableView().getItems().add( idx - 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );

						icon = new FontAwesomeIconView( FontAwesomeIcon.ANGLE_DOWN );
						down.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );
						down.setGraphic(icon);
						down.setOnAction( event -> {
							int idx = getIndex();
//							System.out.println(idx);
							ChannelItem data = getTableView().getItems().get( idx );
							getTableView().getItems().remove( idx );
							if(idx == getTableView().getItems().size()) getTableView().getItems().add( data );
							else if(idx < getTableView().getItems().size()) getTableView().getItems().add( idx + 1, data );
							getTableView().getSelectionModel().select( data );
							getTableView().requestFocus();
						} );
					}


					@Override
					public void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							setGraphic(new HBox( 5, up, down ));
						}
					}
				};
				return cell;
			}
		};

		btnColumn.setCellFactory(cellFactory);

		tv.getColumns().add(btnColumn);

		// Drag and drop
		tv.setRowFactory(createDragNDropRowFactory(tv));

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

	public static TableView< TimePointItem > createTimePointItemDataView() {
		TableView<TimePointItem> tv = new TableView<>();

		TableColumn<TimePointItem, Number> numberColumn = new TableColumn<>( "#" );
		numberColumn.setPrefWidth( 20 );
		numberColumn.setCellValueFactory(new Callback< TableColumn.CellDataFeatures<TimePointItem, Number>, ObservableValue<Number> >() {
			@Override public ObservableValue<Number> call( TableColumn.CellDataFeatures<TimePointItem, Number> p) {
				return new ReadOnlyIntegerWrapper(tv.getItems().indexOf(p.getValue()) + 1);
			}
		});
		numberColumn.setSortable(false);
		tv.getColumns().add(numberColumn);

		ObservableList<TimePointItem.Type> types = FXCollections.observableArrayList();
		ObservableList<TimePointItem.IntervalUnit> units = FXCollections.observableArrayList();

		types.addAll( Arrays.asList( TimePointItem.Type.values() ) );
		units.addAll( Arrays.asList( TimePointItem.IntervalUnit.values() ) );

		TableColumn<TimePointItem, TimePointItem.Type> typeColumn = new TableColumn<>();
		Label columnLabel = new Label("Type");
		columnLabel.setTooltip(new Tooltip("Choose either Acq(acquisition) or Wait(wait for specific time)"));
		typeColumn.setGraphic( columnLabel );
		typeColumn.setSortable(false);
		typeColumn.setPrefWidth(60);
		typeColumn.setCellValueFactory( new PropertyValueFactory<>( "type" ) );
		typeColumn.setCellFactory( TimePointChoiceBoxTableCell.forTableColumn( types ) );
		typeColumn.setOnEditCommit( event -> {
			event.getRowValue().setType( event.getNewValue() );

			if(event.getRowValue().getType().equals( TimePointItem.Type.Wait )) {
				event.getRowValue().setNoTimePoints( 0 );
			}
			event.getTableView().refresh();
		} );
		tv.getColumns().add(typeColumn);


		TableColumn<TimePointItem, Void> optionColumn = new TableColumn<>("Plan");
		optionColumn.setPrefWidth(500);
		Callback<TableColumn<TimePointItem, Void>, TableCell<TimePointItem, Void>> cellFactory = new Callback<TableColumn<TimePointItem, Void>, TableCell<TimePointItem, Void>>() {
			@Override
			public TableCell<TimePointItem, Void> call(final TableColumn<TimePointItem, Void> param) {
				final TableCell<TimePointItem, Void> cell = new TableCell<TimePointItem, Void>() {
					HBox createHbox()
					{
						HBox hbox = null;
						TimePointItem data = getTableView().getItems().get( getIndex() );
						if(data != null) {
							hbox = new HBox();
							hbox.setAlignment( Pos.CENTER_LEFT );

							NumberStringConverter converter = new NumberStringConverter();

							if(data.getType().equals( TimePointItem.Type.Acq )) {
								TextField tp = new TextField();
								tp.setMaxWidth( 50 );
								tp.textProperty().bindBidirectional( data.getNoTimePointsProperty(), converter );

								hbox.getChildren().addAll( new Label( "No. Timepoints: " ), tp, new Label( "  Interval: " ) );
							} else if(data.getType().equals( TimePointItem.Type.Wait )) {
								hbox.getChildren().addAll( new Label( "Wait for: " ));
							}

							TextField interval = new TextField();
							interval.setMaxWidth( 50 );
							interval.textProperty().bindBidirectional( data.getIntervalProperty(), converter );

							ComboBox<TimePointItem.IntervalUnit> comboBox = new ComboBox<>();
							comboBox.getItems().setAll( TimePointItem.IntervalUnit.values() );

							comboBox.valueProperty().bindBidirectional( data.getIntervalUnitProperty() );

							hbox.getChildren().addAll( interval, comboBox );
						}
						return hbox;
					}

					@Override
					public void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							setGraphic(createHbox());
						}
					}
				};
				return cell;
			}
		};
		optionColumn.setCellFactory( cellFactory );
		tv.getColumns().add(optionColumn);

//		numberColumn = new TableColumn<>();
//		columnLabel = new Label("Int.");
//		columnLabel.setTooltip(new Tooltip("Acq: interval value between each time points.\nWait: wait for this value and go to the next step."));
//		numberColumn.setGraphic(columnLabel);
//
//		numberColumn.setSortable(false);
//		numberColumn.setPrefWidth(50);
//		numberColumn.setCellValueFactory( (param) ->
//				new ReadOnlyIntegerWrapper( param.getValue().getInterval() )
//		);
//		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
//		numberColumn.setOnEditCommit( event -> event.getRowValue().setInterval( event.getNewValue().intValue() ) );
//		numberColumn.setEditable( true );
//		tv.getColumns().add(numberColumn);
//
//		TableColumn<TimePointItem, TimePointItem.IntervalUnit> intervalTypeColumn = new TableColumn<>();
//		columnLabel = new Label("Unit");
//		columnLabel.setTooltip(new Tooltip("Interval time unit among ms(milli-second), sec(second), min(minute), hour(hour)"));
//		intervalTypeColumn.setGraphic( columnLabel );
//
//		intervalTypeColumn.setSortable(false);
//		intervalTypeColumn.setPrefWidth(55);
//		intervalTypeColumn.setCellValueFactory( new PropertyValueFactory<>( "intervalUnit" ) );
//		intervalTypeColumn.setCellFactory( ChoiceBoxTableCell.forTableColumn( units ) );
//		intervalTypeColumn.setOnEditCommit( event -> {
//			event.getRowValue().setIntervalUnit( event.getNewValue() );
//		} );
//		tv.getColumns().add(intervalTypeColumn);
//
//		numberColumn = new TableColumn<>();
//		columnLabel = new Label("Timepoints");
//		columnLabel.setTooltip(new Tooltip("Set the number of timepoints for Acquisition type.\nThis value is ignored for Wait type."));
//		numberColumn.setGraphic( columnLabel );
//
//		numberColumn.setSortable(false);
//		numberColumn.setPrefWidth(80);
//		numberColumn.setCellValueFactory( new PropertyValueFactory<>( "noTimePoints" ) );
//		numberColumn.setCellFactory( NumberFieldTableCell.forTableColumn( new NumberStringConverter() ) );
//		numberColumn.setOnEditCommit( event -> event.getRowValue().setNoTimePoints( event.getNewValue().intValue() ) );
//		numberColumn.setEditable( true );
//		tv.getColumns().add(numberColumn);

		// Drag and drop
		tv.setRowFactory(createDragNDropRowFactory(tv));

		return tv;
	}

	public static TableView< DeviceItem > createDeviceItemTableView() {
		TableView<DeviceItem> tv = new TableView<>(  );

		TableColumn<DeviceItem, String> column = new TableColumn<>("Property");
		column.setPrefWidth(300);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getName() )
		);
		tv.getColumns().add(column);

		column = new TableColumn<>("Value");
		column.setPrefWidth(200);
		column.setCellValueFactory( (param) ->
				new ReadOnlyStringWrapper( param.getValue().getValue() )
		);
		tv.getColumns().add(column);

		for (TableColumn<DeviceItem, ?> col : tv.getColumns()) {
			addTooltipToColumnCells(col);
		}

		return tv;
	}


	/////////////////////////////////////////////////
	// There are Private Utility functions from here
	/////////////////////////////////////////////////
	private static <T> Callback< TableView<T>, TableRow<T>> createDragNDropRowFactory(TableView<T> tv)
	{
		return new Callback< TableView<T>, TableRow<T> >()
		{
			@Override public TableRow< T > call( TableView< T > ev )
			{
				TableRow< T > row = new TableRow<>();

				row.setOnDragDetected( event -> {
					if ( !row.isEmpty() )
					{
						Integer index = row.getIndex();
						Dragboard db = row.startDragAndDrop( TransferMode.MOVE );
						db.setDragView( row.snapshot( null, null ) );
						ClipboardContent cc = new ClipboardContent();
						cc.put( SERIALIZED_MIME_TYPE, index );
						db.setContent( cc );
						event.consume();
					}
				} );

				row.setOnDragOver( event -> {
					Dragboard db = event.getDragboard();
					if ( db.hasContent( SERIALIZED_MIME_TYPE ) )
					{
						if ( row.getIndex() != ( ( Integer ) db.getContent( SERIALIZED_MIME_TYPE ) ).intValue() )
						{
							event.acceptTransferModes( TransferMode.COPY_OR_MOVE );
							event.consume();
						}
					}
				} );

				row.setOnDragDropped( event -> {
					Dragboard db = event.getDragboard();
					if ( db.hasContent( SERIALIZED_MIME_TYPE ) )
					{
						int draggedIndex = ( Integer ) db.getContent( SERIALIZED_MIME_TYPE );
						T draggedPerson = tv.getItems().remove( draggedIndex );

						int dropIndex;

						if ( row.isEmpty() )
						{
							dropIndex = tv.getItems().size();
						}
						else
						{
							dropIndex = row.getIndex();
						}

						tv.getItems().add( dropIndex, draggedPerson );

						event.setDropCompleted( true );
						tv.getSelectionModel().select( dropIndex );
						event.consume();
					}
				} );

				return row;
			}
		};
	}

	private static void cascadeUpdatedValues( AcquisitionPanel acquisitionPanel, TableView< PositionItem > tv )
	{
		int i = tv.getSelectionModel().getSelectedIndex();
		tv.getSelectionModel().clearSelection();
		tv.getSelectionModel().select( i );
		acquisitionPanel.computeTotalPositionImages();
		tv.refresh();
	}

	private static <DT, T> void addTooltipToColumnCells(TableColumn<DT,T> column) {

		Callback<TableColumn<DT, T>, TableCell<DT,T>> existingCellFactory
				= column.getCellFactory();

		column.setCellFactory(c -> {
			TableCell<DT, T> cell = existingCellFactory.call(c);

			Tooltip tooltip = new Tooltip();
			// can use arbitrary binding here to make text depend on cell
			// in any way you need:
			tooltip.textProperty().bind(cell.itemProperty().asString());

			cell.setTooltip(tooltip);
			return cell ;
		});
	}
}
