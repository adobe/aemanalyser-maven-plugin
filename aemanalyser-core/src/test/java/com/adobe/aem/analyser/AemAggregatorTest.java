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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.extension.apiregions.api.artifacts.ArtifactRules;
import org.apache.sling.feature.extension.apiregions.api.artifacts.VersionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AemAggregatorTest {
    public @Rule TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testUserAggregates1() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("lala:hoho:0.0.1"));
        agg.setAddOnIds(Arrays.asList(ArtifactId.parse("com.adobe.aem:hihi:1.2.2"), ArtifactId.parse("com.adobe.aem:aem-sdk-api:9.9.1")));

        copyTestResource("mappingfiles/runmode_1.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        final Map<String, Feature> projectFeatures = new HashMap<>();
        projectFeatures.put("test1.all.json", new Feature(ArtifactId.parse("g:f:1")));

        final Map<String, List<Feature>> aggregates = agg.getUserAggregates(projectFeatures);
        assertEquals(2, aggregates.size());

        boolean authorFound = false;
        boolean publishFound = false;
        for (Map.Entry<String, List<Feature>> entry : aggregates.entrySet()) {

            switch (entry.getKey()) {
            case "user-aggregated-author":
                authorFound = true;
                break;
            case "user-aggregated-publish":
                publishFound = true;
                break;
            default:
                fail("Unexpected classifier: " + entry.getKey());
            }
        }

        assertTrue(authorFound);
        assertTrue(publishFound);
    }

    @Test
    public void testUserAggregatesWithArtifactOverride() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("lala:hoho:0.0.1"));
        agg.setAddOnIds(Arrays.asList(ArtifactId.parse("com.adobe.aem:hihi:1.2.2"), ArtifactId.parse("com.adobe.aem:aem-sdk-api:9.9.1")));

        copyTestResource("mappingfiles/runmode_3.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        final Map<String, Feature> projectFeatures = new HashMap<>();
        projectFeatures.put("test1.author.json", new Feature(ArtifactId.parse("g:f:zip:cp2fm:2")));
        projectFeatures.put("test1.all.json", new Feature(ArtifactId.parse("g:f:zip:cp2fm:1")));
        final Map<String, List<Feature>> aggregates = agg.getUserAggregates(projectFeatures);

        List<Feature> features = agg.aggregate(aggregates, AemAggregator.Mode.USER,projectFeatures) ;
        assertEquals(features.get(0).getExtensions().get(0).getArtifacts().get(0).getId().toString(),"g:f:zip:cp2fm:2");
        assertEquals(features.get(1).getExtensions().get(0).getArtifacts().get(0).getId().toString(),"g:f:zip:cp2fm:1");

    }

    @Test
    public void testUserAggregates2() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("lala:hoho:0.0.1"));
        agg.setAddOnIds(Arrays.asList(ArtifactId.parse("com.adobe.aem:hihi:1.2.2"), ArtifactId.parse("com.adobe.aem:aem-sdk-api:9.9.1")));

        copyTestResource("mappingfiles/runmode_2.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        final Map<String, Feature> projectFeatures = new HashMap<>();
        projectFeatures.put("test1.all.json", new Feature(ArtifactId.parse("g:f:1")));
        projectFeatures.put("test1.author.json", new Feature(ArtifactId.parse("g:f:2")));
        projectFeatures.put("test1.author-prod.json", new Feature(ArtifactId.parse("g:f:3")));

        final Map<String, List<Feature>> aggregates = agg.getUserAggregates(projectFeatures);
        assertEquals(3, aggregates.size());

        boolean authorFound = false;
        boolean authorProdFound = false;
        boolean publishFound = false;
        for (Map.Entry<String, List<Feature>> entry : aggregates.entrySet()) {

            switch (entry.getKey()) {
            case "user-aggregated-author":
                authorFound = true;
                break;
            case "user-aggregated-author.prod":
                authorProdFound = true;
                break;
            case "user-aggregated-publish":
                publishFound = true;
                break;
            default:
                fail("Unexpected classifier: " + entry.getKey());
            }
        }

        assertTrue(authorFound);
        assertTrue(authorProdFound);
        assertTrue(publishFound);
    }

    @Test
    public void testProductAggregates() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("lala:hoho:0.0.1"));

        agg.setFeatureProvider(new FeatureProvider(){

            @Override
            public Feature provide(ArtifactId id) {
                return new Feature(id);
            }

        });
        final Map<ProductVariation, List<Feature>> aggregates = agg.getProductAggregates();
        assertEquals(2, aggregates.size());

        boolean authorFound = false;
        boolean publishFound = false;
        for (Map.Entry<ProductVariation, List<Feature>> entry : aggregates.entrySet()) {

            switch (entry.getKey().getProductAggregateName()) {
            case "product-aggregated-author":
                authorFound = true;
                assertEquals(1, entry.getValue().size());
                assertEquals(ArtifactId.parse("lala:hoho:slingosgifeature:aem-author-sdk:0.0.1"), entry.getValue().get(0).getId());
                assertEquals(SdkProductVariation.AUTHOR, entry.getKey());
                break;
            case "product-aggregated-publish":
                publishFound = true;
                assertEquals(1, entry.getValue().size());
                assertEquals(ArtifactId.parse("lala:hoho:slingosgifeature:aem-publish-sdk:0.0.1"), entry.getValue().get(0).getId());
                assertEquals(SdkProductVariation.PUBLISH, entry.getKey());
                break;
            default:
                fail("Unexpected classifier: " + entry.getKey());
            }
        }

        assertTrue(authorFound);
        assertTrue(publishFound);
    }

    @Test
    public void testFinalAggregates() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("lala:hoho:0.0.1"));

        agg.setFeatureProvider(new FeatureProvider(){

            @Override
            public Feature provide(ArtifactId id) {
                return new Feature(id);
            }

        });
        final Map<String, List<Feature>> userAggregates = new HashMap<>();
        userAggregates.put("user-aggregated-author", Collections.emptyList());
        userAggregates.put("user-aggregated-author.prod", Collections.emptyList());
        userAggregates.put("user-aggregated-publish", Collections.emptyList());

        final Map<String, Feature> projectFeatures = new HashMap<>();
        projectFeatures.put("user-aggregated-author", new Feature(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-author:4")));
        projectFeatures.put("user-aggregated-author.prod", new Feature(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-author.prod:4")));
        projectFeatures.put("user-aggregated-publish", new Feature(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-publish:4")));
        projectFeatures.put("product-aggregated-author", new Feature(agg.getSdkId().changeClassifier("product-aggregated-author").changeType("slingosgifeature")));
        projectFeatures.put("product-aggregated-publish", new Feature(agg.getSdkId().changeClassifier("product-aggregated-publish").changeType("slingosgifeature")));

        final Map<String, List<Feature>> aggregates = agg.getFinalAggregates(userAggregates, projectFeatures);
        assertEquals(3, aggregates.size());

        boolean authorFound = false;
        boolean publishFound = false;
        boolean authorProdFound = false;
        for (Map.Entry<String, List<Feature>> entry : aggregates.entrySet()) {

            switch (entry.getKey()) {
            case "aggregated-author":
                authorFound = true;
                assertEquals(2, entry.getValue().size());
                assertEquals(agg.getSdkId().changeClassifier("product-aggregated-author").changeType("slingosgifeature"), entry.getValue().get(0).getId());
                assertEquals(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-author:4"), entry.getValue().get(1).getId());
                break;
            case "aggregated-author.prod":
                authorProdFound = true;
                assertEquals(agg.getSdkId().changeClassifier("product-aggregated-author").changeType("slingosgifeature"), entry.getValue().get(0).getId());
                assertEquals(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-author.prod:4"), entry.getValue().get(1).getId());
                assertEquals(2, entry.getValue().size());
                break;
            case "aggregated-publish":
                publishFound = true;
                assertEquals(2, entry.getValue().size());
                assertEquals(agg.getSdkId().changeClassifier("product-aggregated-publish").changeType("slingosgifeature"), entry.getValue().get(0).getId());
                assertEquals(ArtifactId.parse("projectgroup:project:slingosgifeature:user-aggregated-publish:4"), entry.getValue().get(1).getId());
                break;
            default:
                fail("Unexpected classifier: " + entry.getKey());
            }
        }

        assertTrue(authorFound);
        assertTrue(publishFound);
        assertTrue(authorProdFound);
    }

    private void copyTestResource(String resource, String file) throws IOException {
        URL runmodesFile = getClass().getResource("/" + resource);
        try (InputStream is = runmodesFile.openStream()) {
            Path targetPath = tempDir.getRoot().toPath().resolve(file);
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath);
        }
    }

    @Test
    public void testPostProcessProductFeatureNoRules() {
        // this test just ensures that features without rules do not cause problems
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        final AemAggregator agg = new AemAggregator();
        agg.postProcessProductFeature(f);
        final ArtifactRules newRules = ArtifactRules.getArtifactRules(f);
        assertNotNull(newRules);
    }

    @Test
    public void testPostProcessProductFeatureRules() {
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));

        final ArtifactRules rules = new ArtifactRules();
        final VersionRule rule1 = new VersionRule();
        rule1.setArtifactId(ArtifactId.parse("g:a:zip:c:1"));
        rules.getArtifactVersionRules().add(rule1);
        final VersionRule rule2 = new VersionRule();
        rule2.setArtifactId(ArtifactId.parse("g:b:jar:c:1"));
        rules.getArtifactVersionRules().add(rule2);
        final VersionRule rule3 = new VersionRule();
        rule3.setArtifactId(ArtifactId.parse("g:c:zip:1"));
        rules.getArtifactVersionRules().add(rule3);
        final VersionRule rule4 = new VersionRule();
        rules.getArtifactVersionRules().add(rule4);

        ArtifactRules.setArtifactRules(f, rules);

        final AemAggregator agg = new AemAggregator();
        agg.postProcessProductFeature(f);

        final ArtifactRules newRules = ArtifactRules.getArtifactRules(f);
        assertEquals(4, newRules.getArtifactVersionRules().size());

        assertEquals(ArtifactId.parse("g:a:zip:cp2fm-converted:1"), newRules.getArtifactVersionRules().get(0).getArtifactId());
        assertEquals(ArtifactId.parse("g:b:jar:c:1"), newRules.getArtifactVersionRules().get(1).getArtifactId());
        assertEquals(ArtifactId.parse("g:c:zip:cp2fm-converted:1"), newRules.getArtifactVersionRules().get(2).getArtifactId());
        assertNull(newRules.getArtifactVersionRules().get(3).getArtifactId());
    }

    @Test
    public void testResolveSdkProductVariationNullValue() {
        final Feature f = new Feature(ArtifactId.parse("g:a:zip:cp2fm-converted:1"));

        AemAggregator agg = new AemAggregator();
        SdkProductVariation sdkProductVariation = agg.resolveSdkProductVariation(f);

        assertNull(sdkProductVariation);
    }

    @Test
    public void testResolveSdkProductVariation() {
        final Feature f = new Feature(ArtifactId.parse("g:a:zip:user-aggregated-publish:1"));

        AemAggregator agg = new AemAggregator();
        SdkProductVariation sdkProductVariation = agg.resolveSdkProductVariation(f);

        assertEquals(SdkProductVariation.PUBLISH, sdkProductVariation);
    }

    @Test
    public void testCopyingExtensionsEmptyExtension() {
        final Feature f = new Feature(ArtifactId.parse("g:a:zip:user-aggregated-publish:1"));
        List<Feature> userResult = List.of(f);

        Map<ProductVariation, List<Feature>> productAggregates = new HashMap<>();
        final Feature f21 = new Feature(ArtifactId.parse("g:a:zip:aem-publish-sdk:1"));
        Extension ext1 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext1.setText("{\"key\":\"1\"}");
        f21.getExtensions().add(ext1);

        productAggregates.put(SdkProductVariation.PUBLISH, List.of(f21));

        AemAggregator agg = new AemAggregator();
        agg.copyArtifactRulesFromProductAggregates(productAggregates, userResult);

        assertEquals(1, userResult.size());
        assertEquals(1, userResult.get(0).getExtensions().size());
        assertEquals("{\"key\":\"1\"}", userResult.get(0).getExtensions().get(0).getText());
    }

    @Test
    public void testCopyingExtensionsReplaceExtension() {
        final Feature f1 = new Feature(ArtifactId.parse("g:a:zip:user-aggregated-publish:1"));
        Extension ext1 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext1.setText("{}");
        f1.getExtensions().add(ext1);

        List<Feature> userResult = List.of(f1);

        Map<ProductVariation, List<Feature>> productAggregates = new HashMap<>();
        final Feature f21 = new Feature(ArtifactId.parse("g:a:zip:aem-publish-sdk:1"));
        Extension ext21 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext21.setText("{\"key\":\"1\"}");
        f21.getExtensions().add(ext21);

        productAggregates.put(SdkProductVariation.PUBLISH, List.of(f21));

        AemAggregator agg = new AemAggregator();
        agg.copyArtifactRulesFromProductAggregates(productAggregates, userResult);

        assertEquals(1, userResult.size());
        assertEquals(1, userResult.get(0).getExtensions().size());
        assertEquals("{\"key\":\"1\"}", userResult.get(0).getExtensions().get(0).getText());
    }

    @Test
    public void testCopyingExtensionsTwoFeatures() {
        final Feature f1 = new Feature(ArtifactId.parse("g:a:zip:user-aggregated-publish:1"));
        Extension ext1 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext1.setText("{}");
        f1.getExtensions().add(ext1);

        final Feature f2 = new Feature(ArtifactId.parse("g:a:zip:user-aggregated-author:1"));
        Extension ext2 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext2.setText("{}");
        f2.getExtensions().add(ext2);

        List<Feature> userResult = List.of(f1, f2);

        final Feature f21 = new Feature(ArtifactId.parse("g:a:zip:aem-publish-sdk:1"));
        Extension ext21 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext21.setText("{\"key\":\"21\"}");
        f21.getExtensions().add(ext21);

        final Feature f22 = new Feature(ArtifactId.parse("g:a:zip:aem-author-sdk:1"));
        Extension ext22 = new Extension(ExtensionType.TEXT, "artifact-rules", ExtensionState.OPTIONAL);
        ext22.setText("{\"key\":\"22\"}");
        f22.getExtensions().add(ext22);

        Map<ProductVariation, List<Feature>> productAggregates = Map.of(SdkProductVariation.PUBLISH, List.of(f21), SdkProductVariation.AUTHOR, List.of(f22));

        AemAggregator agg = new AemAggregator();
        agg.copyArtifactRulesFromProductAggregates(productAggregates, userResult);

        assertEquals(2, userResult.size());
        assertEquals(1, userResult.get(0).getExtensions().size());
        assertEquals("{\"key\":\"21\"}", userResult.get(0).getExtensions().get(0).getText());
        assertEquals("{\"key\":\"22\"}", userResult.get(1).getExtensions().get(0).getText());
    }
}
