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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.junit.Test;

import com.adobe.aem.project.ServiceType;

public class AemSdkProductFeatureGeneratorTest {

    @Test
    public void testGetProductAggregatesAuthor() throws IOException {
        FeatureProvider fp = id -> new Feature(id);

        ArtifactId sdkID = ArtifactId.fromMvnId("foo:bar:123");
        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, sdkID, null, Collections.emptyList(), Collections.emptyList());

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));
        assertEquals(1, res.size());
        List<Feature> fl = res.get(SdkProductVariation.AUTHOR);
        assertEquals(1, fl.size());
        Feature f = fl.get(0);
        assertEquals("bar", f.getId().getArtifactId());
    }

    @Test
    public void testGetProductAggregates() throws IOException {
        FeatureProvider fp = id -> new Feature(id);

        ArtifactId sdkID = ArtifactId.fromMvnId("foo:bar:123");
        ArtifactId addonID = ArtifactId.fromMvnId("my:testaddon:999");
        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, sdkID, null, Collections.singletonList(addonID), Collections.emptyList());

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.allOf(ServiceType.class));
        assertEquals(2, res.size());
        List<Feature> fla = res.get(SdkProductVariation.AUTHOR);
        assertEquals(2, fla.size());
        assertEquals("bar", fla.get(0).getId().getArtifactId());
        assertEquals("testaddon", fla.get(1).getId().getArtifactId());

        List<Feature> flp = res.get(SdkProductVariation.PUBLISH);
        assertEquals(2, flp.size());
        assertEquals("bar", flp.get(0).getId().getArtifactId());
        assertEquals("testaddon", flp.get(1).getId().getArtifactId());
    }

    @Test
    public void testGetProductAggregatesUsesStableWhenPrereleaseIsEmpty() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0");
        final ArtifactId prereleaseSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-prerelease-sdk-api:1.0.0");

        FeatureProvider fp = id -> {
            if ("aem-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.bundle:1")));
                return stable;
            }
            final Feature prerelease = new Feature(id);
            final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
            prerelease.getExtensions().add(apiRegions);
            return prerelease;
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, stableSdkId, prereleaseSdkId, Collections.emptyList(), Collections.emptyList());
        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));

        Feature productSource = res.get(SdkProductVariation.AUTHOR).get(0);
        assertEquals("aem-sdk-api", productSource.getId().getArtifactId());
        assertEquals(1, productSource.getBundles().size());
    }

    @Test
    public void testGetProductAggregatesMergesWhenBothAreNonEmpty() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0");
        final ArtifactId prereleaseSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-prerelease-sdk-api:1.0.0");

        FeatureProvider fp = id -> {
            if ("aem-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:bundle.a:1")));
                final Extension ext = new Extension(ExtensionType.TEXT, "stable-ext", ExtensionState.OPTIONAL);
                stable.getExtensions().add(ext);
                return stable;
            }
            final Feature prerelease = new Feature(id);
            prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:bundle.a:1")));
            prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:bundle.b:1")));
            final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
            prerelease.getExtensions().add(apiRegions);
            return prerelease;
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, stableSdkId, prereleaseSdkId, Collections.emptyList(), Collections.emptyList());
        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));

        Feature merged = res.get(SdkProductVariation.AUTHOR).get(0);
        assertEquals("aem-sdk-api", merged.getId().getArtifactId());
        assertEquals(2, merged.getBundles().size());
        assertNotNull(merged.getExtensions().getByName("stable-ext"));
        assertNotNull(merged.getExtensions().getByName("api-regions"));
    }

    @Test
    public void testGetProductAggregatesMergesStableAndPrereleaseAddOns() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0");
        final ArtifactId stableAddOnId = new ArtifactId("com.adobe.aem", "aem-forms-sdk-api", "1.0.0", "aem-forms-sdk", null);
        final ArtifactId prereleaseAddOnId = new ArtifactId("com.adobe.aem", "aem-forms-prerelease-sdk-api", "1.0.0", "aem-forms-sdk", null);

        FeatureProvider fp = id -> {
            final Feature feature = new Feature(id);
            if ("aem-forms-sdk-api".equals(id.getArtifactId())) {
                feature.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:addon.bundle:1")));
                return feature;
            }
            if ("aem-forms-prerelease-sdk-api".equals(id.getArtifactId())) {
                feature.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:addon.bundle:1")));
                feature.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:addon.bundle.prerelease:1")));
                return feature;
            }
            return feature;
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(
                fp,
                stableSdkId,
                null,
                Collections.singletonList(stableAddOnId),
                Collections.singletonList(prereleaseAddOnId));
        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));

        Feature mergedAddOn = res.get(SdkProductVariation.AUTHOR).get(1);
        assertEquals("aem-forms-sdk-api", mergedAddOn.getId().getArtifactId());
        assertEquals(2, mergedAddOn.getBundles().size());
    }
}
