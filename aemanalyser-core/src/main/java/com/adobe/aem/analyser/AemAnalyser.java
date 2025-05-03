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
import org.apache.sling.feature.ArtifactId;
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

import com.adobe.aem.analyser.result.AemAnalyserAnnotation;
import com.adobe.aem.analyser.result.AemAnalyserResult;
import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.ArtifactsFile;
import com.adobe.aem.project.model.ConfigurationFile;
import com.adobe.aem.project.model.FeatureParticipantResolver;

public class AemAnalyser {

    /**
     * These tasks are executed by default on the final aggregates
     */
    public static final String DEFAULT_TASKS = "requirements-capabilities"
        + ",api-regions"
        + ",api-regions-check-order"
        + ",api-regions-crossfeature-dups"
        + ",api-regions-exportsimports"
        + ",configuration-api"
        + ",region-deprecated-api";

    /**
     * These tasks are executed on the user aggregates (before the product is merged in)
     */
    public static final String DEFAULT_USER_TASKS =
          "bundle-resources"
        + ",bundle-nativecode"
        + ",bundle-unversioned-packages"
	    + ",artifact-rules"
        + ",aem-env-var"
        + ",repoinit"
        + ",content-packages-validation"
        + ",configurations-basic"
        + ",aem-provider-type"
        + ",cors-configuration-validator";

    private static final String CONTENT_PACKAGE_ORIGINS = "content-package-origins";
    private static final String CONFIGURATION_ORIGINS = Configuration.CONFIGURATOR_PREFIX.concat(CONTENT_PACKAGE_ORIGINS);
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ArtifactProvider artifactProvider;

    private FeatureProvider featureProvider;

    private Set<String> includedTasks;

    private Set<String> includedUserTasks;

    private Map<String, Map<String, String>> taskConfigurations;

    private FeatureParticipantResolver fpResolver;

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

    public void setFeatureParticipantResolver(final FeatureParticipantResolver resolver) {
        this.fpResolver = resolver;
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
        config.computeIfAbsent("region-deprecated-api", (key) -> apiRegionsDeprecationDefaults());
    }

    private Map<String, String> apiRegionsCrossfeatureDupsDefaults() {
        final Map<String, String> config = new HashMap<>();
        config.put("regions", "global,com.adobe.aem.deprecated");
        config.put("definingFeatures", "com.adobe.aem:aem-sdk-api:slingosgifeature:*");
        config.put("warningPackages", "*");
        return config;
    }

    private Map<String, String> contentPackagesValidationDefaults() {
        return singletonMap("enabled-validators", "jackrabbit-docviewparser");
    }

    private Map<String, String> apiRegionsCheckOrderDefaults() {
        return singletonMap("order", "global,com.adobe.aem.deprecated,com.adobe.aem.internal");
    }

    private Map<String, String> apiRegionsDeprecationDefaults() {
        return singletonMap("regions", "global,com.adobe.aem.deprecated");
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

        final Map<String, List<AemAnalyserAnnotation>> featureErrors = new LinkedHashMap<>();
        final Map<String, List<AemAnalyserAnnotation>> featureWarnings = new LinkedHashMap<>();

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
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(new AemAnalyserAnnotation(report.toString()));
            }
            for(final ArtifactReport report : r.getArtifactErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getArtifactAnnotation(f, report));
            }
            for(final ExtensionReport report : r.getExtensionErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getExtensionAnnotation(f, report));
            }
            for(final ConfigurationReport report : r.getConfigurationErrors()) {
                featureErrors.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getConfigurationAnnotation(f, report));
            }

            // report warnings
            for(final GlobalReport report : r.getGlobalWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(new AemAnalyserAnnotation(report.toString()));
            }
            for(final ArtifactReport report : r.getArtifactWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getArtifactAnnotation(f, report));
            }
            for(final ExtensionReport report : r.getExtensionWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getExtensionAnnotation(f, report));
            }
            for(final ConfigurationReport report : r.getConfigurationWarnings()) {
                featureWarnings.computeIfAbsent(msgKey, key -> new ArrayList<>()).add(getConfigurationAnnotation(f, report));
            }
        }

        logOutput(result.getErrors(), featureErrors, "errors");
        logOutput(result.getWarnings(), featureWarnings, "warnings");

        return result;
    }

    private AemAnalyserAnnotation getExtensionAnnotation(final Feature f, final ExtensionReport report) {
        if ( report.getKey().equals(Extension.EXTENSION_NAME_REPOINIT) ) {
            // TODO how do we find the source?
        }
        return new AemAnalyserAnnotation(report.toString());
    }

    private AemAnalyserAnnotation getConfigurationAnnotation(final Feature f, final ConfigurationReport report) {
        final Object val = report.getKey().getProperties().get(CONFIGURATION_ORIGINS);
        if ( val != null ) {
            if ( this.fpResolver != null ) {
                try {
                    final ArtifactId originId = ArtifactId.parse(val.toString()).changeType(null).changeClassifier(null);
                    ServiceType originServiceType = null;
                    if ( f.getId().getClassifier() != null && f.getId().getClassifier().contains(ServiceType.AUTHOR.asString())) {
                        originServiceType = ServiceType.AUTHOR;
                    } else if ( f.getId().getClassifier() != null && f.getId().getClassifier().contains(ServiceType.PUBLISH.asString())) {
                        originServiceType = ServiceType.PUBLISH;
                    }

                    final ConfigurationFile source = this.fpResolver.getSource(report.getKey(), originServiceType, originId);
                    if ( source != null ) {
                        return new AemAnalyserAnnotation(source.getSource(), report.toString());
                    }

                } catch ( final IllegalArgumentException iae) {
                    // ignore
                }
            }
            return new AemAnalyserAnnotation(report.toString().concat(" (").concat(val.toString()).concat(")"));
        }
        return new AemAnalyserAnnotation(report.toString());
    }

    private AemAnalyserAnnotation getArtifactAnnotation(final Feature f, final ArtifactReport report) {
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
            final Object val = artifact.getMetadata().get(CONTENT_PACKAGE_ORIGINS);
            if ( val != null ) {
                if ( this.fpResolver != null ) {
                    try {
                        final ArtifactId originId = ArtifactId.parse(val.toString()).changeType(null).changeClassifier(null);
                        ServiceType originServiceType = null;
                        if ( f.getId().getClassifier() != null && f.getId().getClassifier().contains(ServiceType.AUTHOR.asString())) {
                            originServiceType = ServiceType.AUTHOR;
                        } else if ( f.getId().getClassifier() != null && f.getId().getClassifier().contains(ServiceType.PUBLISH.asString())) {
                            originServiceType = ServiceType.PUBLISH;
                        }
    
                        final ArtifactsFile source = this.fpResolver.getSource(report.getKey(), originServiceType, originId);
                        if ( source != null ) {
                            return new AemAnalyserAnnotation(source.getSource(), report.toString());
                        }
    
                    } catch ( final IllegalArgumentException iae) {
                        // ignore
                    }
                }
                return new AemAnalyserAnnotation(report.toString().concat(" (").concat(val.toString()).concat(")"));
            }
        }
        return new AemAnalyserAnnotation(report.toString());
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
        for(final String k : AemAnalyserUtil.ALL_USED_MODES) {
            KEYS.add(PREFIX.concat(k));
        }
    }

    /**
     * Either directly return the messages for the tier, like author - or if that doesn't exist,
     * then dev/prod/stage exists. Return the common set of messages from those instead.
     * @return
     */
    private List<AemAnalyserAnnotation> getTierMessages(final Map<String, List<AemAnalyserAnnotation>> messages, final String tier) {
        List<AemAnalyserAnnotation> msgs = messages.get(tier);
        if ( msgs == null ) {
            for(final EnvironmentType env : EnvironmentType.values()) {
                final String key = tier.concat(".").concat(env.asString());
                if ( msgs == null ) {
                    msgs = new ArrayList<>();
                    msgs.addAll(messages.getOrDefault(key, Collections.emptyList()));
                } else {
                    msgs.retainAll(messages.getOrDefault(key, Collections.emptyList()));
                }
            }
            messages.put(tier, msgs);
        }
        return msgs;
    }

    protected void logOutput(final List<AemAnalyserAnnotation> output, final Map<String, List<AemAnalyserAnnotation>> messages, final String type) {
        // clean up environment specific messages
        final List<AemAnalyserAnnotation> authorMsgs = getTierMessages(messages, KEY_AUTHOR);
        final List<AemAnalyserAnnotation> publishMsgs = getTierMessages(messages, KEY_PUBLISH);

        for(final Map.Entry<String, List<AemAnalyserAnnotation>> entry : messages.entrySet()) {
            if ( entry.getKey().startsWith(PREFIX_AUTHOR) ) {
                entry.getValue().removeAll(authorMsgs);
            }
            if ( entry.getKey().startsWith(PREFIX_PUBLISH) ) {
                entry.getValue().removeAll(publishMsgs);
            }
        }

        // author and publish
        final List<AemAnalyserAnnotation> list = new ArrayList<>();
        list.addAll(authorMsgs);
        list.retainAll(publishMsgs);
        if ( !list.isEmpty() ) {
            messages.put(KEY_AUTHOR_AND_PUBLISH, list);
            authorMsgs.removeAll(list);
            publishMsgs.removeAll(list);
        }

        // log default classifiers
        for(final String k : KEYS) {
            final List<AemAnalyserAnnotation> m = messages.get(k);
            if ( m!= null && !m.isEmpty() ) {
                final String id = k.startsWith(PREFIX) ? k.substring(PREFIX.length()) : k;
                output.add(new AemAnalyserAnnotation("The analyser found the following ".concat(type).concat(" for ").concat(id).concat(" : ")));
                m.stream().forEach(t -> output.add(t));
            }
        }
    }
}
