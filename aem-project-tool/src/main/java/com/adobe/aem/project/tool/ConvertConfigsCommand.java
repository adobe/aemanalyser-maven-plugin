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
package com.adobe.aem.project.tool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Objects;

import org.apache.felix.cm.json.Configurations;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;

import com.adobe.aem.analyser.result.AemAnalyserResult;
import com.adobe.aem.analyser.tasks.ConfigurationsTask;
import com.adobe.aem.analyser.tasks.ConfigurationsTaskConfig;
import com.adobe.aem.analyser.tasks.TaskContext;
import com.adobe.aem.project.RunModes;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.ConfigurationFile;
import com.adobe.aem.project.model.ConfigurationFileType;
import com.adobe.aem.project.model.ConfigurationFile.Location;

public class ConvertConfigsCommand extends AbstractCommand {

    private File repositoryRootDirectory;

    private boolean enforceConfigurationBelowConfigFolder;

    private boolean removeDefaultValues;

    @Override
    public void validate() {
        this.enforceConfigurationBelowConfigFolder = this.parser.getBooleanArgument("enforceConfigurationBelowConfigFolder", true);
        this.removeDefaultValues = this.parser.getBooleanArgument("removeDefaultValues", true);
        this.repositoryRootDirectory = new File(this.parser.arguments.getOrDefault("repositoryRootDirectory", "src/main/content/jcr_root"));
        if ( !this.repositoryRootDirectory.exists() || !this.repositoryRootDirectory.isDirectory() ) {
            throw new IllegalArgumentException("Directory does not exist " + this.repositoryRootDirectory);
        }
    }

    @Override
    public AemAnalyserResult doExecute() throws IOException {
        final TaskContext context = new TaskContext(
            this.getBaseDirectory(),
            this.getProjectId(),
            this.getSdkId(),
            this.getAddons(),
            this.getFeatureProvider()
        );

        final ConfigurationsTask task = new ConfigurationsTask(context, new ConfigurationsTaskConfig());
        final List<ConfigurationFile> files = task.scanRepositoryDirectory(this.repositoryRootDirectory);
        if ( !files.isEmpty() && this.isDryRun() ) {
            logger.info("Dry run enabled, no changes are performed!");
        }
        for(final ConfigurationFile file : files) {
            final Dictionary<String, Object> properties = file.readConfiguration();
            if ( properties != null ) {
                convertConfiguration(context, file, properties);
            }
        }

        return null;
    }

    private void convertConfiguration(final TaskContext context, final ConfigurationFile file, final Dictionary<String, Object> properties) throws IOException {
        final boolean isAuthor = RunModes.matchesRunModeIncludingSDK(ServiceType.AUTHOR, file.getRunMode());
        final boolean isPublish = RunModes.matchesRunModeIncludingSDK(ServiceType.PUBLISH, file.getRunMode());
        if ( !isAuthor && !isPublish ) {
            // unknown run mode
            return;
        }
        final boolean move = this.enforceConfigurationBelowConfigFolder && file.getLevel() > 1 ;
        File directory = file.getSource().getParentFile();
        if ( move ) {
            for(int i = 2; i <= file.getLevel(); i++) {
                directory = directory.getParentFile();
            }
        }
        if ( file.getLocation() == Location.LIBS ) {
            final String path = directory.getAbsolutePath();
            final int lastPos = path.lastIndexOf(File.separator.concat("libs").concat(File.separator));
            directory = new File(path.substring(0, lastPos + 1).concat("apps").concat(path.substring(lastPos + 5)));
        }
        final boolean convert = file.getType() != ConfigurationFileType.JSON;
        boolean removedProperties = false;
        if ( this.removeDefaultValues ) {
            removedProperties = removeDefaults(isAuthor ? context.getProductFeatures().get(ServiceType.AUTHOR) : null,
                isPublish ? context.getProductFeatures().get(ServiceType.PUBLISH) : null,
                file.getPid(), properties);
        }
        boolean remove = properties.isEmpty();
        final File outFile = new File(directory, file.getPid().concat(".cfg.json"));
        if ( remove ) {
            logger.info("Removing empty configuration from {}", context.getRelativePath(file.getSource()));
        } else if ( convert ) {
            logger.info("Converting configuration from {} to {}", context.getRelativePath(file.getSource()), context.getRelativePath(outFile));
        } else if ( move ) {
            logger.info("Moving configuration from {} to {}", context.getRelativePath(file.getSource()), context.getRelativePath(outFile));
        }
        if ( !remove && removedProperties ) {
            logger.info("Removing default configurations from {}", context.getRelativePath(outFile));
        }
        if ( !this.isDryRun() && (convert || move || removedProperties || remove )) {
            if ( !remove ) {
                directory.mkdirs();
                try ( final Writer writer = new FileWriter(outFile)) {
                    Configurations.buildWriter().build(writer).writeConfiguration(properties);
                }    
            }
            if ( convert || move || remove ) {
                logger.debug("Deleting old configuration {}", file.getSource());
                file.getSource().delete();    
            }
        }
    }

    private boolean removeDefaults(final Feature feature1, final Feature feature2, final String pid, final Dictionary<String, Object> properties) {
        final Configuration cfg1 = feature1 == null ? null : feature1.getConfigurations().getConfiguration(pid);
        final Configuration cfg2 = feature2 == null ? null : feature2.getConfigurations().getConfiguration(pid);
        // no product config found
        if ( cfg1 == null && cfg2 == null ) {
            return false;
        }
        // both features, but only one configuratiuon
        if ( cfg1 == null || cfg2 == null && feature1 != null && feature2 != null) {
            return false;
        }
        boolean changed = false;
        if ( cfg1 != null ) {
            for(final String name : Collections.list(cfg1.getConfigurationProperties().keys())) {
                final Object val = cfg1.getProperties().get(name);
                if ( cfg2 == null || Objects.deepEquals(val, cfg2.getProperties().get(name)) ) {
                    if ( Objects.deepEquals(properties.get(name), val) ) {
                        if ( properties.remove(name) != null ) {
                            changed = true;
                        }
                    }
                }
            }    
        }
        if ( cfg2 != null ) {
            for(final String name : Collections.list(cfg2.getConfigurationProperties().keys())) {
                final Object val = cfg2.getProperties().get(name);
                if ( cfg1 == null || Objects.deepEquals(val, cfg1.getProperties().get(name)) ) {
                    if ( Objects.deepEquals(properties.get(name), val) ) {
                        if ( properties.remove(name) != null ) {
                            changed = true;
                        }
                    }
                }
            }    
        }
        return changed;
    }
}
