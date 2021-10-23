# AEM Analyser Maven Plugin

A Maven plugin used by developers of applications for AEM as a Cloud Service (AEMaaCS).

The plugin provides an easy way to run the same checks locally as in the Cloud Manager pipeline and ensures that the application works correctly when deployed in the cloud.

> **_NOTE:_**  Please make sure to always use the latest version. Refrain from using any version below 1.1.10

## Functionality

The plugin performs similar tasks as the Cloud Manager pipeline after the application is built and before it is deployed. It inspects the generated content packages and runs a set of checks (analysers) on the contents of the packages. By default it check against the latest available SDK version.

Most of the analysers are based on the [Apache Sling Feature Model Analyser framework](https://github.com/apache/sling-org-apache-sling-feature-analyser/blob/master/readme.md).

## Installation

This is a plugin to Apache Maven. It can be enabled by referencing its coordinates in a `pom.xml`:

    <plugin>
        <groupId>com.adobe.aem</groupId>
        <artifactId>aemanalyser-maven-plugin</artifactId>
        <version>... version ...</version>
    </plugin>

Example:

    <plugin>
        <groupId>com.adobe.aem</groupId>
        <artifactId>aemanalyser-maven-plugin</artifactId>
        <version>1.1.12</version> <!-- Make sure to use the latest release -->
    </plugin>

As this plugin is available in Maven Central, no additional configuration is needed to bring it into your Maven project.

## Usage

The usage of this plugin depends on the AEM project structure. For most projects the best way to use this plugin is to add it to a Maven project producing a `container` package (aka `all` package). For example if you are using a project structure similar to the [AEM Archetype](https://github.com/adobe/aem-project-archetype) then this is the `all` module.

Enable the plugin by listing it in the `build->plugins` section of the module.

    <build>
        <plugins>
            ....
            <plugin>
                <groupId>com.adobe.aem</groupId>
                <artifactId>aemanalyser-maven-plugin</artifactId>
                <version>1.1.12</version> <!-- Make sure to use the latest release -->
                <executions>
                    <execution>
                        <id>aem-analyser</id>
                        <goals>
                            <goal>project-analyse</goal>                           
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

The analyser plugin will run the default set of analysers on the content packages configured while picking up the AEM SDK version from the `<parent>`. 

### Use Analyser in a Separate Maven Module

The analyser can also be used in a dedicated module with packaging-type `aem-analyse`. Enable the plugin by listing it in the `build->plugins` section and specify the content packages to analyse in the `<dependencies>` section.

With that, your project `pom.xml` needs to look somewhat like this:

    <project>
        <modelVersion>4.0.0</modelVersion>

        <parent>
            <groupId>!!insert.parent.groupId!!</groupId>
            <artifactId>!!insert.parent.artifactId!!</artifactId>
            <version>!!insert.parent.version!!</version>
            <relativePath>../pom.xml</relativePath>
        </parent>
    
        <groupId>!!insert.groupId!!</groupId>
        <artifactId>!!insert.artifactId!!</artifactId>
        <version>!!insert.version!!</version>
        <packaging>aem-analyse</packaging>

        <build>
            <plugins>
                <plugin>
                    <groupId>com.adobe.aem</groupId>
                    <artifactId>aemanalyser-maven-plugin</artifactId>
                    <version>1.1.12</version> <!-- Make sure to use the latest release -->
                    <extensions>true</extensions>
                </plugin>
            </plugins>
        </build>

        <dependencies>
            <dependency>
                <groupId>!!insert.dependency.groupId!!</groupId>
                <artifactId>!!insert.dependency.artifactId!!</artifactId>
                <version>!!insert.dependency.version!!</version>
                <type>zip</type>
            </dependency>
        </dependencies>
    </project>

### Example for a Multi Project Setup

If your AEM project creates multiple container content packages, for example if you have a multi project setup, then instead of running the analyser on every project, it is better to run it once on all packages. This is especially important if there are dependencie between the projects.

All that is needed is a new `pom.xml` in a subfolder, for example named `analyse`, looking like this:

    <project>
        <modelVersion>4.0.0</modelVersion>
        <parent>
            <groupId>!!insert.parent.groupId!!</groupId>
            <artifactId>!!insert.parent.artifactId!!</artifactId>
            <version>!!insert.parent.version!!</version>
            <relativePath>../pom.xml</relativePath>
        </parent>
        <artifactId>!!insert.artifactId!!</artifactId>
        <packaging>aem-analyse</packaging>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.adobe.aem</groupId>
                    <artifactId>aemanalyser-maven-plugin</artifactId>
                    <version>1.1.12</version> <!-- Make sure to use the latest release -->
                    <extensions>true</extensions>
                </plugin>
            </plugins>
        </build>
        <dependencies> <!-- Add all application content packages -->
            <dependency>
                <groupId>!!insert.groupId!!</groupId>
                <artifactId>!!insert.artifactId.project1.all</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <type>zip</type>
            </dependency>
            <dependency>
                <groupId>!!insert.groupId!!</groupId>
                <artifactId>!!insert.artifactId.project2.all</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <type>zip</type>
            </dependency>
            <dependency>
                <groupId>!!insert.groupId!!</groupId>
                <artifactId>!!insert.artifactId.project3.all</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <type>zip</type>
            </dependency>
        </dependencies>
    </project>

And then you need to add this module to the parent project by adding this line to the parent `pom.xml` in the modules section:

    <module>analyse</module>   <!-- This is the name of the subfolder -->

Make sure to add it as the last module.

## AEM as a Cloud Service

During the build in a Cloud Manager pipeline, the plugin as part of the Maven project build is automatically disabled. Instead the Cloud Manager pipeline performs the required validation. Please also consult the [AEM Documentation](https://experienceleague.adobe.com/docs/experience-manager-core-components/using/developing/archetype/build-analyzer-maven-plugin.html).

## Maven Goals

The plugin provides the following goals:

* **analyse** : This is the default goal used by a 'aem-analyse' Maven project. If the packaging type is `aem-analyse` all dependencies of type content package will be converted. Otherwise the current artifact will be converted.
* **project-analyse** : This goal can be used in existing projects. By default it runs during the `verify` phase and will analyse the artifact of the current project.

A typical use would be to just configure the **project-analyse** goal if the plugin should be integrated into an existing project.

### Configuration

The plugin can be configured with the following configuration properties:

* **skip** : If this is set to `true` the plugin execution will be skipped. The plugin can also be skipped by setting the property `aem.analyser.skip` to `true`.
* **failOnAnalyserErrors** : This is by default enabled and causes the build to fail if any analyser reports an error. This can be set to `false` to continue the build.
* **strictValidation** : If this is set to `true` all warnings from the analyser will be turned in errors and fail the build. The property `aem.analyser.strict` can be used to set this from the commandline.
* **sdkArtifactId** : By default, the plugin inspects the dependencies of the project and looks for an artifact with the group id `com.adobe.aem` and an artifact id of either `aem-prerelease-sdk-api` or `aem-sdk-api`. For advanced usages, this property can be set to disable the detection mechanism.
* **sdkVersion** : This property can be used to exactly specify the SDK version to be used for the analysis. If not set, the plugin will use the latest available SDK. The value for this property can also be specified via the command line by setting `sdkVersion`.
* **useDependencyVersions** : If this property is enabled, the version for the SDK as specified via the dependencies is used. This is by default disabled to use the latest version. The value for this property can also be specified via the command line by setting `sdkUseDependency`.
* **classifier**  : If this property is set the content package to analyze is retrieved from the attached project artifact with the given classifier. The value for this property can also be specified via the command line by setting `aem.analyser.classifier`.

## Advanced Configurations

The following configuration options can be provided to the plugin. However they alter the behaviour of the plugin. Therefore using these configurations might result in your project build succeeding locally - but it might fail in the Cloud Manager pipeline as a different configuration is running there. Therefore it is generally not recommended to change the configuration of the plugin.

### Selecting Tasks

The plugin will execute a number of default analysers. It is possible to select a different set of analyser tasks, for example with the following configuration, the set of analysers running on the final aggregates can be changes.

    <analyserTasks>
        <analyserTask>bundle-packages</analyserTask>
        <analyserTask>requirements-capabilities</analyserTask>
    </analyserTasks>

Some analysers are only run on the provided content packages. These can be changed with setting the user tasks:

    <analyserUserTasks>
        <analyserUserTask>bundle-content</analyserUserTask>
    </analyserUserTasks>

Please note that if you remove tasks which are run by default, the plugin might not report any errors in your project, while the analysers that run as part of the Cloud Manager pipeline might report errors.

### Configuring Analyser Tasks

Some analyser tasks require configuration. Default configuration is used by the plugin for the default set of analysers. Additional or different configuration can be provided like this:

    <analyserTaskConfigurations>
        <api-regions-check-order>
            <order>global,myregion</order>
        </api-regions-check-order>
    </analyserTaskConfigurations>

Please note, that overriding the default configuration for the analysers might hide errors locally that will be catched in the Cloud Manager pipeline.

## Deprecated Maven Goals

Both, the `aggregate` as well as the `convert` Maven mojo have been deprecated. It is recommended to remove them from your project. Their implementation has been changed to do no work. All work has been moved into the remaining `analyse` mojo.
