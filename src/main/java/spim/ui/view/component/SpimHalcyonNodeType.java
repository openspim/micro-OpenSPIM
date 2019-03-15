package spim.ui.view.component;

import javafx.scene.Node;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public enum SpimHalcyonNodeType implements halcyon.model.node.HalcyonNodeType
{
	LASER, CAMERA, STAGE, EDITOR, CONTROL;

	@Override
	public Node getIcon()
	{
		return getIcon(ResourceUtil.getString(name().toLowerCase()
				+ ".icon"));
	}
}
