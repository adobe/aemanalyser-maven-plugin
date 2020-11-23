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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.maven.mojos.AnalyseFeaturesMojo;
import org.apache.sling.feature.maven.mojos.Scan;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.TEST)
public class AnalyseMojo extends AnalyseFeaturesMojo {
    boolean unitTestMode = false;

    @Parameter(defaultValue =
        "requirements-capabilities,"
        + "bundle-resources,"
        + "api-regions,"
        + "api-regions-check-order,"
        + "api-regions-dependencies,"
        + "api-regions-duplicates,"
        + "api-regions-crossfeature-dups,"
        + "api-regions-exportsimports",
        property = "includeTasks")
    List<String> includeTasks;

    @Parameter
    Map<String, Properties> taskConfiguration;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (taskConfiguration == null) {
            taskConfiguration = new HashMap<>();
        }

        // Set default task configuration
        if (!taskConfiguration.containsKey("api-regions-crossfeature-dups")) {
            Properties cfd = new Properties();
            cfd.setProperty("regions", "global,com.adobe.aem.deprecated");
            cfd.setProperty("warningPackages", "*");
            taskConfiguration.put("api-regions-crossfeature-dups", cfd);
        }

        if (!taskConfiguration.containsKey("api-regions-check-order")) {
            Properties ord = new Properties();
            ord.setProperty("order", "global,com.adobe.aem.deprecated,com.adobe.aem.internal");
            taskConfiguration.put("api-regions-check-order", ord);
        }

        Scan s = new Scan();
        @SuppressWarnings("unchecked")
        Set<String> aggregates =
                (Set<String>) project.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates");
        aggregates.forEach(s::setIncludeClassifier);

        for (String task : includeTasks) {
            s.setIncludeTask(task);
        }

        for (Map.Entry<String, Properties> entry : taskConfiguration.entrySet()) {
            Properties p = entry.getValue();
            Map<String, String> m = new HashMap<>();

            p.stringPropertyNames().forEach(n -> m.put(n, p.getProperty(n)));

            s.setTaskConfiguration(entry.getKey(), m);
        }

        setParameter(this, "scans", Collections.singletonList(s));

        if (unitTestMode)
            return;

        super.execute();
    }
}
