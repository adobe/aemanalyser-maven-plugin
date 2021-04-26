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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.adobe.aem.analyser.AemAggregator;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;

@Mojo(name = "aggregate", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class AggregateWithSDKMojo extends AbstractMojo {
    private static final String SDK_GROUP_ID = "com.adobe.aem";
    private static final String SDK_ARTIFACT_ID = "aem-sdk-api";
    private static final List<Addon> DEFAULT_ADDONS =
            Arrays.asList(
                    new Addon("com.adobe.aem", "aem-forms-sdk-api", "aem-forms-sdk"),
                    new Addon("com.adobe.aem", "aem-cif-sdk-api", "aem-cif-sdk"));

    @Parameter(defaultValue = SDK_GROUP_ID, property = "sdkGroupId")
    String sdkGroupId;

    @Parameter(defaultValue = SDK_ARTIFACT_ID, property = "sdkArtifactId")
    String sdkArtifactId;

    @Parameter(required = false, property = "sdkVersion")
    String sdkVersion;

    @Parameter
    List<Addon> addons;

    @Parameter(defaultValue = MojoUtils.DEFAULT_SKIP_ENV_VAR, property = MojoUtils.PROPERTY_SKIP_VAR)
    String skipEnvVarName;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    @Component
    protected ArtifactResolver artifactResolver;

    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Override
    public void execute() throws MojoExecutionException {
        if (MojoUtils.skipRun(skipEnvVarName)) {
            getLog().info("Skipping AEM analyser plugin as variable " + skipEnvVarName + " is set.");
            return;
        }

        try {
            final AemAggregator a = new AemAggregator();
            a.setFeatureOutputDirectory(MojoUtils.getGeneratedFeaturesDir(project));
            a.setArtifactProvider(new ArtifactProvider(){
                @Override
                public URL provide(final ArtifactId id) {
                    try {
                        return MojoUtils
                            .getOrResolveArtifact(project, mavenSession, artifactHandlerManager, artifactResolver, id)
                            .getFile().toURI().toURL();
                    } catch (Exception e) {
                        getLog().debug("Artifact " + id.toMvnId() + " not found");
                        return null;
                    }
                }
            });
            a.setFeatureProvider(new FeatureProvider(){
                
                @Override
                public Feature provide(ArtifactId id) {
                    return MojoUtils.getOrResolveFeature(project, mavenSession, artifactHandlerManager,
                        artifactResolver, id);
                }
            });
            a.setProjectId(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, null));
            a.setSdkId(this.getSDKFeature());
            a.setAddOnIds(this.discoverAddons(this.addons));
            final List<Feature> features = a.aggregate();

            // share features for the analyse goal
            MojoUtils.setAggregates(project, features);
        
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private ArtifactId getSDKFeature() throws MojoExecutionException {
        ArtifactId sdkDep;
        if (sdkVersion == null) {
            sdkDep = getSDKFromDependencies(SDK_GROUP_ID, SDK_ARTIFACT_ID, true);
        } else {
            sdkDep = new ArtifactId(sdkGroupId, sdkArtifactId, sdkVersion, null, null);
        }

        getLog().info("Using SDK Version for analysis: " + sdkDep);
        return sdkDep;
    }

    List<ArtifactId> discoverAddons(final List<Addon> addons) throws MojoExecutionException {
        final List<ArtifactId> result = new ArrayList<>();
        for (Addon addon : addons == null ? DEFAULT_ADDONS : addons) {
            ArtifactId addonSDK = getSDKFromDependencies(addon.groupId, addon.artifactId, false);

            if (addonSDK == null)
                continue;

            getLog().info("Using Add-On for analysis: " + addonSDK);

            result.add(addonSDK);
        }

        return result;
    }

    ArtifactId getSDKFromDependencies(String groupId, String artifactId, boolean failOnError) throws MojoExecutionException {
        List<Dependency> dependencies = project.getDependencies();
        if (dependencies != null) {
            for (Dependency d : dependencies) {
                if (groupId.equals(d.getGroupId()) &&
                        artifactId.equals(d.getArtifactId())) {
                    return new ArtifactId(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getClassifier(), d.getType());
                }
            }
        }

        DependencyManagement depMgmt = project.getDependencyManagement();
        if (depMgmt != null) {
            List<Dependency> deps = depMgmt.getDependencies();
            if (deps != null) {
                for (Dependency d : deps) {
                    if (groupId.equals(d.getGroupId()) &&
                            artifactId.equals(d.getArtifactId())) {
                        return new ArtifactId(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getClassifier(), d.getType());
                    }
                }
            }
        }

        if (failOnError) {
            throw new MojoExecutionException(
                    "Unable to find SDK artifact in dependencies or dependency management: "
                    + groupId + ":" + artifactId);
        }
        return null;
    }

    public static class Addon {
        String groupId;
        String artifactId;
        String classifier;

        public Addon() {}

        Addon(String gid, String aid, String clsf) {
            groupId = gid;
            artifactId = aid;
            classifier = clsf;
        }
    }
}
