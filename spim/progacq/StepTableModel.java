/**
 * 
 */
package spim.progacq;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

/**
 * @author Luke Stuyvenberg
 * 
 */
public class StepTableModel extends AbstractTableModel implements
		Iterable<String[]> {
	private static final long serialVersionUID = -7369997010095627461L;

	private String[] columnNames;
	private Vector<String[]> data;

	public StepTableModel() {
		super();

		columnNames = new String[0];
		data = new Vector<String[]>();
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
		return columnNames.length;
	}

	public String[] getColumnNames() {
		return columnNames;
	}

	public Vector<String[]> getRows() {
		return data;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.table.TableModel#getValueAt(int, int)
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return data.get(rowIndex)[columnIndex];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<String[]> iterator() {
		return data.iterator();
	}

	/**
	 * Reformats the table to use the newly-specified columns. Columns shared
	 * between new and old save their data, the rest is dropped. This is a
	 * somewhat expensive operation; try not to call it often...
	 * 
	 * @param columns
	 *            The new vector of column headers to use.
	 */
	public void setColumns(List<String> columns) {
		// Build a temporary map of our data.
		Vector<HashMap<String, String>> tempData = new Vector<HashMap<String, String>>();
		for (String[] row : data) {
			HashMap<String, String> map = new HashMap<String, String>();
			for (int i = 0; i < columnNames.length; ++i)
				map.put(columnNames[i], row[i]);
			tempData.add(map);
		}

		columnNames = columns.toArray(new String[columns.size()]);

		data = new Vector<String[]>();

		for (HashMap<String, String> row : tempData) {
			String[] newRow = new String[columnNames.length];

			for (int i = 0; i < columnNames.length; ++i)
				newRow[i] = row.containsKey(columnNames[i]) ? row
						.get(columnNames[i]) : "";

			data.add(newRow);
		}

		this.fireTableStructureChanged();
		this.fireTableDataChanged();
	}

	public void insertRow(Object[] values) {
		insertRow(data.size(), values);
	}
	
	public void insertRow(int index, Object[] values) {
		// TODO: Handle this more gracefully? Fail silently? Chop?
		if (values.length != columnNames.length)
			throw new Error("Wrong colum count, silly!");

		String[] fixed = new String[values.length];
		for (int i = 0; i < values.length; ++i)
			fixed[i] = values[i].toString();

		data.add(index, fixed);

		this.fireTableDataChanged();
	}

	public void removeRows(int[] rows) {
		for (int rowidx = 0; rowidx < rows.length; ++rowidx)
			data.remove(rows[rowidx] - rowidx);

		this.fireTableDataChanged();
	}

	@Override
	public String getColumnName(int columnIndex) {
		return columnNames[columnIndex];
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
		data.get(rowIndex)[columnIndex] = aValue.toString();
	}
};
