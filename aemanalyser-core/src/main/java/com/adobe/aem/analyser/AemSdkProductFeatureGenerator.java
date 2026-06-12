/*
  Copyright 2022 Adobe. All rights reserved.
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.project.ServiceType;

/**
 * Generates product features based on the AEM SDK and configured add-on ids
 */
public class AemSdkProductFeatureGenerator implements ProductFeatureGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AemSdkProductFeatureGenerator.class);
    private static final String API_REGIONS_EXTENSION_NAME = "api-regions";

    private final FeatureProvider featureProvider;
    private final ArtifactId sdkId;
    private final ArtifactId prereleaseSdkId;
    private final List<ArtifactId> addOnIds;
    private final List<ArtifactId> prereleaseAddOnIds;

    public AemSdkProductFeatureGenerator(
            FeatureProvider featureProvider,
            ArtifactId sdkId,
            ArtifactId prereleaseSdkId,
            List<ArtifactId> addOnIds,
            List<ArtifactId> prereleaseAddOnIds) {
        this.featureProvider = featureProvider;
        this.sdkId = sdkId;
        this.prereleaseSdkId = prereleaseSdkId;
        this.addOnIds = addOnIds == null ? Collections.emptyList() : addOnIds;
        this.prereleaseAddOnIds = prereleaseAddOnIds == null ? Collections.emptyList() : prereleaseAddOnIds;
    }

    @Override
    public Map<ProductVariation, List<Feature>> getProductAggregates(EnumSet<ServiceType> serviceTypes) throws IOException {
        final Map<ProductVariation, List<Feature>> aggregates = new HashMap<>();

        List<String> stl = serviceTypes.stream()
            .map(ServiceType::toString)
            .collect(Collectors.toList());

        for ( SdkProductVariation variation : SdkProductVariation.values() ) {
            if (!stl.contains(variation.toString()))
                continue;

            final List<Feature> list = aggregates.computeIfAbsent(variation, n -> new ArrayList<>());
            final Feature sdkFeature = getSdkFeature(variation);
            list.add(sdkFeature);
            final Map<String, ArtifactId> prereleaseByKey = toPrereleaseAddOnMap(prereleaseAddOnIds);
            for(final ArtifactId id : addOnIds) {
                final ArtifactId prereleaseId = prereleaseByKey.get(addOnKey(id));
                final Feature feature = getAddOnFeature(id, prereleaseId, variation);
                list.add(feature);
            }
        }

        return aggregates;
    }

    private Feature getSdkFeature(final SdkProductVariation variation) throws IOException {
        final Feature stableFeature = resolveSdkFeature(sdkId, variation, "SDK");
        if (prereleaseSdkId == null) {
            LOGGER.info("Using stable SDK feature for {} because prerelease SDK is not configured.", variation);
            return stableFeature;
        }

        final Feature prereleaseFeature = resolveSdkFeature(prereleaseSdkId, variation, "prerelease SDK");

        final boolean stableEmpty = isEffectivelyEmpty(stableFeature);
        final boolean prereleaseEmpty = isEffectivelyEmpty(prereleaseFeature);

        if (stableEmpty && prereleaseEmpty) {
            LOGGER.info("Both stable and prerelease SDK features are effectively empty for {}. Using stable SDK feature.", variation);
            return stableFeature;
        }
        if (stableEmpty) {
            LOGGER.info("Stable SDK feature is effectively empty for {}. Using prerelease SDK feature.", variation);
            return prereleaseFeature;
        }
        if (prereleaseEmpty) {
            LOGGER.info("Prerelease SDK feature is effectively empty for {}. Using stable SDK feature.", variation);
            return stableFeature;
        }

        LOGGER.info("Merging stable and prerelease SDK features for {} with stable precedence on conflicts.", variation);
        return mergeFeatures(stableFeature, prereleaseFeature, variation, "SDK");
    }

    private Feature getAddOnFeature(final ArtifactId stableAddOnId,
            final ArtifactId prereleaseAddOnId,
            final SdkProductVariation variation) throws IOException {
        final Feature stableFeature = resolveAddOnFeature(stableAddOnId, "stable add-on");
        if (prereleaseAddOnId == null) {
            LOGGER.info("Using stable add-on feature {} for {} because prerelease add-on is not configured.",
                    stableAddOnId.toMvnId(), variation);
            return stableFeature;
        }

        final Feature prereleaseFeature = resolveAddOnFeature(prereleaseAddOnId, "prerelease add-on");

        final boolean stableEmpty = isEffectivelyEmpty(stableFeature);
        final boolean prereleaseEmpty = isEffectivelyEmpty(prereleaseFeature);

        if (stableEmpty && prereleaseEmpty) {
            LOGGER.info("Both stable and prerelease add-on features are effectively empty for {} in {}. Using stable add-on.",
                    stableAddOnId.toMvnId(), variation);
            return stableFeature;
        }
        if (stableEmpty) {
            LOGGER.info("Stable add-on feature is effectively empty for {} in {}. Using prerelease add-on.",
                    stableAddOnId.toMvnId(), variation);
            return prereleaseFeature;
        }
        if (prereleaseEmpty) {
            LOGGER.info("Prerelease add-on feature is effectively empty for {} in {}. Using stable add-on.",
                    stableAddOnId.toMvnId(), variation);
            return stableFeature;
        }

        LOGGER.info("Merging stable and prerelease add-on features for {} in {} with stable precedence on conflicts.",
                stableAddOnId.toMvnId(), variation);
        return mergeFeatures(stableFeature, prereleaseFeature, variation, "add-on");
    }

    private Feature resolveSdkFeature(final ArtifactId sourceSdkId, final SdkProductVariation variation, final String label) throws IOException {
        final ArtifactId featureId = sourceSdkId
                .changeClassifier(getProductClassifier(variation))
                .changeType(AemAggregator.FEATUREMODEL_TYPE);
        final Feature feature = featureProvider.provide(featureId);
        if (feature == null) {
            throw new IOException("Unable to find " + label + " feature for " + sourceSdkId.toMvnId());
        }
        return feature;
    }

    private Feature resolveAddOnFeature(final ArtifactId sourceAddOnId, final String label) throws IOException {
        final ArtifactId featureId = sourceAddOnId.changeType(AemAggregator.FEATUREMODEL_TYPE);
        final Feature feature = featureProvider.provide(featureId);
        if (feature == null) {
            throw new IOException("Unable to find " + label + " feature for " + sourceAddOnId.toMvnId());
        }
        return feature;
    }

    private boolean isEffectivelyEmpty(final Feature feature) {
        if (!feature.getBundles().isEmpty() || !feature.getConfigurations().isEmpty()) {
            return false;
        }
        for (final Extension extension : feature.getExtensions()) {
            if (!API_REGIONS_EXTENSION_NAME.equals(extension.getName())) {
                return false;
            }
        }
        return true;
    }

    private Feature mergeFeatures(final Feature stable,
            final Feature prerelease,
            final SdkProductVariation variation,
            final String featureLabel) {
        final Feature merged = new Feature(stable.getId());

        for (final Artifact bundle : stable.getBundles()) {
            merged.getBundles().add(bundle);
        }
        for (final Artifact bundle : prerelease.getBundles()) {
            if (merged.getBundles().getExact(bundle.getId()) == null) {
                merged.getBundles().add(bundle);
            } else {
                LOGGER.info("Keeping stable bundle {} for {} {} and dropping duplicate from prerelease {}.",
                        bundle.getId().toMvnId(), featureLabel, variation, featureLabel);
            }
        }

        for (final Configuration cfg : stable.getConfigurations()) {
            merged.getConfigurations().add(cfg);
        }

        for (final Configuration cfg : prerelease.getConfigurations()) {
            if (!merged.getConfigurations().contains(cfg)) {
                merged.getConfigurations().add(cfg);
            } else {
                LOGGER.info("Keeping stable configuration {} for {} {} and dropping duplicate from prerelease {}.",
                        cfg.getPid(), featureLabel, variation, featureLabel);
            }
        }

        for (final Extension extension : stable.getExtensions()) {
            merged.getExtensions().add(extension);
        }
        for (final Extension extension : prerelease.getExtensions()) {
            if (merged.getExtensions().getByName(extension.getName()) == null) {
                merged.getExtensions().add(extension);
            } else {
                LOGGER.info("Keeping stable extension {} for {} {} and dropping duplicate from prerelease {}.",
                        extension.getName(), featureLabel, variation, featureLabel);
            }
        }

        return merged;
    }

    protected String getProductClassifier(SdkProductVariation variation) {
        return variation.getSdkClassifier();
    }

    private Map<String, ArtifactId> toPrereleaseAddOnMap(final List<ArtifactId> addOnIds) {
        final Map<String, ArtifactId> byKey = new LinkedHashMap<>();
        for (final ArtifactId id : addOnIds) {
            byKey.put(addOnKey(id), id);
        }
        return byKey;
    }

    private String addOnKey(final ArtifactId id) {
        return id.getGroupId() + ":" + Objects.toString(id.getClassifier(), "") + ":" + Objects.toString(id.getType(), "");
    }

    @Override
    public ProductVariation getVariation(String name) {
        if ( name.startsWith("user-aggregated-author") )
            return SdkProductVariation.AUTHOR;
        return SdkProductVariation.PUBLISH;
    }
}