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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.adobe.aem.analyser.AemAggregator;
import com.adobe.aem.analyser.AemAnalyser;
import com.adobe.aem.analyser.AemAnalyserResult;
import com.adobe.aem.analyser.AemPackageConverter;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.codehaus.mojo.versions.api.ArtifactVersions;
import org.codehaus.mojo.versions.api.DefaultVersionsHelper;
import org.codehaus.mojo.versions.api.UpdateScope;
import org.codehaus.mojo.versions.api.VersionsHelper;

public class AemAnalyseMojo extends AbstractMojo {

    /**
     * The artifact id of the sdk api jar. The artifact id is automatically detected by this plugin,
     * but using this configuration the auto detection can be disabled
     */
    @Parameter(defaultValue = Constants.SDK_ARTIFACT_ID, property = "sdkArtifactId")
    String sdkArtifactId;
    
    /**
     * The version of the sdk api. Can be used to specify the exact version to be used. Otherwise the
     * plugin detects the version to use.
     */
    @Parameter(required = false, property = "sdkVersion")
    String sdkVersion;

    /**
     * Use dependency versions. If this is enabled, the version for the SDK and the Add-ons is taken
     * from the project dependencies. By default, the latest version is used.
     */
    @Parameter(required = false, defaultValue = "false")
    boolean useDependencyVersions;

    /**
     * The list of add ons.
     */
    @Parameter
    List<Addon> addons;
    
    /**
     * The analyser tasks run be the analyser
     */
    @Parameter(defaultValue = AemAnalyser.DEFAULT_TASKS,
        property = "analyserTasks")
    List<String> analyserTasks;

    /**
     * Optional configurations for the analyser tasks
     */
    @Parameter
    Map<String, Properties> analyserTaskConfigurations;

    /**
     * Skip the execution
     */
    @Parameter(defaultValue = "false", property = "aem.analyser.skip")
    boolean skip;

    /**
     * Fail on analyser errors?
     */
    @Parameter(defaultValue = "true", property = "failon.analyser.errors")
    private boolean failOnAnalyserErrors;

    /**
     * The maven project
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The artifact manager to resolve artifacts
     */
    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    /**
     * The artifact resolver
     */
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * The maven session
     */
    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Component
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    @Component
    private ArtifactMetadataSource artifactMetadataSource;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private List<ArtifactRepository> remoteArtifactRepositories;

    @Parameter(defaultValue = "${localRepository}", readonly = true)
    private ArtifactRepository localRepository;
    
    /**
     * Artifact cache
     */
    private final Map<String, Artifact> artifactCache = new ConcurrentHashMap<>();

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
     * Detect if the execution should be skipped
     * @return {@code true} if execution should be skipped
     */
    boolean skipRun() {
        // check env var
        final String skipVar = System.getenv(Constants.SKIP_ENV_VAR);
        boolean skipExecution = skipVar != null && skipVar.length() > 0;
        if ( skipExecution ) {
            getLog().info("Skipping AEM analyser plugin as variable " + Constants.SKIP_ENV_VAR + " is set.");
        } else if ( this.skip ) {
            skipExecution = true;
            getLog().info("Skipping AEM analyser plugin as configured.");
        }

        return skipExecution;
    }

    /**
     * Execute the plugin
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skipRun()) {
            return;
        }

        final ArtifactId sdkId = this.getSDKArtifactId();

        // 1. Phase : convert content packages
        this.convertContentPackages();

        // 2. Phase : aggregate feature models
        final List<Feature> features = this.aggregateFeatureModels(sdkId);

        // 3. Phase : analyse features
        this.analyseFeatures(features);
    }

    /**
     * Convert the content packages
     * @throws MojoExecutionException If anything goes wrong
     */
    void convertContentPackages() throws MojoExecutionException {
        final AemPackageConverter converter = new AemPackageConverter();
        converter.setArtifactIdOverride(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, "slingosgifeature").toMvnId());
        converter.setConverterOutputDirectory(getConversionOutputDir());
        converter.setFeatureOutputDirectory(getGeneratedFeaturesDir());
    
        for(final Artifact contentPackage: getContentPackages()) {
            final File source = contentPackage.getFile();
            try {
                converter.convert(Collections.singletonMap(contentPackage.toString(), source));
            } catch (final IOException t) {
                throw new MojoExecutionException("Content Package Converter Exception " + t.getMessage(), t);        
            }
        }
    }

    /**
     * Search for all content packages.
     * @return The list of artifacts (non empty)
     * @throws MojoExecutionException If anything goes wrong, for example no content packages are found
     */
    private List<Artifact> getContentPackages() throws MojoExecutionException {
        final List<Artifact> result = new ArrayList<>();
        
        if (!Constants.PACKAGING_AEM_ANALYSE.equals(project.getPackaging())) {
            // Use the current project artifact as the content package
            getLog().info("Using current project as content package: " + project.getArtifact());
            result.add(project.getArtifact());
        } else {
            for (final Artifact d : project.getDependencyArtifacts()) {
                if (Constants.PACKAGING_ZIP.equals(d.getType()) || Constants.PACKAGING_CONTENT_PACKAGE.equals(d.getType())) {
                    // If a dependency is of type 'zip' it is assumed to be a content package
                    result.add(d);
                }
            }    
            getLog().info("Found content packages from dependencies: " + result);
        }

        if (result.isEmpty()) {
            throw new MojoExecutionException("No content packages found for project.");
        }
        return result;
    }

    /**
     * Aggregate the feature models
     * @return A list of feature models
     * @throws MojoExecutionException If anything goes wrong
     */
    List<Feature> aggregateFeatureModels(final ArtifactId sdkId) throws MojoExecutionException {
        try {
            final AemAggregator a = new AemAggregator();
            a.setFeatureOutputDirectory(getGeneratedFeaturesDir());
            a.setArtifactProvider(getArtifactProvider());
            a.setFeatureProvider(new FeatureProvider() {
                @Override
                public Feature provide(final ArtifactId id) {
                    return getOrResolveFeature(id);
                }
            });
            a.setProjectId(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, null));
            a.setSdkId(sdkId);
            a.setAddOnIds(this.discoverAddons(this.addons));
            
            return a.aggregate();
        
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Get the SDK artifact id
     * @return The artifact id of the SDK
     * @throws MojoExecutionException If the artifact id can't be detected
     */
    ArtifactId getSDKArtifactId() throws MojoExecutionException {
        ArtifactId sdkDep;
        if (this.sdkVersion == null) {
            sdkDep = getArtifactIdFromDependencies(Constants.SDK_GROUP_ID, this.sdkArtifactId);

            // check for latest version
            final Dependency dep = new Dependency();
            dep.setGroupId(Constants.SDK_GROUP_ID);
            dep.setArtifactId(this.sdkArtifactId);
            dep.setVersion(sdkDep == null ? "1.0" : sdkDep.getVersion());
            final String foundVersion = this.useDependencyVersions ? null : this.getLatestVersion(dep);
            if ( foundVersion == null && sdkDep == null ) {
                throw new MojoExecutionException("Unable to find SDK artifact in dependencies or dependency management: "
                                    + Constants.SDK_GROUP_ID + ":" + this.sdkArtifactId);
            }
            String useVersion = sdkDep != null ? sdkDep.getVersion() : null;
            if ( sdkDep != null && foundVersion != null && isNewer(useVersion, foundVersion)) {
                getLog().warn("Project is configured with outdated SDK version : " + sdkDep.getVersion());
                getLog().warn("Please update to SDK version : " + foundVersion);
                useVersion = foundVersion;
            }
            sdkDep = new ArtifactId(Constants.SDK_GROUP_ID, this.sdkArtifactId, useVersion, null, null);

            getLog().info("Using detected SDK Version for analysis: " + sdkDep);

        } else {
            sdkDep = new ArtifactId(Constants.SDK_GROUP_ID, this.sdkArtifactId, sdkVersion, null, null);
            getLog().info("Using configured SDK Version for analysis: " + sdkDep);
        }

        return sdkDep;
    }

    /**
     * Get the list of addons
     * @param addons Configured add ons
     * @return The list of discovered addons
     * @throws MojoExecutionException
     */
    List<ArtifactId> discoverAddons(final List<Addon> addons) throws MojoExecutionException {
        final List<ArtifactId> result = new ArrayList<>();
        for (Addon addon : addons == null ? Constants.DEFAULT_ADDONS : addons) {
            ArtifactId addonSDK = getArtifactIdFromDependencies(addon.groupId, addon.artifactId);

            if (addonSDK != null ) {
                // check for latest version
                final Dependency dep = new Dependency();
                dep.setGroupId(addonSDK.getGroupId());
                dep.setArtifactId(addonSDK.getArtifactId());
                dep.setVersion(addonSDK.getVersion());
                final String foundVersion = this.useDependencyVersions ? null : this.getLatestVersion(dep);
                String useVersion = dep.getVersion();
                if ( foundVersion != null && isNewer(useVersion, foundVersion)) {
                    getLog().warn("Project is configured with outdated Add-On version : " + dep);
                    getLog().warn("Please update to version : " + foundVersion);
                    useVersion = foundVersion;
                }
                addonSDK = addonSDK.changeVersion(useVersion);

                getLog().info("Using Add-On for analysis: " + addonSDK);

                result.add(addonSDK);
            }
        }

        return result;
    }

    /**
     * Get the artifact id for the dependency
     * @param groupId The group id 
     * @param artifactId The artifact id
     * @return The artifact id or {@code null}
     * @throws MojoExecutionException On error
     */
    ArtifactId getArtifactIdFromDependencies(final String groupId, final String artifactId) 
    throws MojoExecutionException {
        final List<Dependency> allDependencies = new ArrayList<>();
        
        if (project.getDependencies() != null) {
            allDependencies.addAll(project.getDependencies());
        }
        if (project.getDependencyManagement() != null && project.getDependencyManagement().getDependencies() != null ) {
            allDependencies.addAll(project.getDependencyManagement().getDependencies());
        }

        for (final Dependency d : allDependencies) {
            if (groupId.equals(d.getGroupId()) &&
                    artifactId.equals(d.getArtifactId())) {

                return new ArtifactId(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getClassifier(), d.getType());
            }
        }
        return null;
    }

    /**
     * Analyse the features
     * 
     * @param features The features
     * @throws MojoFailureException If the analysis fails
     * @throws MojoExecutionException If something goes wrong
     */
    void analyseFeatures(final List<Feature> features) throws MojoFailureException, MojoExecutionException {
        boolean hasErrors = false;
        try {
            final AemAnalyser analyser = new AemAnalyser();
            analyser.setArtifactProvider(getArtifactProvider());
            analyser.setIncludedTasks(this.getAnalyserTasks());
            analyser.setTaskConfigurations(this.getAnalyserTaskConfigurations());

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

    private boolean isNewer(final String existingVersion, final String foundVersion) {
        if ( foundVersion == null ) {
            return false;
        }
        final ArtifactVersion ev = new DefaultArtifactVersion(existingVersion);
        final ArtifactVersion fv = new DefaultArtifactVersion(foundVersion);
        return fv.compareTo(ev) > 0;
    }

    /**
     * Get the artifact provider
     * @return The provider
     * @throws IOException If creation of the provider fails
     */
    ArtifactProvider getArtifactProvider() throws IOException {
        final ArtifactProvider localProvider = this.getLocalArtifactProvider();
        return new ArtifactProvider() {

            @Override
            public URL provide(final ArtifactId id) {
                URL url = localProvider.provide(id);
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
        return new LinkedHashSet<>(this.analyserTasks);
    }

    /**
     * Get the local artifact provider
     * @return The provider
     * @throws IOException If the provider can't be created
     */
    ArtifactProvider getLocalArtifactProvider() throws IOException {
        ArtifactManagerConfig amcfg = new ArtifactManagerConfig();
        amcfg.setRepositoryUrls(new String[] { getConversionOutputDir().toURI().toURL().toString() });

        return ArtifactManager.getArtifactManager(amcfg);
    }

    /**
     * Find the artifact in the collection
     * @param id The artifact id
     * @param artifacts The collection
     * @return The artifact or {@code null}
     */
    private static Artifact findArtifact(final ArtifactId id, final Collection<Artifact> artifacts) {
        if (artifacts != null) {
            for(final Artifact artifact : artifacts) {
                if ( artifact.getGroupId().equals(id.getGroupId())
                   && artifact.getArtifactId().equals(id.getArtifactId())
                   && artifact.getVersion().equals(id.getVersion())
                   && artifact.getType().equals(id.getType())
                   && ((id.getClassifier() == null && artifact.getClassifier() == null) || (id.getClassifier() != null && id.getClassifier().equals(artifact.getClassifier()))) ) {
                    return artifact.getFile() == null ? null : artifact;
                }
            }
        }
        return null;
    }

    /**
     * Get a resolved Artifact from the coordinates provided
     *
     * @param id The ID of the artifact to get/resolve.
     * @return the artifact, which has been resolved.
     * @throws RuntimeException if the artifact can't be resolved
     */
    @SuppressWarnings("deprecation")
    Artifact getOrResolveArtifact(final ArtifactId id) {
        Artifact result = this.artifactCache.get(id.toMvnId());
        if ( result == null ) {
            result = findArtifact(id, project.getAttachedArtifacts());
            if ( result == null ) {
                result = findArtifact(id, project.getDependencyArtifacts());
                if ( result == null ) {
                    final Artifact prjArtifact = new DefaultArtifact(id.getGroupId(),
                            id.getArtifactId(),
                            VersionRange.createFromVersion(id.getVersion()),
                            Artifact.SCOPE_PROVIDED,
                            id.getType(),
                            id.getClassifier(),
                            artifactHandlerManager.getArtifactHandler(id.getType()));
                    try {
                        this.artifactResolver.resolve(prjArtifact, project.getRemoteArtifactRepositories(), this.mavenSession.getLocalRepository());
                    } catch (final ArtifactResolutionException | ArtifactNotFoundException e) {
                        throw new RuntimeException("Unable to get artifact for " + id.toMvnId(), e);
                    }
                    result = prjArtifact;
                }
            }
            this.artifactCache.put(id.toMvnId(), result);
        }

        return result;
    }
    
    /**
     * Get a resolved feature
     *
     * @param id The artifact id of the feature
     * @return The feature
     * @throws RuntimeException if the feature can't be resolved
     */
    Feature getOrResolveFeature(final ArtifactId id) {
        final File artFile = getOrResolveArtifact(id).getFile();
        try (final Reader reader = new FileReader(artFile)) {
            return FeatureJSONReader.read(reader, artFile.getAbsolutePath());
        } catch (final IOException ioe) {
            throw new RuntimeException("Unable to read feature file " + artFile + " for " + id.toMvnId(), ioe);
        }
    }

    /**
     * Find the latest version for the dependency
     * @param dependency The dependency to check
     * @return The latest version or {code null}
     * @throws MojoExecutionException If something goes wrong
     */
    String getLatestVersion(final Dependency dependency) throws MojoExecutionException {
        final VersionsHelper helper = new DefaultVersionsHelper(artifactFactory, artifactResolver, artifactMetadataSource,
                remoteArtifactRepositories, null, localRepository, null, null, null,
                null, getLog(), this.mavenSession, null);
        try {
            final Map<Dependency, ArtifactVersions> updateInfos = helper.lookupDependenciesUpdates(Collections.singleton(dependency), false);

            final Map<Dependency, String> result = new HashMap<>();

            for (final Map.Entry<Dependency, ArtifactVersions> entry : updateInfos.entrySet()) {
                UpdateScope scope = UpdateScope.ANY;
                final String versionInfo = entry.getKey().getSystemPath();
                String newVersion = null;
                if (versionInfo != null && !versionInfo.trim().isEmpty()) {
                    scope = getScope(versionInfo);
                    if (scope == null) {
                        getLog().debug("Using provided version " + versionInfo + " for " + entry.getKey());
                        newVersion = versionInfo;
                    }
                }
                if (newVersion == null) {
                    newVersion = getVersion(entry, scope);
                    getLog().debug("Detected new version " + newVersion + " using scope " + scope.toString() + " for "
                            + entry.getKey());
    
                }
                if (newVersion != null) {
                    result.put(entry.getKey(), newVersion);
                }
            }
    
            return result.get(dependency);
    
        } catch (ArtifactMetadataRetrievalException
                | InvalidVersionSpecificationException e) {
            throw new MojoExecutionException("Unable to calculate updates", e);
        }
    }

    /**
     * Get the latest version
     * @param entry The result
     * @param scope The scope to use
     * @return The latest version
     */
    private String getVersion(final Map.Entry<Dependency, ArtifactVersions> entry, final UpdateScope scope) {
        ArtifactVersion latest;
        if (entry.getValue().isCurrentVersionDefined()) {
            latest = entry.getValue().getNewestUpdate(scope, false);
        } else {
            ArtifactVersion newestVersion = entry.getValue()
                    .getNewestVersion(entry.getValue().getArtifact().getVersionRange(), false);
            latest = newestVersion == null ? null
                    : entry.getValue().getNewestUpdate(newestVersion, scope, false);
            if (latest != null
                    && ArtifactVersions.isVersionInRange(latest, entry.getValue().getArtifact().getVersionRange())) {
                latest = null;
            }
        }
        return latest != null ? latest.toString() : null;
    }

    /**
     * Get the scope from the version info
     * @param versionInfo The info
     * @return The scope
     */
    private UpdateScope getScope(final String versionInfo) {
        final UpdateScope scope;
        if (versionInfo == null || "ANY".equalsIgnoreCase(versionInfo)) {
            scope = UpdateScope.ANY;
        } else if ("MAJOR".equalsIgnoreCase(versionInfo)) {
            scope = UpdateScope.MAJOR;
        } else if ("MINOR".equalsIgnoreCase(versionInfo)) {
            scope = UpdateScope.MINOR;
        } else if ("INCREMENTAL".equalsIgnoreCase(versionInfo)) {
            scope = UpdateScope.INCREMENTAL;
        } else if ("SUBINCREMENTAL".equalsIgnoreCase(versionInfo)) {
            scope = UpdateScope.SUBINCREMENTAL;
        } else {
            scope = null;
        }
        return scope;
    }
}
