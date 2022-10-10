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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import com.adobe.aem.analyser.AemAnalyserResult;

/**
 * Abstract base class for all analyse mojos
 */
public abstract class AbstractAnalyseMojo extends AbstractMojo {

    /**
     * The artifact id of the sdk api jar. The artifact id is automatically detected by this plugin,
     * by using this configuration the auto detection can be disabled
     */
    @Parameter(property = "sdkArtifactId")
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
    @Parameter(required = false, defaultValue = "false", property = "sdkUseDependency")
    boolean useDependencyVersions;

    /**
     * The list of add ons.
     */
    @Parameter
    List<Addon> addons;

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
     * If enabled, all analyser warnings will be turned into errors and fail the build.
     * @since 1.0.12
     */
    @Parameter(defaultValue = "false", property = "aem.analyser.strict")
    private boolean strictValidation;

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
     * The maven session
     */
    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter( defaultValue = "${plugin}", readonly = true ) // Maven 3 only
    private PluginDescriptor plugin;

    /**
     * Artifact cache
     */
    private final Map<String, Artifact> artifactCache = new ConcurrentHashMap<>();

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

        final VersionUtil versionUtil = new VersionUtil(this.getLog(), this.project, artifactHandlerManager,
                this.repoSystem, this.repoSession,
                this.mavenSession.isOffline());

        versionUtil.checkPluginVersion(this.plugin.getGroupId(), this.plugin.getArtifactId(), this.plugin.getVersion());

        final ArtifactId sdkId = versionUtil.getSDKArtifactId(this.sdkArtifactId, this.sdkVersion, this.useDependencyVersions);
        final List<ArtifactId> addons = versionUtil.discoverAddons(this.addons, this.useDependencyVersions);

        final AemAnalyserResult result = this.doExecute(sdkId, addons);

        // version util warnings
        versionUtil.getVersionWarnings().stream().forEach(msg -> {if ( strictValidation) getLog().error(msg); else getLog().warn(msg);});

        // result warnings and errors
        result.getWarnings().stream().forEach(msg -> {if ( strictValidation) getLog().error(msg); else getLog().warn(msg);});
        if ( strictValidation ) {
            versionUtil.getVersionWarnings().stream().forEach(msg -> getLog().error(msg));
        }
        result.getErrors().stream().forEach(msg -> getLog().error(msg));

        // finally, analyser errors
        final boolean hasErrors = result.hasErrors() 
            || (strictValidation && (result.hasWarnings() || !versionUtil.getVersionWarnings().isEmpty()));

        if (hasErrors) {
            if ( failOnAnalyserErrors ) {
                throw new MojoFailureException(
                    "One or more feature analyser(s) detected feature error(s), please read the plugin log for more details");
            }
            getLog().warn("Errors found during analyser run, but this plugin is configured to ignore errors and continue the build!");
        }            
    }

    protected abstract AemAnalyserResult doExecute(final ArtifactId sdkId, 
        final List<ArtifactId> addons) 
        throws MojoExecutionException, MojoFailureException;

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
    Artifact getOrResolveArtifact(final ArtifactId id) {
        Artifact result = this.artifactCache.get(id.toMvnId());
        if ( result == null ) {
            result = findArtifact(id, project.getAttachedArtifacts());
            if ( result == null ) {
                result = findArtifact(id, project.getArtifacts());
                if ( result == null ) {
                    ArtifactRequest req = new ArtifactRequest(new org.eclipse.aether.artifact.DefaultArtifact(id.toMvnId()), project.getRemoteProjectRepositories(), null);
                    try {
                        ArtifactResult resolutionResult = repoSystem.resolveArtifact(repoSession, req);
                        result = RepositoryUtils.toArtifact(resolutionResult.getArtifact());
                    } catch (ArtifactResolutionException e) {
                        throw new RuntimeException("Unable to get artifact for " + id.toMvnId(), e);
                    }
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
}
