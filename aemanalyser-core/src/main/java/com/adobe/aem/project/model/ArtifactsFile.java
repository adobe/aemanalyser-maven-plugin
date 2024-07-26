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

import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;

import com.adobe.aem.project.ServiceType;

/**
 * A artifacts file
 */
public final class ArtifactsFile implements Serializable {

    public enum FileType {
        BUNDLES,
        CONTENT_PACKAGES
    }

    private ServiceType serviceType;
    private transient Artifacts artifacts;
    private final File source;
    private final FileType type;

    public ArtifactsFile(final FileType type, final File source) {
        this.source = source;
        this.type = type;
    }

    public FileType getFileType() {
        return this.type;
    }

    /**
     * @return the source
     */
    public File getSource() {
        return source;
    }

    public void resetArtifacts() {
        this.artifacts = null;
    }

    public Artifacts getArtifacts() {
        return this.artifacts;
    }

    private String getFeatureJson(final String contents) {
        if ( this.type == FileType.CONTENT_PACKAGES ) {
            return "{ \"id\":\"g:a:1\",\"content-packages:ARTIFACTS|true\": ".concat(contents).concat("}");
        }
        return "{ \"id\":\"g:a:1\",\"bundles\": ".concat(contents).concat("}");
    }

    public Artifacts readArtifacts() throws IOException {
        if ( this.artifacts == null ) {
            final String contents = Files.readString(this.source.toPath());
            final String featureJson = this.getFeatureJson(contents);
            final Feature f = FeatureJSONReader.read(new StringReader(featureJson), source.getAbsolutePath());
            if ( this.type == FileType.CONTENT_PACKAGES ) {
                final Extension ext = f.getExtensions().getByName("content-packages");
                this.artifacts = ext.getArtifacts();    
            } else {
                this.artifacts = f.getBundles();
            }
        }
        return this.artifacts;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(final ServiceType serviceType) {
        this.serviceType = serviceType;
    }
}