The [OpenSPIM](http://openspim.org/) project is a collaboration between multiple
labs to establish an accessible, easily adjustable light sheet microscope
system.

[![DOI:21.11101/0000-0007-F040-1](https://zenodo.org/badge/doi/21.11101/0000-0007-F040-1.svg)](https://doi.org/21.11101/0000-0007-F040-1)

The *µOpenSPIM* application for [Micro Manager](https://micro-manager.org) is
the primary means to acquire image stacks with the OpenSPIM setups.

## Our [publication](https://onlinelibrary.wiley.com/doi/10.1002/adbi.202101182) is out

If you find µOpenSPIM useful, please consider citing our publication.

> *"Time to Upgrade: A New OpenSPIM Guide to Build and Operate Advanced OpenSPIM Configurations"*<br/>
> Johannes Girstmair, HongKee Moon, Charlène Brillard, Robert Haase, Pavel Tomancak<br/>
> **Advanced Biology** (2021) doi: 10.1002/adbi.202101182


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
