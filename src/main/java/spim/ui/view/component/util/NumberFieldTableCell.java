package spim.ui.view.component.util;

import javafx.event.EventHandler;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: July 2019
 */
public class NumberFieldTableCell<S,T> extends TextFieldTableCell<S,T>
{
	public static <S> Callback<TableColumn<S,String>, TableCell<S,String>> forTableColumn() {
		return forTableColumn(new DefaultStringConverter());
	}

	public static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> forTableColumn(
			final StringConverter<T> converter) {
		return list -> new NumberFieldTableCell<S,T>(converter);
	}

	NumberFieldTableCell(StringConverter<T> converter) {
		super(converter);
		this.addEventFilter( MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() > 1) {
					NumberFieldTableCell c = (NumberFieldTableCell) event.getSource();

					c.startSuperStartEdit();
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

	void startSuperStartEdit() {
		super.startEdit();
	}
}
