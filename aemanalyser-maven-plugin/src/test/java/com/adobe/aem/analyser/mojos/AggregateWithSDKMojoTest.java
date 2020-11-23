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
package com.adobe.aem.analyser.mojos;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AggregateWithSDKMojoTest {
    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws IOException {
        // Delete the temp dir again
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test
    public void testExecute() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;

        Dependency dep1 = new Dependency();
        dep1.setGroupId("lala");
        dep1.setArtifactId("hoho");
        dep1.setVersion("0.0.1");
        Dependency dep2 = new Dependency();
        dep2.setGroupId("com.adobe.aem");
        dep2.setArtifactId("hihi");
        dep2.setVersion("1.2.3");
        Dependency sdk = new Dependency();
        sdk.setGroupId("com.adobe.aem");
        sdk.setArtifactId("aem-sdk-api");
        sdk.setVersion("9.9.1");

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
            .thenReturn(tempDir + "/target");
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencies())
            .thenReturn(Arrays.asList(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement())
            .thenReturn(Mockito.mock(DependencyManagement.class));
        Mockito.when(prj.getBuild())
            .thenReturn(build);
        MojoUtils.setParameter(mojo, "project", prj);

        copyTestResource("mappingfiles/runmode_1.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(2, aggregates.size());

        boolean authorFound = false;
        boolean publishFound = false;
        for (Aggregate agg : aggregates) {
            String apInfix = "";

            switch (agg.classifier) {
            case "aggregated-author":
                authorFound = true;
                apInfix = "-author";
                break;
            case "aggregated-publish":
                publishFound = true;
                apInfix = "-publish";
                break;
            default:
                fail("Unexpected classifier");
            }

            assertFalse(agg.markAsComplete);
            assertEquals(Arrays.asList(
                    "com.adobe.cq:core.wcm.components.core:FIRST",
                    "com.adobe.cq:core.wcm.components.extensions.amp:FIRST",
                    "org.apache.sling:org.apache.sling.models.impl:FIRST",
                    "*:core.wcm.components.content:zip:*:FIRST",
                    "*:core.wcm.components.extensions.amp.content:zip:*:FIRST",
                    "*:*:ALL"),
                    agg.artifactsOverrides);
            assertEquals(Collections.singletonList("*=MERGE_LATEST"), agg.configurationOverrides);

            // Note getSelections() returns a private type...
            List<?> sels = agg.getSelections();
            String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
            assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem"
                    + apInfix + "-sdk:9.9.1",
                    artSelInstr);
            String clsSelInstr = getSelectionInstruction(sels, "CLASSIFIER");
            assertEquals("user-aggregated" + apInfix, clsSelInstr);
        }

        assertTrue(authorFound);
        assertTrue(publishFound);

        File generated = (File) TestUtil.getField(mojo, "generatedFeatures");
        assertEquals(null, generated);
    }

    @Test
    public void testExecute1() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;
        mojo.unitTestEarlyExit = true;

        Dependency dep1 = new Dependency();
        dep1.setGroupId("lala");
        dep1.setArtifactId("hoho");
        dep1.setVersion("0.0.1");
        Dependency dep2 = new Dependency();
        dep2.setGroupId("com.adobe.aem");
        dep2.setArtifactId("hihi");
        dep2.setVersion("1.2.3");
        Dependency sdk = new Dependency();
        sdk.setGroupId("com.adobe.aem");
        sdk.setArtifactId("aem-sdk-api");
        sdk.setVersion("9.9.1");

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
            .thenReturn(tempDir + "/target");
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencies())
            .thenReturn(Arrays.asList(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement())
            .thenReturn(Mockito.mock(DependencyManagement.class));
        Mockito.when(prj.getBuild())
            .thenReturn(build);
        MojoUtils.setParameter(mojo, "project", prj);

        copyTestResource("mappingfiles/runmode_1.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(2, aggregates.size());

        boolean authorFound = false;
        boolean publishFound = false;
        for (Aggregate agg : aggregates) {
            switch (agg.classifier) {
            case "user-aggregated-author":
                authorFound = true;
                break;
            case "user-aggregated-publish":
                publishFound = true;
                break;
            default:
                fail("Unexpected classifier");
            }
            assertFalse(agg.markAsComplete);
            assertEquals(Collections.singletonList("*:*:HIGHEST"), agg.artifactsOverrides);
            assertEquals(Collections.singletonList("*=MERGE_LATEST"), agg.configurationOverrides);

            // Note getSelections() returns a private type...
            List<?> sels = agg.getSelections();
            String filesInclInstr = getSelectionInstruction(sels, "FILES_INCLUDE");
            assertEquals("**/test1.all.json", filesInclInstr);
        }
        assertTrue(authorFound);
        assertTrue(publishFound);

        File expected = new File(tempDir.toFile(), "/target/cp-conversion/fm.out");
        File generated = (File) TestUtil.getField(mojo, "generatedFeatures");
        assertEquals(expected, generated);

    }

    @Test
    public void testExecute2() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;

        Dependency dep1 = new Dependency();
        dep1.setGroupId("lala");
        dep1.setArtifactId("hoho");
        dep1.setVersion("0.0.1");
        Dependency dep2 = new Dependency();
        dep2.setGroupId("com.adobe.aem");
        dep2.setArtifactId("hihi");
        dep2.setVersion("1.2.3");
        Dependency sdk = new Dependency();
        sdk.setGroupId("com.adobe.aem");
        sdk.setArtifactId("aem-sdk-api");
        sdk.setVersion("9.9.1");

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
            .thenReturn(tempDir + "/target");
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencies())
            .thenReturn(Arrays.asList(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement())
            .thenReturn(Mockito.mock(DependencyManagement.class));
        Mockito.when(prj.getBuild())
            .thenReturn(build);
        MojoUtils.setParameter(mojo, "project", prj);

        copyTestResource("mappingfiles/runmode_2.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(3, aggregates.size());

        boolean authorFound = false;
        boolean authorProdFound = false;
        boolean publishFound = false;
        for (Aggregate agg : aggregates) {
            String apInfix = "";

            switch (agg.classifier) {
            case "aggregated-author":
                authorFound = true;
                apInfix = "-author";
                break;
            case "aggregated-author.prod":
                authorProdFound = true;
                apInfix = "-author";
                break;
            case "aggregated-publish":
                publishFound = true;
                apInfix = "-publish";
                break;
            default:
                fail("Unexpected classifier");
            }

            // Note getSelections() returns a private type...
            List<?> sels = agg.getSelections();
            String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
            assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem"
                    + apInfix + "-sdk:9.9.1",
                    artSelInstr);

            String clsSelInstr = getSelectionInstruction(sels, "CLASSIFIER");
            assertEquals("user-" + agg.classifier, clsSelInstr);
        }
        assertTrue(authorFound);
        assertTrue(authorProdFound);
        assertTrue(publishFound);

        Mockito.verify(prj).setContextValue("com.adobe.aem.analyser.mojos.AggregateWithSDKMojo-aggregates",
                new HashSet<>(Arrays.asList("aggregated-author", "aggregated-author.prod", "aggregated-publish")));
    }

    @Test
    public void testSDKIDOverrides() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;
        mojo.sdkGroupId = "foo";
        mojo.sdkArtifactId = "bar";
        mojo.sdkVersion = "42";

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory())
            .thenReturn(tempDir + "/target");
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencyManagement())
            .thenReturn(Mockito.mock(DependencyManagement.class));
        Mockito.when(prj.getBuild())
            .thenReturn(build);
        MojoUtils.setParameter(mojo, "project", prj);

        copyTestResource("mappingfiles/runmode_1.mapping",
                "target/cp-conversion/fm.out/runmode.mapping");

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(2, aggregates.size());
        Aggregate agg = aggregates.get(0);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("foo:bar:slingosgifeature:aem-author-sdk:42",
                artSelInstr);
        assertTrue("foo:bar:slingosgifeature:aem-author-sdk:42".equals(artSelInstr)
                || "foo:bar:slingosgifeature:aem-publish-sdk:42".equals(artSelInstr));
    }

    @Test
    public void testGetUserAggregatesToCreate() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        Properties p = new Properties();

        p.put("prod", "myproj.all-prod.json");
        p.put("stage", "myproj.all-stage.json");
        p.put("publish.stage", "myproj.all-publish.stage.json");
        p.put("publish", "myproj.all-publish.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("(default)", "myproj.all.json");

        Map<String, Set<String>> aggs = mojo.getUserAggregatesToCreate(p);

        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json")),
                aggs.get("author"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-prod.json")),
                aggs.get("author.prod"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-stage.json")),
                aggs.get("author.stage"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-publish.dev.json")),
                aggs.get("publish.dev"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-stage.json", "myproj.all-publish.stage.json")),
                aggs.get("publish.stage"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-prod.json", "myproj.all-publish.prod.json")),
                aggs.get("publish.prod"));
        assertEquals(6, aggs.size());
    }

    @Test
    public void testPruneModels() {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        Map<String, Set<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singleton("x"));
        allModels.put("publish", Collections.singleton("x"));

        Map<String, Set<String>> expected = new HashMap<>(allModels);
        mojo.pruneModels(allModels);
        assertEquals(expected, allModels);
    }

    @Test
    public void testPruneModels2() {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        Map<String, Set<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singleton("x"));
        allModels.put("author.dev", new HashSet<>(Arrays.asList("x", "x1")));
        allModels.put("author.stage", new HashSet<>(Arrays.asList("x", "x2")));
        allModels.put("author.prod", new HashSet<>(Arrays.asList("x", "x3")));
        allModels.put("publish", Collections.singleton("y"));
        allModels.put("publish.dev", new HashSet<>(Arrays.asList("y", "y1", "y2")));
        allModels.put("publish.stage", new HashSet<>(Arrays.asList("y")));
        allModels.put("publish.prod", new HashSet<>(Arrays.asList("y", "y3")));

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("author.dev", new HashSet<>(Arrays.asList("x", "x1")));
        expected.put("author.stage", new HashSet<>(Arrays.asList("x", "x2")));
        expected.put("author.prod", new HashSet<>(Arrays.asList("x", "x3")));
        expected.put("publish", Collections.singleton("y"));
        expected.put("publish.dev", new HashSet<>(Arrays.asList("y", "y1", "y2")));
        expected.put("publish.prod", new HashSet<>(Arrays.asList("y", "y3")));

        mojo.pruneModels(allModels);
        assertEquals(expected, allModels);
    }

    @Test
    public void testGetSDKFromDependencies() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        MavenProject prj = Mockito.mock(MavenProject.class);
        MojoUtils.setParameter(mojo, "project", prj);

        try {
            mojo.getSDKFromDependencies();
            fail("Should have thrown a MojoExecutionException");
        } catch (MojoExecutionException e) {
            // good
            assertTrue(e.getMessage().contains("Unable to find SDK artifact"));
        }
    }

    @Test
    public void testGetSDKFromDependencies2() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        Dependency dep = new Dependency();
        Dependency sdkDep = new Dependency();
        sdkDep.setGroupId("com.adobe.aem");
        sdkDep.setArtifactId("aem-sdk-api");
        sdkDep.setVersion("1.3.5");

        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(dep);
        depMgmt.addDependency(sdkDep);

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencyManagement()).thenReturn(depMgmt);

        MojoUtils.setParameter(mojo, "project", prj);
        assertEquals(sdkDep, mojo.getSDKFromDependencies());
    }

    private void copyTestResource(String resource, String file) throws IOException {
        URL runmodesFile = getClass().getResource("/" + resource);
        try (InputStream is = runmodesFile.openStream()) {
            Path targetPath = tempDir.resolve(file);
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath);
        }
    }

    private String getSelectionInstruction(List<?> sels, String type) throws Exception {
        for (Object s : sels) {
            if (type.equals(TestUtil.getField(s, "type").toString())) {
                return TestUtil.getField(s, "instruction").toString();
            }
        }
        return null;
    }
}
