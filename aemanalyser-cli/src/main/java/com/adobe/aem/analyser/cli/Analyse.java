/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.cli;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.felix.configadmin.plugin.interpolation.StandaloneInterpolator;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;

import com.adobe.aem.analyser.AemAnalyser;
import com.adobe.aem.analyser.AemAnalyserResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "analyse", mixinStandardHelpOptions =  true,
    description = "Analyse one or more feature models",
    versionProvider = VersionProvider.class)
public class Analyse implements Callable<Integer> {
    @Option(names = {"-f", "--features"}, description = "Feature files to analyser")
    private File[] featureFiles;

    @Option(names = {"-a", "--analyser"}, description = "Analysers to execute")
    private Set<String> analysers;

    @Option(names = {"-i", "--interpolate"}, description = "Interpolate (substitute) variable placeholders")
    private boolean interpolate;

    @Option(names = {"-s", "--secret-dirs"}, description = "Directories where secrets files for interpolation can be found")
    private File[] interpolationSecretDirectories;

    @Override
    public Integer call() throws Exception {
        System.out.println("Analysing feature files: " + Arrays.toString(featureFiles));
        System.out.println("Analysers used: " + analysers);
        System.out.println();

        List<Feature> features = new ArrayList<>();
        for (File ff : featureFiles) {
            try (Reader r = new FileReader(ff)) {
                features.add(FeatureJSONReader.read(r, ff.getName()));
            }
        }

        if (interpolate) {
            interpolatePlaceholders(features);
        }

        AemAnalyser analyser = new CliAnalyser();
        analyser.setIncludedTasks(analysers);
        AemAnalyserResult result = analyser.analyse(features);

        if (!result.getErrors().isEmpty()) {
            System.out.println("Errors:");
            result.getErrors().forEach(e -> System.out.println(e));
        }

        if (!result.getWarnings().isEmpty()) {
            System.out.println("\nWarnings:");
            result.getWarnings().forEach(w -> System.out.println(w));
        }

        return result.hasErrors() ? 1 : 0;
    }

    void interpolatePlaceholders(List<Feature> features) {
        for (Feature f : features) {
            interpolatePlaceholders(f);
        }
    }

    private void interpolatePlaceholders(Feature f) {
        Map<String, String> fprops = f.getFrameworkProperties();
        StandaloneInterpolator interpolator = new StandaloneInterpolator(fprops, interpolationSecretDirectories);

        for (Configuration c : f.getConfigurations()) {
            // We cannot directly work on the result of c.getProperties() as that causes a concurrent modification error
            Dictionary<String, Object> dict = c.getConfigurationProperties();
            interpolator.interpolate(c.getPid(), dict);

            // Persist the changes in the configuration
            for (Enumeration<String> e = dict.keys(); e.hasMoreElements(); ) {
                String key = e.nextElement();

                switch (key) {
                case Constants.SERVICE_PID:
                case ConfigurationAdmin.SERVICE_FACTORYPID:
                    // Don't allow the changing of these keys
                    continue;
                }

                c.getProperties().put(key, dict.get(key));
            }
        }
    }
}
