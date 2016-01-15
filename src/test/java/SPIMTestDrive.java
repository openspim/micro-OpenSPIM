import java.io.File;

import ij.IJ;
import ij.ImageJ;
import org.micromanager.MMStudio;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;
import org.scijava.util.ClassUtils;
import spim.SPIMAcquisition;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * SPIMTestDrive launches MMStudio with the current SPIMAcquisitionPlugin
 * Note: set the working folder to where Micro-Manager1.4 is located
 */
public class SPIMTestDrive
{
	static MMStudio studio_;

	public static void main(String[] args)
	{
		final File classesDirectory = new File(ClassUtils.getLocation( SPIMAcquisition.class.getName() ).getPath());
		final File targetDirectory = classesDirectory.getParentFile();
		System.setProperty("org.micromanager.plugin.path", targetDirectory.getPath());

		System.err.println(System.getProperty("org.micromanager.plugin.path"));

		// Choose either openMMStudioInImageJ() or openMMStudioOnly()

		//openMMStudioInImageJ()
		openMMStudioOnly();
	}

	// When users want to test the mmplugin with ImageJ instance
	public static void openMMStudioInImageJ()
	{
		// This way would work with IJ directory together
		new ImageJ();
		IJ.getInstance().exitWhenQuitting( true );
	}

	// When users want to test only the mmplugin without ImageJ instance
	public static void openMMStudioOnly()
	{
		SwingUtilities.invokeLater( new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					if ( studio_ == null || !studio_.getIsProgramRunning() )
					{
						// OS-specific stuff
						if ( JavaUtils.isMac() )
						{
							System.setProperty( "apple.laf.useScreenMenuBar", "true" );
						}
						try
						{
							UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
						}
						catch ( Exception e )
						{
							ReportingUtils.logError( e );
						}

						studio_ = new MMStudio( false );
						MMStudio.getFrame().setVisible( true );
						MMStudio.getFrame().setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
					}
				}
				catch ( Exception e )
				{
					ReportingUtils.logError( e );
				}
			}
		} );
	}
}
