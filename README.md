The [OpenSPIM](http://openspim.org/) project is a collaboration between multiple
labs to establish an accessible, easily adjustable light sheet microscope
system.

The *µOpenSPIM* application for [Micro Manager](https://micro-manager.org) is
the primary means to acquire image stacks with the OpenSPIM setups.

# Where to get the latest development build

This project is built and tested by the ImageJ Jenkins server:

	http://jenkins.imagej.net/view/OpenSPIM/job/micro-OpenSPIM/

The latest builds including sources and javadoc attachments are available
on that web site, too.

# How to build yourself

This project is a regular Maven project, drawing on the
[SciJava](http://scijava.org/) project. You can import it into your IDE of choice
(such as Eclipse, IntelliJ, Netbeans, etc) or build it from the command-line:

```sh
mvn exec:java -Dexec.mainClass="spim.microOpenSPIM"
```

This builds the `µOpenSPIM` JFX application which can load µManager 2.0 by pointing the µManager installed location. 
