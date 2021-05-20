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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureBuilder;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.builder.MergeHandler;
import org.apache.sling.feature.builder.PostProcessHandler;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create all the aggregates
 */
public class AemAggregator {

    private static final String SDK_FEATUREMODEL_AUTHOR_CLASSIFIER = "aem-author-sdk";
    private static final String SDK_FEATUREMODEL_PUBLISH_CLASSIFIER = "aem-publish-sdk";
    private static final String FEATUREMODEL_TYPE = "slingosgifeature";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private File featureOutputDirectory;

    private ArtifactProvider artifactProvider;

    private FeatureProvider featureProvider;

    private ArtifactId projectId;

    private ArtifactId sdkId;

    private List<ArtifactId> addOnIds;

    /**
     * @return the artifactProvider
     */
    public ArtifactProvider getArtifactProvider() {
        return artifactProvider;
    }

    /**
     * @param artifactProvider the artifactProvider to set
     */
    public void setArtifactProvider(ArtifactProvider artifactProvider) {
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

    /**
     * @return the projectId
     */
    public ArtifactId getProjectId() {
        return projectId;
    }

    /**
     * @param projectId the projectId to set
     */
    public void setProjectId(ArtifactId projectId) {
        this.projectId = projectId;
    }

    /**
     * @return the featureOutputDirectory
     */
    public File getFeatureOutputDirectory() {
        return featureOutputDirectory;
    }

    /**
     * @param featureOutputDirectory the featureOutputDirectory to set
     */
    public void setFeatureOutputDirectory(File featureOutputDirectory) {
        this.featureOutputDirectory = featureOutputDirectory;
    }

    /**
     * @return the sdkId
     */
    public ArtifactId getSdkId() {
        return sdkId;
    }

    /**
     * @param sdkId the sdkId to set
     */
    public void setSdkId(final ArtifactId sdkId) {
        this.sdkId = sdkId;
    }

    /**
     * @return the addOnIds
     */
    public List<ArtifactId> getAddOnIds() {
        return addOnIds;
    }

    /**
     * @param addOnIds the addOnIds to set
     */
    public void setAddOnIds(final List<ArtifactId> addOnIds) {
        this.addOnIds = addOnIds;
    }

    public enum Mode {
        USER,
        PRODUCT,
        FINAL
    }

    /**
     * Create the aggregates and return the final one
     * @return The list of final aggregates
     * @throws IOException If something goes wrong
     */
    public List<Feature> aggregate() throws IOException {
        // read all features
        final Map<String, Feature> projectFeatures = readFeatures();

        // Produce the user aggregates
        final Map<String, List<Feature>> userAggregates = getUserAggregates(projectFeatures);

        this.aggregate(userAggregates, Mode.USER, projectFeatures);

        // Produce the product aggregates
        final Map<String, List<Feature>> productAggregates = getProductAggregates();

        this.aggregate(productAggregates, Mode.PRODUCT, projectFeatures);

        // Produce the final aggregates
        final Map<String, List<Feature>> finalAggregates = getFinalAggregates(userAggregates, projectFeatures);

        return this.aggregate(finalAggregates, Mode.FINAL, projectFeatures);
    }

    private Map<String, Feature> readFeatures() throws IOException {
        final Map<String, Feature> result = new HashMap<>();
        for(final File f : this.getFeatureOutputDirectory().listFiles()) {
            if ( f.getName().endsWith(".json") && !f.getName().startsWith(".") ) {
                logger.info("Reading feature {}...", f.getName());
                try (final Reader reader = new FileReader(f)) {
                    final Feature feature = FeatureJSONReader.read(reader, f.getName());
                    result.put(f.getName(), feature);
                }
            }
        }
        return result;
    }

    private Properties getRunmodeMappings() throws IOException {
        File mappingFile = new File(this.featureOutputDirectory, "runmode.mapping");
        if (!mappingFile.isFile())
            throw new IOException("File generated by content package to feature model converter not found: " + mappingFile);

        Properties p = new Properties();
        try (InputStream is = new FileInputStream(mappingFile)) {
            p.load(is);
        }
        return p;
    }

    Map<String, List<Feature>> getUserAggregates(final Map<String, Feature> projectFeatures)
    throws IOException {
        // get run modes from converter output
        final Properties runmodes = getRunmodeMappings();

        final Map<String, List<Feature>> aggregates = new HashMap<>();

        Map<String, Set<String>> toCreate = getUserAggregatesToCreate(runmodes);
        for (final Map.Entry<String, Set<String>> entry : toCreate.entrySet()) {
            final String name = "user-aggregated-".concat(entry.getKey());

            final List<Feature> list = aggregates.computeIfAbsent(name, n -> new ArrayList<>());
            entry.getValue().forEach(n -> list.add(projectFeatures.get(n)));
        }

        return aggregates;
    }

    Map<String, Set<String>> getUserAggregatesToCreate(final Properties runmodes) throws IOException {
        try {
            return AemAnalyserUtil.getAggregates(runmodes);
        } catch ( final IllegalArgumentException iae) {
            throw new IOException(iae.getMessage());
        }
    }

    Map<String, List<Feature>> getProductAggregates() throws IOException {
        final Map<String, List<Feature>> aggregates = new HashMap<>();

        for (boolean isAuthor : new boolean [] {true, false}) {
            final String aggClassifier = getProductAggregateName(isAuthor);

            final List<Feature> list = aggregates.computeIfAbsent(aggClassifier, n -> new ArrayList<>());
            final Feature sdkFeature = getFeatureProvider().provide(this.getSdkId()
                    .changeClassifier(isAuthor ? SDK_FEATUREMODEL_AUTHOR_CLASSIFIER : SDK_FEATUREMODEL_PUBLISH_CLASSIFIER)
                    .changeType(FEATUREMODEL_TYPE));
            if ( sdkFeature == null ) {
                throw new IOException("Unable to find SDK feature for " + this.getSdkId().toMvnId());
            }
            list.add(sdkFeature);
            if ( this.getAddOnIds() != null ) {
                for(final ArtifactId id : this.getAddOnIds()) {
                    final Feature feature = getFeatureProvider().provide(id.changeType(FEATUREMODEL_TYPE));
                    if ( feature == null ) {
                        throw new IOException("Unable to find addon feature for " + id.toMvnId());
                    }
                    list.add(feature);
                }
            }
        }

        return aggregates;
    }

    Map<String, List<Feature>> getFinalAggregates(final Map<String, List<Feature>> userAggregate,
            final Map<String, Feature> projectFeatures) throws IOException {
        final Map<String, List<Feature>> aggregates = new HashMap<>();

        for (final String name : userAggregate.keySet()) {
            final boolean isAuthor = name.startsWith("user-aggregated-author");

            final String classifier = name.substring(5);
            final List<Feature> list = aggregates.computeIfAbsent(classifier, n -> new ArrayList<>());

            list.add(projectFeatures.get(getProductAggregateName(isAuthor)));
            list.add(projectFeatures.get(name));
        }

        return aggregates;
    }

    private String getProductAggregateName(final boolean author) {
        return "product-aggregated-" + (author ? "author" : "publish");
    }


    List<Feature> aggregate(final Map<String, List<Feature>> aggregates, final Mode mode,
        final Map<String, Feature> projectFeatures) throws IOException {

        final List<Feature> result = new ArrayList<>();
        for (final Map.Entry<String, List<Feature>> aggregate : aggregates.entrySet()) {

            logger.info("Building aggregate {}...", aggregate.getKey());

            final BuilderContext builderContext = new BuilderContext(new FeatureProvider(){

                @Override
                public Feature provide(final ArtifactId id) {
                    // check in selection
                    for (final Feature feat : projectFeatures.values()) {
                        if (feat.getId().equals(id)) {
                            return feat;
                        }
                    }
                    return getFeatureProvider().provide(id);
                }
            });
            builderContext.setArtifactProvider(getArtifactProvider());

            builderContext.addMergeExtensions(StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                    ServiceLoader.load(MergeHandler.class).iterator(), Spliterator.ORDERED),
                    false).toArray(MergeHandler[]::new))
                .addPostProcessExtensions(StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                    ServiceLoader.load(PostProcessHandler.class).iterator(), Spliterator.ORDERED),
                    false).toArray(PostProcessHandler[]::new));

            // specific rules for the different aggregates
            if ( mode == Mode.USER || mode == Mode.PRODUCT ) {
                builderContext.addArtifactsOverride(ArtifactId.parse("*:*:HIGHEST"));

            } else if ( mode == Mode.FINAL) {
                builderContext.addArtifactsOverride(ArtifactId.parse("com.adobe.cq:core.wcm.components.core:FIRST"));
                builderContext.addArtifactsOverride(ArtifactId.parse("com.adobe.cq:core.wcm.components.extensions.amp:FIRST"));
                builderContext.addArtifactsOverride(ArtifactId.parse("org.apache.sling:org.apache.sling.models.impl:FIRST"));
                builderContext.addArtifactsOverride(ArtifactId.parse("*:core.wcm.components.content:zip:*:FIRST"));
                builderContext.addArtifactsOverride(ArtifactId.parse("*:core.wcm.components.extensions.amp.content:zip:*:FIRST"));
                builderContext.addArtifactsOverride(ArtifactId.parse("*:*:jar:*:ALL"));

            }
            builderContext.addConfigsOverrides(Collections.singletonMap("*", "MERGE_LATEST"));

            final ArtifactId newFeatureID = this.getProjectId().changeClassifier(aggregate.getKey()).changeType(FEATUREMODEL_TYPE);

            final Feature feature = FeatureBuilder.assemble(newFeatureID, builderContext,
                  aggregate.getValue().toArray(new Feature[aggregate.getValue().size()]));

            try ( final Writer writer = new FileWriter(new File(this.getFeatureOutputDirectory(), aggregate.getKey().concat(".json")))) {
                FeatureJSONWriter.write(writer, feature);
            }
            projectFeatures.put(aggregate.getKey(), feature);

            result.add(feature);
        }

        return result;
    }
}
