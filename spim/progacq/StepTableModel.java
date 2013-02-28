/**
 * 
 */
package spim.progacq;

import java.util.Iterator;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import spim.DeviceManager.SPIMDevice;

/**
 * @author Luke Stuyvenberg
 * 
 */
public class StepTableModel extends AbstractTableModel implements
		Iterable<AcqRow> {
	private static final long serialVersionUID = -7369997010095627461L;

	private SPIMDevice[] devices;
	private Vector<AcqRow> data;

	public StepTableModel(SPIMDevice[] devices) {
		super();

		this.devices = devices;
		data = new Vector<AcqRow>();
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

	public Vector<AcqRow> getRows() {
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
	public Iterator<AcqRow> iterator() {
		return data.iterator();
	}

	public void insertRow(Object[] values) {
		insertRow(data.size(), values);
	}
	
	public void insertRow(int index, Object[] values) {
		// TODO: Handle this more gracefully? Fail silently? Chop?
		if (values.length != devices.length)
			throw new Error("Wrong colum count, silly!");

		String[] fixed = new String[values.length];
		for (int i = 0; i < values.length; ++i)
			fixed[i] = values[i].toString();

		data.add(index, new AcqRow(devices, fixed));

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
