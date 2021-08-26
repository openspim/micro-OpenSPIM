package spim.plugin.compile;

/**
 * Dynamic code injection is based on the below blog.
 * http://vanillajava.blogspot.co.uk/2010/11/more-uses-for-dynamic-code-in-java.html
 *
 * @author Peter Lawrey
 * @version 0.1beta
 * @since 9/3/13
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class IOUtils {
	private static final Log LOG = LogFactory.getLog(IOUtils.class);
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private IOUtils() {
	}

	public static String readText(String resourceName) throws IOException {
		if (resourceName.startsWith("="))
			return resourceName.substring(1);
		StringWriter sw = new StringWriter();
		Reader isr = new InputStreamReader(getInputStream(resourceName), UTF_8);
		try {
			char[] chars = new char[8 * 1024];
			int len;
			while ((len = isr.read(chars)) > 0)
				sw.write(chars, 0, len);
		} finally {
			close(isr);
		}
		return sw.toString();
	}

	public static String readText(File file) {
		byte[] bytes = readBytes(file);
		return decodeUTF8(bytes);
	}

	private static String decodeUTF8(byte[] bytes) {
		try {
			return new String(bytes, UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings({"ReturnOfNull"})
	public static byte[] readBytes(File file) {
		if (!file.exists()) return null;
		long len = file.length();
		if (len > Runtime.getRuntime().totalMemory() / 10)
			throw new IllegalStateException("Attempted to read large file " + file + " was " + len + " bytes.");
		byte[] bytes = new byte[(int) len];
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(file));
			dis.readFully(bytes);
		} catch (IOException e) {
			close(dis);
			LOG.error("Unable to read " + file, e);
			throw new IllegalStateException("Unable to read file " + file, e);
		}

		return bytes;
	}

	public static void close(Closeable closeable) {
		if (closeable != null)
			try {
				closeable.close();
			} catch (IOException e) {
				if (LOG.isTraceEnabled()) LOG.trace("Failed to close " + closeable, e);
			}
	}

	public static boolean writeText(File file, String text) {
		return writeBytes(file, encodeUTF8(text));
	}

	private static byte[] encodeUTF8(String text) {
		try {
			return text.getBytes(UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	public static boolean writeBytes(File file, byte[] bytes) {
		File parentDir = file.getParentFile();
		if (!parentDir.isDirectory() && !parentDir.mkdirs())
			throw new IllegalStateException("Unable to create directory " + parentDir);
		// only write to disk if it has changed.
		File bak = null;
		if (file.exists()) {
			byte[] bytes2 = readBytes(file);
			if (Arrays.equals(bytes, bytes2))
				return false;
			bak = new File(parentDir, file.getName() + ".bak");
			file.renameTo(bak);
		}

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(bytes);
		} catch (IOException e) {
			close(fos);
			LOG.error("Unable to write " + file + " as " + decodeUTF8(bytes), e);
			file.delete();
			if (bak != null)
				bak.renameTo(file);
			throw new IllegalStateException("Unable to write " + file, e);
		}
		return true;
	}

	public static InputStream getInputStream(String filename) throws FileNotFoundException {
		if (filename.length() == 0) throw new IllegalArgumentException("The file name cannot be empty.");
		if (filename.charAt(0) == '=') return new ByteArrayInputStream(encodeUTF8(filename.substring(1)));
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		InputStream is = contextClassLoader.getResourceAsStream(filename);
		if (is != null) return is;
		InputStream is2 = contextClassLoader.getResourceAsStream('/' + filename);
		if (is2 != null) return is2;
		return new FileInputStream(filename);
	}

	public static LineNumberReader getLineNumberReader(String filename) throws FileNotFoundException {
		return new LineNumberReader(new InputStreamReader(getInputStream(filename), UTF_8));
	}

	public static File findFile(String path) throws FileNotFoundException {
		File file = new File(path);
		do {
			if (file.exists()) return file;
			String path2 = file.getPath();
			int pos = path2.indexOf(File.separator);
			if (pos < 0)
				throw new FileNotFoundException("Unable to derive the directory required from " + path);
			file = new File(path2.substring(pos + 1));
		} while (true);
	}

	static void createJar(String inputDir, String output) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		JarOutputStream target = new JarOutputStream(new FileOutputStream(output), manifest);
		addFileJar(inputDir.replace("\\", "/"), new File(inputDir), target);
		target.close();
	}

	private static void addFileJar(String root, File source, JarOutputStream target) throws IOException
	{
		BufferedInputStream in = null;
		try
		{
			if (source.isDirectory())
			{
				String name = source.getPath().replace("\\", "/").replace(root, "");
				if (!name.isEmpty())
				{
					if (!name.endsWith("/"))
						name += "/";
					JarEntry entry = new JarEntry(name);
					entry.setTime(source.lastModified());
					target.putNextEntry(entry);
					target.closeEntry();
				}
				for (File nestedFile: source.listFiles())
					addFileJar(root, nestedFile, target);
				return;
			}

			JarEntry entry = new JarEntry(source.getPath().replace("\\", "/").replace(root, ""));
			entry.setTime(source.lastModified());
			target.putNextEntry(entry);
			in = new BufferedInputStream(new FileInputStream(source));

			byte[] buffer = new byte[1024];
			while (true)
			{
				int count = in.read(buffer);
				if (count == -1)
					break;
				target.write(buffer, 0, count);
			}
			target.closeEntry();
		}
		finally
		{
			if (in != null)
				in.close();
		}
	}
}