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
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("aggregated", agg.classifier);
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

        File generated = (File) TestUtil.getField(mojo, "generatedFeatures");
        assertEquals(null, generated);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem-author-sdk:9.9.1",
                artSelInstr);
        String clsSelInstr = getSelectionInstruction(sels, "CLASSIFIER");
        assertEquals("user-aggregated", clsSelInstr);
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

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("user-aggregated", agg.classifier);
        assertFalse(agg.markAsComplete);
        assertEquals(Collections.singletonList("*:*:HIGHEST"), agg.artifactsOverrides);
        assertEquals(Collections.singletonList("*=MERGE_LATEST"), agg.configurationOverrides);

        File expected = new File(tempDir.toFile(), "/target/cp-conversion/fm.out");
        File generated = (File) TestUtil.getField(mojo, "generatedFeatures");
        assertEquals(expected, generated);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String filesInclInstr = getSelectionInstruction(sels, "FILES_INCLUDE");
        assertEquals("**/*.json", filesInclInstr);
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

        MavenProject prj = Mockito.mock(MavenProject.class);
        DependencyManagement dm = Mockito.mock(DependencyManagement.class);
        Mockito.when(dm.getDependencies())
            .thenReturn(Arrays.asList(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement()).thenReturn(dm);
        Mockito.when(prj.getBuild())
            .thenReturn(Mockito.mock(Build.class));

        MojoUtils.setParameter(mojo, "project", prj);

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("aggregated", agg.classifier);
        assertFalse(agg.markAsComplete);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem-author-sdk:9.9.1",
                artSelInstr);

        String clsSelInstr = getSelectionInstruction(sels, "CLASSIFIER");
        assertEquals("user-aggregated", clsSelInstr);
    }

    @Test
    public void testSDKIDOverrides() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;
        mojo.sdkGroupId = "foo";
        mojo.sdkArtifactId = "bar";
        mojo.sdkVersion = "42";

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild())
            .thenReturn(Mockito.mock(Build.class));
        MojoUtils.setParameter(mojo, "project", prj);

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("foo:bar:slingosgifeature:aem-author-sdk:42",
                artSelInstr);
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
