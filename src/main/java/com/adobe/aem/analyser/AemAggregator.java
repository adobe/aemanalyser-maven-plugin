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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureBuilder;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.builder.MergeHandler;
import org.apache.sling.feature.builder.PostProcessHandler;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.FileArtifactWriter;
import org.apache.sling.feature.extension.apiregions.api.artifacts.ArtifactRules;
import org.apache.sling.feature.extension.apiregions.api.artifacts.VersionRule;
import org.apache.sling.feature.extension.apiregions.api.config.ConfigurationApi;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.project.ServiceType;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Create all the aggregates
 */
public class AemAggregator {

    static final String FEATUREMODEL_TYPE = "slingosgifeature";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private File featureInputDirectory;

    private File featureOutputDirectory;

    private ArtifactProvider artifactProvider;

    private ArtifactsDeployer artifactsDeployer;

    private FeatureProvider featureProvider;

    private UserFeatureAggregator userFeatureAggregator;

    private ProductFeatureGenerator productFeatureGenerator;

    private ArtifactId projectId;

    private ArtifactId sdkId;

    private List<ArtifactId> addOnIds;

    private EnumSet<ServiceType> serviceTypes = EnumSet.allOf(ServiceType.class);

    private boolean enableDuplicateBundleHandling = false;

    /**
     * Is the special handling for duplicate bundles enabled?
     * @return {@code true} if enabled
     */
    public boolean isEnableDuplicateBundleHandling() {
        return enableDuplicateBundleHandling;
    }

    /**
     * Enable or disable the special handling for duplicate bundles
     * @param enableDuplicateBundleHandling {@code true} to enable
     */
    public void setEnableDuplicateBundleHandling(boolean enableDuplicateBundleHandling) {
        this.enableDuplicateBundleHandling = enableDuplicateBundleHandling;
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
    public void setArtifactProvider(ArtifactProvider artifactProvider) {
        this.artifactProvider = artifactProvider;
    }

    /**
     * @return the artifactsDeployer
     */
    public ArtifactsDeployer getArtifactsDeployer() {
        return artifactsDeployer;
    }

    /**
     * Sets an artifactDeployer
     *
     * <p>When a deployer is set it will be used to deploy the aggregated features.</p>
     *
     * @param artifactsDeployer the artifactsDeployer to set
     */
    public void setArtifactsDeployer(final ArtifactsDeployer artifactsDeployer) {
        this.artifactsDeployer = artifactsDeployer;
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
    public void setFeatureProvider(final FeatureProvider featureProvider) {
        this.featureProvider = featureProvider;
    }

    public UserFeatureAggregator getUserFeatureAggregator() {
        if ( userFeatureAggregator == null )
            return new RunmodeMappingUserFeatureAggregator(getFeatureInputDirectory());
        return userFeatureAggregator;
    }

    public void setUserFeatureAggregator(UserFeatureAggregator userFeatureAggregator) {
        this.userFeatureAggregator = userFeatureAggregator;
    }

    public ProductFeatureGenerator getProductFeatureGenerator() {
        if ( productFeatureGenerator == null )
            return new AemSdkProductFeatureGenerator(getFeatureProvider(), getSdkId(), getAddOnIds());
        return productFeatureGenerator;
    }

    public void setProductFeatureGenerator(ProductFeatureGenerator productFeatureGenerator) {
        this.productFeatureGenerator = productFeatureGenerator;
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
    public void setProjectId(final ArtifactId projectId) {
        this.projectId = projectId;
    }

    /**
     * Returns the feature input directory
     *
     * <p>If none is configured, uses the value of the {@code featureOutputDirectory} instead.</p>
     *
     * @return the feature input directory
     */
    public File getFeatureInputDirectory() {
        if ( featureInputDirectory == null )
            return featureOutputDirectory;
        return featureInputDirectory;
    }

    /**
     * @param featureInputDirectory the featureInputDirectory to set
     */
    public void setFeatureInputDirectory(File featureInputDirectory) {
        this.featureInputDirectory = featureInputDirectory;
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
    public void setFeatureOutputDirectory(final File featureOutputDirectory) {
        this.featureOutputDirectory = featureOutputDirectory;
    }

    /**
     * @return the runmodes
     */
    public EnumSet<ServiceType> getServiceTypes() {
        return serviceTypes;
    }

    /**
     * @param rm the runmode(s) to set
     */
    public void setServiceTypes(ServiceType ... rm) {
        serviceTypes = EnumSet.copyOf(Arrays.asList(rm));
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

        final List<Feature> userResult = this.aggregate(userAggregates, Mode.USER, projectFeatures);

        // Produce the product aggregates
        final Map<ProductVariation, List<Feature>> productAggregates = getProductAggregates();

        this.aggregateFeatureInfo(productAggregates, Mode.PRODUCT, projectFeatures);

        // Produce the final aggregates
        final Map<String, List<Feature>> finalAggregates = getFinalAggregates(userAggregates, projectFeatures);

        final List<Feature> finalResult = this.aggregate(finalAggregates, Mode.FINAL, projectFeatures);

        // find final author and publish feature and get configuration api and artifact rules
        Map<ProductVariation, ConfigurationApi> apiMapping = new HashMap<>();
        Map<ProductVariation, ArtifactRules> rules = new HashMap<>();
        for ( ProductVariation variation : productAggregates.keySet()) {
            final Feature f = findFeature(finalResult, variation);
            final ConfigurationApi configApi = ConfigurationApi.getConfigurationApi(f);
            apiMapping.put(variation, configApi);
            final ArtifactRules r = ArtifactRules.getArtifactRules(f);
            if (r != null) {
                rules.put(variation, r);
            }
        }

        // add configuration api and artifact rules to all user features
        for(final Feature f : userResult) {
            ProductVariation variation = getProductFeatureGenerator().getVariation(f.getId().getClassifier());
            ConfigurationApi configApi = apiMapping.get(variation);
            ConfigurationApi.setConfigurationApi(f, configApi);
            ArtifactRules r = rules.get(variation);
            ArtifactRules.setArtifactRules(f, r);
        }

        final List<Feature> result = new ArrayList<>();
        result.addAll(userResult);
        result.addAll(finalResult);
        return result;
    }

    // visible for testing
    Map<String, List<Feature>> getUserAggregates(Map<String, Feature> projectFeatures) throws IOException {
        return getUserFeatureAggregator().getUserAggregates(projectFeatures, serviceTypes);
    }

    /**
     * Find a feature, either author or publish
     * @throws IOException
     */
    private Feature findFeature(final List<Feature> finalFeatures, final ProductVariation variation) throws IOException {
        Feature f = findFeatureWithClassifier(finalFeatures, variation.getFinalAggregateName());
        if ( f == null )
            f = findFeatureWithClassifier(finalFeatures, variation.getFinalAggregateName()+".prod");

        if ( f == null )
            throw new IOException("Unable to find final feature for variation " + variation);

        return f;
    }

    /**
     * Search a feature with the given classifier
     * @param features List of features
     * @param classifier  The classifier
     * @return The feature or {@code null}
     */
    private Feature findFeatureWithClassifier(final List<Feature> features, final String classifier) {
        for(final Feature f : features) {
            if ( f.getId().getClassifier().equals(classifier)) {
                return f;
            }
        }
        return null;
    }

    private Map<String, Feature> readFeatures() throws IOException {
        final Map<String, Feature> result = new HashMap<>();
        for(final File f : this.getFeatureInputDirectory().listFiles()) {
            if ( ( f.getName().endsWith(".json") || f.getName().endsWith(".slingosgifeature")&& !f.getName().startsWith(".") ) ) {
                logger.info("Reading feature model {}...", f.getName());
                try (final Reader reader = new FileReader(f)) {
                    final Feature feature = FeatureJSONReader.read(reader, f.getName());
                    result.put(f.getName(), feature);
                }
            }
        }
        return result;
    }

    Map<ProductVariation, List<Feature>> getProductAggregates() throws IOException {
        Map<ProductVariation, List<Feature>> res = getProductFeatureGenerator().getProductAggregates(serviceTypes);

        return res;
    }

    final void postProcessProductFeature(final Feature feature) {
        // check for artifact rules
        final ArtifactRules rules = ArtifactRules.getArtifactRules(feature);
        if ( rules != null ) {
            for(final VersionRule rule : rules.getArtifactVersionRules()) {
                if ( rule.getArtifactId() != null && "zip".equals(rule.getArtifactId().getType()) ) {
                    rule.setArtifactId(rule.getArtifactId().changeClassifier("cp2fm-converted"));
                }
            }
            ArtifactRules.setArtifactRules(feature, rules);
        } else {
            // create empty rules with LENIENT mode to avoid analyser warning
            final ArtifactRules r = new ArtifactRules();
            r.setMode(org.apache.sling.feature.extension.apiregions.api.artifacts.Mode.LENIENT);
            ArtifactRules.setArtifactRules(feature, r);
        }
    }

    Map<String, List<Feature>> getFinalAggregates(final Map<String, List<Feature>> userAggregate,
            final Map<String, Feature> projectFeatures) throws IOException {
        final Map<String, List<Feature>> aggregates = new HashMap<>();

        for (final String name : userAggregate.keySet()) {
            final ProductVariation variation = getProductFeatureGenerator().getVariation(name);

            final String classifier = name.replaceAll("^user-", "");
            final List<Feature> list = aggregates.computeIfAbsent(classifier, n -> new ArrayList<>());

            list.add(getNotNull(projectFeatures, variation.getProductAggregateName()));
            list.add(getNotNull(projectFeatures, name));
        }

        return aggregates;
    }

    private static Feature getNotNull(Map<String, Feature> featureCache, String key) {
        Feature feature = featureCache.get(key);
        if ( feature == null )
            throw new IllegalArgumentException("Did not find a feature with key " + key);
        return feature;
    }

    private void aggregateFeatureInfo(Map<ProductVariation, List<Feature>> productAggregates, Mode product,
            Map<String, Feature> projectFeatures) throws IOException {
        aggregate(productAggregates.entrySet().stream().collect(Collectors.toMap( e -> e.getKey().getProductAggregateName(),  Map.Entry::getValue)), product, projectFeatures);
    }


    List<Feature> aggregate(final Map<String, List<Feature>> aggregates, final Mode mode,
        final Map<String, Feature> projectFeatures) throws IOException {

        final List<Feature> result = new ArrayList<>();
        for (final Map.Entry<String, List<Feature>> aggregate : aggregates.entrySet()) {

            logger.info("Building aggregate feature model {}...", aggregate.getKey());

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
            List<ArtifactId> artifactOverrides = getArtifactsOverrides(mode);
            artifactOverrides.forEach(builderContext::addArtifactsOverride);

            builderContext.addConfigsOverrides(Collections.singletonMap("*", "MERGE_LATEST"));

            final ArtifactId newFeatureID = this.getProjectId().changeClassifier(aggregate.getKey()).changeType(FEATUREMODEL_TYPE);

            final Feature feature = FeatureBuilder.assemble(newFeatureID, builderContext,
                  aggregate.getValue().toArray(new Feature[aggregate.getValue().size()]));

            // special handling for exactly same mvn coordinates in user and product feature
            if ( mode == Mode.FINAL && this.isEnableDuplicateBundleHandling()) {
                handleDuplicateBundles(aggregate, feature);
            }

            postProcessProductFeature(feature);

            final File featureFile = new File(this.getFeatureOutputDirectory(), aggregate.getKey().concat(".json"));
            try ( final Writer writer = new FileWriter(featureFile)) {
                FeatureJSONWriter.write(writer, feature);
            }

            if ( artifactsDeployer != null ) {
                artifactsDeployer.deploy(new FileArtifactWriter(featureFile), null, newFeatureID);
            }
            projectFeatures.put(aggregate.getKey(), feature);

            result.add(feature);
        }

        return result;
    }

    protected List<ArtifactId> getArtifactsOverrides(Mode mode) {
        switch (mode) {
            case USER:
            case PRODUCT:
                return List.of(
                        ArtifactId.parse("*:*:HIGHEST"),
                        ArtifactId.parse("*:*:*:*:HIGHEST"));
            case FINAL:
                return List.of(
                        ArtifactId.parse("com.adobe.cq:core.wcm.components.core:FIRST"),
                        ArtifactId.parse("com.adobe.cq:core.wcm.components.extensions.amp:FIRST"),
                        ArtifactId.parse("org.apache.sling:org.apache.sling.models.impl:FIRST"),
                        ArtifactId.parse("*:core.wcm.components.content:zip:*:FIRST"),
                        ArtifactId.parse("*:core.wcm.components.extensions.amp.content:zip:*:FIRST"),
                        ArtifactId.parse("*:*:jar:*:ALL"));
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Handle duplicate bundles
     * If the user and the product feature contain the exact same bundle, then the feature handling merges
     * them into a single artifact - which then means the artifact is not correctly checked as a user bundle
     * @param aggregate The features to be aggregated
     * @param feature The final feature
     * @throws IOException If an error occurs
     */
    private void handleDuplicateBundles(final Map.Entry<String, List<Feature>> aggregate, final Feature feature)
            throws IOException {
        // sanity check
        if ( aggregate.getValue().size() != 2) {
            throw new IOException("Expected two features for final aggregate, got " + aggregate.getValue().size());
        }
        final Feature productFeature = aggregate.getValue().get(0);
        final Feature userFeature = aggregate.getValue().get(1);

        // check if a bundle from the user feature is in the product feature
        for(final Artifact userBundle : userFeature.getBundles()) {
            if ( productFeature.getBundles().contains(userBundle)) {
                logger.debug("Found duplicate bundle {} in user and product feature.", userBundle.getId().toMvnId());

                // use bundle metadata from user provided bundle (e.g. origin)
                final Artifact mergedBundle = feature.getBundles().getExact(userBundle.getId());
                mergedBundle.getMetadata().clear();
                mergedBundle.getMetadata().putAll(userBundle.getMetadata());

                // Clear analyser-metadata for product bundle
                final Extension me = feature.getExtensions().getByName("analyser-metadata");
                if ( me != null && me.getType() == ExtensionType.JSON) {
                    final JsonObject metadata = me.getJSONStructure().asJsonObject();

                    // Create new JSON Object and copy metadata except for the bundle
                    final JsonObjectBuilder newMetadataBuilder = Json.createObjectBuilder();
                    for(final String key : metadata.keySet()) {
                        if ( !key.equals(userBundle.getId().toMvnId()) ) {
                            newMetadataBuilder.add(key, metadata.getJsonObject(key));
                        }
                    }

                    // replace JSON in extension
                    me.setJSONStructure(newMetadataBuilder.build());
                }
            }
        }
    }
}
