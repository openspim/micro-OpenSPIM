The [OpenSPIM](http://openspim.org/) project is a collaboration between multiple
labs to establish an accessible, easily adjustable light sheet microscope
system.

The *SPIMAcquisition* plugin for [Micro Manager](https://micro-manager.org) is
the primary means to acquire image stacks with the OpenSPIM setups.

# How to build

This project is a regular Maven project, drawing on the
[SciJava](http://scijava.org/) project. You can import it into your IDE of choice
(such as Eclipse, IntelliJ, Netbeans, etc) or build it from the command-line:

```sh
mvn -Dscijava.enforce.skip
```

This builds the `SPIMAcquisition` plugin which can then be installed by copying
`target/SPIMAcquisition-<version>.jar` into `Fiji.app/mmplugins/`.

# Installing into a Micro-Manager directory instead of Fiji

The SPIMAcquisition plugin depends on a couple of components that are shipped
with Fiji but not with Micro-Manager. Therefore, you will have to work a little
harder if you want to install the plugin into a vanilla Micro-Manager as
downloaded from [the Micro-Manager website](https://micro-manager.org):

1. Run `mvn dependency:copy-dependencies` in the SPIMAcquisition directory
2. From `target/dependency/` in that subdirectory, copy the `imglib2-*.jar` files to your Micro-Manager's `mmplugins/` subdirectory.
3. Copy `target/SPIMAcquisition-1.0.0-SNAPSHOT.jar` to the `mmplugins/` subdirectory, too.
4. Restart Micro-Manager.
