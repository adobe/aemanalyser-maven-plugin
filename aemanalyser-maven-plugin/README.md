## AEM Analyser Maven Plugin

A Maven plugin used by developers who develop applications for AEM as a Cloud Service (AEMaaCS).
The plugin runs the same checks locally as in the Cloud Service pipeline and ensures that the application works correctly when deployed in the cloud. 

## Functionality

Provide an easy way for AEMaaCS developers to run analysers with their application during local builds to ensure the application code will also pass the analysers at AEMaaCS deploy time and 
function as expected at runtime. Analysers are based on the Apache Sling Feature Model Analyser framework: https://github.com/apache/sling-org-apache-sling-feature-analyser/blob/master/readme.md

## Installation

This is a plugin to Apache Maven it can be enabled by referencing its coordinates in 
a `pom.xml`:

    <plugin>
        <groupId>com.adobe.aem</groupId>
        <artifactId>aemanalyser-maven-plugin</artifactId>
        <version>... version ...</version>
    </plugin>


Example:

    <plugin>
        <groupId>com.adobe.aem</groupId>
        <artifactId>aemanalyser-maven-plugin</artifactId>
        <version>0.9.0</version>
        <extensions>true</extensions>
    </plugin>


As this plugin is available in Maven Central, no additional configuration is needed to bring it into your Maven project.

## Usage

The easiest way to use this plugin is in a dedicated module, with packaging-type `aem-analyse`. Enable the plugin by listing it in the `build->plugins` section and specify the content packages to analyse in the `<dependencies>` section. 

The analyser plugin will run the default set of analysers on the content packages configured while picking up the aem sdk version from the `<parent>`. Please note that it is important that you have an aem sdk version configured in your parent that is equal or higher to:

```
        <aem.sdk.api>2020.11.4506.20201112T235200Z-201028</aem.sdk.api>
```

With that, your project pom.xml needs to look somewhat like this:

```
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
                <version>0.9.0</version>
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
```

### Example

As an example, consider adding a new module to the wknd project. All that is needed is a new pom in a subfolder looking like this:

```
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
                <version>0.9.0</version>
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
```

#### Selecting Tasks

The plugin will execute a number of default analysers. It is possible to select a different set of
analyser tasks, for example with the following configuration:

    <includeTasks>
        <includeTask>bundle-packages</includeTask>
        <includeTask>requirements-capabilities</includeTask>
    </includeTasks>

Please note that if you remove tasks which are run by default, the plugin might not report any errors in your project, while the analysers run as part of the AEM as a Cloud Service pipeline might report errors. 

#### Configuring Analyser Tasks

Some analyser tasks require configuration. Default configuration is used by the plugin for the
default set of analysers. Additional or different configuration can be provided like this:

    <includeTasks>
        <includeTask>bundle-packages</includeTask>
        <includeTask>requirements-capabilities</includeTask>
        <includeTask>bundle-resources</includeTask>
        <includeTask>api-regions-check-order</includeTask>
    </includeTasks>

    <taskConfiguration>
        <api-regions-check-order>
            <order>global,myregion</order>
        </api-regions-check-order>
    </taskConfiguration>

Please note, that overriding the default configuration for the analysers might hide errors locally that will be catched in the Cloud Service pipeline.

### Maven Goals

The plugin also contains a number of Maven goals that can be executed directly for integration into existing projects.

* **convert** - convert the content packages to feature models to prepare them for the analysers. If the packaging type is `aem-analyse` all dependencies of type content package will be converted. Otherwise the current artifact will be converted.
* **project-aggregate** - aggregate the feature models produced by the conversion with the AEMaaCS SDK. Will also trigger the **convert** project.
* **project-analyse** - run the analysers on the current project, will also trigger **project-aggregate**.

A typical use would be to just configure the **project-analyse** goal if the plugin should be integrated into an existing project.
