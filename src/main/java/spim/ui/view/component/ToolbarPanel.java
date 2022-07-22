package spim.ui.view.component;

import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.imagestack.ImageStackImageLoader;
import bdv.img.virtualstack.VirtualStackImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.Prefs;
import bdv.viewer.ConverterSetups;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerState;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.dockfx.DockNode;

import org.micromanager.Studio;

import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.GUIRefreshEvent;
import spim.hardware.SPIMSetup;
import spim.io.*;
import spim.mm.MMUtils;
import spim.mm.MicroManager;
import spim.model.event.ControlEvent;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;


/**
 * Description: Basic tool bar panel contains Micro-Manager start button as well as
 * the important property values, e.g. Pixel Size μm, Rotator Step Size μm and Z-Stage Step Size μm.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class ToolbarPanel extends DockNode implements SPIMSetupInjectable
{
	final ObjectProperty<Studio> studioProperty;
	final ObjectProperty<SPIMSetup> spimSetupObjectProperty;

	final VBox topHbox;
	final VBox liveViewHbox;
	final HBox buttonHbox;

	final Label liveDemoLabel;
	final Button mmButton;
//	final Button liveViewButton;
	final Button openDatasetButton;
	final Button openDatasetWithBDV;

	final SimpleDoubleProperty waitSeconds;

	final Text pixelSizeValue;
	final Text rotatorStepSizeValue;
	final Text zStageStepSizeValue;
	HalcyonMain halcyonMain;

	public ToolbarPanel( Studio mmStudio, ObjectProperty< Studio > mmStudioObjectProperty, ObjectProperty<GUIRefreshEvent> refreshEventProperty )
	{
		super(new VBox());
		this.studioProperty = new SimpleObjectProperty<>( mmStudio );
		this.spimSetupObjectProperty = new SimpleObjectProperty<>();
		this.waitSeconds =  new SimpleDoubleProperty(-1);

		getDockTitleBar().setVisible(false);

		setTitle("µOpenSPIM");

		GridPane gridpane = new GridPane();

		gridpane.setVgap( 5 );
		gridpane.setHgap( 5 );

		setContents( gridpane );

		Image logoImg = new javafx.scene.image.Image( getClass().getResourceAsStream( "uOS-logo.png" ) );
		ImageView iv = new ImageView(logoImg);
		iv.setPreserveRatio(true);
		iv.setFitWidth(160);

		liveDemoLabel = new Label( "LIVE DEMO" );
		liveDemoLabel.setStyle( "-fx-font: 18 arial; -fx-background-color: #0faff0" );

		topHbox = new VBox(10);
		topHbox.setAlignment( Pos.CENTER );
		topHbox.getChildren().addAll( iv, liveDemoLabel );

		gridpane.addRow( 1, topHbox );

		Button ijButton = new Button( "Fiji" );
		ijButton.setStyle("-fx-font: 18 arial; -fx-base: #8bb5e7;");
		ijButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(null == IJ.getInstance() ) {
					ij.ImageJ ij = new ij.ImageJ();
					ij.show();
				} else {
					IJ.getInstance().show();
				}
			}
		} );

		mmButton = new Button( "µManager");
		mmButton.setStyle("-fx-font: 18 arial; -fx-base: #e7e45d;");
		mmButton.setOnAction( new EventHandler< ActionEvent >()
		{
			@Override public void handle( ActionEvent event )
			{
				if(null == MicroManager.getInstance()) {
					Stage stage = new Stage();
					MMUtils.resetCancelled();

					if (!MMUtils.isSystemLibrairiesLoaded())
					{
						// load micro manager libraries
						if (!MMUtils.fixSystemLibrairies( stage ))
						{
							if(halcyonMain != null)
								System.exit(0);
							return;
						}
					}

					MicroManager.init( stage, mmStudioObjectProperty, refreshEventProperty );

					while(MMUtils.invalidMMPath() && !MMUtils.cancelled())
					{
						if (!MMUtils.isSystemLibrairiesLoaded())
						{
							// load micro manager libraries
							if (!MMUtils.fixSystemLibrairies( stage ))
								if(halcyonMain != null)
									System.exit(0);
								return;
						}

						MicroManager.init( stage, mmStudioObjectProperty, refreshEventProperty );
					}

					if(halcyonMain != null) halcyonMain.show();
				} else {
					MicroManager.getInstance().show();
				}
			}
		} );

		buttonHbox = new HBox(3, ijButton, mmButton);
		buttonHbox.setAlignment( Pos.CENTER );
		gridpane.addRow( 2, buttonHbox );


//		SimpleBooleanProperty liveOn = new SimpleBooleanProperty( false );
//		liveViewButton = new Button( "LiveView");
//		liveViewButton.setMinSize( 100, 40 );
//		liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
//		liveViewButton.setOnAction( new EventHandler< ActionEvent >()
//		{
//			@Override public void handle( ActionEvent event )
//			{
//				if(studioProperty.get() == null) {
//					new Alert( Alert.AlertType.WARNING, "MM2 config is not loaded.").show();
//					return;
//				}
//
//				liveOn.set( !liveOn.get() );
//				if(liveOn.get())
//				{
//					liveViewButton.setText( "Stop LiveView" );
//					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #e77d8c;");
//					if(studioProperty.get() != null)
//						studioProperty.get().live().setLiveMode( true );
//				} else {
//					liveViewButton.setText( "LiveView" );
//					liveViewButton.setStyle("-fx-font: 18 arial; -fx-base: #43a5e7;");
//					if(studioProperty.get() != null)
//						studioProperty.get().live().setLiveMode( false );
//				}
//			}
//		} );

		openDatasetButton = new Button("Open Dataset");
		openDatasetButton.setStyle("-fx-font: 14 arial; -fx-base: #e7e45d;");
		openDatasetButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				try {
					loadData();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		openDatasetWithBDV = new Button("Show in BDV");
		openDatasetWithBDV.setStyle("-fx-font: 14 arial; -fx-base: #e7e45d;");
		openDatasetWithBDV.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				try {
					loadDataWithBDV();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		liveViewHbox = new VBox(3);
		liveViewHbox.setAlignment( Pos.CENTER );

		gridpane.addRow( 3, liveViewHbox );

		VBox timerBox = new VBox(3);
		timerBox.setAlignment( Pos.CENTER );

		final Label timerLabel = new Label();
		timerLabel.setStyle( "-fx-font: 16 arial;" );
		final ProgressBar pb = new ProgressBar(-1);

		waitSeconds.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				Platform.runLater(() -> {
					if (newValue.doubleValue() > -1) {
						if(timerBox.getChildren().size() == 0) {
							timerBox.getChildren().addAll(timerLabel, pb);
						}
						timerLabel.setText(String.format("Acquisition in:\n\t%.0f sec", newValue.doubleValue()));
					} else {
						timerBox.getChildren().clear();
					}
				});
			}
		});

		gridpane.addRow( 4, timerBox );

		Supplier<Text> nl = () -> new Text("\n");

		Font helveticaBold = Font.font("Helvetica", FontWeight.BOLD, 12);
		Text pixelSizeLabel = new Text("Pixel Size μm: ");
		pixelSizeLabel.setFont(helveticaBold);
		pixelSizeValue = new Text("N.A.");
		pixelSizeValue.setFont(Font.font("Helvetica", 12));

		Text rotatorStepSizeLabel = new Text("Rotator Step Size μm: ");
		rotatorStepSizeLabel.setFont(helveticaBold);
		rotatorStepSizeValue = new Text("N.A.");
		rotatorStepSizeValue.setFont(Font.font("Helvetica", 12));

		Text zStageStepSizeLabel = new Text("Z-Stage Step Size μm: ");
		zStageStepSizeLabel.setFont(helveticaBold);
		zStageStepSizeValue = new Text("N.A.");
		zStageStepSizeValue.setFont(Font.font("Helvetica", 12));

		refreshEventProperty.addListener((observable, oldValue, newValue) -> {
			if(studioProperty.get() != null) {
				pixelSizeValue.setText(studioProperty.get().core().getPixelSizeUm() + "");
				rotatorStepSizeValue.setText(spimSetupObjectProperty.get().getThetaStage().getStepSize() + "");
				zStageStepSizeValue.setText(spimSetupObjectProperty.get().getZStage().getStepSize() + "");
			}
		});

		TextFlow textFlow = new TextFlow(pixelSizeLabel, pixelSizeValue, nl.get(),
				rotatorStepSizeLabel, rotatorStepSizeValue, nl.get(),
				zStageStepSizeLabel, zStageStepSizeValue, nl.get());

		gridpane.addRow( 5, textFlow );

//		btn = new Button("Test Std Err");
//		btn.setOnAction(e -> {
//
//			for (int i = 0; i < 2000; i++)
//			{
//				System.err.println("" + i + " " + "Console Test");
//			}
//
//		});
//
//		box.getChildren().add(btn);
//
//		// Wavelength color check
//		btn = new Button("488");
//		btn.setStyle("-fx-background-color: #0FAFF0");
//		box.getChildren().add(btn);

		addEventHandler( ControlEvent.MM, new EventHandler< ControlEvent >()
		{
			@Override public void handle( ControlEvent event )
			{
				if(event.getEventType().equals( ControlEvent.MM_OPEN )) {
					halcyonMain = (HalcyonMain) event.getParam()[0];
					mmButton.fire();
				}
			}
		} );
	}

	@SuppressWarnings("Duplicates")
	public void loadData() throws IOException {

		DirectoryChooser d = new DirectoryChooser();
		File f = d.showDialog(null);

		if(f == null) return;

		File[] list = f.listFiles((file, s) -> s.endsWith("_metadata.txt"));

		if (list.length == 0) return;

		StorageType storageType = null;
		String prefix = "";

		if (list.length == 1) {
			prefix = list[0].getName().replaceFirst("_metadata.txt", "");
			String directory = f.getAbsolutePath();
			storageType = StorageOpener.checkStorageType(directory, prefix);
		} else {
			// List up all the prefix candidates and let the user choose one
		}

		String directory = f.getAbsolutePath();
		DefaultDatastore result = new DefaultDatastore(studioProperty.get());

		switch (storageType) {
			case SinglePlaneTiff: result.setStorage(new OpenSPIMSinglePlaneTiffSeries(result, directory, prefix, false));
			break;
			case OMETiff: result.setStorage(new OMETIFFStorage(result, directory, prefix, false));
			break;
			case N5: result.setStorage(new N5MicroManagerStorage(result, directory, prefix, 1, false));
			break;
		}

		result.setSavePath(directory);
		result.freeze();

		studioProperty.get().displays().manage(result);
		studioProperty.get().displays().loadDisplays(result);
	}

	public ImagePlus toImageJ(DisplayWindow display) {
		final boolean copy = false;
		final boolean setProps = true;
		// TODO: UI to set copy, give option to only do partial data, and multiple positions

		DataProvider dp = display.getDataProvider();
		Coords displayPosition = display.getDisplayPosition();
		int p = displayPosition.getP();

		ImagePlus iPlus = null;
		org.micromanager.data.Image image = null;
		if (dp.getNumImages() == 1) {
			try {
				image = dp.getAnyImage();
				ImageProcessor iProc = DefaultImageJConverter.createProcessor(image, copy);
				iPlus = new ImagePlus(dp.getName() + "-ij", iProc);

			} catch (IOException ex) {
				// TODO: report error
			}
			if (setProps && iPlus != null && image != null) {
				setCalibration(iPlus, dp, image);
			}
			if (iPlus != null) {
				return iPlus;
			}
		} else if (dp.getNumImages() > 1) {
			try {
				ImageStack imgStack = new ImageStack(dp.getAnyImage().getWidth(),
						dp.getAnyImage().getHeight());
				Coords.Builder cb = Coordinates.builder().c(0).t(0).p(p).z(0);
				for (int t = 0; t < dp.getNextIndex(Coords.T); t++) {
					for (int z = 0; z < dp.getNextIndex(Coords.Z); z++) {
						for (int c = 0; c < dp.getNextIndex(Coords.C); c++) {
							image = dp.getImage(cb.c(c).t(t).z(z).build());
							ImageProcessor iProc;
							if (image != null) {
								iProc = DefaultImageJConverter.createProcessor(
										image, copy);
							} else { // handle missing images - should be handled by MM
								// so remove this code once this is done nicely in MM
								iProc = DefaultImageJConverter.createBlankProcessor(
										dp.getAnyImage());
							}
							imgStack.addSlice(iProc);
						}
					}
				}
				iPlus = new ImagePlus(dp.getName() + "-ij");
				iPlus.setOpenAsHyperStack(true);
				iPlus.setStack(imgStack, dp.getNextIndex(Coords.C),
						dp.getNextIndex(Coords.Z), dp.getNextIndex(Coords.T));

				int displayMode;
				switch (display.getDisplaySettings().getColorMode()) {
					case COLOR: { displayMode = IJ.COLOR; break; }
					case COMPOSITE: { displayMode = IJ.COMPOSITE; break; }
					case GRAYSCALE: { displayMode = IJ.GRAYSCALE; break; }
					default: { displayMode = IJ.GRAYSCALE; break; }
				}
				iPlus.setDisplayMode(displayMode);
				CompositeImage ci = new CompositeImage(iPlus, displayMode);
				ci.setTitle(dp.getName() + "-ij");
				for (int c = 0; c < dp.getNextIndex(Coords.C); c++) {
					ci.setChannelLut(
							LUT.createLutFromColor(display.getDisplaySettings().getChannelColor(c)),
							c + 1);
				}
				if (setProps && image != null) {
					setCalibration(ci, dp, image);
				}
				return ci;

			} catch (IOException ex) {
				// TODO: report
			}

			return null;
		}

		return null;
	}

	private void setCalibration(ImagePlus iPlus, DataProvider dp, org.micromanager.data.Image image) {
		Calibration cal = new Calibration(iPlus);
		Double pSize = image.getMetadata().getPixelSizeUm();
		if (pSize != null && pSize != 0) {
			cal.pixelWidth = pSize;
			cal.pixelHeight = pSize;
		} else {
			cal.pixelWidth = 1;
			cal.pixelHeight = 1;
		}
		Double zStep = dp.getSummaryMetadata().getZStepUm();
		if (zStep != null && zStep != 0) {
			cal.pixelDepth = zStep;
		} else {
			cal.pixelDepth = 1;
		}
		Double waitInterval = dp.getSummaryMetadata().getWaitInterval();
		if (waitInterval != null) {
			cal.frameInterval = waitInterval / 1000.0;  // MM in ms, IJ in s
		}

		if(pSize != null && pSize != 0) {
			cal.setUnit("micron");
		}

		iPlus.setCalibration(cal);
	}

	@SuppressWarnings("Duplicates")
	public void loadDataWithBDV() throws IOException {
		Prefs.showScaleBar(true);
		DisplayWindow displayWindow = studioProperty.get().displays().getCurrentWindow();

		if(displayWindow == null) {
			new Alert( Alert.AlertType.WARNING, "Please, load a dataset first").showAndWait();
			System.err.println("There is no dataset to be opened.");
			return;
		}

		ArrayList< ImagePlus > inputImgList = new ArrayList<>();
		inputImgList.add( toImageJ(displayWindow) );

		SwingUtilities.invokeLater(() -> {
			final ArrayList<ConverterSetup> converterSetups = new ArrayList<>();
			final ArrayList<SourceAndConverter< ? >> sources = new ArrayList<>();

			final CacheControl.CacheControls cache = new CacheControl.CacheControls();
			int nTimepoints = 1;
			int setup_id_offset = 0;
			final ArrayList< ImagePlus > imgList = new ArrayList<>();
			boolean is2D = true;

			for ( ImagePlus imp : inputImgList )
			{
				if ( imp.getNSlices() > 1 )
					is2D = false;
				final AbstractSpimData< ? > spimData = load( imp, converterSetups, sources, setup_id_offset );
				if ( spimData != null )
				{
					imgList.add( imp );
					cache.addCacheControl( ( (ViewerImgLoader) spimData.getSequenceDescription().getImgLoader() ).getCacheControl() );
					setup_id_offset += imp.getNChannels();
					nTimepoints = Math.max( nTimepoints, imp.getNFrames() );
				}
			}

			if ( !imgList.isEmpty() )
			{
				final BigDataViewer bdv = BigDataViewer.open( converterSetups, sources,
						nTimepoints, cache,
						"BigDataViewer", null,
						ViewerOptions.options().is2D( is2D ) );

				final SynchronizedViewerState state = bdv.getViewer().state();
				synchronized ( state )
				{
					int channelOffset = 0;
					int numActiveChannels = 0;
					for ( ImagePlus imp : imgList )
					{
						numActiveChannels += transferChannelVisibility( channelOffset, imp, state );
						transferChannelSettings( channelOffset, imp, state, bdv.getConverterSetups() );
						channelOffset += imp.getNChannels();
					}
					state.setDisplayMode( numActiveChannels > 1 ? DisplayMode.FUSED : DisplayMode.SINGLE );
				}
			}
		});
	}

	protected AbstractSpimData< ? > load( ImagePlus imp, ArrayList< ConverterSetup > converterSetups, ArrayList< SourceAndConverter< ? > > sources,
										  int setup_id_offset )
	{
		// check the image type
		switch ( imp.getType() )
		{
			case ImagePlus.GRAY8:
			case ImagePlus.GRAY16:
			case ImagePlus.GRAY32:
			case ImagePlus.COLOR_RGB:
				break;
			default:
				IJ.showMessage( imp.getShortTitle() + ": Only 8, 16, 32-bit images and RGB images are supported currently!" );
				return null;
		}

		// get calibration and image size
		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;
		String punit = imp.getCalibration().getUnit();
		if ( punit == null || punit.isEmpty() )
			punit = "px";
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pw, ph, pd );
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getNSlices();
		final FinalDimensions size = new FinalDimensions( w, h, d );

		// propose reasonable mipmap settings
//		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

		// create ImgLoader wrapping the image
		final BasicImgLoader imgLoader;
		if ( imp.getStack().isVirtual() )
		{
			switch ( imp.getType() )
			{
				case ImagePlus.GRAY8:
					imgLoader = VirtualStackImageLoader.createUnsignedByteInstance( imp, setup_id_offset );
					break;
				case ImagePlus.GRAY16:
					imgLoader = VirtualStackImageLoader.createUnsignedShortInstance( imp, setup_id_offset );
					break;
				case ImagePlus.GRAY32:
					imgLoader = VirtualStackImageLoader.createFloatInstance( imp, setup_id_offset );
					break;
				case ImagePlus.COLOR_RGB:
				default:
					imgLoader = VirtualStackImageLoader.createARGBInstance( imp, setup_id_offset );
					break;
			}
		}
		else
		{
			switch ( imp.getType() )
			{
				case ImagePlus.GRAY8:
					imgLoader = ImageStackImageLoader.createUnsignedByteInstance( imp, setup_id_offset );
					break;
				case ImagePlus.GRAY16:
					imgLoader = ImageStackImageLoader.createUnsignedShortInstance( imp, setup_id_offset );
					break;
				case ImagePlus.GRAY32:
					imgLoader = ImageStackImageLoader.createFloatInstance( imp, setup_id_offset );
					break;
				case ImagePlus.COLOR_RGB:
				default:
					imgLoader = ImageStackImageLoader.createARGBInstance( imp, setup_id_offset );
					break;
			}
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create setups from channels
		final HashMap< Integer, BasicViewSetup> setups = new HashMap<>( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final BasicViewSetup setup = new BasicViewSetup( setup_id_offset + s, String.format( imp.getTitle() + " channel %d", s + 1 ), size, voxelSize );
			setup.setAttribute( new Channel( s + 1 ) );
			setups.put( setup_id_offset + s, setup );
		}

		// create timepoints
		final ArrayList<TimePoint> timepoints = new ArrayList<>( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		// create ViewRegistrations from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );
		final ArrayList<ViewRegistration> registrations = new ArrayList<>();
		for ( int t = 0; t < numTimepoints; ++t )
			for ( int s = 0; s < numSetups; ++s )
				registrations.add( new ViewRegistration( t, setup_id_offset + s, sourceTransform ) );

		final File basePath = new File( "." );
		final AbstractSpimData< ? > spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );
		WrapBasicImgLoader.wrapImgLoaderIfNecessary( spimData );
		BigDataViewer.initSetups( spimData, converterSetups, sources );

		return spimData;
	}

	/**
	 * @return number of setups that were set active.
	 */
	protected int transferChannelVisibility( int channelOffset, final ImagePlus imp, final ViewerState state )
	{
		final int nChannels = imp.getNChannels();
		final CompositeImage ci = imp.isComposite() ? ( CompositeImage ) imp : null;
		final List< SourceAndConverter< ? > > sources = state.getSources();
		if ( ci != null && ci.getCompositeMode() == IJ.COMPOSITE )
		{
			final boolean[] activeChannels = ci.getActiveChannels();
			int numActiveChannels = 0;
			for ( int i = 0; i < Math.min( activeChannels.length, nChannels ); ++i )
			{
				final SourceAndConverter< ? > source = sources.get( channelOffset + i );
				state.setSourceActive( source, activeChannels[ i ] );
				state.setCurrentSource( source );
				numActiveChannels += activeChannels[ i ] ? 1 : 0;
			}
			return numActiveChannels;
		}
		else
		{
			final int activeChannel = imp.getChannel() - 1;
			for ( int i = 0; i < nChannels; ++i )
				state.setSourceActive( sources.get( channelOffset + i ), i == activeChannel );
			state.setCurrentSource( sources.get( channelOffset + activeChannel ) );
			return 1;
		}
	}

	protected void transferChannelSettings( int channelOffset, final ImagePlus imp, final ViewerState state, final ConverterSetups converterSetups )
	{
		final int nChannels = imp.getNChannels();
		final CompositeImage ci = imp.isComposite() ? ( CompositeImage ) imp : null;
		final List< SourceAndConverter< ? > > sources = state.getSources();
		if ( ci != null )
		{
			final int mode = ci.getCompositeMode();
			final boolean transferColor = mode == IJ.COMPOSITE || mode == IJ.COLOR;
			for ( int c = 0; c < nChannels; ++c )
			{
				final LUT lut = ci.getChannelLut( c + 1 );
				ImageProcessor ip = ci.getChannelProcessor();
				ImageStatistics s = ip.getStats();

				final ConverterSetup setup = converterSetups.getConverterSetup( sources.get( channelOffset + c ) );
				if ( transferColor )
					setup.setColor( new ARGBType( lut.getRGB( 255 ) ) );
				setup.setDisplayRange( s.min, s.max );
			}
		}
		else
		{
			ImageProcessor ip = imp.getChannelProcessor();
			ImageStatistics s = ip.getStats();

			final double displayRangeMin = s.min;
			final double displayRangeMax = s.max;
			for ( int i = 0; i < nChannels; ++i )
			{
				final ConverterSetup setup = converterSetups.getConverterSetup( sources.get( channelOffset + i ) );
				final LUT[] luts = imp.getLuts();
				if ( luts.length != 0 )
					setup.setColor( new ARGBType( luts[ 0 ].getRGB( 255 ) ) );
				setup.setDisplayRange( displayRangeMin, displayRangeMax );
			}
		}
	}

	public SimpleDoubleProperty waitSecondsProperty() {
		return waitSeconds;
	}

	@Override public void setSetup(SPIMSetup setup, Studio studio ) {
		this.studioProperty.set(studio);
		this.spimSetupObjectProperty.set(setup);

//		java.awt.Rectangle roi;
		if(null != studio) {
			topHbox.getChildren().remove( liveDemoLabel );
			buttonHbox.getChildren().remove( mmButton );
//			liveViewHbox.getChildren().add( 0, liveViewButton);
			liveViewHbox.getChildren().addAll( openDatasetButton, openDatasetWithBDV );
			pixelSizeValue.setText(studio.core().getPixelSizeUm() + "");
			rotatorStepSizeValue.setText(setup.getThetaStage().getStepSize() + "");
			zStageStepSizeValue.setText(setup.getZStage().getStepSize() + "");
//			roi = new java.awt.Rectangle(0, 0, 0, 0);
		} else {
			topHbox.getChildren().add( liveDemoLabel );
			buttonHbox.getChildren().add( mmButton );
//			liveViewHbox.getChildren().remove( liveViewButton );
			liveViewHbox.getChildren().removeAll( openDatasetButton, openDatasetWithBDV );
			pixelSizeValue.setText("N.A.");
			rotatorStepSizeValue.setText("N.A.");
			zStageStepSizeValue.setText("N.A.");
//			roi = new java.awt.Rectangle( 0, 0, 0, 0 );
		}
	}
}