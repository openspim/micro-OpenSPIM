package spim.ui.view.component;

import org.junit.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2023
 */
public class AcquisitionPanelTest {

	@Test
	public void getCurrentTime() {
		Date date = Calendar.getInstance().getTime();
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmm");
		String strDate = dateFormat.format(date);

		assertEquals(strDate, AcquisitionPanel.getCurrentTime());
	}
}