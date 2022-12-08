/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.project.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Bundles;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;

import com.adobe.aem.analyser.tasks.ConfigurationFile;
import com.adobe.aem.analyser.tasks.ConfigurationFileType;
import com.adobe.aem.analyser.tasks.TaskResult;
import com.adobe.aem.analyser.tasks.ConfigurationFile.Location;
import com.adobe.aem.analyser.tasks.TaskResult.Annotation;
import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.SDKType;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.ArtifactsFile.FileType;

public class Application implements Serializable {

    private final File directory;

    public Application(final File f) {
        this.directory = f;
    }

    public File getDirectory() {
        return this.directory;
    }

    private File getSourceFile(final String name) {
        final File src = new File(this.getDirectory(), "src");
        return new File(src, name);
    }

    /**
     * Get the relative path for a project file
     * @param file The file
     * @return The relative path
     */
    public String getRelativePath(final File file) {
        return file.getAbsolutePath().substring(this.directory.getAbsolutePath().length() + 1);
    }

    private void getArtifactsFile(final List<ArtifactsFile> result, final ArtifactsFile.FileType fileType, final ServiceType serviceType) {
        final String prefix = fileType == FileType.BUNDLES ? "bundles" : "content-packages";
         final String filename = serviceType == null ? prefix.concat(".json") : prefix.concat(".").concat(serviceType.asString()).concat(".json");
         final File file = getSourceFile(filename);
         if ( file.exists()) {
             final ArtifactsFile f = new ArtifactsFile(fileType, file);
             f.setServiceType(serviceType);
             result.add(f);
         }
     }

    public List<ArtifactsFile> getBundleFiles() {
        final List<ArtifactsFile> result = new ArrayList<>();
        getArtifactsFile(result, ArtifactsFile.FileType.BUNDLES, null);
        getArtifactsFile(result, ArtifactsFile.FileType.BUNDLES, ServiceType.AUTHOR);
        getArtifactsFile(result, ArtifactsFile.FileType.BUNDLES, ServiceType.PUBLISH);
        return result;
    }

    public List<ArtifactsFile> getContentPackageFiles() {
        final List<ArtifactsFile> result = new ArrayList<>();
        getArtifactsFile(result, ArtifactsFile.FileType.CONTENT_PACKAGES, null);
        getArtifactsFile(result, ArtifactsFile.FileType.CONTENT_PACKAGES, ServiceType.AUTHOR);
        getArtifactsFile(result, ArtifactsFile.FileType.CONTENT_PACKAGES, ServiceType.PUBLISH);
        return result;
    }

    private File getConfigurationDirectory(final ServiceType type, final SDKType sdkType, final EnvironmentType envType) {
        if ( sdkType != null && envType != null ) {
            return null;
        }
        final String filename;
        if ( type == null && sdkType == null && envType == null ) {
            filename = "configs";
        } else if ( type == null && envType == null ) {
            filename = "configs.".concat(sdkType.asString());
        } else if ( type == null && sdkType == null ) {
            filename = "configs.".concat(envType.asString());
        } else if ( sdkType == null && envType == null ) {
            filename = "configs.".concat(type.asString());
        } else if ( sdkType != null ) {
            filename = "configs.".concat(type.asString()).concat(".").concat(sdkType.asString());
        } else {
            filename = "configs.".concat(type.asString()).concat(".").concat(envType.asString());
        }
        final File f = this.getSourceFile(filename);
        return f.exists() ? f : null;
    }

    private void getConfigurationFiles(final List<ConfigurationFile> files, 
          final ServiceType serviceType, final SDKType sdkType, final EnvironmentType envType) {
        final File directory = this.getConfigurationDirectory(serviceType, sdkType, envType);
        if ( directory != null && directory.isDirectory() ) {
            for(final File f : directory.listFiles()) {
                if ( !f.getName().startsWith(".") ) {
                    final ConfigurationFileType fileType = ConfigurationFileType.fromFileName(f.getName());
                    if ( fileType != null ) {
                        final ConfigurationFile cfg = new ConfigurationFile(Location.APPS, f, fileType);
                        cfg.setServiceType(serviceType);
                        cfg.setSdkType(sdkType);
                        cfg.setEnvType(envType);
                        files.add(cfg);    
                    }
                }
            }
        }
    }

    public List<ConfigurationFile> getConfigurationFiles() {
        final List<ConfigurationFile> result = new ArrayList<>();
        this.getConfigurationFiles(result, null, null, null);
        this.getConfigurationFiles(result, null, SDKType.RDE, null);
        this.getConfigurationFiles(result, null, null, EnvironmentType.DEV);
        this.getConfigurationFiles(result, null, null, EnvironmentType.STAGE);
        this.getConfigurationFiles(result, null, null, EnvironmentType.PROD);

        this.getConfigurationFiles(result, ServiceType.AUTHOR, null, null);
        this.getConfigurationFiles(result, ServiceType.AUTHOR, null, EnvironmentType.DEV);
        this.getConfigurationFiles(result, ServiceType.AUTHOR, null, EnvironmentType.STAGE);
        this.getConfigurationFiles(result, ServiceType.AUTHOR, null, EnvironmentType.PROD);
        this.getConfigurationFiles(result, ServiceType.AUTHOR, SDKType.RDE, null);

        this.getConfigurationFiles(result, ServiceType.PUBLISH, null, null);
        this.getConfigurationFiles(result, ServiceType.PUBLISH, null, EnvironmentType.DEV);
        this.getConfigurationFiles(result, ServiceType.PUBLISH, null, EnvironmentType.STAGE);
        this.getConfigurationFiles(result, ServiceType.PUBLISH, null, EnvironmentType.PROD);
        this.getConfigurationFiles(result, ServiceType.PUBLISH, SDKType.RDE, null);
        return result;
    }

    public TaskResult verify(final List<ConfigurationFile> configs,
        final List<RepoinitFile> repoinit,
        final List<ArtifactsFile> bundles,
        final List<ArtifactsFile> contentPackages) {
        final TaskResult result = new TaskResult();
        for(final ConfigurationFile f : configs) {
            try {
                if ( f.getType() != ConfigurationFileType.JSON ) {
                    result.getWarnings().add(new Annotation(this.getRelativePath(f.getSource()), "Configurations should use the JSON format"));
                }
                final Configuration c = new Configuration(f.getPid());
                if ( RepoinitFile.REPOINIT_FACTORY_PID.equals(c.getFactoryPid()) && (!c.isFactoryConfiguration() && RepoinitFile.REPOINIT_PID.equals(c.getPid()))) {
                    result.getErrors().add(new Annotation(this.getRelativePath(f.getSource()), "Repoinit must be put inside separate txt files"));
                }
                f.readConfiguration();
            } catch ( final IOException ioe) {
                result.getErrors().add(new Annotation(this.getRelativePath(f.getSource()), ioe.getMessage()));
            }
        }
        for(final RepoinitFile f : repoinit) {
            try {
                f.readContents();
            } catch ( final IOException ioe) {
                result.getErrors().add(new Annotation(this.getRelativePath(f.getSource()), ioe.getMessage()));
            }
        }
        for(final ArtifactsFile f : bundles) {
            try {
                f.readArtifacts();
            } catch ( final IOException ioe) {
                result.getErrors().add(new Annotation(this.getRelativePath(f.getSource()), ioe.getMessage()));
            }
        }
        for(final ArtifactsFile f : contentPackages) {
            try {
                f.readArtifacts();
            } catch ( final IOException ioe) {
                result.getErrors().add(new Annotation(this.getRelativePath(f.getSource()), ioe.getMessage()));
            }
        }
        return result;
    }

    public List<RepoinitFile> getRepoInitFiles() {
        final List<RepoinitFile> result = new ArrayList<>();
        this.getRepoInitFile(result, null);
        this.getRepoInitFile(result, ServiceType.AUTHOR);
        this.getRepoInitFile(result, ServiceType.PUBLISH);
        return result;
    }

    private void getRepoInitFile(final List<RepoinitFile> result, final ServiceType serviceType) {
        final String filename = serviceType == null ? "repoinit.txt" : (serviceType == ServiceType.AUTHOR ? "repoinit.author.txt" : "repoinit.publish.txt");
        final File file = this.getSourceFile(filename);
        if ( file.exists() ) {
            final RepoinitFile r = new RepoinitFile(file);
            r.setServiceType(serviceType);
            result.add(r);
        }
    }
}
