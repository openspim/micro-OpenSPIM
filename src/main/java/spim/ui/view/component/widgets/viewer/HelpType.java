package spim.ui.view.component.widgets.viewer;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public enum HelpType
{
	ACQUISITION, ZSTACK, POSITION, IMAGING, SAVEIMAGE, CHANNEL, TIMEPOINT, STAGE, CONSOLE, SUMMARY;

	public String getHtml()
	{
		return getClass().getResource(HelpResourceUtil.getString(name().toLowerCase())).toExternalForm();
	}
}

