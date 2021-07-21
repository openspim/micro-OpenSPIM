package spim.ui.view.component;

import org.micromanager.Studio;
import spim.hardware.SPIMSetup;

/**
 * Description: All the derived class should implement SPIMSetup and Studio Injectable status.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
interface SPIMSetupInjectable
{
	void setSetup( SPIMSetup setup, Studio studio ) throws Exception;
}
