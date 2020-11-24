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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.apache.sling.feature.maven.mojos.Scan;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

public class AnalyseMojoTest {

    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws IOException {
        // Delete the temp dir
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test
    public void testExecute() throws Exception {
        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.toString());

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild()).thenReturn(build);
        Mockito.when(prj.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates"))
            .thenReturn(new HashSet<>(Arrays.asList("agg1", "agg2")));

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.unitTestMode = true;
        mojo.includeTasks = Collections.emptyList();

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Scan> scans = (List<Scan>) TestUtil.getField(
                mojo, mojo.getClass(), "scans");
        assertEquals(1, scans.size());
        Scan scan = scans.get(0);
        assertEquals(2, scan.getSelections().size());

        Map<String,String> expected = new HashMap<>();
        expected.put("regions", "global,com.adobe.aem.deprecated");
        expected.put("warningPackages", "*");
        assertEquals("Default task configuration not as expected",
                expected, scan.getTaskConfiguration().get("api-regions-crossfeature-dups"));

        assertEquals("Default task configuration not as expected",
                "global,com.adobe.aem.deprecated,com.adobe.aem.internal",
                scan.getTaskConfiguration().get("api-regions-check-order").get("order"));

        // Note getSelections() returns a private type...
        List<?> sels = scan.getSelections();
        assertEquals(new HashSet<>(Arrays.asList("agg1", "agg2")),
                getSelectionInstructions(sels, "CLASSIFIER"));
    }

    @Test
    public void testAddTaskConfig() throws Exception {
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild()).thenReturn(Mockito.mock(Build.class));
        Mockito.when(prj.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates"))
            .thenReturn(Collections.singleton("aggregates"));

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.unitTestMode = true;
        mojo.includeTasks = Collections.singletonList("mytask");

        Properties myTaskConfig = new Properties();
        myTaskConfig.put("x", "y");
        Properties overriddenConfig = new Properties();
        overriddenConfig.put("traa", "laa");

        mojo.taskConfiguration = new HashMap<>();
        mojo.taskConfiguration.put("mytask", myTaskConfig);
        mojo.taskConfiguration.put("api-regions-crossfeature-dups", overriddenConfig);

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Scan> scans = (List<Scan>) TestUtil.getField(
                mojo, mojo.getClass(), "scans");
        assertEquals(1, scans.size());
        Scan scan = scans.get(0);

        assertEquals(Collections.singleton("mytask"), scan.getIncludeTasks());
        assertEquals("y", scan.getTaskConfiguration().get("mytask").get("x"));

        assertEquals("Overridden task configuration not as expected",
                Collections.singletonMap("traa", "laa"),
                scan.getTaskConfiguration().get("api-regions-crossfeature-dups"));
        assertEquals("Default task configuration not as expected",
                "global,com.adobe.aem.deprecated,com.adobe.aem.internal",
                scan.getTaskConfiguration().get("api-regions-check-order").get("order"));
    }

    @Test
    public void testLocalArtifactManager() throws Exception {
        File ab123 = Files.createTempFile(tempDir, "test", ".tmp").toFile();

        Path dir = tempDir.resolve("cp-conversion/d/e/f/456");
        Files.createDirectories(dir);
        Path def456 = dir.resolve("f-456.jar");
        Files.createFile(def456);

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.toString());

        List<Artifact> attached = new ArrayList<>();
        Artifact a = Mockito.mock(Artifact.class);
        Mockito.when(a.getGroupId()).thenReturn("a");
        Mockito.when(a.getArtifactId()).thenReturn("b");
        Mockito.when(a.getVersion()).thenReturn("123");
        Mockito.when(a.getType()).thenReturn("jar");
        Mockito.when(a.getFile()).thenReturn(ab123);
        attached.add(a);

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getAttachedArtifacts()).thenReturn(attached);
        Mockito.when(prj.getBuild()).thenReturn(build);
        Mockito.when(prj.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates"))
            .thenReturn(Collections.singleton("aggregates"));

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.unitTestMode = true;
        mojo.includeTasks = Collections.singletonList("mytask");

        mojo.execute();

        ArtifactProvider ap = mojo.getArtifactProvider();
        URL url = ap.provide(ArtifactId.fromMvnId("a:b:123"));
        assertEquals(ab123.toURI().toURL(), url);

        URL url2 = ap.provide(ArtifactId.fromMvnId("d.e:f:456"));
        assertEquals(def456.toUri().toURL(), url2);
    }

    @Test
    public void testCustomArtifactProvider() throws Exception {
        File ab123 = Files.createTempFile(tempDir, "test", ".tmp").toFile();

        Path dir = tempDir.resolve("d/e/f/456");
        Files.createDirectories(dir);
        Path def456 = dir.resolve("f-456.jar");
        Files.createFile(def456);

        List<Artifact> attached = new ArrayList<>();
        Artifact a = Mockito.mock(Artifact.class);
        Mockito.when(a.getGroupId()).thenReturn("a");
        Mockito.when(a.getArtifactId()).thenReturn("b");
        Mockito.when(a.getVersion()).thenReturn("123");
        Mockito.when(a.getType()).thenReturn("jar");
        Mockito.when(a.getFile()).thenReturn(ab123);
        attached.add(a);

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getAttachedArtifacts()).thenReturn(attached);

        ArtifactManagerConfig amc = new ArtifactManagerConfig();
        amc.setRepositoryUrls(new String [] { tempDir.toUri().toString() });
        ArtifactManager lam = ArtifactManager.getArtifactManager(amc);

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.localArtifactManager = lam;

        ArtifactProvider ap = mojo.getArtifactProvider();
        URL url = ap.provide(ArtifactId.fromMvnId("a:b:123"));
        assertEquals(ab123.toURI().toURL(), url);

        URL url2 = ap.provide(ArtifactId.fromMvnId("d.e:f:456"));
        assertEquals(def456.toUri().toURL(), url2);
    }

    @Test
    public void testNoCustomArtifactProvider() throws Exception {
        File ab123 = Files.createTempFile(tempDir, "test", ".tmp").toFile();

        List<Artifact> attached = new ArrayList<>();
        Artifact a = Mockito.mock(Artifact.class);
        Mockito.when(a.getGroupId()).thenReturn("a");
        Mockito.when(a.getArtifactId()).thenReturn("b");
        Mockito.when(a.getVersion()).thenReturn("123");
        Mockito.when(a.getType()).thenReturn("slingosgifeature");
        Mockito.when(a.getFile()).thenReturn(ab123);
        attached.add(a);

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getAttachedArtifacts()).thenReturn(attached);

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        ArtifactProvider ap = mojo.getArtifactProvider();
        URL url = ap.provide(ArtifactId.fromMvnId("a:b:slingosgifeature:123"));
        assertEquals(ab123.toURI().toURL(), url);

    }

    private Set<String> getSelectionInstructions(List<?> sels, String type) throws Exception {
        Set<String> l = new HashSet<>();
        for (Object s : sels) {
            if (type.equals(TestUtil.getField(s, "type").toString())) {
                l.add(TestUtil.getField(s, "instruction").toString());
            }
        }
        return l;
    }

    private static class TestAnalyseMojo extends AnalyseMojo {
        private TestAnalyseMojo(MavenProject prj) {
            project = prj;
        }
    }
}
