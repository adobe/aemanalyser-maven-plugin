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
import java.util.Dictionary;
import java.util.List;

import org.apache.felix.cm.json.Configurations;

import com.adobe.aem.analyser.tasks.ConfigurationFile;
import com.adobe.aem.analyser.tasks.ConfigurationFileType;
import com.adobe.aem.analyser.tasks.ConfigurationsTask;
import com.adobe.aem.analyser.tasks.ConfigurationsTaskConfig;
import com.adobe.aem.analyser.tasks.TaskContext;
import com.adobe.aem.analyser.tasks.TaskResult;

public class ConvertConfigsCommand extends AbstractCommand {

    private File repositoryRootDirectory;

    private boolean enforceConfigurationBelowConfigFolder;

    @Override
    public void validate() {
        this.enforceConfigurationBelowConfigFolder = this.parser.getBooleanArgument("enforceConfigurationBelowConfigFolder", true);
        this.repositoryRootDirectory = new File(this.parser.arguments.getOrDefault("repositoryRootDirectory", "src/main/content/jcr_root"));
        if ( !this.repositoryRootDirectory.exists() || !this.repositoryRootDirectory.isDirectory() ) {
            throw new IllegalArgumentException("Directory does not exist " + this.repositoryRootDirectory);
        }
    }

    @Override
    public TaskResult doExecute() throws IOException {
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
            if ( file.getType() != ConfigurationFileType.JSON ) {
                final Dictionary<String, Object> properties = file.readConfiguration();
                if ( properties == null ) {
                    throw new IOException("Unable to read configuration " + file.getSource());
                }
                File directory = file.getSource().getParentFile();
                if ( this.enforceConfigurationBelowConfigFolder && file.getLevel() > 1 ) {
                    for(int i = 2; i <= file.getLevel(); i++) {
                        directory = directory.getParentFile();
                    }
                }
                final File outFile = new File(directory, file.getFileName());
                logger.info("Writing configuration " + outFile.getAbsolutePath());
                if ( !this.isDryRun() ) {
                    try ( final Writer writer = new FileWriter(file.getSource())) {
                        Configurations.buildWriter().build(writer).writeConfiguration(properties);
                    }
                }
                logger.info("Deleting old configuration " + file.getSource());
                if ( !this.isDryRun() ) {
                    file.getSource().delete();
                }
            }
        }

        return null;
    }
}
