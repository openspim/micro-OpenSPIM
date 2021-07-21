package spim.ui.view.component;

import javafx.scene.Node;
import spim.ui.view.component.util.ResourceUtil;

/**
 * Description: This node type and icons are used in the Halcyon Tree View.
 * Each item is linked to specific panel for controlling the hardware.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public enum SpimHalcyonNodeType implements halcyon.model.node.HalcyonNodeType
{
	LASER, CAMERA, STAGE, EDITOR, CONTROL, SHUTTER, CONSOLE;

	@Override
	public Node getIcon()
	{
		return getIcon(ResourceUtil.getString(name().toLowerCase()
				+ ".icon"));
	}
}
