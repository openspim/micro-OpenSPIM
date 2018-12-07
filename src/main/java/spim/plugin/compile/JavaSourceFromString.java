package spim.plugin.compile;

/**
 * Dynamic code injection is based on the below blog.
 * http://vanillajava.blogspot.co.uk/2010/11/more-uses-for-dynamic-code-in-java.html
 *
 * @author Peter Lawrey
 * @version 0.1beta
 * @since 9/3/13
 */
import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/* A file object used to represent source coming from a string.
 */
public class JavaSourceFromString extends SimpleJavaFileObject {
	/**
	 * The source code of this "file".
	 */
	private final String code;

	/**
	 * Constructs a new JavaSourceFromString.
	 *
	 * @param name the name of the compilation unit represented by this file object
	 * @param code the source code for the compilation unit represented by this file object
	 */
	public JavaSourceFromString(String name, String code) {
		super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
				Kind.SOURCE);
		this.code = code;
	}

	@SuppressWarnings({"RefusedBequest"})
	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return code;
	}
}
