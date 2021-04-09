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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.Analyser;
import org.apache.sling.feature.analyser.AnalyserResult;
import org.apache.sling.feature.analyser.AnalyserResult.ArtifactReport;
import org.apache.sling.feature.analyser.AnalyserResult.ExtensionReport;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.apache.sling.feature.maven.ProjectHelper;
import org.apache.sling.feature.maven.mojos.AbstractIncludingFeatureMojo;
import org.apache.sling.feature.maven.mojos.FeatureSelectionConfig;
import org.apache.sling.feature.scanner.Scanner;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.TEST)
public class AnalyseMojo extends AbstractIncludingFeatureMojo {
    boolean unitTestMode = false;

    @Parameter(defaultValue =
        "requirements-capabilities,"
        + "bundle-content,"
        + "bundle-resources,"
        + "bundle-nativecode,"
        + "api-regions,"
        + "api-regions-check-order,"
        + "api-regions-crossfeature-dups,"
        + "api-regions-exportsimports,"
//        + "repoinit," disable until SLING-10215 is fixed
        + "configuration-api",
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

    protected class BaseFeatureProvider implements FeatureProvider {
        @Override
        public Feature provide(ArtifactId id) {
            // Check for the feature in the local context
            for (final Feature feat : ProjectHelper.getAssembledFeatures(project).values()) {
                if (feat.getId().equals(id)) {
                    return feat;
                }
            }

            if (ProjectHelper.isLocalProjectArtifact(project, id)) {
                throw new RuntimeException("Unable to resolve local artifact " + id.toMvnId());
            }

            // Finally, look the feature up via Maven's dependency mechanism
            return ProjectHelper.getOrResolveFeature(project, mavenSession, artifactHandlerManager,
                artifactResolver, id);
        }
    }

    protected FeatureProvider getFeatureProvider() {
        return new BaseFeatureProvider();
    }

    private static final String FILE_STORAGE_CONFIG_KEY = "fileStorage";

    private static final String ANALYSER_CONFIG_WILDCARD = "all";

    void addTaskConfigurationDefaults(Map<String, Map<String, String>> taskConfiguration) {
        String featureModelFileStorage = project.getBuild().getDirectory() + "/sling-slingfeature-maven-plugin-fmtmp";
        Map<String, String> wildCardCfg = taskConfiguration.get(ANALYSER_CONFIG_WILDCARD);
        if (wildCardCfg == null) {
            wildCardCfg = new HashMap<String, String>();
            taskConfiguration.put(ANALYSER_CONFIG_WILDCARD, wildCardCfg);
        }
        if (!wildCardCfg.containsKey(FILE_STORAGE_CONFIG_KEY)) {
            new File(featureModelFileStorage).mkdirs();
            wildCardCfg.put(FILE_STORAGE_CONFIG_KEY, featureModelFileStorage);
        }
    }
    
    Map<String, Map<String, String>> getTaskConfiguration() {
        Map<String, Map<String, String>> config = new HashMap<>();
        if (this.taskConfiguration != null) {
            for(final Map.Entry<String, Properties> entry : this.taskConfiguration.entrySet()) {
                final Map<String, String> m = new HashMap<>();

                entry.getValue().stringPropertyNames().forEach(n -> m.put(n, entry.getValue().getProperty(n)));
                config.put(entry.getKey(), m);
            }
        }

        // Set default task configuration
        if (!config.containsKey("api-regions-crossfeature-dups")) {
            final Map<String, String> cfd = new HashMap<>();
            cfd.put("regions", "global,com.adobe.aem.deprecated");
            cfd.put("definingFeatures", "com.adobe.aem:aem-sdk-api:slingosgifeature:*");
            cfd.put("warningPackages", "*");
            config.put("api-regions-crossfeature-dups", cfd);
        }

        if (!config.containsKey("api-regions-check-order")) {
            final Map<String, String> ord = new HashMap<>();
            ord.put("order", "global,com.adobe.aem.deprecated,com.adobe.aem.internal");
            config.put("api-regions-check-order", ord);
        }

        addTaskConfigurationDefaults(config);
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
 
        getLog().debug(MessageUtils.buffer().a("Setting up the ").strong("Scanner").a("...").toString());
        Scanner scanner;
        try {
            scanner = new Scanner(getArtifactProvider(getLocalArtifactProvider()));
        } catch (final IOException e) {
            throw new MojoExecutionException("A fatal error occurred while setting up the Scanner, see error cause:",
                    e);
        }
        getLog().debug(MessageUtils.buffer().strong("Scanner").a(" successfully set up").toString());

        FeatureProvider featureProvider = getFeatureProvider();

        boolean hasErrors = false;
        try {
            final Map<String, Map<String, String>> taskConfiguration = this.getTaskConfiguration();
            final Set<String> includedTasks = this.getIncludedTasks();

            getLog().debug(MessageUtils.buffer().a("Setting up the ").strong("analyser")
                    .a(" with following configuration:").toString());
            getLog().debug(" * Task Configuration = " + taskConfiguration);
            getLog().debug(" * Include Tasks = " + includedTasks);
            final Analyser analyser = new Analyser(scanner, taskConfiguration, includedTasks, null);
            getLog().debug(MessageUtils.buffer().strong("Analyser").a(" successfully set up").toString());

            getLog().debug("Retrieving Feature files...");
            final Collection<Feature> features = this.getSelectedFeatures(getFeatureSelection()).values();

            final Map<ArtifactId, AnalyserResult> results = new LinkedHashMap<>();
            for (final Feature f : features) {
                try {
                    getLog().debug(MessageUtils.buffer().a("Analyzing feature ").strong(f.getId().toMvnId())
                            .a(" ...").toString());
                    final AnalyserResult result = analyser.analyse(f, null, featureProvider);
                    results.put(f.getId(), result);

                    hasErrors |= !result.getErrors().isEmpty();
                } catch (Exception t) {
                    throw new MojoFailureException(
                            "Exception during analysing feature " + f.getId().toMvnId() + " : " + t.getMessage(),
                            t);
                }
            }

            logOutput(results);

        } catch (IOException e) {
            throw new MojoExecutionException(
                    "A fatal error occurred while setting up the analyzer, see error cause:", e);
        }
        if (hasErrors) {
            if ( failOnAnalyserErrors ) {
                throw new MojoFailureException(
                    "One or more feature analyser(s) detected feature error(s), please read the plugin log for more details");
            }
            getLog().warn("Errors found during analyser run, but this plugin is configured to ignore errors and continue the build!");
        }
    }

    private static final ArtifactId COMMON_ID = ArtifactId.parse("__:__:1");

    private Map<ArtifactId, List<String>> compactErrors(final Map<ArtifactId, AnalyserResult> results) {
        final Map<ArtifactId, List<String>> errors = new LinkedHashMap<>();

        List<String> commonMessages = null;
        for(final Map.Entry<ArtifactId, AnalyserResult> entry : results.entrySet()) {
            final List<String> msgs = new ArrayList<>(entry.getValue().getErrors());
            if ( commonMessages == null ) {
                commonMessages = msgs;
                errors.put(COMMON_ID, commonMessages);
            } else {
                commonMessages.retainAll(msgs);
                errors.put(entry.getKey(), msgs);
            }
        }
        for(final List<String> msgs : errors.values()) {
            if ( msgs != commonMessages) {
                msgs.removeAll(commonMessages);
            }
        }

        return errors;
    }

    private void logOutput(final Map<ArtifactId, AnalyserResult> results) {
        final Map<ArtifactId, List<String>> errors = compactErrors(results);
        
        for(final Map.Entry<ArtifactId, List<String>> entry : errors.entrySet()) {
            if ( !entry.getValue().isEmpty()) {
                if ( entry.getKey() == COMMON_ID ) {
                    getLog().error("Analyser detected global errors.");
                    for(final String msg : entry.getValue() ) {
                        getLog().error(msg);
                    }
                } else {
                    getLog().error("Analyser detected errors on feature '" + entry.getKey().toMvnId() + "'.");
                    for(final String msg : entry.getValue() ) {
                        getLog().error(msg);
                    }
                }
            }
        }
    }
}
