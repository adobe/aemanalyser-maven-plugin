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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.adobe.aem.analyser.AemAnalyserUtil;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "aggregate", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class AggregateWithSDKMojo extends AggregateFeaturesMojo {
    private static final String SDK_GROUP_ID = "com.adobe.aem";
    private static final String SDK_ARTIFACT_ID = "aem-sdk-api";
    private static final String SDK_FEATUREMODEL_AUTHOR_CLASSIFIER = "aem-author-sdk";
    private static final String SDK_FEATUREMODEL_PUBLISH_CLASSIFIER = "aem-publish-sdk";
    private static final String FEATUREMODEL_TYPE = "slingosgifeature";

    private static final List<Addon> DEFAULT_ADDONS =
            Arrays.asList(
                    new Addon("com.adobe.aem", "aem-forms-sdk-api", "aem-forms-sdk"),
                    new Addon("com.adobe.aem", "aem-cif-sdk-api", "aem-cif-sdk"));

    boolean unitTestMode = false;
    boolean unitTestEarlyExit = false;
    boolean unitTestEarlyExit2 = false;

    // Shadow this field for maven as we don't need to provide it from the pom.xml
    @Parameter(required = false)
    private List<Aggregate> aggregates;

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

    @Override
    public void execute() throws MojoExecutionException {
        if (MojoUtils.skipRun(skipEnvVarName)) {
            getLog().info("Skipping AEM analyser plugin as variable " + skipEnvVarName + " is set.");
            return;
        }

        if (addons == null)
            addons = DEFAULT_ADDONS;

        // Produce the user aggregates
        Properties runmodes = getRunmodeMappings();

        Map<String, Aggregate> userAggregates = getUserAggregates(runmodes);

        setParameter(this, "generatedFeatures",
                MojoUtils.getGeneratedFeaturesDir(project));
        setParameter(this, AggregateFeaturesMojo.class,
                "aggregates", new ArrayList<>(userAggregates.values()));

        // Generate the aggregate of the user features first
        if (!unitTestMode)
            super.execute();

        if (unitTestEarlyExit)
            return;

        // Produce the product aggregates
        Set<Aggregate> productAggregates = getProductAggregates();
        setParameter(this, "generatedFeatures", null);
        setParameter(this, AggregateFeaturesMojo.class,
                "aggregates", new ArrayList<>(productAggregates));

        // Generate the aggregate of the product with addons first
        if (!unitTestMode)
            super.execute();

        if (unitTestEarlyExit2)
            return;

        // Aggregate with the AEM SDK API feature
        setParameter(this, "generatedFeatures", null);
        setParameter(this, AggregateFeaturesMojo.class,
                "aggregates", getFinalAggregates(userAggregates.keySet()));

        // Now generate the final aggregate
        if (!unitTestMode)
            super.execute();
    }

    private Properties getRunmodeMappings() throws MojoExecutionException {
        File dir = MojoUtils.getGeneratedFeaturesDir(project);

        File mappingFile = new File(dir, "runmode.mapping");
        if (!mappingFile.isFile())
            throw new MojoExecutionException("File generated by content package to feature model converter not found: " + mappingFile);

        Properties p = new Properties();
        try (InputStream is = new FileInputStream(mappingFile)) {
            p.load(is);
        } catch (IOException e) {
            throw new MojoExecutionException("Problem reading " + mappingFile, e);
        }
        return p;
    }

    private Map<String, Aggregate> getUserAggregates(Properties runmodes) throws MojoExecutionException {
        Map<String, Aggregate> aggregates = new HashMap<>();

        Map<String, Set<String>> toCreate = getUserAggregatesToCreate(runmodes);
        for (Map.Entry<String, Set<String>> entry : toCreate.entrySet()) {
            String name = entry.getKey();

            Aggregate a = new Aggregate();
            a.classifier = "user-aggregated-" + name;
            entry.getValue().forEach(n -> a.setFilesInclude("**/" + n));
            a.markAsComplete = false;
            a.artifactsOverrides = Collections.singletonList("*:*:HIGHEST");
            a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");
            aggregates.put(name, a);
        }

        return aggregates;
    }

    Map<String, Set<String>> getUserAggregatesToCreate(final Properties runmodes) throws MojoExecutionException {
        try {
            return AemAnalyserUtil.getAggregates(runmodes);            
        } catch ( final IllegalArgumentException iae) {
            throw new MojoExecutionException(iae.getMessage());
        }
    }

    private List<Aggregate> getFinalAggregates(Set<String> userAggregateNames) throws MojoExecutionException {
        List<Aggregate> aggregates = new ArrayList<>();

        for (String name : userAggregateNames) {
            boolean isAuthor = name.startsWith("author");

            Aggregate a = new Aggregate();
            a.classifier = "aggregated-" + name;
            a.setIncludeClassifier(getProductAggregateName(isAuthor));
            a.setIncludeClassifier("user-aggregated-" + name);
            a.markAsComplete = false; // The feature may not be complete as some packages could
            a.artifactsOverrides = Arrays.asList(
                    "com.adobe.cq:core.wcm.components.core:FIRST",
                    "com.adobe.cq:core.wcm.components.extensions.amp:FIRST",
                    "org.apache.sling:org.apache.sling.models.impl:FIRST",
                    "*:core.wcm.components.content:zip:*:FIRST",
                    "*:core.wcm.components.extensions.amp.content:zip:*:FIRST",
                    "*:*:jar:*:ALL");
            a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");

            aggregates.add(a);
        }

        project.setContextValue(getClass().getName() + "-aggregates",
                aggregates.stream().map(a -> a.classifier).collect(Collectors.toSet()));

        return aggregates;
    }

    private Set<Aggregate> getProductAggregates() throws MojoExecutionException {
        Set<Aggregate> aggregates = new HashSet<>();

        for (boolean isAuthor : new boolean [] {true, false}) {
            String aggClassifier = getProductAggregateName(isAuthor);

            Aggregate a = new Aggregate();
            a.classifier = aggClassifier;
            a.setIncludeArtifact(getSDKFeature(isAuthor, isAuthor));

            List<Dependency> addonDeps = discoverAddons(isAuthor);
            for (Dependency addonDep : addonDeps) {
                a.setIncludeArtifact(addonDep);
            }

            a.artifactsOverrides = Collections.singletonList("*:*:HIGHEST");
            a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");

            aggregates.add(a);
        }

        return aggregates;
    }

    private String getProductAggregateName(boolean author) {
        return "product-aggregated-" + (author ? "author" : "publish");
    }

    private Dependency getSDKFeature(boolean author, boolean log) throws MojoExecutionException {
        Dependency sdkDep;
        if (sdkVersion == null) {
            sdkDep = getSDKFromDependencies(SDK_GROUP_ID, SDK_ARTIFACT_ID, true);
        } else {
            sdkDep = new Dependency();
            sdkDep.setGroupId(sdkGroupId);
            sdkDep.setArtifactId(sdkArtifactId);
            sdkDep.setVersion(sdkVersion);
        }

        if (log)
            getLog().info("Using SDK Version for analysis: " + sdkDep);

        // The SDK Feature Model has the same version as the SDK
        Dependency sdkFM = new Dependency();
        sdkFM.setGroupId(sdkDep.getGroupId());
        sdkFM.setArtifactId(sdkDep.getArtifactId());
        sdkFM.setVersion(sdkDep.getVersion());
        sdkFM.setType(FEATUREMODEL_TYPE);
        sdkFM.setClassifier(author ? SDK_FEATUREMODEL_AUTHOR_CLASSIFIER : SDK_FEATUREMODEL_PUBLISH_CLASSIFIER);
        return sdkFM;
    }

    private List<Dependency> discoverAddons(boolean log) throws MojoExecutionException {
        if (addons == null)
            return Collections.emptyList();

        List<Dependency> addonFMs = new ArrayList<>();
        for (Addon addon : addons) {
            Dependency addonSDK = getSDKFromDependencies(addon.groupId, addon.artifactId, false);

            if (addonSDK == null)
                continue;

            if (log)
                getLog().info("Using Add-On for analysis: " + addonSDK);

            Dependency addonFM = new Dependency();
            addonFM.setGroupId(addonSDK.getGroupId());
            addonFM.setArtifactId(addonSDK.getArtifactId());
            addonFM.setVersion(addonSDK.getVersion());
            addonFM.setClassifier(addon.classifier);
            addonFM.setType("slingosgifeature");
            addonFMs.add(addonFM);
        }

        return addonFMs;
    }

    Dependency getSDKFromDependencies(String groupId, String artifactId, boolean failOnError) throws MojoExecutionException {
        List<Dependency> dependencies = project.getDependencies();
        if (dependencies != null) {
            for (Dependency d : dependencies) {
                if (groupId.equals(d.getGroupId()) &&
                        artifactId.equals(d.getArtifactId())) {
                    return d;
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
                        return d;
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
