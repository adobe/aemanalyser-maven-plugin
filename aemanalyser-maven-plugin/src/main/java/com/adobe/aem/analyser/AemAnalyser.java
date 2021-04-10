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
package com.adobe.aem.analyser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.Analyser;
import org.apache.sling.feature.analyser.AnalyserResult;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemAnalyser {

    public static final String DEFAULT_TASKS = "requirements-capabilities,"
    + "bundle-content,"
    + "bundle-resources,"
    + "bundle-nativecode,"
    + "api-regions,"
    + "api-regions-check-order,"
    + "api-regions-crossfeature-dups,"
    + "api-regions-exportsimports,"
//        + "repoinit," disable until SLING-10215 is fixed
    + "configuration-api";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ArtifactProvider artifactProvider;

    private Set<String> includedTasks;

    private Map<String, Map<String, String>> taskConfigurations;

    public AemAnalyser() {
        this.setIncludedTasks(new LinkedHashSet<>(Arrays.asList(DEFAULT_TASKS.split(","))));
        this.setTaskConfigurations(new HashMap<>());
    }
    
    /**
     * @return the includedTasks
     */
    public Set<String> getIncludedTasks() {
        return includedTasks;
    }

    /**
     * @param includedTasks the includedTasks to set
     */
    public void setIncludedTasks(final Set<String> includedTasks) {
        this.includedTasks = includedTasks;
    }

    /**
     * @return the taskConfigurations
     */
    public Map<String, Map<String, String>> getTaskConfigurations() {
        return taskConfigurations;
    }

    /**
     * @param taskConfigurations the taskConfigurations to set
     */
    public void setTaskConfigurations(final Map<String, Map<String, String>> taskConfigurations) {
        this.taskConfigurations = taskConfigurations;
        this.applyDefaultTaskConfigurations();
    }

    private void applyDefaultTaskConfigurations() {
        Map<String, Map<String, String>> config = this.getTaskConfigurations();
    
        // Set default task configuration
        if (!config.containsKey("api-regions-crossfeature-dups")) {
            final Map<String, String> cfd = new HashMap<>();
            cfd.put("regions", "global,com.adobe.aem.deprecated");
            cfd.put("definingFeatures", "com.adobe.aem:aem-sdk-api:slingosgifeature:*");
            cfd.put("warningPackages", "*");
            config.put("api-regions-crossfeature-dups", cfd);
        }
    
        if (!config.containsKey("api-regions-check-order")) {
            final Map<String, String> ord = new HashMap<>();
            ord.put("order", "global,com.adobe.aem.deprecated,com.adobe.aem.internal");
            config.put("api-regions-check-order", ord);
        }    
    }

    /**
     * @return the artifactProvider
     */
    public ArtifactProvider getArtifactProvider() {
        return artifactProvider;
    }

    /**
     * @param artifactProvider the artifactProvider to set
     */
    public void setArtifactProvider(final ArtifactProvider artifactProvider) {
        this.artifactProvider = artifactProvider;
    }

    private Scanner createScanner() throws IOException {
        logger.debug("Setting up scanner");
        final Scanner scanner = new Scanner(this.getArtifactProvider());
        logger.debug("Scanner successfully set up : {}", scanner);

        return scanner;
    }

    private Analyser createAnalyser() throws IOException {
        final Scanner scanner = this.createScanner();

        logger.debug("Setting up analyser with task configurations = {}, included tasks = {}", this.getTaskConfigurations(), this.getIncludedTasks());

        final Analyser analyser = new Analyser(scanner, this.getTaskConfigurations(), this.getIncludedTasks(), null);
        logger.debug("Analyser successfully set up : {}", analyser);

        return analyser;
    }

    public AemAnalyserResult analyse(final Collection<Feature> features) throws Exception {
        final AemAnalyserResult result = new AemAnalyserResult();

        final Analyser analyser = this.createAnalyser();

        final Map<ArtifactId, AnalyserResult> featureResults = new LinkedHashMap<>();
        for (final Feature f : features) {
            featureResults.put(f.getId(), analyser.analyse(f, null, null));
        }

        logOutput(result, featureResults);

        return result;
    }

    private static final ArtifactId COMMON_ID = ArtifactId.parse("__:__:1");

    private Map<ArtifactId, List<String>> compactErrors(final Map<ArtifactId, AnalyserResult> results, final boolean compactErrors) {
        final Map<ArtifactId, List<String>> errors = new LinkedHashMap<>();

        List<String> commonMessages = null;
        for(final Map.Entry<ArtifactId, AnalyserResult> entry : results.entrySet()) {
            final List<String> msgs = new ArrayList<>(compactErrors ? entry.getValue().getErrors() : entry.getValue().getWarnings());
            if ( commonMessages == null ) {
                commonMessages = msgs;
                errors.put(COMMON_ID, commonMessages);
            } else {
                commonMessages.retainAll(msgs);
                errors.put(entry.getKey(), msgs);
            }
        }
        for(final List<String> msgs : errors.values()) {
            if ( msgs != commonMessages) {
                msgs.removeAll(commonMessages);
            }
        }

        return errors;
    }

    private void logOutput(final AemAnalyserResult result, final Map<ArtifactId, AnalyserResult> featureResults) {
        final Map<ArtifactId, List<String>> warnings = compactErrors(featureResults, false);
        
        for(final Map.Entry<ArtifactId, List<String>> entry : warnings.entrySet()) {
            if ( !entry.getValue().isEmpty()) {
                if ( entry.getKey() == COMMON_ID ) {
                    result.getErrors().add("Analyser detected the following warnings:");
                    for(final String msg : entry.getValue() ) {
                        result.getWarnings().add(msg);
                    }
                } else {
                    result.getErrors().add("Analyser detected the following warnings in feature '" + entry.getKey().toMvnId() + "'.");
                    for(final String msg : entry.getValue() ) {
                        result.getWarnings().add(msg);
                    }
                }
            }
        }

        final Map<ArtifactId, List<String>> errors = compactErrors(featureResults, true);
        
        for(final Map.Entry<ArtifactId, List<String>> entry : errors.entrySet()) {
            if ( !entry.getValue().isEmpty()) {
                if ( entry.getKey() == COMMON_ID ) {
                    result.getErrors().add("Analyser detected the following errors:");
                    for(final String msg : entry.getValue() ) {
                        result.getErrors().add(msg);
                    }
                } else {
                    result.getErrors().add("Analyser detected the following errors in feature '" + entry.getKey().toMvnId() + "'.");
                    for(final String msg : entry.getValue() ) {
                        result.getErrors().add(msg);
                    }
                }
            }
        }
    }
}
