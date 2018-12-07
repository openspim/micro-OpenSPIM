package spim.plugin.compile;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: October 2018
 */
public class PluginRuntime {

	public boolean compile(String className, String code)
	{
		CachedCompiler cc = CompilerUtils.CACHED_COMPILER;

		return cc.compileCheckFromJava(className, code);
	}

	public Class instanciate(String className, String code) throws
			ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		CachedCompiler cc = CompilerUtils.CACHED_COMPILER;

		Class pluginClass = cc.loadFromJava(className, code);

		return pluginClass;
	}
}
