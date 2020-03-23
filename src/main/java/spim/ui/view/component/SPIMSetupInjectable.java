package spim.ui.view.component;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */

import org.micromanager.Studio;
import spim.hardware.SPIMSetup;

interface SPIMSetupInjectable
{
	void setSetup( SPIMSetup setup, Studio studio ) throws Exception;
}
