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
import java.util.ArrayList;
import java.util.List;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;

import com.adobe.aem.project.ServiceType;

public class Project implements FeatureParticipantResolver, Serializable {

    private final List<Module> modules = new ArrayList<>();

    private final File rootDirectory;

    private Application application;

    public Project(final File rootDir) {
        this.rootDirectory = rootDir;
    }
  
    /**
     * 
     */
    public void scan() {
        for(final File f : this.getRootDirectory().listFiles()) {
            if ( f.isDirectory() ) {
                if ( "application".equals(f.getName()) ) {
                    this.setApplication(new Application(f));
                } else if ( f.getName().startsWith("bundle_") ) {
                    final Module m = new Module(f);
                    m.setType(ModuleType.BUNDLE);
                    this.getModules().add(m);
                } else if ( f.getName().startsWith("content_") ) {
                    final Module m = new Module(f);
                    m.setType(ModuleType.CONTENT);
                    this.getModules().add(m);
                }
            }
        }
    } 

    public File getRootDirectory() {
        return this.rootDirectory;
    }

    public List<Module> getModules() {
        return this.modules;
    }

    public Application getApplication() {
      return application;
    }

    public void setApplication(final Application application) {
        this.application = application;
    }

    @Override
    public ConfigurationFile getSource(final Configuration cfg, final ServiceType serviceType, final ArtifactId origin) {
        for(final Module m : this.getModules()) {
            if ( m.getId().changeClassifier(null).isSame(origin) ) {
                // TODO What should we return?
            }
        }
        if ( this.getApplication() != null && this.getApplication().getId().equals(origin) ) {
            for(final ConfigurationFile current : this.getApplication().getConfigurationFiles()) {
                if ( current.getPid().equals(cfg.getPid()) && serviceType == current.getServiceType() ) {
                    return current;
                }
            }
            if ( serviceType != null ) {
                return getSource(cfg, null, origin);
            }
        }
        return null;
    }

    @Override
    public ArtifactsFile getSource(final ArtifactId artifactId, final ServiceType serviceType, final ArtifactId origin) {
        for(final Module m : this.getModules()) {
            if ( m.getId().changeClassifier(null).isSame(origin) ) {
                // TODO What should we return?
            }
        }
        if ( this.getApplication() != null && this.getApplication().getId().equals(origin) ) {
            for(final ArtifactsFile current : this.getApplication().getBundleFiles()) {
                if ( current.getServiceType() == serviceType ) {
                    final ArtifactsFile file = this.getSource(artifactId, serviceType, current);
                    if ( file != null ) {
                        return file;
                    }
                }
            }
            for(final ArtifactsFile current : this.getApplication().getContentPackageFiles()) {
                final ArtifactsFile file = this.getSource(artifactId, serviceType, current);
                if ( current.getServiceType() == serviceType ) {
                    if ( file != null ) {
                        return file;
                    }
                }
            }
            if ( serviceType != null ) {
                return getSource(artifactId, null, origin);
            }
        }
        return null;
    }

    private ArtifactsFile getSource(final ArtifactId artifactId, final ServiceType serviceType, final ArtifactsFile artifacts) {
        try {
            for(final Artifact a : artifacts.readArtifacts()) {
                if ( a.getId().isSame(artifactId) ) {
                    return artifacts;
                }
            }
        } catch ( final IOException ignore ) {
            // ignore
        }
        return null;
    }
}
