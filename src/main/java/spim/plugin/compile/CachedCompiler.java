package spim.plugin.compile;

/**
 * Dynamic code injection is based on the below blog.
 * http://vanillajava.blogspot.co.uk/2010/11/more-uses-for-dynamic-code-in-java.html
 *
 *
 * @author Peter Lawrey
 * @version 0.1beta
 * @since 9/3/13
 */

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.LogFactory;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@SuppressWarnings({"StaticNonFinalField"})
public class CachedCompiler {
	private final File sourceDir;
	private File classDir;
//	private final Writer writer;

	public CachedCompiler(File sourceDir, File classDir) {
		this.sourceDir = sourceDir;
		this.classDir = classDir;
//		this.writer = new Writer()
//		{
//			@Override public void write( char[] cbuf, int off, int len ) throws IOException
//			{
//				System.err.println( CharBuffer.wrap( cbuf, off, len ) );
//			}
//
//			@Override public void flush() throws IOException
//			{
//
//			}
//
//			@Override public void close() throws IOException
//			{
//
//			}
//		};
	}

	public Class loadFromJava(String className, String javaCode) throws ClassNotFoundException {
//		return loadFromJava( getClass().getClassLoader(), className, javaCode);
		return loadFromJava(new ReloadableClassLoader( getClass().getClassLoader() ), className, javaCode);
//		return loadFromJava(new ReloadableClassLoader( getDefaultClassLoader() ), className, javaCode);
//		return loadFromJava(new ReloadableClassLoader( ToolProvider.getSystemToolClassLoader() ), className, javaCode);
//		return loadFromJava(null, className, javaCode);
	}

	private static ClassLoader getDefaultClassLoader() {
		ClassLoader cl = null;
		try {
			cl = Thread.currentThread().getContextClassLoader();
		} catch (final SecurityException e) {
			// ignore
		}

		if (cl == null) {
			cl = CachedCompiler.class.getClassLoader();
		}

		if (cl != null) {
			return cl;
		}

		return ClassLoader.getSystemClassLoader();
	}


	public Map<String, byte[]> compileFromJava(String className, String javaCode) {
		Iterable<? extends JavaFileObject> compilationUnits;
		if (sourceDir != null) {
			String filename = className.replaceAll("\\.", '\\' + File.separator) + ".java";
			File file = new File(sourceDir, filename);
			IOUtils.writeText(file, javaCode);
			compilationUnits = CompilerUtils.s_standardJavaFileManager.getJavaFileObjects(file);
		} else {
			compilationUnits = Arrays.asList(new JavaSourceFromString(className, javaCode));
		}
		// reuse the same file manager to allow caching of jar files
		CompilerUtils.s_compiler.getTask(null, CompilerUtils.s_fileManager, null, null, null, compilationUnits);

		return CompilerUtils.s_fileManager.getAllBuffers();
	}

	public boolean compileCheckFromJava(String className, String javaCode) {
		Iterable<? extends JavaFileObject> compilationUnits;
		if (sourceDir != null) {
			String filename = className.replaceAll("\\.", '\\' + File.separator) + ".java";
			File file = new File(sourceDir, filename);
			IOUtils.writeText(file, javaCode);
			compilationUnits = CompilerUtils.s_standardJavaFileManager.getJavaFileObjects(file);
		} else {
			compilationUnits = Arrays.asList(new JavaSourceFromString(className, javaCode));
		}
		// reuse the same file manager to allow caching of jar files

		return CompilerUtils.s_compiler.getTask(null, CompilerUtils.s_fileManager, null, null, null, compilationUnits).call();
	}

	public Class loadFromJava(ClassLoader classLoader, String className, String javaCode) throws ClassNotFoundException {
		for (Map.Entry<String, byte[]> entry : compileFromJava(className, javaCode).entrySet()) {
			String className2 = entry.getKey();
			byte[] bytes = entry.getValue();
			if (classDir != null) {
				String filename = className2.replaceAll("\\.", '\\' + File.separator) + ".class";
				boolean changed = IOUtils.writeBytes(new File(System.getProperty("user.dir") + "file:" + System.getProperty("user.dir"), filename), bytes);
				if (changed)
					LogFactory.getLog(CachedCompiler.class).info("Updated " + className2 + " in " + System.getProperty("user.dir"));
			}
			CompilerUtils.defineClass(classLoader, className2, bytes);
		}

		if(classDir != null) {
			try {
				IOUtils.createJar(System.getProperty("user.dir") + "file:" + System.getProperty("user.dir"), classDir + "/" + className + ".jar");
				FileUtils.cleanDirectory( new File(System.getProperty("user.dir") + "file:") );
				new File(System.getProperty("user.dir") + "file:").delete();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		CompilerUtils.s_fileManager.clearBuffers();
		return classLoader.loadClass(className);
	}



	public static void close() {
		try {
			CompilerUtils.s_fileManager.close();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public void setClassDir(File classDir) {
		this.classDir = classDir;
	}
}

