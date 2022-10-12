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
import java.io.IOException;
import java.util.List;

import com.adobe.aem.analyser.tasks.ConfigurationFile;
import com.adobe.aem.analyser.tasks.ConfigurationsTask;
import com.adobe.aem.analyser.tasks.ConfigurationsTaskConfig;
import com.adobe.aem.analyser.tasks.TaskContext;
import com.adobe.aem.analyser.tasks.TaskResult;

public class AnalyseConfigsCommand extends AbstractCommand {

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
        logger.info("Using SDK {}", this.getSdkId().toMvnId());
        final TaskContext context = new TaskContext(
            this.getBaseDirectory(),
            this.getProjectId(),
            this.getSdkId(),
            this.getAddons(),
            this.getFeatureProvider()
        );
        final ConfigurationsTaskConfig config = new ConfigurationsTaskConfig();
        config.setEnforceRepositoryConfigurationBelowConfigFolder(this.enforceConfigurationBelowConfigFolder);

        final ConfigurationsTask task = new ConfigurationsTask(context, config);
        final List<ConfigurationFile> files = task.scanRepositoryDirectory(this.repositoryRootDirectory);
        return task.analyseConfigurations(files);
    }
}
