package spim.progacq;

import java.io.File;

import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;

public class AcqParams {
	private CMMCore			core;
	private String[]		stepDevices;
	private AcqRow[]		rows;

	private double			timeStepSeconds;
	private int				timeSeqCount;

	private boolean			continuous;

	private AcqOutputHandler outputHandler;

	private ProgrammaticAcquisitor.AcqProgressCallback	progressListener;

	private String[]		metaDevices;

	private AntiDrift.Factory	adFactory;
	private boolean				updateLive;
	private boolean				illumFullStack;
	private int					zWaitMillis;

	public AcqParams() {
		this(null, null, null, 0D, 0, false, null, null, false, null);
	}

	public AcqParams(CMMCore icore, String[] idevices, AcqRow[] rows) {
		this(icore, idevices, rows, 0D, 1, false, null, idevices, false, null);
	}

	public AcqParams(CMMCore core, String[] devs, AcqRow[] rows, double deltat, int count)
	{
		this(core, devs, rows, deltat, count, false, null, devs, false, null);
	}

	public AcqParams(CMMCore iCore, String[] iDevices, AcqRow[] iRows,
			double iTimeStep, int iTimeSeqCnt, boolean iContinuous,
			ProgrammaticAcquisitor.AcqProgressCallback iListener, String[] iMetaDevices, boolean saveIndv,
			File rootDir) {
		this(
			iCore,
			iDevices,
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

	public AcqParams(CMMCore iCore, String[] iDevs, AcqRow[] iRows,
			double iTimeStep, int iTimeSeqCnt, boolean iContinuous,
			ProgrammaticAcquisitor.AcqProgressCallback iListener, String[] iMetaDevices,
			AcqOutputHandler handler) {

		setCore(iCore);
		setStepDevices(iDevs);
		setRows(iRows);
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
	 * @return the stepDevices
	 */
	public String[] getStepDevices() {
		return stepDevices;
	}

	/**
	 * @param stepDevices the stepDevices to set
	 */
	public void setStepDevices(String[] stepDevices) {
		this.stepDevices = stepDevices;
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
	public ProgrammaticAcquisitor.AcqProgressCallback getProgressListener() {
		return progressListener;
	}

	/**
	 * @param progressListener the progressListener to set
	 */
	public void setProgressListener(ProgrammaticAcquisitor.AcqProgressCallback progressListener) {
		this.progressListener = progressListener;
	}

	/**
	 * @return the metaDevices
	 */
	public String[] getMetaDevices() {
		return metaDevices;
	}

	/**
	 * @param metaDevices the metaDevices to set
	 */
	public void setMetaDevices(String[] metaDevices) {
		this.metaDevices = metaDevices;
	}

	public AcqOutputHandler getOutputHandler() {
		return outputHandler;
	}

	public void setOutputHandler(AcqOutputHandler outputHandler) {
		this.outputHandler = outputHandler;
	}

	public boolean isAntiDriftOn() {
		return adFactory != null;
	};

	public void setAntiDrift(AntiDrift.Factory in) {
		adFactory = in;
	};

	public AntiDrift getAntiDrift(AcqRow r) {
		return (adFactory != null ? adFactory.Manufacture(this, r) : null);
	};

	public AcqRow[] getRows() {
		return rows;
	};

	public void setRows(AcqRow[] irows) {
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

}
