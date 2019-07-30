package spim.ui.view.component.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: July 2019
 */
public class NumberFieldTableCell<S,T> extends TableCell<S,T>
{

	/***************************************************************************
	 *                                                                         *
	 * Static cell factories                                                   *
	 *                                                                         *
	 **************************************************************************/

	/**
	 * Provides a {@link TextField} that allows editing of the cell content when
	 * the cell is double-clicked, or when
	 * {@link TableView#edit(int, javafx.scene.control.TableColumn)} is called.
	 * This method will only  work on {@link TableColumn} instances which are of
	 * type String.
	 *
	 * @return A {@link Callback} that can be inserted into the
	 *      {@link TableColumn#cellFactoryProperty() cell factory property} of a
	 *      TableColumn, that enables textual editing of the content.
	 */
	public static <S> Callback<TableColumn<S,String>, TableCell<S,String>> forTableColumn() {
		return forTableColumn(new DefaultStringConverter());
	}

	/**
	 * Provides a {@link TextField} that allows editing of the cell content when
	 * the cell is double-clicked, or when
	 * {@link TableView#edit(int, javafx.scene.control.TableColumn) } is called.
	 * This method will work  on any {@link TableColumn} instance, regardless of
	 * its generic type. However, to enable this, a {@link StringConverter} must
	 * be provided that will convert the given String (from what the user typed
	 * in) into an instance of type T. This item will then be passed along to the
	 * {@link TableColumn#onEditCommitProperty()} callback.
	 *
	 * @param converter A {@link StringConverter} that can convert the given String
	 *      (from what the user typed in) into an instance of type T.
	 * @return A {@link Callback} that can be inserted into the
	 *      {@link TableColumn#cellFactoryProperty() cell factory property} of a
	 *      TableColumn, that enables textual editing of the content.
	 */
	public static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final StringConverter<T> converter) {
		return list -> new NumberFieldTableCell<S,T>(converter);
	}


	/***************************************************************************
	 *                                                                         *
	 * Fields                                                                  *
	 *                                                                         *
	 **************************************************************************/

	private TextField textField;



	/***************************************************************************
	 *                                                                         *
	 * Constructors                                                            *
	 *                                                                         *
	 **************************************************************************/

	/**
	 * Creates a default TextFieldTableCell with a null converter. Without a
	 * {@link StringConverter} specified, this cell will not be able to accept
	 * input from the TextField (as it will not know how to convert this back
	 * to the domain object). It is therefore strongly encouraged to not use
	 * this constructor unless you intend to set the converter separately.
	 */
	public NumberFieldTableCell() {
		this(null);
	}

	/**
	 * Creates a TextFieldTableCell that provides a {@link TextField} when put
	 * into editing mode that allows editing of the cell content. This method
	 * will work on any TableColumn instance, regardless of its generic type.
	 * However, to enable this, a {@link StringConverter} must be provided that
	 * will convert the given String (from what the user typed in) into an
	 * instance of type T. This item will then be passed along to the
	 * {@link TableColumn#onEditCommitProperty()} callback.
	 *
	 * @param converter A {@link StringConverter converter} that can convert
	 *      the given String (from what the user typed in) into an instance of
	 *      type T.
	 */
	public NumberFieldTableCell(StringConverter<T> converter) {
		this.getStyleClass().add("text-field-table-cell");
		setConverter(converter);
		this.addEventFilter( MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() > 1) {
					TableCell c = (TableCell) event.getSource();

					if (isEditing()) {
						if (textField == null) {
							textField = CellUtils.createTextField(c, getConverter());
						}

						CellUtils.startEdit(c, getConverter(), null, null, textField);
					}
				}
			}
		});
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

	/** {@inheritDoc} */
	@Override public void startEdit() {
		if (! isEditable()
				|| ! getTableView().isEditable()
				|| ! getTableColumn().isEditable()) {
			return;
		}
		super.startEdit();
//
//		if (isEditing()) {
//			if (textField == null) {
//				textField = CellUtils.createTextField(this, getConverter());
//			}
//
//			CellUtils.startEdit(this, getConverter(), null, null, textField);
//		}
	}

	/** {@inheritDoc} */
	@Override public void cancelEdit() {
		super.cancelEdit();
		CellUtils.cancelEdit(this, getConverter(), null);
	}

	/** {@inheritDoc} */
	@Override public void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);
		CellUtils.updateItem(this, getConverter(), null, null, textField);
	}
}
