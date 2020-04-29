package spim.ui.view.component.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import spim.model.data.TimePointItem;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2020
 */
public class TimePointChoiceBoxTableCell<S,T>  extends TableCell<S,T>
{
	public static <S,T> Callback< TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final ObservableList<T> items1) {
		return forTableColumn(null, items1);
	}
	public static <S,T> Callback< TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final StringConverter<T> converter,
			final ObservableList<T> items1) {
		return list -> new TimePointChoiceBoxTableCell<S,T>(converter, items1);
	}

	private final ObservableList<T> items1;

	private ChoiceBox<T> choiceBox;

	public TimePointChoiceBoxTableCell(ObservableList<T> items1) {
		this(null, items1);
	}

	public TimePointChoiceBoxTableCell(StringConverter<T> converter, ObservableList<T> items1) {
		this.getStyleClass().add("choice-box-table-cell");
		this.items1 = items1;
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

	/** {@inheritDoc} */
	@Override public void startEdit() {
		if (! isEditable() || ! getTableView().isEditable() || ! getTableColumn().isEditable()) {
			return;
		}

		choiceBox = createChoiceBox(this, items1, converterProperty());
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
			setStyle( "" );
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
				TableRow<T> row = getTableRow();
				T item = row.getItem();
				if( item instanceof TimePointItem ) {
					TimePointItem tItem = ( TimePointItem ) item;

					if(tItem.getType().equals( TimePointItem.Type.Wait ))
						setStyle( "-fx-background-color: -fx-background; -fx-background: #ffecea;" );
					else if(tItem.getType().equals( TimePointItem.Type.Acq ))
						setStyle( "-fx-background-color: -fx-background; -fx-background: #e0ffe4;" );
				}

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
