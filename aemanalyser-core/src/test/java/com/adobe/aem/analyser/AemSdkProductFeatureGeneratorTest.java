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
import java.util.*;

import org.apache.sling.feature.*;
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

    /**
     * Input:
     * - stable SDK version is newer than prerelease SDK version
     * - both SDK features contain bundles and api-regions extension
     *
     * Expected:
     * - stable SDK feature is selected for AUTHOR aggregate
     * - selected bundles and their versions come from stable
     */
    @Test
    public void testGetProductAggregatesSelectsNewerStableOverPrerelease() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:2.1.0");
        final ArtifactId prereleaseSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-prerelease-sdk-api:2.0.0");


        FeatureProvider fp = id -> {
            if ("aem-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.bundle:2.1")));
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:another.bundle:2.1")));
                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                stable.getExtensions().add(apiRegions);
                return stable;
            }
            if ("aem-prerelease-sdk-api".equals(id.getArtifactId())) {
                final Feature prerelease = new Feature(id);
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.bundle:2.0")));
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:another.bundle:2.0")));
                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                prerelease.getExtensions().add(apiRegions);
                return prerelease;
            }
            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, stableSdkId, prereleaseSdkId,
                null, null);
        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));

        Feature productFeature = res.get(SdkProductVariation.AUTHOR).get(0);
        assertEquals("aem-sdk-api", productFeature.getId().getArtifactId());
        assertEquals("2.1.0", productFeature.getId().getVersion());
        assertEquals(2, productFeature.getBundles().size());
        assertNotNull(productFeature.getExtensions().getByName("api-regions"));

        assertEquals("stable.bundle", productFeature.getBundles().get(0).getId().getArtifactId());
        assertEquals("2.1", productFeature.getBundles().get(0).getId().getVersion());
        assertEquals("another.bundle", productFeature.getBundles().get(1).getId().getArtifactId());
        assertEquals("2.1", productFeature.getBundles().get(1).getId().getVersion());
        assertNotNull(productFeature.getExtensions().getByName("api-regions"));
    }

    /**
     * Input:
     * - prerelease SDK version is newer than stable SDK version
     * - both SDK features contain bundles and api-regions extension
     *
     * Expected:
     * - prerelease SDK feature is selected for AUTHOR aggregate
     * - selected bundles and versions come from prerelease
     */
    @Test
    public void testGetProductAggregatesSelectsNewerPrereleaseOverStable() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:2.0.0");
        final ArtifactId prereleaseSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-prerelease-sdk-api:2.1.0");

        FeatureProvider fp = id -> {
            if ("aem-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.bundle:2.0")));
                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                stable.getExtensions().add(apiRegions);
                return stable;
            }

            if ("aem-prerelease-sdk-api".equals(id.getArtifactId())) {
                final Feature prerelease = new Feature(id);
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.bundle:2.1")));
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:another.bundle:2.1")));
                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                prerelease.getExtensions().add(apiRegions);
                return prerelease;
            }
            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, stableSdkId, prereleaseSdkId,
                null, null);
        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));

        Feature productFeature = res.get(SdkProductVariation.AUTHOR).get(0);
        assertEquals("aem-prerelease-sdk-api", productFeature.getId().getArtifactId());
        assertEquals("2.1.0", productFeature.getId().getVersion());
        assertEquals(2, productFeature.getBundles().size());

        assertEquals("prerelease.bundle", productFeature.getBundles().get(0).getId().getArtifactId());
        assertEquals("2.1", productFeature.getBundles().get(0).getId().getVersion());
        assertEquals("another.bundle", productFeature.getBundles().get(1).getId().getArtifactId());
        assertEquals("2.1", productFeature.getBundles().get(1).getId().getVersion());
        assertNotNull(productFeature.getExtensions().getByName("api-regions"));
    }

    /**
     * Input:
     * - stable and prerelease SDK features have the same version
     * - both define overlapping bundles/configurations/extensions with different values
     *
     * Expected:
     * - conflict resolver is used
     * - prerelease values are preferred for duplicates
     * - non-duplicate stable data is preserved
     */
    @Test
    public void testGetProductAggregatesResolvesSameVersionConflicts() throws IOException {
        final ArtifactId stableSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:2.1.0");
        final ArtifactId prereleaseSdkId = ArtifactId.fromMvnId("com.adobe.aem:aem-prerelease-sdk-api:2.1.0");

        FeatureProvider fp = id -> {
            if ("aem-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.bundle:1")));
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:other.bundle:2")));

                final Configuration stableConfig = new Configuration("stable.pid");
                stableConfig.getProperties().put("source", "stable");
                stable.getConfigurations().add(stableConfig);

                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                stable.getExtensions().add(apiRegions);

                final Extension other = new Extension(ExtensionType.TEXT, "other-ext", ExtensionState.OPTIONAL);
                other.setText("stable-other-ext-content");
                stable.getExtensions().add(other);
                return stable;
            }
            if ("aem-prerelease-sdk-api".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.bundle:1")));
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:other.bundle:1")));

                final Configuration stableConfig = new Configuration("stable.pid");
                stableConfig.getProperties().put("source", "prerelease");
                stable.getConfigurations().add(stableConfig);

                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                stable.getExtensions().add(apiRegions);

                final Extension other = new Extension(ExtensionType.TEXT, "other-ext", ExtensionState.OPTIONAL);
                other.setText("prerelease-other-ext-content");
                stable.getExtensions().add(other);
                return stable;
            }

            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(
                fp,
                stableSdkId,
                prereleaseSdkId,
                null,
                Collections.emptyList());

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));
        List<Feature> features = res.get(SdkProductVariation.AUTHOR);

        assertEquals(1, features.size());

        Feature sdkFeature = features.get(0);
        assertEquals("aem-prerelease-sdk-api", sdkFeature.getId().getArtifactId());

        assertEquals(3, sdkFeature.getBundles().size());
        assertEquals("prerelease.bundle", sdkFeature.getBundles().get(0).getId().getArtifactId());
        assertEquals("other.bundle", sdkFeature.getBundles().get(1).getId().getArtifactId());
        assertEquals("1", sdkFeature.getBundles().get(1).getId().getVersion());
        assertEquals("stable.bundle", sdkFeature.getBundles().get(2).getId().getArtifactId());

        assertEquals(1, sdkFeature.getConfigurations().size());
        Configuration config = sdkFeature.getConfigurations().iterator().next();
        assertEquals("stable.pid", config.getPid());
        assertEquals("prerelease", config.getProperties().get("source"));

        Extension apiRegionsExt = sdkFeature.getExtensions().getByName("api-regions");
        assertNotNull(apiRegionsExt);
        assertEquals(ExtensionType.JSON, apiRegionsExt.getType());

        Extension otherExt = sdkFeature.getExtensions().getByName("other-ext");
        assertNotNull(otherExt);
        assertEquals(ExtensionType.TEXT, otherExt.getType());
        assertEquals("prerelease-other-ext-content", otherExt.getText());
    }

    /**
     * Input:
     * - stable add-on version is newer than prerelease add-on version
     * - both add-on counterparts are available for the same logical add-on
     *
     * Expected:
     * - stable add-on feature is selected
     * - selected add-on bundles/configurations are from stable
     */
    @Test
    public void testGetAddOnSelectsNewerStableOverPrerelease() throws IOException {
        final ArtifactId stableAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon:2.1.0");
        final ArtifactId prereleaseAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon-prerelease:2.0.0");

        FeatureProvider fp = id -> {
            if ("aem-addon".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.addon.bundle:2.1")));
                stable.getConfigurations().add(new Configuration("addon.stable.pid"));
                return stable;
            }
            if ("aem-addon-prerelease".equals(id.getArtifactId())) {
                final Feature prerelease = new Feature(id);
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.addon.bundle:2.0")));
                return prerelease;
            }
            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(
                fp,
                ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0"),
                null,
                Collections.singletonList(stableAddonId),
                Collections.singletonList(prereleaseAddonId));

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));
        List<Feature> features = res.get(SdkProductVariation.AUTHOR);

        assertEquals(2, features.size());
        Feature addon = features.get(1);
        assertEquals("aem-addon", addon.getId().getArtifactId());
        assertEquals("2.1.0", addon.getId().getVersion());
        assertEquals(1, addon.getBundles().size());
        assertEquals("stable.addon.bundle", addon.getBundles().get(0).getId().getArtifactId());
        assertEquals(1, addon.getConfigurations().size());
    }

    /**
     * Input:
     * - prerelease add-on version is newer than stable add-on version
     * - both add-on counterparts are available for the same logical add-on
     *
     * Expected:
     * - prerelease add-on feature is selected
     * - selected add-on bundles/configurations are from prerelease
     */
    @Test
    public void testGetAddOnSelectsNewerPrereleaseOverStable() throws IOException {
        final ArtifactId stableAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon:2.0.0");
        final ArtifactId prereleaseAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon-prerelease:2.1.0");

        FeatureProvider fp = id -> {
            if ("aem-addon".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.addon.bundle:2.0")));
                return stable;
            }
            if ("aem-addon-prerelease".equals(id.getArtifactId())) {
                final Feature prerelease = new Feature(id);
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.addon.bundle:2.1")));
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:another.addon.bundle:2.1")));
                prerelease.getConfigurations().add(new Configuration("addon.prerelease.pid"));
                return prerelease;
            }
            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(
                fp,
                ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0"),
                null,
                Collections.singletonList(stableAddonId),
                Collections.singletonList(prereleaseAddonId));

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));
        List<Feature> features = res.get(SdkProductVariation.AUTHOR);

        assertEquals(2, features.size());
        Feature addon = features.get(1);
        assertEquals("aem-addon-prerelease", addon.getId().getArtifactId());
        assertEquals("2.1.0", addon.getId().getVersion());
        assertEquals(2, addon.getBundles().size());
        assertEquals("prerelease.addon.bundle", addon.getBundles().get(0).getId().getArtifactId());
        assertEquals("another.addon.bundle", addon.getBundles().get(1).getId().getArtifactId());
        assertEquals(1, addon.getConfigurations().size());
    }

    /**
     * Input:
     * - stable and prerelease add-on features have the same version
     * - both contain overlapping bundles/configurations/extensions with different values
     *
     * Expected:
     * - add-on conflict resolver is used
     * - prerelease values are preferred for duplicates
     * - non-duplicate stable add-on data remains in result
     */
    @Test
    public void testGetAddOnResolvesSameVersionConflict() throws IOException {
        final ArtifactId stableAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon:1.5.0");
        final ArtifactId prereleaseAddonId = ArtifactId.fromMvnId("com.adobe.aem:aem-addon-prerelease:1.5.0");

        FeatureProvider fp = id -> {
            if ("aem-addon".equals(id.getArtifactId())) {
                final Feature stable = new Feature(id);
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:stable.addon.bundle:1")));
                stable.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:shared.addon.bundle:2")));

                final Configuration stableConfig = new Configuration("addon.pid");
                stableConfig.getProperties().put("addon.source", "stable");
                stable.getConfigurations().add(stableConfig);

                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                stable.getExtensions().add(apiRegions);

                final Extension other = new Extension(ExtensionType.TEXT, "other-ext", ExtensionState.OPTIONAL);
                other.setText("stable-other-ext-content");
                stable.getExtensions().add(other);
                return stable;
            }
            if ("aem-addon-prerelease".equals(id.getArtifactId())) {
                final Feature prerelease = new Feature(id);
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:prerelease.addon.bundle:1")));
                prerelease.getBundles().add(new org.apache.sling.feature.Artifact(ArtifactId.fromMvnId("g:shared.addon.bundle:1")));

                final Configuration prereleaseConfig = new Configuration("addon.pid");
                prereleaseConfig.getProperties().put("addon.source", "prerelease");
                prerelease.getConfigurations().add(prereleaseConfig);

                final Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
                prerelease.getExtensions().add(apiRegions);

                final Extension other = new Extension(ExtensionType.TEXT, "other-ext", ExtensionState.OPTIONAL);
                other.setText("prerelease-other-ext-content");
                prerelease.getExtensions().add(other);
                return prerelease;
            }
            return new Feature(id);
        };

        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(
                fp,
                ArtifactId.fromMvnId("com.adobe.aem:aem-sdk-api:1.0.0"),
                null,
                Collections.singletonList(stableAddonId),
                Collections.singletonList(prereleaseAddonId));

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(EnumSet.of(ServiceType.AUTHOR));
        List<Feature> features = res.get(SdkProductVariation.AUTHOR);

        assertEquals(2, features.size());
        Feature addon = features.get(1);
        assertEquals("aem-addon-prerelease", addon.getId().getArtifactId());
        assertEquals("1.5.0", addon.getId().getVersion());

        assertEquals(3, addon.getBundles().size());
        assertEquals("prerelease.addon.bundle", addon.getBundles().get(0).getId().getArtifactId());
        assertEquals("1", addon.getBundles().get(0).getId().getVersion());
        assertEquals("shared.addon.bundle", addon.getBundles().get(1).getId().getArtifactId());
        assertEquals("1", addon.getBundles().get(1).getId().getVersion());
        assertEquals("stable.addon.bundle", addon.getBundles().get(2).getId().getArtifactId());
        assertEquals("1", addon.getBundles().get(2).getId().getVersion());

        assertEquals(1, addon.getConfigurations().size());
        Configuration config = addon.getConfigurations().iterator().next();
        assertEquals("addon.pid", config.getPid());
        assertEquals("prerelease", config.getProperties().get("addon.source"));

        Extension apiRegionsExt = addon.getExtensions().getByName("api-regions");
        assertNotNull(apiRegionsExt);
        assertEquals(ExtensionType.JSON, apiRegionsExt.getType());

        Extension otherExt = addon.getExtensions().getByName("other-ext");
        assertNotNull(otherExt);
        assertEquals(ExtensionType.TEXT, otherExt.getType());
        assertEquals("prerelease-other-ext-content", otherExt.getText());
    }
}
