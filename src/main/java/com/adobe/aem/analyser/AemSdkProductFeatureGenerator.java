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

import org.apache.sling.feature.ArtifactId;
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
    private final FeatureProvider featureProvider;
    private final ArtifactId sdkId;
    private final ArtifactId prereleaseSdkId;
    private final List<ArtifactId> addOnIds;
    private final List<ArtifactId> prereleaseAddOnIds;
    private final FeatureConflictResolver conflictResolver;

    public AemSdkProductFeatureGenerator(
            FeatureProvider featureProvider,
            ArtifactId sdkId,
            ArtifactId prereleaseSdkId,
            List<ArtifactId> addOnIds,
            List<ArtifactId> prereleaseAddOnIds) {
        this(featureProvider, sdkId, prereleaseSdkId, addOnIds, prereleaseAddOnIds,
                new AssemblyBasedFeatureConflictResolver());
    }

    public AemSdkProductFeatureGenerator(
            FeatureProvider featureProvider,
            ArtifactId sdkId,
            ArtifactId prereleaseSdkId,
            List<ArtifactId> addOnIds,
            List<ArtifactId> prereleaseAddOnIds,
            FeatureConflictResolver conflictResolver) {
        this.featureProvider = featureProvider;
        this.sdkId = sdkId;
        this.prereleaseSdkId = prereleaseSdkId;
        this.addOnIds = addOnIds == null ? Collections.emptyList() : addOnIds;
        this.prereleaseAddOnIds = prereleaseAddOnIds == null ? Collections.emptyList() : prereleaseAddOnIds;
        this.conflictResolver = conflictResolver;
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
        final Feature stableFeature = resolveSdkFeature(sdkId, variation);
        if (prereleaseSdkId == null) {
            LOGGER.info("Using SDK feature {} for {} because prerelease SDK is not configured.",
                    stableFeature.getId().toMvnId(), variation);
            return stableFeature;
        }

        final Feature prereleaseFeature = resolveSdkFeature(prereleaseSdkId, variation);
        return selectFeatureByVersion(stableFeature, prereleaseFeature, variation);
    }

    private Feature getAddOnFeature(final ArtifactId stableAddOnId,
            final ArtifactId prereleaseAddOnId,
            final SdkProductVariation variation) throws IOException {
        final Feature stableFeature = resolveAddOnFeature(stableAddOnId);
        if (prereleaseAddOnId == null) {
            LOGGER.info("Using add-on feature {} for {} because prerelease add-on is not configured.",
                    stableFeature.getId().toMvnId(), variation);
            return stableFeature;
        }

        final Feature prereleaseFeature = resolveAddOnFeature(prereleaseAddOnId);
        return selectFeatureByVersion(stableFeature, prereleaseFeature, variation);
    }

    private Feature resolveSdkFeature(final ArtifactId sourceSdkId, final SdkProductVariation variation) throws IOException {
        final ArtifactId featureId = sourceSdkId
                .changeClassifier(getProductClassifier(variation))
                .changeType(AemAggregator.FEATUREMODEL_TYPE);
        final Feature feature = featureProvider.provide(featureId);
        if (feature == null) {
            throw new IOException("Unable to find feature for " + sourceSdkId.toMvnId());
        }
        return feature;
    }

    private Feature resolveAddOnFeature(final ArtifactId sourceAddOnId) throws IOException {
        final ArtifactId featureId = sourceAddOnId.changeType(AemAggregator.FEATUREMODEL_TYPE);
        final Feature feature = featureProvider.provide(featureId);
        if (feature == null) {
            throw new IOException("Unable to find feature for " + sourceAddOnId.toMvnId());
        }
        return feature;
    }

    private int compareFeatureVersions(final ArtifactId stable, final ArtifactId prerelease) {
        return stable.getVersion().compareTo(prerelease.getVersion());
    }

    private Feature selectFeatureByVersion(final Feature stableFeature,
            final Feature prereleaseFeature,
            final SdkProductVariation variation) {
        final int comparison = compareFeatureVersions(stableFeature.getId(), prereleaseFeature.getId());
        final String stableId = stableFeature.getId().toMvnId();
        final String prereleaseId = prereleaseFeature.getId().toMvnId();

        if (comparison > 0) {
            LOGGER.info("Using stable feature {} for {} because it has newer version than {}.",
                    stableId, variation, prereleaseId);
            return stableFeature;
        }
        if (comparison < 0) {
            LOGGER.info("Using prerelease feature {} for {} because it has newer version than {}.",
                    prereleaseId, variation, stableId);
            return prereleaseFeature;
        }

        LOGGER.info("Features {} and {} have the same version for {}. Using conflict resolver.",
                stableId, prereleaseId, variation);
        return conflictResolver.resolveVersionConflict(stableFeature, prereleaseFeature, variation);
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