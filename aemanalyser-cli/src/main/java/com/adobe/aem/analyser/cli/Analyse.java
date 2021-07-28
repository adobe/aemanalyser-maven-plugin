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
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;

import com.adobe.aem.analyser.AemAnalyser;
import com.adobe.aem.analyser.AemAnalyserResult;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "analyse", mixinStandardHelpOptions =  true,
    description = "Execute feature model analysers",
    versionProvider = VersionProvider.class)
public class Analyse implements Callable<Integer> {
    @Option(names = {"-f", "--features"}, description = "Feature files to analyser")
    private File[] featureFiles;

    @Option(names = {"-a", "--analyser"}, description = "Analysers to execute")
    private Set<String> analysers;

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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Analyse()).execute(args);
        System.exit(exitCode);
    }
}
