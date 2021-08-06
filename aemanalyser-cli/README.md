# AEM Analyser Command-line Tool

This tool can run the AEM analysers from the commandline. There is also a [Maven Plugin](../aemanalyser-maven-plugin/README.md) which can run these from within Maven.

## Functionality

The tool performs similar tasks as the Cloud Manager pipeline after the application is built and before it is deployed. It inspects the generated content packages and runs a set of checks (analysers) on the contents of the packages.

Most of the analysers are based on the [Apache Sling Feature Model Analyser framework](https://github.com/apache/sling-org-apache-sling-feature-analyser/blob/master/readme.md).

## Installation

Obtain a distro zip file and unzip this somewhere on the file system. The executable jar file is
called `aemanalyser-cli-<version>.jar` All dependencies of the tool are
inside the distro and placed in the lib subdirectory.

## Usage

For the latest usage information, run the help option of the tool:

```
$ java -jar aemanalyser-cli.jar -h
Usage: java -jar analyser.jar [-hV] [COMMAND]
Execute feature model analyser tool
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  analyse  Analyse one or more feature models
```

Help information on subcommands can be obtained by providing the -h option to the subcommand:

```
$ java -jar aemanalyser-cli.jar analyse -h
Usage: java -jar analyser.jar analyse [-hV] [-a=<analysers>]...
                                      [-f=<featureFiles>]...
Analyse one or more feature models
  -a, --analyser=<analysers>
                  Analysers to execute
  -f, --features=<featureFiles>
                  Feature files to analyser
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
```