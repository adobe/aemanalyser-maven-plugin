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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
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

import com.adobe.aem.analyser.result.AemAnalyserAnnotation;
import com.adobe.aem.analyser.result.AemAnalyserResult;
import com.adobe.aem.project.model.Application;
import com.adobe.aem.project.model.Project;

/**
 * Abstract base class for all mojos
 */
public abstract class AbstractAemMojo extends AbstractMojo {

    /**
     * The maven project
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    protected RepositorySystemSession repoSession;

    /**
     * Artifact cache
     */
    private final Map<String, Artifact> artifactCache = new ConcurrentHashMap<>();

    /**
     * If enabled, all analyser warnings will be turned into errors and fail the build.
     * @since 1.0.12
     */
    @Parameter(defaultValue = "false", property = "aem.analyser.strict")
    protected boolean strictValidation;

    @Parameter(defaultValue = "false", property = "aem.analyser.sysout")
    protected boolean useSysout;

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

    protected Project getProject() {
        Project pr = DependencyLifecycleParticipant.getProject(this.project);
        if ( pr == null ) {
            pr = new Project(this.project.getBasedir().getParentFile());
            pr.scan();
            DependencyLifecycleParticipant.setProject(this.project, pr);
        }
        if ( pr.getApplication() == null || !pr.getApplication().getDirectory().getAbsolutePath().equals(this.project.getBasedir().getAbsolutePath())) {
            pr.setApplication(new Application(this.project.getBasedir()));
        }
        pr.getApplication().setId(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, null));
        return pr;
    }

    private File getBaseDirectory() {
        File baseDir = this.project.getBasedir();
        if ( this.useSysout ) {
            boolean done = false;
            do {
                final File gitDir = new File(baseDir, ".git");
                if ( gitDir.exists() && gitDir.isDirectory() ) {
                    done = true;
                } else {
                    baseDir = baseDir.getParentFile();
                    if ( baseDir == null ) {
                        baseDir = this.project.getBasedir();
                        done = true;
                    }
                }
            } while ( !done );
        }
        return baseDir;
    }

    protected void printResult(final AemAnalyserResult result) {
        final File baseDir = this.getBaseDirectory();
        for(final AemAnalyserAnnotation ann : result.getWarnings()) {
            if ( this.strictValidation ) {
                if ( this.useSysout ) {
                    System.out.println(ann.toMessage(AemAnalyserAnnotation.Level.error, baseDir));
                } else {
                    getLog().error(ann.toString(baseDir));
                }
            } else {
                if ( this.useSysout ) {
                    System.out.println(ann.toMessage(AemAnalyserAnnotation.Level.warning, baseDir));
                } else {
                    getLog().warn(ann.toString(baseDir));
                }
            }
        }
        for(final AemAnalyserAnnotation ann : result.getErrors()) {
            if ( this.useSysout ) {
                System.out.println(ann.toMessage(AemAnalyserAnnotation.Level.error, baseDir));
            } else {
                getLog().error(ann.toString(baseDir));
            }
        }
    }
}
