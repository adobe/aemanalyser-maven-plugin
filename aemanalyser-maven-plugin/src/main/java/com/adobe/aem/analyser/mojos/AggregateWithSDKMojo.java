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
    boolean unitTestMode = false;

    // Shadow this field for maven as we don't need to provide it from the pom.xml
    @Parameter(required = false)
    private List<Aggregate> aggregates;

    @Override
    public void execute() throws MojoExecutionException {
        setParameter(this, AggregateFeaturesMojo.class,
                "aggregates", getAggregates());

        if (unitTestMode)
            return;

        super.execute();
    }

    private List<Aggregate> getAggregates() {
        List<Aggregate> l = new ArrayList<>();

        Aggregate a = new Aggregate();
        a.classifier = "aggregated";
        Dependency d = new Dependency();
        d.setGroupId("com.day.cq"); // TODO
        d.setArtifactId("cq-quickstart"); // TODO
        d.setVersion("6.6.0-SNAPSHOT"); // TODO
        d.setType("slingosgifeature");
        d.setClassifier("aem-sdk");
        a.setIncludeArtifact(d);

        a.setFilesInclude("**/*.json");
        a.markAsComplete = true;
        a.artifactsOverrides = Collections.singletonList("*:*:LATEST");
        a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");
        l.add(a);

        return l;
    }
}
