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
package com.adobe.aem.analyser.mojos;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.sling.feature.ArtifactId;

import com.adobe.aem.analyser.AemAnalyserResult;
import com.adobe.aem.analyser.tasks.ConfigurationFile;
import com.adobe.aem.analyser.tasks.ConfigurationsTask;
import com.adobe.aem.analyser.tasks.ConfigurationsTaskConfig;
import com.adobe.aem.analyser.tasks.TaskContext;
import com.adobe.aem.analyser.tasks.TaskResult;

@Mojo(name = "analyse-configs", 
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class AnalyseConfigsMojo extends AbstractAnalyseMojo {

    /**
     * Configuration of the repository root directory
     */
    @Parameter(defaultValue = "src/main/content/jcr_root")
    private File repositoryRootDirectory;

    @Parameter(defaultValue = "false")
    private boolean enforceConfigurationBelowConfigFolder;

    @Override
    protected AemAnalyserResult doExecute(final ArtifactId sdkId, final List<ArtifactId> addons) throws MojoExecutionException, MojoFailureException {
        try {
            final TaskContext context = new TaskContext(
                this.project.getBasedir(),
                new ArtifactId(this.project.getGroupId(), this.project.getArtifactId(), this.project.getVersion(), null, this.project.getPackaging()),
                sdkId,
                addons,
                id -> { return this.getOrResolveFeature(id);}
            );
            final ConfigurationsTaskConfig config = new ConfigurationsTaskConfig();
            config.setEnforceRepositoryConfigurationBelowConfigFolder(this.enforceConfigurationBelowConfigFolder);

            final ConfigurationsTask task = new ConfigurationsTask(context, config);
            final List<ConfigurationFile> files = task.scanRepositoryDirectory(this.repositoryRootDirectory);
            final TaskResult result = task.analyseConfigurations(files);
    
            final AemAnalyserResult mojoResult = new AemAnalyserResult();
            result.getErrors().stream().forEach(a -> mojoResult.getErrors().add(a.toString()));
            result.getWarnings().stream().forEach(a -> mojoResult.getWarnings().add(a.toString()));
            return mojoResult;
        } catch ( final IOException ioe ) {
            throw new MojoExecutionException(ioe.getMessage(), ioe);
        }
    }
}
