package spim.ui.view.component.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: August 2023
 */
public class EditTextFieldTableCell<S,T> extends TableCell<S,T>
{
	private TextField textField;
	private ObjectProperty<StringConverter<T>> converter;
	private boolean isCancelling = false;

	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forTableColumn() {
		return forTableColumn(new DefaultStringConverter());
	}

	public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> forTableColumn(StringConverter<T> var0) {
		return (var1) -> {
			return new EditTextFieldTableCell(var0);
		};
	}

	public EditTextFieldTableCell() {
		this((StringConverter)null);
	}

	public EditTextFieldTableCell(StringConverter<T> var1) {
		this.converter = new SimpleObjectProperty(this, "converter");
		this.getStyleClass().add("text-field-table-cell");
		this.setConverter(var1);
	}

	public final ObjectProperty<StringConverter<T>> converterProperty() {
		return this.converter;
	}

	public final void setConverter(StringConverter<T> var1) {
		this.converterProperty().set(var1);
	}

	public final StringConverter<T> getConverter() {
		return (StringConverter)this.converterProperty().get();
	}

	public void startEdit() {
		if (this.isEditable() && this.getTableView().isEditable() && this.getTableColumn().isEditable()) {
			super.startEdit();
			if (this.isEditing()) {
				if (this.textField == null) {
					this.textField = CellUtils.createTextField(this, this.getConverter());
					textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
						if (!isNowFocused) {
							this.commitEdit(getConverter().fromString(this.textField.getText()));
						}
					});
				}

				CellUtils.startEdit(this, this.getConverter(), (HBox)null, (Node)null, this.textField);
			}
		}
	}

	static void requestFocusOnControlOnlyIfCurrentFocusOwnerIsChild(Control var0) {
		Scene var1 = var0.getScene();
		Node var2 = var1 == null ? null : var1.getFocusOwner();
		if (var2 == null) {
			var0.requestFocus();
		} else if (!var0.equals(var2)) {
			for(Parent var3 = var2.getParent(); var3 != null; var3 = var3.getParent()) {
				if (var0.equals(var3)) {
					var0.requestFocus();
					break;
				}
			}
		}

	}

	public void commitEdit(T var1) {
		if (this.isEditing()) {
			TableView var2 = this.getTableView();
			if (var2 != null) {
				TableColumn.CellEditEvent var3 = new TableColumn.CellEditEvent(var2, var2.getEditingCell(), TableColumn.editCommitEvent(), var1);
				Event.fireEvent(this.getTableColumn(), var3);
			}

			super.commitEdit(var1);
			this.updateItem(var1, true);
			if (var2 != null) {
				var2.edit(-1, (TableColumn)null);
				requestFocusOnControlOnlyIfCurrentFocusOwnerIsChild(var2);
			}
		} else {
			TableView var2 = this.getTableView();
			if (var2 != null) {
				TablePosition position = new TablePosition(var2, this.getTableRow().getIndex(), this.getTableColumn());
				TableColumn.CellEditEvent var3 = new TableColumn.CellEditEvent(var2, position, TableColumn.editCommitEvent(), var1);
				Event.fireEvent(this.getTableColumn(), var3);
			}

			this.updateItem(var1, true);
		}
	}

	public void cancelEdit() {
		isCancelling = true;
		super.cancelEdit();
		CellUtils.cancelEdit(this, this.getConverter(), (Node)null);
	}

	public void updateItem(T var1, boolean var2) {
		super.updateItem(var1, var2);
		CellUtils.updateItem(this, this.getConverter(), (HBox)null, (Node)null, this.textField);
	}
}
