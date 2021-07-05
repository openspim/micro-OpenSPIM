package spim.ui.view.component;

import eu.hansolo.enzo.common.Marker;
import eu.hansolo.enzo.common.SymbolType;
import eu.hansolo.enzo.onoffswitch.IconSwitch;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.micromanager.Studio;
import spim.hardware.Laser;
import spim.hardware.SPIMSetup;
import spim.ui.view.component.rbg.RadialBargraph;
import spim.ui.view.component.rbg.RadialBargraphBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class LaserDevicePanel extends HBox implements SPIMSetupInjectable
{

	//	private LaserDeviceInterface mLaserDeviceInterface;
	private SPIMSetup spimSetup;
	private String powerUnits;
	private double maxPower, minPower;

	private IconSwitch laserOnSwitch;
	private RadialBargraph targetPowerGauge;
	private RadialBargraph currentPowerGauge;
	private Marker targetPowerMarker;
	private Label laserLabel;
	private VBox wavelengthColorBox;

	private VBox properties;
	private HBox pane;

	private int waveLength;
	final double granularity = 3;
	private String laserName;
	ScheduledExecutorService executor;
	Studio studio;

	public LaserDevicePanel( Laser laser ) {
		this(laser, Integer.parseInt( laser.getWavelength() ));
	}

	public LaserDevicePanel( Laser laser, int wl )
	{
		laserName = null == laser ? "" : laser.getDeviceName();
		waveLength = wl;
		waveLength = null == laser ? wl : (int) Double.parseDouble( laser.getWavelength() );

		powerUnits = "mW";
		maxPower = null == laser ? 100 : laser.getMaxPower();
		minPower = null == laser ? 0 : laser.getMinPower();

		init();

		// data -> GUI
		// CurrentPower update (data -> GUI)
		if(null == laser) {
			executor.scheduleAtFixedRate( () -> {
				if( laserOnSwitch.isSelected() ) {
					double target = targetPowerGauge.valueProperty().get();
					double device = currentPowerGauge.valueProperty().get();
					double error = target - device;

					double newDevice = device + granularity * signum( error );

					if ( abs( error ) > granularity )
						currentPowerGauge.valueProperty().setValue( newDevice );
					else
						currentPowerGauge.valueProperty().setValue( device * 0.8 + target * 0.2 + Math.random() * 5 );
				} else {

				}

			}, 500, 100, TimeUnit.MILLISECONDS );
		} else {
			executor.scheduleAtFixedRate( () -> {
				if(this.spimSetup == null || this.studio == null || laser.isInvalid()) {
					executor.shutdown();
					executor = null;
					return;
				}

				// In case of live image, it turns the laser on
				//				if(laserOnSwitch.isSelected() != laser.getPoweredOn()) {
				//					laserOnSwitch.setSelected( laser.getPoweredOn() );
				//				}

				currentPowerGauge.valueProperty().setValue( laser.getPower() );
			}, 500, 100, TimeUnit.MILLISECONDS );
		}

		// Wavelength update (data -> GUI)
		wavelengthColorBox.setBackground( new Background( new BackgroundFill( getWebColor( waveLength ),
				CornerRadii.EMPTY,
				Insets.EMPTY ) ) );
		laserLabel.setText( waveLength + " nm" );

		// GUI -> data
		// TargetPower update (GUI -> data)
		if( null != laser )
		{
			// Laser Switch update (data -> GUI)
			laserOnSwitch.setSelected( laser.getPoweredOn() );

			targetPowerGauge.valueProperty().setValue( laser.getPower() );

			targetPowerGauge.setOnMouseReleased( event -> laser.setPower( targetPowerGauge.getValue() ) );

			// Laser switch update (GUI -> data)
			laserOnSwitch.setOnMouseReleased( event -> laser.setPoweredOn( !laserOnSwitch.isSelected() ) );
		}

		setBackground( null );
		// hBox.setPadding(new Insets(15, 15, 15, 15));
		setSpacing( 10 );
		getChildren().addAll( pane, targetPowerGauge, currentPowerGauge );
		setStyle( "-fx-border-style: solid;" + "-fx-border-width: 1;"
				+ "-fx-border-color: black" );
	}

	@Override public void setSetup( SPIMSetup setup, Studio studio )
	{
		this.spimSetup = setup;
		this.studio = studio;

		initExecutor();

		if(setup == null) {
			laserOnSwitch.setSelected(false);
		}
	}

	private void initExecutor() {
		if(executor != null) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
			}
		}

		executor = Executors.newScheduledThreadPool( 5 );
	}

	private void init()
	{
		initExecutor();

		// Power on/off
		laserOnSwitch = new IconSwitch();

		laserOnSwitch.setSymbolType( SymbolType.POWER );
		laserOnSwitch.setSymbolColor( Color.web( "#ffffff" ) );
		laserOnSwitch.setSwitchColor( Color.web( "#34495e" ) );
		laserOnSwitch.setThumbColor( Color.web( "#ff495e" ) );

		// Gauge bar gradient
		List< Stop > stops = new ArrayList<>();
		stops.add( new Stop( 0.0, Color.BLUE ) );
		stops.add( new Stop( 0.31, Color.CYAN ) );
		stops.add( new Stop( 0.5, Color.LIME ) );
		stops.add( new Stop( 0.69, Color.YELLOW ) );
		stops.add( new Stop( 1.0, Color.RED ) );

		// Target gauge build

		// Marker for user input
		targetPowerMarker = new Marker( 0, powerUnits );
		targetPowerGauge = RadialBargraphBuilder.create()
				.title( "Target" )
				.unit( powerUnits )
				.markers( targetPowerMarker )
				.minValue( minPower )
				.maxValue( maxPower )
				.build();
		targetPowerGauge.setBarGradientEnabled( true );
		targetPowerGauge.setBarGradient( stops );
		targetPowerGauge.setAnimated( false );
		targetPowerGauge.setInteractive( true );

		// As soon as user changes the target value, it updates gauge value
		targetPowerMarker.valueProperty()
				.bindBidirectional( targetPowerGauge.valueProperty() );

		// Actual gauge build
		currentPowerGauge = RadialBargraphBuilder.create()
				.title( "Current" )
				.unit( powerUnits )
				.minValue( minPower )
				.maxValue( maxPower )
				.build();
		currentPowerGauge.setAnimated( false );
		currentPowerGauge.setBarGradientEnabled( true );
		currentPowerGauge.setBarGradient( stops );
		currentPowerGauge.setDisable( true );

		// Laser name with Wavelength
		properties = new VBox();
		// properties.setPadding(new Insets(10));
		properties.setPrefWidth( 100 );
		properties.setSpacing( 3 );

		laserLabel = new Label();
		String fontFamily = "Arial Black";
		laserLabel.setText( waveLength + " nm" );
		laserLabel.setFont( new Font( fontFamily, 24 ) );

		wavelengthColorBox = new VBox();
		wavelengthColorBox.setBackground( new Background( new BackgroundFill( getWebColor( waveLength ),
				CornerRadii.EMPTY,
				Insets.EMPTY ) ) );
		Rectangle rectangle = new Rectangle( 33, 80, Color.TRANSPARENT );

		properties.widthProperty()
				.addListener( new ChangeListener< Number >()
				{
					@Override
					public void changed( ObservableValue< ? extends Number > observable,
							Number oldValue,
							Number newValue )
					{
						laserLabel.fontProperty()
								.set( Font.font( fontFamily,
										newValue.doubleValue() / 4.1 ) );
					}
				} );

		if(laserName.isEmpty())
		{
			properties.getChildren().add( laserLabel );
		}
		else
		{
			final Label laserNameLabel = new Label( laserName );
			laserNameLabel.setFont( new Font( fontFamily, 16 ) );
			properties.getChildren().addAll( laserNameLabel, laserLabel );
		}

		pane = new HBox();

		pane.widthProperty().addListener( new ChangeListener< Number >()
		{
			@Override
			public void changed( ObservableValue< ? extends Number > observable,
					Number oldValue,
					Number newValue )
			{
				rectangle.setWidth( newValue.doubleValue() / 4.5 );
			}
		} );

		wavelengthColorBox.getChildren().add( rectangle );

		VBox vBox = new VBox();
		// vBox.setPadding(new Insets(10, 10, 10, 10));

		// vBox.setBackground(new Background(new BackgroundFill( Color.web(
		// WavelengthColors.getWebColorString( waveLength ) ), CornerRadii.EMPTY,
		// Insets.EMPTY)));
		vBox.setSpacing( 8 );
		vBox.setAlignment( Pos.CENTER );
		vBox.getChildren().addAll( properties, laserOnSwitch );

		pane.getChildren().addAll( wavelengthColorBox, vBox );
	}

	public Color getWebColor( int pWavelength )
	{
		Color lColor = WavelengthToRGB.waveLengthToJFXColor( pWavelength );

		return lColor;
	}

	static class WavelengthToRGB
	{
		static private double Gamma = 0.80;
		static private double IntensityMax = 255;

		/**
		 * Taken from Earl F. Glynn's web page: <a
		 * href="http://www.efg2.com/Lab/ScienceAndEngineering/Spectra.htm">Spectra
		 * Lab Report</a>
		 */
		public static Color waveLengthToJFXColor( double Wavelength )
		{
			int[] lWaveLengthToRGB = waveLengthToRGB( Wavelength );

			Color lColor = Color.rgb( lWaveLengthToRGB[ 0 ],
					lWaveLengthToRGB[ 1 ],
					lWaveLengthToRGB[ 2 ] );

			return lColor;
		}

		/**
		 * Taken from Earl F. Glynn's web page: <a
		 * href="http://www.efg2.com/Lab/ScienceAndEngineering/Spectra.htm">Spectra
		 * Lab Report</a>
		 */
		public static int[] waveLengthToRGB( double Wavelength )
		{
			double factor;
			double Red, Green, Blue;

			if ( ( Wavelength >= 380 ) && ( Wavelength < 440 ) )
			{
				Red = -( Wavelength - 440 ) / ( 440 - 380 );
				Green = 0.0;
				Blue = 1.0;
			}
			else if ( ( Wavelength >= 440 ) && ( Wavelength < 490 ) )
			{
				Red = 0.0;
				Green = ( Wavelength - 440 ) / ( 490 - 440 );
				Blue = 1.0;
			}
			else if ( ( Wavelength >= 490 ) && ( Wavelength < 510 ) )
			{
				Red = 0.0;
				Green = 1.0;
				Blue = -( Wavelength - 510 ) / ( 510 - 490 );
			}
			else if ( ( Wavelength >= 510 ) && ( Wavelength < 580 ) )
			{
				Red = ( Wavelength - 510 ) / ( 580 - 510 );
				Green = 1.0;
				Blue = 0.0;
			}
			else if ( ( Wavelength >= 580 ) && ( Wavelength < 645 ) )
			{
				Red = 1.0;
				Green = -( Wavelength - 645 ) / ( 645 - 580 );
				Blue = 0.0;
			}
			else if ( ( Wavelength >= 645 ) && ( Wavelength < 781 ) )
			{
				Red = 1.0;
				Green = 0.0;
				Blue = 0.0;
			}
			else
			{
				Red = 0.0;
				Green = 0.0;
				Blue = 0.0;
			}
			;

			// Let the intensity fall off near the vision limits

			if ( ( Wavelength >= 380 ) && ( Wavelength < 420 ) )
			{
				factor = 0.3 + 0.7 * ( Wavelength - 380 ) / ( 420 - 380 );
			}
			else if ( ( Wavelength >= 420 ) && ( Wavelength < 701 ) )
			{
				factor = 1.0;
			}
			else if ( ( Wavelength >= 701 ) && ( Wavelength < 781 ) )
			{
				factor = 0.3 + 0.7 * ( 780 - Wavelength ) / ( 780 - 700 );
			}
			else
			{
				factor = 0.0;
			}
			;

			int[] rgb = new int[ 3 ];

			// Don't want 0^x = 1 for x <> 0
			rgb[ 0 ] = Red == 0.0 ? 0
					: ( int ) Math.round( IntensityMax * Math.pow( Red * factor,
					Gamma ) );
			rgb[ 1 ] = Green == 0.0 ? 0
					: ( int ) Math.round( IntensityMax * Math.pow( Green * factor,
					Gamma ) );
			rgb[ 2 ] = Blue == 0.0 ? 0
					: ( int ) Math.round( IntensityMax * Math.pow( Blue * factor,
					Gamma ) );

			return rgb;
		}
	}
}
