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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
        mojo.includeTasks = Collections.singletonList("mytask");

        ArtifactProvider ap = mojo.getArtifactProvider(mojo.getLocalArtifactProvider());
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

        ArtifactProvider ap = mojo.getArtifactProvider(lam);
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
        ArtifactProvider ap = mojo.getArtifactProvider(null);
        URL url = ap.provide(ArtifactId.fromMvnId("a:b:slingosgifeature:123"));
        assertEquals(ab123.toURI().toURL(), url);

    }

    private static class TestAnalyseMojo extends AnalyseMojo {
        private TestAnalyseMojo(MavenProject prj) {
            project = prj;
        }
    }
}
