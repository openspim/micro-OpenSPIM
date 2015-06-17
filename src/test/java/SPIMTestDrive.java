import java.io.File;
import org.scijava.util.ClassUtils;
import spim.SPIMAcquisition;

/**
 * Created by moon on 4/20/15.
 */
public class SPIMTestDrive
{
	public static void main(String[] args)
	{
		final File classesDirectory = new File(ClassUtils.getLocation( SPIMAcquisition.class.getName() ).getPath());
		final File targetDirectory = classesDirectory.getParentFile();
		System.setProperty("org.micromanager.plugin.path", targetDirectory.getPath());

		System.err.println(System.getProperty("org.micromanager.plugin.path"));

		MMStudioPlugin mm = new MMStudioPlugin();

		mm.run( "" );
	}
}
