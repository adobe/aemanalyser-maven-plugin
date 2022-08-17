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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;

/**
 * Generates product features based on the AEM SDK and configured add-on ids
 */
public class AemSdkProductFeatureGenerator implements ProductFeatureGenerator {

    private final FeatureProvider featureProvider;
    private final ArtifactId sdkId;
    private final List<ArtifactId> addOnIds;

    public AemSdkProductFeatureGenerator(FeatureProvider featureProvider, ArtifactId sdkId, List<ArtifactId> addOnIds) {
        this.featureProvider = featureProvider;
        this.sdkId = sdkId;
        this.addOnIds = addOnIds == null ? Collections.emptyList() : addOnIds;
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
            final Feature sdkFeature = featureProvider.provide(sdkId
                    .changeClassifier(variation.getSdkClassifier())
                    .changeType(AemAggregator.FEATUREMODEL_TYPE));
            if ( sdkFeature == null ) {
                throw new IOException("Unable to find SDK feature for " + sdkId.toMvnId());
            }
            list.add(sdkFeature);
            for(final ArtifactId id : addOnIds) {
                final Feature feature = featureProvider.provide(id.changeType(AemAggregator.FEATUREMODEL_TYPE));
                if ( feature == null ) {
                    throw new IOException("Unable to find addon feature for " + id.toMvnId());
                }
                list.add(feature);
            }
        }

        return aggregates;
    }

    @Override
    public ProductVariation getVariation(String name) {
        if ( name.startsWith("user-aggregated-author") )
            return SdkProductVariation.AUTHOR;
        return SdkProductVariation.PUBLISH;
    }
}