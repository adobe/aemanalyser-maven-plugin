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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "aggregate", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class AggregateWithSDKMojo extends AggregateFeaturesMojo {
    private static final String SDK_GROUP_ID = "com.adobe.aem";
    private static final String SDK_ARTIFACT_ID = "aem-sdk-api";
    private static final String SDK_FEATUREMODEL_CLASSIFIER = "aem-sdk";
    private static final String SDK_FEATUREMODEL_TYPE = "slingosgifeature";

    boolean unitTestMode = false;

    // Shadow this field for maven as we don't need to provide it from the pom.xml
    @Parameter(required = false)
    private List<Aggregate> aggregates;

    @Parameter(defaultValue = SDK_GROUP_ID, property = "sdkGroupId")
    String sdkGroupId;

    @Parameter(defaultValue = SDK_ARTIFACT_ID, property = "sdkArtifactId")
    String sdkArtifactId;

    @Parameter(required = false, property = "sdkVersion")
    String sdkVersion;

    @Override
    public void execute() throws MojoExecutionException {
        setParameter(this, AggregateFeaturesMojo.class,
                "aggregates", getAggregates());

        if (unitTestMode)
            return;

        super.execute();
    }

    private List<Aggregate> getAggregates() throws MojoExecutionException {
        List<Aggregate> l = new ArrayList<>();

        // TODO currently just 1 aggregate. Do we need multiple, e.g. for author/publish?
        // Maybe also for dev/stage/prod?
        Aggregate a = new Aggregate();
        a.classifier = "aggregated";
        a.setIncludeArtifact(getSDKFeatureModel());
        a.setFilesInclude("**/*.json"); // TODO we can split this up in author/publish
        a.markAsComplete = true;
        a.artifactsOverrides = Collections.singletonList("*:*:LATEST");
        a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");
        l.add(a);

        return l;
    }

    private Dependency getSDKFeatureModel() throws MojoExecutionException {
        Dependency sdkDep;
        if (sdkVersion == null) {
            sdkDep = getSDKFromDependencies();
        } else {
            sdkDep = new Dependency();
            sdkDep.setGroupId(sdkGroupId);
            sdkDep.setArtifactId(sdkArtifactId);
            sdkDep.setVersion(sdkVersion);
        }

        // The SDK Feature Model has the same version as the SDK
        Dependency sdkFM = new Dependency();
        sdkFM.setGroupId(sdkDep.getGroupId());
        sdkFM.setArtifactId(sdkDep.getArtifactId());
        sdkFM.setVersion(sdkDep.getVersion());
        sdkFM.setType(SDK_FEATUREMODEL_TYPE);
        sdkFM.setClassifier(SDK_FEATUREMODEL_CLASSIFIER);
        return sdkFM;
    }

    private Dependency getSDKFromDependencies() throws MojoExecutionException {
        for (Dependency d : project.getDependencies()) {
            if (SDK_GROUP_ID.equals(d.getGroupId()) &&
                    SDK_ARTIFACT_ID.equals(d.getArtifactId())) {
                return d;
            }
        }

        for (Dependency d : project.getDependencyManagement().getDependencies()) {
            if (SDK_GROUP_ID.equals(d.getGroupId()) &&
                    SDK_ARTIFACT_ID.equals(d.getArtifactId())) {
                return d;
            }
        }

        throw new MojoExecutionException(
                "Unable to find SDK artifact in dependencies or dependency management");
    }
}
