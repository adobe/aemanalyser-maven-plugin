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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MojoUtils {
    static final String PROPERTY_SKIP_VAR = "skipVar";
    static final String DEFAULT_SKIP_ENV_VAR = "CM_PROGRAM_ID";

    /** Artifact cache */
    private static final String ARTIFACT_CACHE = Artifact.class.getName() + "/cache";

    /** Aggregates key */
    private static final String AGGREGATES_KEY = "aem-analyser-aggregates";

    static final Map<String, String> ENV_VARS = new HashMap<>(System.getenv()); // wrap in a map to support unit testing

    private MojoUtils() {}

    static void setParameter(Object mojo, String field, Object value)
            throws MojoExecutionException {
        setParameter(mojo, mojo.getClass(), field, value);
    }

    static void setParameter(Object mojo, Class<?> cls, String field, Object value)
            throws MojoExecutionException {
        try {
            try {
                Field f = cls.getDeclaredField(field);

                f.setAccessible(true);
                f.set(mojo, value);
            } catch (NoSuchFieldException e) {
                Class<?> sc = cls.getSuperclass();
                if (!sc.equals(Object.class)) {
                    // Try the superclass
                    setParameter(mojo, sc, field, value);
                } else {
                    throw e;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }

    static File getConversionOutputDir(MavenProject project) {
        return new File(project.getBuild().getDirectory() + "/cp-conversion");
    }

    static File getGeneratedFeaturesDir(MavenProject project) {
        return new File(getConversionOutputDir(project), "fm.out");
    }

    static boolean skipRun(String skipEnvVarName) {
        if (skipEnvVarName == null)
            skipEnvVarName = DEFAULT_SKIP_ENV_VAR;

        String skipVar = ENV_VARS.get(skipEnvVarName);
        return skipVar != null && skipVar.length() > 0;
    }

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
     * @param project The maven project
     * @param session The maven session
     * @param artifactHandlerManager The artifact handler manager
     * @param resolver The artifact resolver
     * @param id The ID of the artifact to get/resolve.
     * @return the artifact, which has been resolved.
     */
    @SuppressWarnings("deprecation")
    public static Artifact getOrResolveArtifact(final MavenProject project,
            final MavenSession session,
            final ArtifactHandlerManager artifactHandlerManager,
            final ArtifactResolver resolver,
            final ArtifactId id) {
        @SuppressWarnings("unchecked")
        Map<String, Artifact> cache = (Map<String, Artifact>) project.getContextValue(ARTIFACT_CACHE);
        if ( cache == null ) {
            cache = new ConcurrentHashMap<>();
            project.setContextValue(ARTIFACT_CACHE, cache);
        }
        Artifact result = cache.get(id.toMvnId());
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
                        resolver.resolve(prjArtifact, project.getRemoteArtifactRepositories(), session.getLocalRepository());
                    } catch (final ArtifactResolutionException | ArtifactNotFoundException e) {
                        throw new RuntimeException("Unable to get artifact for " + id.toMvnId(), e);
                    }
                    result = prjArtifact;
                }
            }
            cache.put(id.toMvnId(), result);
        }

        return result;
    }

    public static Feature getOrResolveFeature(final MavenProject project, final MavenSession session,
            final ArtifactHandlerManager artifactHandlerManager, final ArtifactResolver resolver, final ArtifactId id) {
        final File artFile = getOrResolveArtifact(project, session, artifactHandlerManager, resolver, id).getFile();
        try (final Reader reader = new FileReader(artFile)) {
            return FeatureJSONReader.read(reader, artFile.getAbsolutePath());
        } catch (final IOException ioe) {
            throw new RuntimeException("Unable to read feature file " + artFile + " for " + id.toMvnId(), ioe);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Feature> getAggregates(final MavenProject project) {
        return (List<Feature>)project.getContextValue(AGGREGATES_KEY);
    }

    public static void setAggregates(final MavenProject project, final List<Feature> features) {
        project.setContextValue(AGGREGATES_KEY, features);
    }
}
