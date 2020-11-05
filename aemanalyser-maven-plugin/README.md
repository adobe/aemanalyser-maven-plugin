## AEM Analyser Maven Plugin

A Maven plugin used by developers who develop Java components for AEMaaCS 
(AEM as a Cloud Service) during their local Java 
builds to ensure that their components work correctly in when deployed to 
AEM as a Cloud Service.

## Functionality

Provide an easy way for AEMaaCS users to run analysers with their components during local 
builds to ensure these components will also pass the analysers at AEMaaCS deploy time and 
function as expected at runtime. Analysers are based on the Sling Feature Model Analyser framework: https://github.com/apache/sling-org-apache-sling-feature-analyser/blob/master/readme.md

### Installation

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
        <version>1.0.0</version>
        <extensions>true</extensions>
    </plugin>

### Configuration

The easiest way to use this plugin is in a dedicated module, with packaging-type `aem-analyse`. Enable the plugin by listing it in the `build->plugins` section and specify the content packages to analyse in the `<dependencies>` section. 
The analyser plugin will run the default set of analysers on the content packages configured.

```
<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.acme</groupId>
    <artifactId>my-analyse-project</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>aem-analyse</packaging>

    <build>
        <plugins>
            <plugin>
                <groupId>com.adobe.aem</groupId>
                <artifactId>aemanalyser-maven-plugin</artifactId>
                <version>1.0.0</version>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>org.acme</groupId>
            <artifactId>my-content-package</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>zip</type>
        </dependency>
    </dependencies>
</project>
```

### Maven Goals

The plugin also contains a number of Maven goals that can be executed directly for integration into existing projects.

* **convert** - convert the content packages to feature models to prepare them for the analysers. If the packaging type is `aem-analyse` all dependencies of type content package will be converted. Otherwise the current artifact will be converted.
* **project-aggregate** - aggregate the feature models produced by the conversion with the AEMaaCS SDK. Will also trigger the **convert** project.
* **project-analyse** - run the analysers on the current project, will also trigger **project-aggregate**.

A typical use would be to just configure the **project-analyse** goal if the plugin should be integrated into an existing project.