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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.codehaus.mojo.versions.api.ArtifactVersions;
import org.codehaus.mojo.versions.api.DefaultVersionsHelper;
import org.codehaus.mojo.versions.api.UpdateScope;
import org.codehaus.mojo.versions.api.VersionsHelper;

/**
 * Helper methods to manage dependencies, versions, ...
 */
public class VersionUtil {

    private final MavenProject project;

    private final Log log;

    private final ArtifactResolver artifactResolver;

    private final MavenSession mavenSession;

    private final ArtifactFactory artifactFactory;

    private final ArtifactMetadataSource artifactMetadataSource;

    private final List<ArtifactRepository> remoteArtifactRepositories;

    private final ArtifactRepository localRepository;

    public VersionUtil(final Log log, 
            final MavenProject project,
            final ArtifactResolver artifactResolver,
            final MavenSession mavenSession,
            final ArtifactFactory artifactFactory,
            final ArtifactMetadataSource artifactMetadataSource,
            final List<ArtifactRepository> remoteArtifactRepositories,
            final ArtifactRepository localRepository) {
        this.project = project;
        this.log = log;
        this.artifactResolver = artifactResolver;
        this.mavenSession = mavenSession;
        this.artifactFactory = artifactFactory;
        this.artifactMetadataSource = artifactMetadataSource;
        this.remoteArtifactRepositories = remoteArtifactRepositories;
        this.localRepository = localRepository;
    }

    /**
     * Get the list of addons
     * @param addons Configured add ons
     * @return The list of discovered addons
     * @throws MojoExecutionException
     */
    List<ArtifactId> discoverAddons(final List<Addon> addons, final boolean useDependencyVersions) throws MojoExecutionException {
        final List<ArtifactId> result = new ArrayList<>();
        for (Addon addon : addons == null ? Constants.DEFAULT_ADDONS : addons) {
            ArtifactId addonSDK = getArtifactIdFromDependencies(addon.groupId, addon.artifactId);

            if (addonSDK != null ) {
                // check for latest version
                final Dependency dep = new Dependency();
                dep.setGroupId(addonSDK.getGroupId());
                dep.setArtifactId(addonSDK.getArtifactId());
                dep.setVersion(addonSDK.getVersion());
                final String foundVersion = useDependencyVersions ? null : this.getLatestVersion(dep);
                String useVersion = dep.getVersion();
                if ( foundVersion != null && isNewer(useVersion, foundVersion)) {
                    this.log.warn("Project is configured with outdated Add-On version : " + dep);
                    this.log.warn("Please update to version : " + foundVersion);
                    useVersion = foundVersion;
                }
                addonSDK = addonSDK.changeVersion(useVersion);

                this.log.info("Using Add-On for analysis: " + addonSDK);

                result.add(addonSDK);
            }
        }

        return result;
    }
    
    /**
     * Get the SDK artifact id
     * @return The artifact id of the SDK
     * @throws MojoExecutionException If the artifact id can't be detected
     */
    ArtifactId getSDKArtifactId(
            final String configuredArtifactId,
            final String configuredVersion,
            final boolean useDependencyVersions) throws MojoExecutionException {

        ArtifactId dependencySdk;
        // if an artifact id is configured, use it to find a project dependency
        if ( configuredArtifactId != null ) {
            if ( configuredVersion != null ) {
                dependencySdk = new ArtifactId(Constants.SDK_GROUP_ID, configuredArtifactId, configuredVersion, null, null);
            } else {
                dependencySdk = getArtifactIdFromDependencies(Constants.SDK_GROUP_ID, configuredArtifactId);
                if ( dependencySdk == null && configuredVersion == null ) {
                    throw new MojoExecutionException("Unable to find SDK artifact in dependencies or dependency management: "
                                        + Constants.SDK_GROUP_ID + ":" + configuredArtifactId);
                }    
            }
        } else {
            // first search prerelease SDK
            dependencySdk = getArtifactIdFromDependencies(Constants.SDK_GROUP_ID, Constants.SDK_PRERELEASE_ARTIFACT_ID);
            if ( dependencySdk == null ) {
                // use SDK
                dependencySdk = getArtifactIdFromDependencies(Constants.SDK_GROUP_ID, Constants.SDK_ARTIFACT_ID);
            }
        }

        // use configured, found or default artifact id
        final String useArtifactId = dependencySdk != null ? dependencySdk.getArtifactId() : Constants.SDK_ARTIFACT_ID;

        // if a version is configured, use it
        final ArtifactId result;
        if ( configuredVersion != null ) {
            result = new ArtifactId(Constants.SDK_GROUP_ID, useArtifactId, configuredVersion, null, null);
            this.log.info("Using configured SDK Version for analysis: " + result);

        } else {
            // check for latest version
            final Dependency dep = new Dependency();
            dep.setGroupId(Constants.SDK_GROUP_ID);
            dep.setArtifactId(useArtifactId);
            dep.setVersion(dependencySdk == null ? "1.0" : dependencySdk.getVersion());
            
            final String foundVersion = useDependencyVersions ? null : getLatestVersion(dep);
            if ( foundVersion == null && dependencySdk == null ) {
                throw new MojoExecutionException("Unable to find SDK artifact in dependencies or dependency management: "
                                    + Constants.SDK_GROUP_ID + ":" + useArtifactId);
            }
            String useVersion = dependencySdk != null ? dependencySdk.getVersion() : null;
            if ( dependencySdk != null && foundVersion != null && isNewer(useVersion, foundVersion)) {
                this.log.warn("Project is configured with outdated SDK version : " + dependencySdk.getVersion());
                this.log.warn("Please update to SDK version : " + foundVersion);
                useVersion = foundVersion;
            }
            result = new ArtifactId(Constants.SDK_GROUP_ID, useArtifactId, useVersion, null, null);

            this.log.info("Using detected SDK Version for analysis: " + result);
        }

        return result;
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
     * Get the artifact id from the dependencies of the project
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
     * Find the latest version for the dependency
     * @param dependency The dependency to check
     * @return The latest version or {code null}
     * @throws MojoExecutionException If something goes wrong
     */
    String getLatestVersion(final Dependency dependency) throws MojoExecutionException {
        final VersionsHelper helper = new DefaultVersionsHelper(artifactFactory, artifactResolver, artifactMetadataSource,
                remoteArtifactRepositories, null, localRepository, null, null, null,
                null, this.log, this.mavenSession, null);
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
                        this.log.debug("Using provided version " + versionInfo + " for " + entry.getKey());
                        newVersion = versionInfo;
                    }
                }
                if (newVersion == null) {
                    newVersion = getVersion(entry, scope);
                    this.log.debug("Detected new version " + newVersion + " using scope " + scope.toString() + " for "
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
