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

import static java.util.Collections.singletonMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.Analyser;
import org.apache.sling.feature.analyser.AnalyserResult;
import org.apache.sling.feature.analyser.AnalyserResult.ArtifactReport;
import org.apache.sling.feature.analyser.AnalyserResult.ConfigurationReport;
import org.apache.sling.feature.analyser.AnalyserResult.ExtensionReport;
import org.apache.sling.feature.analyser.AnalyserResult.GlobalReport;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemAnalyser {

    /**
     * These tasks are executed by default on the final aggregates
     */
    public static final String DEFAULT_TASKS = "requirements-capabilities"
        + ",api-regions"
        + ",api-regions-check-order"
        + ",api-regions-crossfeature-dups"
        + ",api-regions-exportsimports"
        + ",repoinit"
        + ",region-deprecated-api";

    /**
     * These tasks are executed on the user aggregates (before the product is merged in)
     */
    public static final String DEFAULT_USER_TASKS = "bundle-content"
        + ",bundle-resources"
        + ",bundle-nativecode"
        + ",artifact-rules"
        + ",configuration-api"
        + ",aem-env-var"
        + ",content-packages-validation";

    private static final String BUNDLE_ORIGINS = "content-package-origins";
    private static final String CONFIGURATION_ORIGINS = Configuration.CONFIGURATOR_PREFIX.concat(BUNDLE_ORIGINS);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ArtifactProvider artifactProvider;

    private FeatureProvider featureProvider;

    private Set<String> includedTasks;

    private Set<String> includedUserTasks;

    private Map<String, Map<String, String>> taskConfigurations;

    public AemAnalyser() {
        this.setIncludedTasks(new LinkedHashSet<>(Arrays.asList(DEFAULT_TASKS.split(","))));
        this.setIncludedUserTasks(new LinkedHashSet<>(Arrays.asList(DEFAULT_USER_TASKS.split(","))));
        this.setTaskConfigurations(new HashMap<>());
    }

    /**
     * @return the includedTasks
     */
    public Set<String> getIncludedTasks() {
        return includedTasks;
    }

    /**
     * @return the included user tasks
     */
    public Set<String> getIncludedUserTasks() {
        return includedUserTasks;
    }

    /**
     * @param includedTasks the includedTasks to set
     */
    public void setIncludedTasks(final Set<String> includedTasks) {
        this.includedTasks = includedTasks;
    }

    /**
     * @param includedTasks the includedTasks to set
     */
    public void setIncludedUserTasks(final Set<String> includedTasks) {
        this.includedUserTasks = includedTasks;
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
        config.computeIfAbsent("api-regions-crossfeature-dups", (key) -> apiRegionsCrossfeatureDupsDefaults());
        config.computeIfAbsent("content-packages-validation", (key) -> contentPackagesValidationDefaults());
        config.computeIfAbsent("api-regions-check-order", (key) -> apiRegionsCheckOrderDefaults());
    }

    private Map<String, String> apiRegionsCrossfeatureDupsDefaults() {
        final Map<String, String> config = new HashMap<>();
        config.put("regions", "global,com.adobe.aem.deprecated");
        config.put("definingFeatures", "com.adobe.aem:aem-sdk-api:slingosgifeature:*");
        config.put("warningPackages", "*");
        return config;
    }
    
    private Map<String, String> contentPackagesValidationDefaults() {
        return singletonMap("disabled-validators", "jackrabbit-nodetypes");
    }
    
    private Map<String, String> apiRegionsCheckOrderDefaults() {
        return singletonMap("order", "global,com.adobe.aem.deprecated,com.adobe.aem.internal");
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

    private Analyser createAnalyser(final Scanner scanner, final Set<String> tasks, final Map<String, Map<String, String>> configs) throws IOException {
        logger.debug("Setting up user analyser with task configurations = {}, included tasks = {}", configs, tasks);

        final Analyser analyser = new Analyser(scanner, configs, tasks, null);
        logger.debug("Analyser successfully set up : {}", analyser);

        return analyser;
    }

    protected boolean checkFinalClassifier(final String classifier) {
        return KEYS.contains(classifier);
    }

    protected boolean checkUserClassifier(final String classifier) {
        return classifier != null && classifier.startsWith("user-") && KEYS.contains(classifier.substring(5));
    }

    public AemAnalyserResult analyse(final Collection<Feature> features) throws Exception {
        final AemAnalyserResult result = new AemAnalyserResult();

        final Scanner scanner = this.createScanner();
        final Analyser userAnalyser = this.createAnalyser(scanner, this.getIncludedUserTasks(), this.getTaskConfigurations());
        final Analyser finalAnalyser = this.createAnalyser(scanner, this.getIncludedTasks(), this.getTaskConfigurations());

        final Map<String, List<String>> featureErrors = new LinkedHashMap<>();
        final Map<String, List<String>> featureWarnings = new LinkedHashMap<>();

        for (final Feature f : features) {
            final String classifier = f.getId().getClassifier();
            String msgKey = null;
            Analyser analyser = null;
            if ( checkFinalClassifier(classifier) ) {
                msgKey = classifier;
                analyser = finalAnalyser;
            } else if ( checkUserClassifier(classifier) ) {
                msgKey = classifier.substring(5);
                analyser = userAnalyser;
            }
            if ( analyser == null ) {
                this.logger.info("Skipping unused feature {}", f.getId());
                continue;
            }
            final AnalyserResult r = analyser.analyse(f, null, this.featureProvider);

            // report errors
            for(final GlobalReport report : r.getGlobalErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(report.toString());
            }
            for(final ArtifactReport report : r.getArtifactErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getArtifactMessage(f, report));
            }
            for(final ExtensionReport report : r.getExtensionErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(report.toString());
            }
            for(final ConfigurationReport report : r.getConfigurationErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getConfigurationMessage(f, report));
            }

            // report warnings
            for(final GlobalReport report : r.getGlobalWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(report.toString());
            }
            for(final ArtifactReport report : r.getArtifactWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getArtifactMessage(f, report));
            }
            for(final ExtensionReport report : r.getExtensionWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(report.toString());
            }
            for(final ConfigurationReport report : r.getConfigurationWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getConfigurationMessage(f, report));
            }
        }

        logOutput(result.getErrors(), featureErrors, "errors");
        logOutput(result.getWarnings(), featureWarnings, "warnings");

        return result;
    }

    private String getConfigurationMessage(final Feature f, final ConfigurationReport report) {
        final Object val = report.getKey().getProperties().get(CONFIGURATION_ORIGINS);
        if ( val != null ) {
            return report.toString().concat(" (").concat(val.toString()).concat(")");
        }
        return report.toString();
    }

    private String getArtifactMessage(final Feature f, final ArtifactReport report) {
        Artifact artifact = f.getBundles().getExact(report.getKey());
        if ( artifact == null ) {
            for(final Extension ext : f.getExtensions()) {
                if ( ext.getType() == ExtensionType.ARTIFACTS ) {
                    for(final Artifact c : ext.getArtifacts()) {
                        if ( c.getId().equals(report.getKey())) {
                            artifact = c;
                            break;
                        }
                    }
                }
            }
        }
        if ( artifact != null ) {
            final Object val = artifact.getMetadata().get(BUNDLE_ORIGINS);
            if ( val != null ) {
                return report.toString().concat(" (").concat(val.toString()).concat(")");
            }
        }
        return report.toString();
    }

    private static final String KEY_AUTHOR_AND_PUBLISH = "author and publish";
    private static final String KEY_AUTHOR = "aggregated-author";
    private static final String KEY_PUBLISH = "aggregated-publish";
    private static final String PREFIX = "aggregated-";
    private static final String PREFIX_AUTHOR = "aggregated-author.";
    private static final String PREFIX_PUBLISH = "aggregated-publish.";

    private static final List<String> KEYS = new ArrayList<>();
    static {
        KEYS.add(KEY_AUTHOR_AND_PUBLISH);
        for(final String k : AemAnalyserUtil.USED_MODES) {
            KEYS.add(PREFIX.concat(k));
        }
    }

    /**
     * Either directly return the messages for the tier, like author - or if that doesn't exist,
     * then dev/prod/stage exists. Return the common set of messages from those instead.
     * @return
     */
    private List<String> getTierMessages(final Map<String, List<String>> messages, final String tier) {
        List<String> msgs = messages.get(tier);
        if ( msgs == null ) {
            msgs = new ArrayList<>();
            msgs.addAll(messages.getOrDefault(tier.concat(".dev"), Collections.emptyList()));
            msgs.retainAll(messages.getOrDefault(tier.concat(".stage"), Collections.emptyList()));
            msgs.retainAll(messages.getOrDefault(tier.concat(".prod"), Collections.emptyList()));
            messages.put(tier, msgs);
        }
        return msgs;
    }

    protected void logOutput(final List<String> output, final Map<String, List<String>> messages, final String type) {
        // clean up environment specific messages
        final List<String> authorMsgs = getTierMessages(messages, KEY_AUTHOR);
        final List<String> publishMsgs = getTierMessages(messages, KEY_PUBLISH);

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
    }
}
