package spim.acquisition;

/**
 * The enum Acquisition status.
 *
 * When the application starts, the status is INIT.
 * When users set up the output directory, the status is changed to INIT.
 *
 * Status is changed based on the below transitions.
 * 1. INIT -> RUNNING -> DONE
 * 2. INIT -> RUNNING -> ABORTED
 */
public enum AcquisitionStatus
{
	INIT,
	RUNNING,
	DONE,
	ABORTED
}
