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
import java.util.ArrayList;
import java.util.List;

import javax.json.stream.JsonParsingException;

import org.apache.sling.feature.Configuration;

import com.adobe.aem.analyser.result.AemAnalyserAnnotation;
import com.adobe.aem.analyser.result.AemAnalyserResult;
import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.SDKType;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.ArtifactsFile.FileType;
import com.adobe.aem.project.model.ConfigurationFile.Location;

public class Application extends AbstractModule {

    private List<ArtifactsFile> bundlesFiles;

    private List<ArtifactsFile> contentFiles;

    private List<ConfigurationFile> configFiles;

    public Application(final File f) {
        super(f);
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
        return file.getAbsolutePath().substring(this.getDirectory().getAbsolutePath().length() + 1);
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
        if ( this.bundlesFiles == null ) {
            this.bundlesFiles = new ArrayList<>();
            getArtifactsFile(this.bundlesFiles, ArtifactsFile.FileType.BUNDLES, null);
            getArtifactsFile(this.bundlesFiles, ArtifactsFile.FileType.BUNDLES, ServiceType.AUTHOR);
            getArtifactsFile(this.bundlesFiles, ArtifactsFile.FileType.BUNDLES, ServiceType.PUBLISH);    
        }
        return this.bundlesFiles;
    }

    public List<ArtifactsFile> getContentPackageFiles() {
        if ( this.contentFiles == null ) {
            this.contentFiles = new ArrayList<>();
            getArtifactsFile(this.contentFiles, ArtifactsFile.FileType.CONTENT_PACKAGES, null);
            getArtifactsFile(this.contentFiles, ArtifactsFile.FileType.CONTENT_PACKAGES, ServiceType.AUTHOR);
            getArtifactsFile(this.contentFiles, ArtifactsFile.FileType.CONTENT_PACKAGES, ServiceType.PUBLISH);
        }
        return this.contentFiles;
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
        if ( this.configFiles == null ) {
            this.configFiles = new ArrayList<>();
            this.getConfigurationFiles(this.configFiles, null, null, null);
            this.getConfigurationFiles(this.configFiles, null, SDKType.RDE, null);
            this.getConfigurationFiles(this.configFiles, null, null, EnvironmentType.DEV);
            this.getConfigurationFiles(this.configFiles, null, null, EnvironmentType.STAGE);
            this.getConfigurationFiles(this.configFiles, null, null, EnvironmentType.PROD);

            this.getConfigurationFiles(this.configFiles, ServiceType.AUTHOR, null, null);
            this.getConfigurationFiles(this.configFiles, ServiceType.AUTHOR, null, EnvironmentType.DEV);
            this.getConfigurationFiles(this.configFiles, ServiceType.AUTHOR, null, EnvironmentType.STAGE);
            this.getConfigurationFiles(this.configFiles, ServiceType.AUTHOR, null, EnvironmentType.PROD);
            this.getConfigurationFiles(this.configFiles, ServiceType.AUTHOR, SDKType.RDE, null);

            this.getConfigurationFiles(this.configFiles, ServiceType.PUBLISH, null, null);
            this.getConfigurationFiles(this.configFiles, ServiceType.PUBLISH, null, EnvironmentType.DEV);
            this.getConfigurationFiles(this.configFiles, ServiceType.PUBLISH, null, EnvironmentType.STAGE);
            this.getConfigurationFiles(this.configFiles, ServiceType.PUBLISH, null, EnvironmentType.PROD);
            this.getConfigurationFiles(this.configFiles, ServiceType.PUBLISH, SDKType.RDE, null);
        }
        return this.configFiles;
    }

    public AemAnalyserResult verify(final List<ConfigurationFile> configs,
        final List<RepoinitFile> repoinit,
        final List<ArtifactsFile> bundles,
        final List<ArtifactsFile> contentPackages) {
        final AemAnalyserResult result = new AemAnalyserResult();
        for(final ConfigurationFile f : configs) {
            try {
                if ( f.getType() != ConfigurationFileType.JSON ) {
                    result.getWarnings().add(new AemAnalyserAnnotation(f.getSource(), "Configurations should use the JSON format"));
                }
                final Configuration c = new Configuration(f.getPid());
                if ( RepoinitFile.REPOINIT_FACTORY_PID.equals(c.getFactoryPid()) && (!c.isFactoryConfiguration() && RepoinitFile.REPOINIT_PID.equals(c.getPid()))) {
                    result.getErrors().add(new AemAnalyserAnnotation(f.getSource(), "Repoinit must be put inside separate txt files"));
                }
                f.readConfiguration();
            } catch ( final IOException ioe) {
                result.getErrors().add(getAnnotation(f.getSource(), ioe));
            }
        }
        for(final RepoinitFile f : repoinit) {
            try {
                f.readContents();
            } catch ( final IOException ioe) {
                result.getErrors().add(new AemAnalyserAnnotation(f.getSource(), ioe.getMessage()));
            }
        }
        for(final ArtifactsFile f : bundles) {
            try {
                f.readArtifacts();
            } catch ( final IOException ioe) {
                result.getErrors().add(getAnnotation(f.getSource(), ioe));
            }
        }
        for(final ArtifactsFile f : contentPackages) {
            try {
                f.readArtifacts();
            } catch ( final IOException ioe) {
                result.getErrors().add(getAnnotation(f.getSource(), ioe));
            }
        }
        return result;
    }

    private AemAnalyserAnnotation getAnnotation(final File f, final IOException ioe) {
        if ( ioe.getCause() != null && ioe.getCause() instanceof JsonParsingException ) {
            final JsonParsingException jpe = (JsonParsingException) ioe.getCause();
            return new AemAnalyserAnnotation(f, jpe.getMessage(), jpe.getLocation() != null ? jpe.getLocation().getLineNumber() : -1,
                jpe.getLocation() != null ? jpe.getLocation().getColumnNumber() : -1);
        }
        return new AemAnalyserAnnotation(f, ioe.getMessage());
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
