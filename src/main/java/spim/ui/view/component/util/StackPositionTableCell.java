package spim.ui.view.component.util;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import spim.model.data.PositionItem;

/**
 * Description: To render different color for "Position" type and "Stack" type for Position Item
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2022
 */
public class StackPositionTableCell <S,T> extends TextFieldTableCell<S,T>
{
	public static <S> Callback<TableColumn<S,String>, TableCell<S,String>> forTableColumn() {
		return forTableColumn(new DefaultStringConverter());
	}

	public static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final StringConverter<T> converter) {
		return list -> new StackPositionTableCell<S,T>(converter);
	}

	static StackPositionTableCell lastCell;

	StackPositionTableCell(StringConverter<T> converter) {
		super(converter);
		this.addEventFilter( MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() > 1) {
					StackPositionTableCell c = (StackPositionTableCell) event.getSource();
					lastCell = c;
					lastCell.startSuperStartEdit();
				} else {
					if (lastCell != null && !lastCell.equals(event.getSource())) {
						lastCell.cancelEdit();
						lastCell = null;
					}
				}
			}
		});
	}

	@Override public void startEdit() {
		if (! isEditable()
				|| ! getTableView().isEditable()
				|| ! getTableColumn().isEditable()) {
			return;
		}
	}

	/** {@inheritDoc} */
	@Override public void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);
		this.updateItem(this, getConverter(), null);
	}

	<T> void updateItem(final Cell<T> cell,
						final StringConverter<T> converter,
						final Node graphic) {
		if (cell.isEmpty()) {
			cell.setText(null);
			cell.setGraphic(null);
			setStyle( "" );
		} else {
			if (cell.isEditing()) {
				cell.setText(null);
			} else {
				TableRow<T> row = getTableRow();
				T item = row.getItem();
				if( item instanceof PositionItem) {
					PositionItem tItem = ( PositionItem ) item;

					if(tItem.getZStart() == tItem.getZEnd())
						cell.setStyle( "-fx-background-color: -fx-background; -fx-background: #ffecea;" );
					else
						cell.setStyle( "-fx-background-color: -fx-background; -fx-background: #e0ffe4;" );
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

	void startSuperStartEdit() {
		super.startEdit();
	}
}