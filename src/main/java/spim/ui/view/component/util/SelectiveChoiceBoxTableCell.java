package spim.ui.view.component.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import spim.model.data.ChannelItem;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2019
 */
public class SelectiveChoiceBoxTableCell<S,T>  extends TableCell<S,T>
{
	public static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final ObservableList<T> items1, final ObservableList<T> items2) {
		return forTableColumn(null, items1, items2);
	}
	public static <S,T> Callback< TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final StringConverter<T> converter,
			final ObservableList<T> items1,
			final ObservableList<T> items2) {
		return list -> new SelectiveChoiceBoxTableCell<S,T>(converter, items1, items2);
	}

	private final ObservableList<T> items1;

	private final ObservableList<T> items2;

	private ChoiceBox<T> choiceBox;

	public SelectiveChoiceBoxTableCell(ObservableList<T> items1, ObservableList<T> items2) {
		this(null, items1, items2);
	}

	public SelectiveChoiceBoxTableCell(StringConverter<T> converter, ObservableList<T> items1, ObservableList<T> items2) {
		this.getStyleClass().add("choice-box-table-cell");
		this.items1 = items1;
		this.items2 = items2;
		setConverter(converter != null ? converter : this.<T>defaultStringConverter());
	}

	private final StringConverter<?> defaultStringConverter = new StringConverter<Object>() {
		@Override public String toString(Object t) {
			return t == null ? null : t.toString();
		}

		@Override public Object fromString(String string) {
			return (Object) string;
		}
	};

	<T> StringConverter<T> defaultStringConverter() {
		return (StringConverter<T>) defaultStringConverter;
	}



	/***************************************************************************
	 *                                                                         *
	 * Properties                                                              *
	 *                                                                         *
	 **************************************************************************/

	// --- converter
	private ObjectProperty<StringConverter<T>> converter =
			new SimpleObjectProperty<StringConverter<T>>(this, "converter");

	/**
	 * The {@link StringConverter} property.
	 */
	public final ObjectProperty<StringConverter<T>> converterProperty() {
		return converter;
	}

	/**
	 * Sets the {@link StringConverter} to be used in this cell.
	 */
	public final void setConverter(StringConverter<T> value) {
		converterProperty().set(value);
	}

	/**
	 * Returns the {@link StringConverter} used in this cell.
	 */
	public final StringConverter<T> getConverter() {
		return converterProperty().get();
	}



	/***************************************************************************
	 *                                                                         *
	 * Public API                                                              *
	 *                                                                         *
	 **************************************************************************/

	/**
	 * Returns the items to be displayed in the ChoiceBox when it is showing.
	 */
	public ObservableList<T> getItems1() {
		return items1;
	}

	public ObservableList<T> getItems2() {
		return items2;
	}

	/** {@inheritDoc} */
	@Override public void startEdit() {
		if (! isEditable() || ! getTableView().isEditable() || ! getTableColumn().isEditable()) {
			return;
		}

		TableRow<T> row = getTableRow();
		T item = row.getItem();
		if( item instanceof ChannelItem ) {
			ChannelItem cItem = ( ChannelItem ) item;
			switch ( cItem.getType() ) {
				case Arduino: choiceBox = createChoiceBox(this, items2, converterProperty());
					break;

				case Laser:
				default: choiceBox = createChoiceBox(this, items1, converterProperty());
					break;
			}
		}

		choiceBox.getSelectionModel().select(getItem());

		super.startEdit();
		setText(null);
		setGraphic(choiceBox);
	}

	/** {@inheritDoc} */
	@Override public void cancelEdit() {
		super.cancelEdit();

		setText(getConverter().toString(getItem()));
		setGraphic(null);
	}

	/** {@inheritDoc} */
	@Override public void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);
		this.updateItem(this, getConverter(), null, null, choiceBox);
	}

	<T> void updateItem(final Cell<T> cell,
			final StringConverter<T> converter,
			final HBox hbox,
			final Node graphic,
			final ChoiceBox<T> choiceBox) {
		if (cell.isEmpty()) {
			cell.setText(null);
			cell.setGraphic(null);
		} else {
			if (cell.isEditing()) {
				if (choiceBox != null) {
					choiceBox.getSelectionModel().select(cell.getItem());
				}
				cell.setText(null);

				if (graphic != null) {
					hbox.getChildren().setAll(graphic, choiceBox);
					cell.setGraphic(hbox);
				} else {
					cell.setGraphic(choiceBox);
				}
			} else {
				cell.setText(getItemText(cell, converter));
				cell.setGraphic(graphic);
			}
		}
	}

	private <T> String getItemText(Cell<T> cell, StringConverter<T> converter) {
		return converter == null ?
				cell.getItem() == null ? "" : cell.getItem().toString() :
				converter.toString(cell.getItem());
	}

	<T> ChoiceBox<T> createChoiceBox(
			final Cell<T> cell,
			final ObservableList<T> items,
			final ObjectProperty<StringConverter<T>> converter) {
		ChoiceBox<T> choiceBox = new ChoiceBox<T>(items);
		choiceBox.setMaxWidth(Double.MAX_VALUE);
		choiceBox.converterProperty().bind(converter);
		choiceBox.getSelectionModel().selectedItemProperty().addListener((ov, oldValue, newValue) -> {
			if (cell.isEditing()) {
				cell.commitEdit(newValue);
			}
		});
		return choiceBox;
	}
}