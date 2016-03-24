package spim.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * ObjectIO class for I/O for object
 */
public class ObjectIO
{
	public static void write(File file, float[][] arrays)
	{
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new FileOutputStream( file, false ));
			out.writeObject(arrays);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			safeClose(out);
		}
	}

	public static float[][] read(File file)
	{
		float[][] arrays = null;
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new FileInputStream( file ));
			arrays = (float[][]) in.readObject();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch ( ClassNotFoundException e )
		{
			e.printStackTrace();
		}
		finally {
			safeClose(in);
		}

		return arrays;
	}

	private static void safeClose(OutputStream out) {
		try {
			if (out != null) {
				out.close();
			}
		} catch (IOException e) {
			// do nothing
		}
	}

	private static void safeClose(InputStream in) {
		try {
			if (in != null) {
				in.close();
			}
		} catch (IOException e) {
			// do nothing
		}
	}
}
