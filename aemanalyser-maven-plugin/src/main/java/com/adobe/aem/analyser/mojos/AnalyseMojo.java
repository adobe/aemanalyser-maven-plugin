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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.sling.feature.maven.mojos.AnalyseFeaturesMojo;
import org.apache.sling.feature.maven.mojos.Scan;

import java.util.Collections;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.TEST)
public class AnalyseMojo extends AnalyseFeaturesMojo {
    boolean unitTestMode = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Dependency fwDep = new Dependency();
        fwDep.setGroupId("org.apache.felix");
        fwDep.setArtifactId("org.apache.felix.framework");
        fwDep.setVersion("6.0.1"); // TODO Where do we get this from ? Some property set in the parent pom?
        setParameter(this, "framework", fwDep);

        Scan s = new Scan();
        s.setIncludeClassifier("aggregated");
        s.setIncludeTask("requirements-capabilities"); // TODO maybe make this configurable
        setParameter(this, "scans", Collections.singletonList(s));

        if (unitTestMode)
            return;

        super.execute();
    }
}
