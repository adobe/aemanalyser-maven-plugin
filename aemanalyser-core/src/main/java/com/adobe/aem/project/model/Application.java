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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Dictionary;

import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Bundles;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;

import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.SDKType;
import com.adobe.aem.project.ServiceType;

public class Application implements Serializable {

    public static final String CFG_SOURCE = Configuration.CONFIGURATOR_PREFIX.concat("source");

    public static final String REPOINIT_FACTORY_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";

    public static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.impl.RepositoryInitializer";

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

    public Bundles getBundles(final ServiceType type) throws IOException {
        final String filename = type == null ? "bundles.json" : (type == ServiceType.AUTHOR ? "bundles.author.json" : "bundles.publish.json");
        final File bundlesFile = getSourceFile(filename);
        if ( bundlesFile.exists()) {
            final String contents = Files.readString(bundlesFile.toPath());
            final String featureJson = "{ \"id\":\"g:a:1\",\"bundles\": ".concat(contents).concat("}");
            final Feature f = FeatureJSONReader.read(new StringReader(featureJson), bundlesFile.getAbsolutePath());
            return f.getBundles().isEmpty() ? null : f.getBundles();
        }
        return null;
    }

    public Artifacts getContentPackages(final ServiceType type) throws IOException {
        final String filename = type == null ? "content-packages.json" : (type == ServiceType.AUTHOR ? "content-packages.author.json" : "content-packages.publish.json");
        final File packagesFile = getSourceFile(filename);
        if ( packagesFile.exists()) {
            final String contents = Files.readString(packagesFile.toPath());
            final String featureJson = "{ \"id\":\"g:a:1\",\"content-packages:ARTIFACTS|true\": ".concat(contents).concat("}");
            final Feature f = FeatureJSONReader.read(new StringReader(featureJson), packagesFile.getAbsolutePath());
            final Extension ext = f.getExtensions().getByName("content-packages");
            return ext.getArtifacts().isEmpty() ? null : ext.getArtifacts();
        }
        return null;
    }

    public Configurations getConfigurations(final ServiceType type, final SDKType sdkType, final EnvironmentType envType) throws IOException {
        if ( sdkType != null && envType != null ) {
            throw new IOException("Only pass in either SDK or env, but not both");
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
        final File configsDir = getSourceFile(filename);
        if ( configsDir.exists()) {
            if ( configsDir.isFile() ) {
                throw new IOException("Must be a directory " + configsDir);
            }
            final Configurations cfgs = new Configurations();
            for(final File f : configsDir.listFiles()) {
                if ( !f.getName().startsWith(".") && f.getName().endsWith(".json") ) {
                    final int cut = f.getName().endsWith(".cfg.json") ? 9 : 5;
                    final String pid = f.getName().substring(0, f.getName().length() - cut).replace('-', '~');
                    final Configuration c = new Configuration(pid);
                    if ( REPOINIT_FACTORY_PID.equals(c.getFactoryPid()) && (!c.isFactoryConfiguration() && REPOINIT_PID.equals(c.getPid()))) {
                        throw new IOException("Repoinit must be put inside separate txt files " + f.getAbsolutePath());
                    }
                    try ( final Reader r = new FileReader(f)) {
                        final Dictionary<String, Object> props = org.apache.felix.cm.json.Configurations.buildReader()
                            .withIdentifier(f.getAbsolutePath())
                            .build(r)
                            .readConfiguration();
                        for(final String propName : Collections.list(props.keys())) {
                            c.getProperties().put(propName, props.get(propName));
                        }
                        c.getProperties().put(CFG_SOURCE, f.getAbsolutePath());
                    }
                    cfgs.add(c);
                }
            }
            return cfgs.isEmpty() ? null : cfgs;
        }
        return null;
    }

    public String getRepoInit(final ServiceType serviceType) throws IOException {
        final String filename = serviceType == null ? "repoinit.txt" : (serviceType == ServiceType.AUTHOR ? "repoinit.author.txt" : "repoinit.publish.txt");
        final File file = this.getSourceFile(filename);
        if ( file.exists() ) {
            return Files.readString(file.toPath());
        }
        return null;
    }
}
