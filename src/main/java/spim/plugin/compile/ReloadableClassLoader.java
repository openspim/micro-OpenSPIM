package spim.plugin.compile;

/**
 * Thanks to http://tutorials.jenkov.com/java-reflection/dynamic-class-loading-reloading.html
 * ReloadableClassLoader is used.
 *
 * @author HongKee Moon
 * @version 0.1beta
 * @since 9/6/13
 */
class ReloadableClassLoader extends ClassLoader{

	private ClassLoader realParent;

	ReloadableClassLoader(ClassLoader parent) {
		super(parent);
		this.realParent = parent;
	}

	@Override public Class< ? > loadClass( String name)  throws ClassNotFoundException
	{
		try {
			return super.loadClass( name );
		}
		catch ( ClassNotFoundException e )
		{
			return realParent.loadClass( name );
		}
	}

	@Override protected Class< ? > findClass( String name ) throws ClassNotFoundException
	{
		Class<?> loaded = super.findLoadedClass( name );
		if( loaded != null ) return loaded;
		else return super.findClass( name );
	}
}
