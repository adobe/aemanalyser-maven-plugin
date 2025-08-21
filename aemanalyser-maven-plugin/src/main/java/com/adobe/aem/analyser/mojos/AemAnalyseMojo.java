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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;

import com.adobe.aem.analyser.AemAggregator;
import com.adobe.aem.analyser.AemAnalyser;
import com.adobe.aem.analyser.AemPackageConverter;
import com.adobe.aem.analyser.result.AemAnalyserAnnotation;
import com.adobe.aem.analyser.result.AemAnalyserResult;

public class AemAnalyseMojo extends AbstractAnalyseMojo {

    /**
     * The analyser tasks run by the analyser on the final aggregates
     */
    @Parameter(defaultValue = AemAnalyser.DEFAULT_TASKS,
        property = "analyserTasks")
    List<String> analyserTasks;

    /**
     * List of analyser tasks that should not be run on the final aggregates.
     * This allows to skip tasks that are configured by default in {@link #analyserTasks}.
     */
    @Parameter(property = "skipAnalyserTasks")
    List<String> skipAnalyserTasks;

    /**
     * The analyser tasks run by the analyser on the user aggregates
     */
    @Parameter(defaultValue = AemAnalyser.DEFAULT_USER_TASKS,
        property = "analyserUserTasks")
    List<String> analyserUserTasks;

    /**
     * List of analyser tasks that should not run on the user aggregates.
     * This allows to skip tasks that are configured by default in {@link #analyserUserTasks}.
     */
    @Parameter(property = "skipAnalyserUserTasks")
    List<String> skipAnalyserUserTasks;

    /**
     * Optional configurations for the analyser tasks
     */
    @Parameter
    Map<String, Properties> analyserTaskConfigurations;

    /**
     * Only analyze the package attached to the project with the given classifier.
     */
    @Parameter(property = "aem.analyser.classifier")
    String classifier;

    /**
     * Analyzes the given list of content package files.
     * If this is configured, only these files are validated (and potentially {@link #additionalContentPackageArtifacts}), 
     * but not the main project artifact or dependencies.
     * The files must be located inside the Maven project directory (e.g. src or target folder). 
     */
    @Parameter
    List<File> contentPackageFiles;

    /**
     * Additional content package artifacts to be considered in the analysis given as list of Maven coordinates/ids in format
     * {@code groupId:artifactId[:packaging[:classifier]]:version}).
     * This is useful for container packages which depend on other container packages deployed together in Cloud Manager
     * (e.g. composed via git submodules) as otherwise analysis may fail.
     */
    @Parameter
    private List<String> additionalContentPackageArtifacts;

    /**
     * Get the output directory for the content package converter
     * @return The directory
     */
    private File getConversionOutputDir() {
        return new File(project.getBuild().getDirectory().concat(File.separator).concat(Constants.CONVERTER_DIRECTORY));
    }

    /**
     * The directory for the generated feature models
     * @return The directory
     */
    private File getGeneratedFeaturesDir() {
        return new File(getConversionOutputDir(), Constants.FM_DIRECTORY);
    }

    /**
     * Execute the plugin
     */
    @Override
    public AemAnalyserResult doExecute(final ArtifactId sdkId, 
        final List<ArtifactId> addons) 
    throws MojoExecutionException, MojoFailureException {
        final List<String> additionalWarnings = new ArrayList<>();
        final List<String> additionalErrors = new ArrayList<>();
        
        // 1. Phase : convert content packages
        this.convertContentPackages(additionalWarnings, additionalErrors);

        try (ArtifactManager artifactManager = getArtifactManager()) {
            ArtifactProvider compositeArtifactProvider = getCompositeArtifactProvider(artifactManager);
            // 2. Phase : aggregate feature models
            final List<Feature> features = this.aggregateFeatureModels(sdkId, addons, compositeArtifactProvider);

            // 3. Phase : analyse features
            final AemAnalyserResult result = this.analyseFeatures(features, compositeArtifactProvider);
            additionalWarnings.stream().forEach(msg -> result.getWarnings().add(new AemAnalyserAnnotation(msg)));
            additionalErrors.stream().forEach(msg -> result.getErrors().add(new AemAnalyserAnnotation(msg)));
            return result;
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Convert the content packages
     * @throws MojoExecutionException If anything goes wrong
     */
    void convertContentPackages(final List<String> additionalWarnings, final List<String> additionalErrors) throws MojoExecutionException {
        final AemPackageConverter converter = new AemPackageConverter();
        converter.setConverterOutputDirectory(getConversionOutputDir());
        converter.setFeatureOutputDirectory(getGeneratedFeaturesDir());
    
        final Map<String, File> packages = new LinkedHashMap<>();
        for(final Artifact contentPackage: getContentPackages()) {
            final File source = contentPackage.getFile();
            if (source == null) {
                throw new MojoExecutionException("Content package " + contentPackage + " has no file attached");
            }
            packages.put(contentPackage.getId().toString(), source);
        }
        try {
            converter.convert(packages);
        } catch ( final ConverterException ce) {
            getLog().error(ce.getMessage());
            throw new MojoExecutionException(ce.getMessage());
        } catch (final IOException t) {
            throw new MojoExecutionException("Content Package Converter Exception " + t.getMessage(), t);
        }
    }

    /**
     * Search for relevant content packages.
     * @return The list of artifacts (non empty)
     * @throws MojoExecutionException If anything goes wrong, for example no content packages are found
     */
    List<Artifact> getContentPackages() throws MojoExecutionException {
        final List<Artifact> result = new ArrayList<>();
        if (!Constants.PACKAGING_AEM_ANALYSE.equals(project.getPackaging())) {
            if (contentPackageFiles != null && !contentPackageFiles.isEmpty()) {
                getLog().info("Using content packages from contentPackageFiles");
                validateContentPackageFiles(contentPackageFiles);
                contentPackageFiles.stream()
                        .map(this::contentPackageFileToArtifact)
                        .forEach(result::add);
            } else if (classifier != null) {
                // look for attached artifact with given classifier
                for (Artifact artifact : project.getAttachedArtifacts()) {
                    if (classifier.equals(artifact.getClassifier()) && Constants.EXTENSION_CONTENT_PACKAGE.equalsIgnoreCase(artifact.getArtifactHandler().getExtension())) {
                        getLog().info("Using attached artifact with classifier '" + classifier + "' as content package: " + project.getArtifact());
                        result.add(artifact);
                        break; // only one attached artifact with matching classifier and extension is expected
                    }
                }
                if (result.isEmpty()) {
                    throw new MojoExecutionException("No attached artifact with classifier \"" + classifier + "\" and extension \"" + Constants.EXTENSION_CONTENT_PACKAGE + "\" found for project.");
                }
            } else {
                // Use the current project artifact as the content package
                getLog().info("Using current project as content package: " + project.getArtifact());
                if (project.getArtifact().getFile() == null) {
                    // in case of a standalone usage of the plugin, the project artifact file might not be set
                    final File target = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + "." + Constants.EXTENSION_CONTENT_PACKAGE);
                    if ( !target.exists() ) {
                        throw new MojoExecutionException("Project artifact file not found. Build the project first. Looking for: " + target.getName());
                    }
                    final DefaultArtifact targetArtifact = new DefaultArtifact(project.getGroupId(),
                        project.getArtifactId(), 
                        project.getVersion(), 
                        null, 
                        project.getPackaging(),
                        null,
                        project.getArtifact().getArtifactHandler());
                    targetArtifact.setFile(target);
                    return Collections.singletonList(targetArtifact);
                }
                result.add(project.getArtifact());
            }
        } else {
            for (final Dependency d : project.getDependencies()) {
                if (Constants.PACKAGING_ZIP.equals(d.getType()) || Constants.PACKAGING_CONTENT_PACKAGE.equals(d.getType())) {
                    // If a dependency is of type 'zip' it is assumed to be a content package
                    final Artifact artifact = getOrResolveArtifact(new ArtifactId(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getClassifier(), d.getType()));
                    result.add(artifact);
                }
            }    
            if (result.isEmpty()) {
                throw new MojoExecutionException("No content packages found for project.");
            }
            getLog().info("Using content packages from dependencies: " + result);
        }
        if (additionalContentPackageArtifacts != null) {
            additionalContentPackageArtifacts.stream()
                .map(ArtifactId::fromMvnId)
                .map(a -> new ArtifactId(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier(), a.getType()))
                .map(this::getOrResolveArtifact)
                .forEach(a -> {
                        getLog().info("Considering additional content package: " + a);
                        result.add(a); 
                    });
        }
        return result;
    }

    /**
     * Ensures that all files are stored inside the project directory.
     * @param files Files
     * @throws MojoExecutionException If invalid files are found.
     */
    private void validateContentPackageFiles(List<File> files) throws MojoExecutionException {
        for (File file : files) {
            try {
                if (!file.exists()) {
                    throw new MojoExecutionException("File not found: " + file.getAbsolutePath());
                }
                if (!FileUtils.directoryContains(project.getBasedir(), file)) {
                    throw new MojoExecutionException("File not inside project directory: " + file.getAbsolutePath());
                }
            }
            catch (IOException ex) {
                throw new MojoExecutionException("Error validation file: " + file.getAbsolutePath(), ex);
            }
        }
    }

    /**
     * Creates a "virtual" maven artifact out of the given custom content package file. 
     * @param file Content package file
     * @return Maven artifact
     */
    private Artifact contentPackageFileToArtifact(File file) {
        String fileClassifier = "hash-" + file.getPath().hashCode();
        String type = "zip";
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(type);
        Artifact fileArtifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, type, fileClassifier, artifactHandler);
        fileArtifact.setFile(file);
        return fileArtifact;
    }

    /**
     * Aggregate the feature models
     * @return A list of feature models
     * @throws MojoExecutionException If anything goes wrong
     */
    List<Feature> aggregateFeatureModels(final ArtifactId sdkId, final List<ArtifactId> addons, final ArtifactProvider artifactProvider) throws MojoExecutionException {
        try {
            final AemAggregator a = new AemAggregator();
            a.setFeatureOutputDirectory(getGeneratedFeaturesDir());
            a.setArtifactProvider(artifactProvider);
            a.setFeatureProvider(new FeatureProvider() {
                @Override
                public Feature provide(final ArtifactId id) {
                    return getOrResolveFeature(id);
                }
            });
            a.setProjectId(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, null));
            a.setSdkId(sdkId);
            a.setAddOnIds(addons);
            a.setEnableDuplicateBundleHandling(true);

            return a.aggregate();
        
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Analyse the features
     * 
     * @param features The features
     * @param artifactProvider The artifact provider
     * @throws MojoFailureException If the analysis fails
     * @throws MojoExecutionException If something goes wrong
     */
    AemAnalyserResult analyseFeatures(final List<Feature> features, 
            final ArtifactProvider artifactProvider) throws MojoFailureException, MojoExecutionException {
        try {
            final AemAnalyser analyser = new AemAnalyser();
            analyser.setArtifactProvider(artifactProvider);
            analyser.setIncludedTasks(this.getAnalyserTasks());
            analyser.setIncludedUserTasks(this.getAnalyserUserTasks());
            analyser.setTaskConfigurations(this.getAnalyserTaskConfigurations());

            analyser.setFeatureParticipantResolver(this.getProject());

            return analyser.analyse(features);            
        } catch ( final Exception e) {
            throw new MojoExecutionException("A fatal error occurred while analysing the features, see error cause:",
                    e);
        }
    }

    /**
     * Get the composite artifact provider of a default artifact manager and a custom provider which is able to resolve the project attached artifacts
     * @return the composite provider
     * @throws IOException If creation of the provider fails
     */
    ArtifactProvider getCompositeArtifactProvider(final ArtifactManager artifactManager) throws IOException {
        return new ArtifactProvider() {

            @Override
            public URL provide(final ArtifactId id) {
                URL url = artifactManager.provide(id);
                if (url != null) {
                    return url;
                }
                try {
                    return getOrResolveArtifact(id).getFile().toURI().toURL();
                } catch (final MalformedURLException e) {
                    getLog().debug("Malformed url " + e.getMessage(), e);
                    // ignore
                    return null;
                }
            }
        };
    }

    /**
     * Get the analyser task configuration
     * @return The analyser task configuration
     */
    Map<String, Map<String, String>> getAnalyserTaskConfigurations() {
        Map<String, Map<String, String>> config = new HashMap<>();
        if (this.analyserTaskConfigurations != null) {
            for(final Map.Entry<String, Properties> entry : this.analyserTaskConfigurations.entrySet()) {
                final Map<String, String> m = new HashMap<>();

                entry.getValue().stringPropertyNames().forEach(n -> m.put(n, entry.getValue().getProperty(n)));
                config.put(entry.getKey(), m);
            }
        }

        return config;
    }

    /**
     * Get the analyser task
     * @return The tasks
     */
    Set<String> getAnalyserTasks() {
        return getFilteredSet(this.analyserTasks, this.skipAnalyserTasks);
    }

    /**
     * Get the analyser user task
     * @return The tasks
     */
    Set<String> getAnalyserUserTasks() {
        return getFilteredSet(this.analyserUserTasks, this.skipAnalyserUserTasks);
    }

    private static Set<String> getFilteredSet(List<String> values, List<String> skipValues) {
        Set<String> skipValuesSet = new HashSet<>(skipValues != null ? skipValues : Collections.emptyList());
        return values.stream()
                .filter(value -> !skipValuesSet.contains(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get an artifact manager. The returned one must be shut down.
     * @return The provider
     * @throws IOException If the provider can't be created
     */
    ArtifactManager getArtifactManager() throws IOException {
        ArtifactManagerConfig amcfg = new ArtifactManagerConfig();
        amcfg.setRepositoryUrls(new String[] { getConversionOutputDir().toURI().toURL().toString() });

        return ArtifactManager.getArtifactManager(amcfg);
    }
}
