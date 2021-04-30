# AEM Analyser Maven Plugin

A Maven plugin used by developers of applications for AEM as a Cloud Service (AEMaaCS).

The plugin provides an easy way to run the same checks locally as in the Cloud Manager pipeline and ensures that the application works correctly when deployed in the cloud.

## Functionality

The plugin performs similar tasks as the Cloud Manager pipeline after the application is built and before it is deployed. It inspects the generated content packages and runs a set of checks (analysers) on the contents of the packages.

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
        <version>0.9.2</version> <!-- Make sure to use the latest release -->
        <extensions>true</extensions>
    </plugin>

As this plugin is available in Maven Central, no additional configuration is needed to bring it into your Maven project.

## Usage

The easiest way to use this plugin is in a dedicated module, with packaging-type `aem-analyse`. Enable the plugin by listing it in the `build->plugins` section and specify the content packages to analyse in the `<dependencies>` section.

The analyser plugin will run the default set of analysers on the content packages configured while picking up the AEM SDK version from the `<parent>`. Please note that it is important that you have an AEM SDK version configured in your parent that is equal or higher to:

    <aem.sdk.api>2020.11.4506.20201112T235200Z-201028</aem.sdk.api>

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
                    <version>0.9.2</version> <!-- Make sure to use the latest release -->
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

### Example

As an example, consider adding a new module to the [AEM WKND Sites project](https://github.com/adobe/aem-guides-wknd]). All that is needed is a new `pom.xml` in a subfolder looking like this:

    <project>
        <modelVersion>4.0.0</modelVersion>
        <parent>
            <groupId>com.adobe.aem.guides</groupId>
            <artifactId>aem-guides-wknd</artifactId>
            <version>0.0.7-SNAPSHOT</version>
            <relativePath>../pom.xml</relativePath>
        </parent>
        <artifactId>wknd.analyse</artifactId>
        <packaging>aem-analyse</packaging>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.adobe.aem</groupId>
                    <artifactId>aemanalyser-maven-plugin</artifactId>
                    <version>0.9.2</version> <!-- Make sure to use the latest release -->
                    <extensions>true</extensions>
                </plugin>
            </plugins>
        </build>
        <dependencies>
            <dependency>
                <groupId>com.adobe.aem.guides</groupId>
                <artifactId>aem-guides-wknd.all</artifactId>
                <version>0.0.7-SNAPSHOT</version>
                <type>zip</type>
            </dependency>
        </dependencies>
    </project>

And then you need to add this module to the parent project by adding this line to the parent `pom.xml` in the modules section:

    <module>analyse</module>   <!-- This is the name of the subfolder -->

Make sure to add it as the last module.

## Maven Goals

While the best way to use this plugin is to create a separate module as outlined above, the plugin can also be added to existing projects, for example a content-package project. It provides the following goals:

* **analyse** : This is the default goal used by a 'aem-analyse' Maven project. If the packaging type is `aem-analyse` all dependencies of type content package will be converted. Otherwise the current artifact will be converted.
* **project-analyse** : This goal can be used in existing projects. By default in runs during the `verify` phase and will analyse the artifact of the current project.

A typical use would be to just configure the **project-analyse** goal if the plugin should be integrated into an existing project.

## Advanced Configurations

The following configuration options can be provided to the plugin. However they alter the behaviour of the plugin. Therefore using these configurations might result in your project build succeeding locally - but it might fail in the Cloud Manager pipeline as a different configuration is running there. Therefore it is generally not recommended to change the configuration of the plugin.

### Selecting Tasks

The plugin will execute a number of default analysers. It is possible to select a different set of analyser tasks, for example with the following configuration:

    <analyserTasks>
        <analyserTask>bundle-packages</analyserTask>
        <analyserTask>requirements-capabilities</analyserTask>
    </analyserTasks>

Please note that if you remove tasks which are run by default, the plugin might not report any errors in your project, while the analysers that run as part of the Cloud Manager pipeline might report errors.

### Configuring Analyser Tasks

Some analyser tasks require configuration. Default configuration is used by the plugin for the default set of analysers. Additional or different configuration can be provided like this:

    <analyserTasks>
        <analyserTask>bundle-packages</analyserTask>
        <analyserTask>requirements-capabilities</analyserTask>
        <analyserTask>bundle-resources</analyserTask>
        <analyserTask>api-regions-check-order</analyserTask>
    </analyserTasks>

    <analyserTaskConfigurations>
        <api-regions-check-order>
            <order>global,myregion</order>
        </api-regions-check-order>
    </analyserTaskConfigurations>

Please note, that overriding the default configuration for the analysers might hide errors locally that will be catched in the Cloud Manager pipeline.

## See Also

Please also consult the [AEM Documentation](https://experienceleague.adobe.com/docs/experience-manager-core-components/using/developing/archetype/build-analyzer-maven-plugin.html).
