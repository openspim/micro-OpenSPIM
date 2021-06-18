package spim.util;

import java.io.*;
import java.net.URL;
import java.util.Properties;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class NativeLoader {
	public static void loadLibrary( final Class< ? > clazz ) {
		final String osName = System.getProperty( "os.name" ).toLowerCase();

		String machinePath = "";
		String libPrefix = "lib";
		final String libSuffix;
		if ( osName.startsWith( "mac" ) ) {
			// MacOSX library naming convention
			machinePath = "/lib/macosx/";
			libPrefix += "/macosx/lib";
			libSuffix = ".dylib";
		} else if ( osName.startsWith( "win" ) ) {
			// Windows library naming convention
			machinePath = "/lib/win64/";
			libPrefix += "/win64/";
			libSuffix = ".dll";
		} else {
			// Linux library naming convention
			machinePath = "/lib/linux64/";
			libPrefix += "/linux64/";
			libSuffix = ".so";
		}

		final String propertiesPath = "/Labels.properties";
		final URL url = clazz.getResource( propertiesPath );
		//IJ.log( "url = " + url );

		final String libName = "blosc";
		final String urlString = url.toString();
		final String libPath;
		if ( urlString.startsWith( "file:" ) ) {

			// for development purpose, we need to load the native libraries as a file itself
			String path = urlString.substring( 5, urlString.length() - propertiesPath.length() );
			libPath = path + "/" + libPrefix + libName + libSuffix;
			machinePath = path + machinePath;
//			System.out.println(libPath);

		} else if ( urlString.startsWith( "jar:file:" ) ) {

			// for Fiji runtime, we load the native library from the jar package.
			int bang = urlString.indexOf( "!/" );
			if ( bang < 0 ) { throw new UnsatisfiedLinkError( "Unexpected URL: " + urlString ); }
			String path = urlString.substring( 9, bang );
			//IJ.log("path = " + path);

			path = "/" + libPrefix + libName + libSuffix;
			//IJ.log("path2 = " + path);
			//IJ.log("path3 = " + libPrefix + libName + libSuffix);
			libPath = loadNative( libPrefix + libName + libSuffix, path );

			//IJ.log("libPath = " + libPath);
		} else {
			throw new UnsatisfiedLinkError( "Could not load native library: URL of .jar is " + urlString );
		}

//		String props = System.getProperty("jna.library.path");
//		System.out.println(props);
		System.setProperty("jna.library.path", machinePath);

		System.load( libPath );
//		System.loadLibrary( libName );
	}

	/*
	 * Packaged native library cannot be loaded directly.
	 * Therefore, it is necessary to extract them in the temporary folder.
	 * http://stackoverflow.com/questions/2937406/how-to-bundle-a-native-library-
	 * and-a-jni-library-inside-a-jar
	 */
	public static String loadNative( String libName, String libPath ) {
		String path = null;
		try {
			// Create an InputStream for the DataOutputStream writer as a temporary file
			InputStream in = NativeLoader.class.getResourceAsStream( libPath );

			File fileOut = File.createTempFile( "lib", libName );
			path = fileOut.getAbsolutePath();

			DataOutputStream writer = new DataOutputStream( new FileOutputStream( fileOut ) );

			long oneChar = 0;
			while ( ( oneChar = in.read() ) != -1 ) {
				writer.write( ( int ) oneChar );
			}

			in.close();
			writer.close();
		} catch ( Exception e ) {
			e.printStackTrace();
		}

		return path;
	}
}
