package spim.ui.view.component.util;

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
import mpicbg.spim.data.SpimDataException;
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
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.gui.StitchingExplorer;
import org.mastodon.feature.FeatureComputerService;
import org.mastodon.feature.FeatureSpecsService;
import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.project.MamutProject;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.display.DisplayWindow;
import org.scijava.Context;
import org.scijava.plugin.PluginService;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2023
 */
public class AdvancedPlugins {
	static class ParseQueryXML extends LoadParseQueryXML {
		public void open(String xmlFilename) {
			this.tryParsing(xmlFilename, true);
		}
	}

	private static ImagePlus toImageJ(DisplayWindow display) {
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

	private static void setCalibration(ImagePlus iPlus, DataProvider dp, org.micromanager.data.Image image) {
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
	public static void loadDataWithBDV(DisplayWindow displayWindow) {
		Prefs.showScaleBar(true);
//		DisplayWindow displayWindow = studioProperty.get().displays().getCurrentWindow();

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

				bdv.getViewerFrame().requestFocus();

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

	protected static AbstractSpimData< ? > load(ImagePlus imp, ArrayList<ConverterSetup> converterSetups, ArrayList<SourceAndConverter<?>> sources,
												int setup_id_offset)
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
	protected static int transferChannelVisibility(int channelOffset, final ImagePlus imp, final ViewerState state)
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

	protected static void transferChannelSettings(int channelOffset, final ImagePlus imp, final ViewerState state, final ConverterSetups converterSetups)
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

	public static void openBigStitcherWindow(String folder) {
		SwingUtilities.invokeLater(() -> {
			final ParseQueryXML result = new ParseQueryXML();

			result.open(folder + File.separator + "dataset.xml");

			final SpimData2 data = result.getData();
			final String xml = result.getXMLFileName();
			final XmlIoSpimData2 io = result.getIO();

			final StitchingExplorer<SpimData2, XmlIoSpimData2> explorer =
					new StitchingExplorer< >( data, xml, io );
		});
	}

	public static void openMastodonWindow(String folder) {
		SwingUtilities.invokeLater(() -> {
//			final File file = new File("/Users/moon/Desktop/cap_3" + File.separator + "dataset.xml");
			final File file = new File(folder + File.separator + "dataset.xml");
			new Thread(() -> {
				try {
					final WindowManager windowManager = new WindowManager( new Context(PluginService.class, FeatureSpecsService.class, FeatureComputerService.class));
					windowManager.getProjectManager().open(new MamutProject(null, file));
					MainWindow mastodonWindow = new MainWindow(windowManager);
					mastodonWindow.setVisible(true);
					mastodonWindow.requestFocus();
				} catch (IOException | SpimDataException e) {

				}
			}).start();
		});
	}
}
