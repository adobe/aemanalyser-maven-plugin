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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.extension.apiregions.api.artifacts.ArtifactRules;
import org.apache.sling.feature.extension.apiregions.api.artifacts.Mode;
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
    public void shouldRemoveIncorrectPathsDuringAggregation() throws Exception {
        final AemAggregator agg = new AemAggregator();
        agg.setEnableFixingIncorrectPathsInRepoinit(true);
        agg.setFeatureOutputDirectory(tempDir.newFolder("target", "cp-conversion", "fm.out"));
        agg.setProjectId(ArtifactId.parse("gp:ap:5"));
        agg.setSdkId(ArtifactId.parse("com.adobe.aem:aem-sdk-api:2026.3.24893.20260312T165332Z-260200"));
        agg.setFeatureProvider(Feature::new);

        copyTestResource("mappingfiles/runmode_4.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        copyTestResourceDir("features/",
                "target/cp-conversion/fm.out/");

        agg.aggregate() ;

        Path outputDir = tempDir.getRoot().toPath().resolve("target/cp-conversion/fm.out");
        Path aggregatedAuthorFile = outputDir.resolve("aggregated-author.json");
        Path aggregatedPublishFile = outputDir.resolve("aggregated-publish.json");

        List<String> forbiddenStrings = List.of(
                "\"create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css\"",
                "\"create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js\""
        );

        assertLineCount(aggregatedAuthorFile, 241);
        assertLineCount(aggregatedPublishFile, 241);

        assertFileDoesNotContain(aggregatedAuthorFile, forbiddenStrings);
        assertFileDoesNotContain(aggregatedPublishFile, forbiddenStrings);
    }

    private void assertFileDoesNotContain(Path file, List<String> forbiddenStrings) throws IOException {
        String content = Files.readString(file);
        for (String forbidden : forbiddenStrings) {
            assertFalse("File " + file + " contains forbidden string: " + forbidden, content.contains(forbidden));
        }
    }

    public static void assertLineCount(Path path, int expected) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            assertEquals(expected, lines.count());
        }
    }

    private void copyTestResourceDir(String resourceDir, String targetDir) throws Exception {
        URL url = getClass().getResource("/" + resourceDir);

        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceDir);
        }

        Path sourcePath = Paths.get(url.toURI());
        Path targetPath = tempDir.getRoot().toPath().resolve(targetDir);

        Files.createDirectories(targetPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePath)) {
            for (Path sourceFile : stream) {
                if (Files.isRegularFile(sourceFile)) {
                    Path targetFile = targetPath.resolve(sourceFile.getFileName());
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
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
        assertEquals(Mode.LENIENT, newRules.getMode());
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
}
