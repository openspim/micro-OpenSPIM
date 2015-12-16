package spim.acquisition;

import java.io.File;

import spim.hardware.SPIMSetup;
import spim.hardware.SPIMSetup.SPIMDevice;

import mmcorej.CMMCore;
import spim.io.OutputHandler;
import spim.io.IndividualImagesHandler;
import spim.io.OutputAsStackHandler;
import spim.controller.AntiDriftController;

public class Params
{
	private CMMCore			core;
	private SPIMSetup		setup;
	private Row[]		rows;

	private double			timeStepSeconds;
	private int				timeSeqCount;

	private boolean			continuous;

	private OutputHandler outputHandler;

	private Program.AcqProgressCallback progressListener;

	private SPIMDevice[]		metaDevices;

	private AntiDriftController.Factory adControllerFactory;
	private boolean				updateLive;
	private boolean				illumFullStack;
	private int					zWaitMillis;

	private boolean				profile;
	private boolean				abortWhenDelayed = false;

	public Params() {
		this(null, null, null, 0D, 0, false, null, null, false, null);
	}

	public Params( CMMCore icore, SPIMSetup setup, Row[] rows ) {
		this(icore, setup, rows, 0D, 1, false, null, rows[0].getDevices(), false, null);
	}

	public Params( CMMCore core, SPIMSetup setup, Row[] rows, double deltat, int count )
	{
		this(core, setup, rows, deltat, count, false, null, rows[0].getDevices(), false, null);
	}

	public Params( CMMCore iCore, SPIMSetup setup, Row[] iRows,
			double iTimeStep, int iTimeSeqCnt, boolean iContinuous,
			Program.AcqProgressCallback iListener, SPIMDevice[] iMetaDevices, boolean saveIndv,
			File rootDir ) {
		this(
			iCore,
			setup,
			iRows,
			iTimeStep,
			iTimeSeqCnt,
			iContinuous,
			iListener,
			iMetaDevices,
			(saveIndv ?
				new IndividualImagesHandler(
					rootDir,
					IndividualImagesHandler.shortNamesToScheme("PA", new boolean[] {true, false, false, true}, null)
				) :
				new OutputAsStackHandler()
			)
		);
	}

	public Params( CMMCore iCore, SPIMSetup setup, Row[] iRows,
			double iTimeStep, int iTimeSeqCnt, boolean iContinuous,
			Program.AcqProgressCallback iListener, SPIMDevice[] iMetaDevices,
			OutputHandler handler ) {

		setCore(iCore);
		setRows(iRows);
		setSetup(setup);
		setTimeStepSeconds(iTimeStep);
		setTimeSeqCount(iTimeSeqCnt);
		setContinuous(iContinuous);
		setProgressListener(iListener);
		setMetaDevices(iMetaDevices);

		setOutputHandler(handler);
	}

	/**
	 * @return the core
	 */
	public CMMCore getCore() {
		return core;
	}

	/**
	 * @param core the core to set
	 */
	public void setCore(CMMCore core) {
		this.core = core;
	}

	/**
	 * @return the timeStepSeconds
	 */
	public double getTimeStepSeconds() {
		return timeStepSeconds;
	}

	/**
	 * @param timeStepSeconds the timeStepSeconds to set
	 */
	public void setTimeStepSeconds(double timeStepSeconds) {
		this.timeStepSeconds = timeStepSeconds;
	}

	/**
	 * @return the timeSeqCount
	 */
	public int getTimeSeqCount() {
		return timeSeqCount;
	}

	/**
	 * @param timeSeqCount the timeSeqCount to set
	 */
	public void setTimeSeqCount(int timeSeqCount) {
		this.timeSeqCount = timeSeqCount;
	}

	/**
	 * @return the continuous
	 */
	public boolean isContinuous() {
		return continuous;
	}

	/**
	 * @param continuous the continuous to set
	 */
	public void setContinuous(boolean continuous) {
		this.continuous = continuous;
	}

	/**
	 * @return the progressListener
	 */
	public Program.AcqProgressCallback getProgressListener() {
		return progressListener;
	}

	/**
	 * @param progressListener the progressListener to set
	 */
	public void setProgressListener(Program.AcqProgressCallback progressListener) {
		this.progressListener = progressListener;
	}

	/**
	 * @return the metaDevices
	 */
	public SPIMDevice[] getMetaDevices() {
		return metaDevices;
	}

	/**
	 * @param iMetaDevices the metaDevices to set
	 */
	public void setMetaDevices(SPIMDevice[] iMetaDevices) {
		this.metaDevices = iMetaDevices;
	}

	public OutputHandler getOutputHandler() {
		return outputHandler;
	}

	public void setOutputHandler(OutputHandler outputHandler) {
		this.outputHandler = outputHandler;
	}

	public boolean isAntiDriftOn() {
		return adControllerFactory != null;
	};

	public void setAntiDrift(AntiDriftController.Factory in) {
		adControllerFactory = in;
	};

	public AntiDriftController getAntiDrift(Row r) {
		return (adControllerFactory != null ? adControllerFactory.newInstance(this, r) : null);
	};

	public Row[] getRows() {
		return rows;
	};

	public void setRows(Row[] irows) {
		rows = irows;
	}

	public boolean isUpdateLive() {
		return updateLive;
	}

	public void setUpdateLive(boolean updateLive) {
		this.updateLive = updateLive;
	}

	public boolean isIllumFullStack() {
		return illumFullStack;
	}

	public void setIllumFullStack(boolean illumFullStack) {
		this.illumFullStack = illumFullStack;
	}

	public int getSettleDelay() {
		return zWaitMillis;
	}

	public void setSettleDelay(int zWaitMillis) {
		this.zWaitMillis = zWaitMillis;
	}

	public SPIMSetup getSetup() {
		return setup;
	}

	public void setSetup(SPIMSetup setup) {
		this.setup = setup;
	}

	public boolean doProfiling() {
		return profile;
	}

	public void setDoProfiling(boolean profile) {
		this.profile = profile;
	}

	public boolean isAbortWhenDelayed()
	{
		return abortWhenDelayed;
	}

	public void setAbortWhenDelayed( boolean abortWhenDelayed )
	{
		this.abortWhenDelayed = abortWhenDelayed;
	}
}
