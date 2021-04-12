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

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.Analyser;
import org.apache.sling.feature.analyser.AnalyserResult;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;
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

    private FeatureProvider featureProvider;
    
    private Set<String> includedTasks;

    private Map<String, Map<String, String>> taskConfigurations;

    private boolean outputAllClassifiers = true;
    
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

    /**
     * @return the outputAllClassifiers
     */
    public boolean isOutputAllClassifiers() {
        return outputAllClassifiers;
    }

    /**
     * @param outputAllClassifiers the outputAllClassifiers to set
     */
    public void setOutputAllClassifiers(boolean outputAllClassifiers) {
        this.outputAllClassifiers = outputAllClassifiers;
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

    /**
     * @return the featureProvider
     */
    public FeatureProvider getFeatureProvider() {
        return featureProvider;
    }

    /**
     * @param featureProvider the featureProvider to set
     */
    public void setFeatureProvider(FeatureProvider featureProvider) {
        this.featureProvider = featureProvider;
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

        final Map<String, List<String>> featureErrors = new LinkedHashMap<>();
        final Map<String, List<String>> featureWarnings = new LinkedHashMap<>();

        for (final Feature f : features) {
            final AnalyserResult r = analyser.analyse(f, null, this.featureProvider);
            featureErrors.computeIfAbsent(f.getId().getClassifier(), key -> new ArrayList<>()).addAll(r.getErrors());
            featureWarnings.computeIfAbsent(f.getId().getClassifier(), key -> new ArrayList<>()).addAll(r.getWarnings());
        }

        logOutput(result.getErrors(), featureErrors, "errors");
        logOutput(result.getWarnings(), featureWarnings, "warnings");

        return result;
    }

    private static final String KEY_AUTHOR_AND_PUBLISH = "author and publish";
    private static final String KEY_AUTHOR = "aggregated-author";
    private static final String KEY_AUTHOR_DEV = "aggregated-author.dev";
    private static final String KEY_AUTHOR_STAGE = "aggregated-author.stage";
    private static final String KEY_AUTHOR_PROD = "aggregated-author.prod";
    private static final String KEY_PUBLISH = "aggregated-publish";
    private static final String KEY_PUBLISH_DEV = "aggregated-publish.dev";
    private static final String KEY_PUBLISH_STAGE = "aggregated-publish.stage";
    private static final String KEY_PUBLISH_PROD = "aggregated-publish.prod";
    private static final String PREFIX = "aggregated-";
    private static final String PREFIX_AUTHOR = "aggregated-author.";
    private static final String PREFIX_PUBLISH = "aggregated-publish.";

    private static final List<String> KEYS = Arrays.asList(KEY_AUTHOR_AND_PUBLISH, KEY_AUTHOR, KEY_PUBLISH,
        KEY_AUTHOR_DEV, KEY_AUTHOR_STAGE, KEY_AUTHOR_PROD, KEY_PUBLISH_DEV, KEY_PUBLISH_STAGE, KEY_PUBLISH_PROD);

    private void logOutput(final List<String> output, final Map<String, List<String>> messages, final String type) {
        // clean up environment specific messages
        final List<String> authorMsgs = messages.get(KEY_AUTHOR);
        final List<String> publishMsgs = messages.get(KEY_PUBLISH);

        for(final Map.Entry<String, List<String>> entry : messages.entrySet()) {
            if ( entry.getKey().startsWith(PREFIX_AUTHOR) ) {
                entry.getValue().removeAll(authorMsgs);
            }
            if ( entry.getKey().startsWith(PREFIX_PUBLISH) ) {
                entry.getValue().removeAll(publishMsgs);
            }
        }

        // author and publish
        final List<String> list = new ArrayList<>();
        list.addAll(authorMsgs);
        list.retainAll(publishMsgs);
        if ( !list.isEmpty() ) {
            messages.put(KEY_AUTHOR_AND_PUBLISH, list);
            authorMsgs.removeAll(list);
            publishMsgs.removeAll(list);
        }

        // log default classifiers
        for(final String k : KEYS) {
            final List<String> m = messages.get(k);
            if ( m!= null && !m.isEmpty() ) {
                final String id = k.startsWith(PREFIX) ? k.substring(PREFIX.length()) : k;
                output.add("The analyser found the following ".concat(type).concat(" for ").concat(id).concat(" : "));
                m.stream().forEach(t -> output.add(t));
            }
        }
        if ( this.isOutputAllClassifiers() ) {
            // log additional classifiers
            for(final Map.Entry<String, List<String>> entry : messages.entrySet()) {
                if ( !KEYS.contains(entry.getKey()) && !entry.getValue().isEmpty() ) {
                    final String id = entry.getKey().startsWith(PREFIX) ? entry.getKey().substring(PREFIX.length()) : entry.getKey();
                    output.add("The analyser found the following ".concat(type).concat(" for ").concat(id).concat(" : "));
                    entry.getValue().stream().forEach(t -> output.add(t));
                }
            }
        }
    }
}
