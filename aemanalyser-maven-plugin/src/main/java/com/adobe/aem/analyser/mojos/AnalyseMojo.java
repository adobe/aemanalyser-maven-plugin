/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.adobe.aem.analyser.AemAnalyser;
import com.adobe.aem.analyser.AemAnalyserResult;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.apache.sling.feature.maven.ProjectHelper;
import org.apache.sling.feature.maven.mojos.AbstractIncludingFeatureMojo;
import org.apache.sling.feature.maven.mojos.FeatureSelectionConfig;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.TEST)
public class AnalyseMojo extends AbstractIncludingFeatureMojo {
    boolean unitTestMode = false;

    @Parameter(defaultValue = AemAnalyser.DEFAULT_TASKS,
        property = "includeTasks")
    List<String> includeTasks;

    @Parameter
    Map<String, Properties> taskConfiguration;

    @Parameter(defaultValue = MojoUtils.DEFAULT_SKIP_ENV_VAR, property = MojoUtils.PROPERTY_SKIP_VAR)
    String skipEnvVarName;

    @Parameter(defaultValue = "true", property = "failon.analyser.errors")
    private boolean failOnAnalyserErrors;

    @Component
    ArtifactHandlerManager artifactHandlerManager;

    @Component
    ArtifactResolver artifactResolver;

    ArtifactProvider getArtifactProvider(final ArtifactProvider localProvider) {
        return new ArtifactProvider() {

            @Override
            public URL provide(final ArtifactId id) {
                URL url = localProvider != null ? localProvider.provide(id) : null;
                if (url != null) {
                    return url;
                }
                try {
                    return ProjectHelper.getOrResolveArtifact(project, mavenSession, artifactHandlerManager, artifactResolver, id).getFile().toURI().toURL();
                } catch (final MalformedURLException e) {
                    getLog().debug("Malformed url " + e.getMessage(), e);
                    // ignore
                    return null;
                }
            }
        };
    }

    Map<String, Map<String, String>> getTaskConfigurations() {
        Map<String, Map<String, String>> config = new HashMap<>();
        if (this.taskConfiguration != null) {
            for(final Map.Entry<String, Properties> entry : this.taskConfiguration.entrySet()) {
                final Map<String, String> m = new HashMap<>();

                entry.getValue().stringPropertyNames().forEach(n -> m.put(n, entry.getValue().getProperty(n)));
                config.put(entry.getKey(), m);
            }
        }

        return config;
    }

    Set<String> getIncludedTasks() {
        return new LinkedHashSet<>(this.includeTasks);
    }

    FeatureSelectionConfig getFeatureSelection() {
        FeatureSelectionConfig s = new FeatureSelectionConfig();
        @SuppressWarnings("unchecked")
        Set<String> aggregates =
                (Set<String>) project.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates");
        aggregates.forEach(s::setIncludeClassifier);
 
       return s;
    }
    
    ArtifactProvider getLocalArtifactProvider() throws IOException {
        ArtifactManagerConfig amcfg = new ArtifactManagerConfig();
        amcfg.setRepositoryUrls(new String[] { MojoUtils.getConversionOutputDir(project).toURI().toURL().toString() });

        return ArtifactManager.getArtifactManager(amcfg);
    }
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (MojoUtils.skipRun(skipEnvVarName)) {
            getLog().info("Skipping AEM analyser plugin as variable " + skipEnvVarName + " is set.");
            return;
        }

        if ( unitTestMode ) {
            return;
        }

        checkPreconditions();
 
 
        boolean hasErrors = false;
        try {
            final AemAnalyser analyser = new AemAnalyser();
            analyser.setArtifactProvider(getArtifactProvider(getLocalArtifactProvider()));
            analyser.setIncludedTasks(this.getIncludedTasks());
            analyser.setTaskConfigurations(this.getTaskConfigurations());

            getLog().debug("Retrieving Feature files...");
            final Collection<Feature> features = this.getSelectedFeatures(getFeatureSelection()).values();

            final AemAnalyserResult result = analyser.analyse(features);
            
            for(final String msg : result.getWarnings()) {
                getLog().warn(msg);
            }
            for(final String msg : result.getErrors()) {
                getLog().error(msg);
            }
            hasErrors = result.hasErrors();

        } catch ( final Exception e) {
            throw new MojoExecutionException("A fatal error occurred while analysing the features, see error cause:",
                    e);
        }

        if (hasErrors) {
            if ( failOnAnalyserErrors ) {
                throw new MojoFailureException(
                    "One or more feature analyser(s) detected feature error(s), please read the plugin log for more details");
            }
            getLog().warn("Errors found during analyser run, but this plugin is configured to ignore errors and continue the build!");
        }
    }
}
