/**
 * 
 */
package spim.gui.model;

import java.util.Iterator;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import spim.acquisition.Row;
import spim.hardware.SPIMSetup.SPIMDevice;

/**
 * @author Luke Stuyvenberg
 * 
 */
public class StepTable extends AbstractTableModel implements
		Iterable<Row > {
	private static final long serialVersionUID = -7369997010095627461L;

	private SPIMDevice[] devices;
	private Vector<Row > data;

	public StepTable( SPIMDevice... devices ) {
		super();

		this.devices = devices;
		data = new Vector<Row >();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.table.TableModel#getRowCount()
	 */
	@Override
	public int getRowCount() {
		return data.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.table.TableModel#getColumnCount()
	 */
	@Override
	public int getColumnCount() {
		return devices.length;
	}

	public SPIMDevice[] getColumns() {
		return devices;
	}

	public Vector<Row > getRows() {
		return data;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.table.TableModel#getValueAt(int, int)
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return data.get(rowIndex).describeValueSet(devices[columnIndex]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Row > iterator() {
		return data.iterator();
	}

	public void insertRow(Row row) {
		insertRow(data.size(), row);
	}

	public void insertRow(int idx, Row row) {
		data.add(idx, row);
		this.fireTableDataChanged();
	}

	public void insertRow(Object... values) {
		insertRow(data.size(), values);
	}
	
	public void insertRow(int index, Object... values) {
		// TODO: Handle this more gracefully? Fail silently? Chop?
		if (values.length != devices.length)
			throw new Error("Wrong colum count, silly!");

		String[] fixed = new String[values.length];
		for (int i = 0; i < values.length; ++i)
			fixed[i] = values[i].toString();

		data.add(index, new Row(devices, fixed));

		this.fireTableDataChanged();
	}

	public void removeRows(int[] rows) {
		for (int rowidx = 0; rowidx < rows.length; ++rowidx)
			data.remove(rows[rowidx] - rowidx);

		this.fireTableDataChanged();
	}

	@Override
	public String getColumnName(int columnIndex) {
		return devices[columnIndex].getText();
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		data.get(rowIndex).setValueSet(devices[columnIndex], aValue.toString());
	}
};
