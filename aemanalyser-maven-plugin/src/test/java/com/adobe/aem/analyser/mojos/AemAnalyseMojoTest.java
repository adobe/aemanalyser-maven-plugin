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
import java.util.Comparator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AemAnalyseMojoTest {

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
    public void testArtifactProvider() throws Exception {
        File ab123 = Files.createTempFile(tempDir, "test", ".tmp").toFile();

        List<Artifact> attached = new ArrayList<>();
        Artifact a = Mockito.mock(Artifact.class);
        Mockito.when(a.getGroupId()).thenReturn("a");
        Mockito.when(a.getArtifactId()).thenReturn("b");
        Mockito.when(a.getVersion()).thenReturn("123");
        Mockito.when(a.getType()).thenReturn("jar");
        Mockito.when(a.getFile()).thenReturn(ab123);
        attached.add(a);

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.toString());
        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild()).thenReturn(build);

        Mockito.when(prj.getAttachedArtifacts()).thenReturn(attached);

        AemAnalyseMojo mojo = new TestAnalyseMojo(prj);

        try (ArtifactManager artifactManager = mojo.getArtifactManager()) {
            ArtifactProvider ap = mojo.getCompositeArtifactProvider(artifactManager);
            URL url = ap.provide(ArtifactId.fromMvnId("a:b:123"));
            assertEquals(ab123.toURI().toURL(), url);
        }
    }

    @Test
    public void testArtifactManager() throws Exception {
        Path dir = tempDir.resolve("cp-conversion/d/e/f/456");
        Files.createDirectories(dir);
        Path def456 = dir.resolve("f-456.jar");
        Files.createFile(def456);

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.toString());

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild()).thenReturn(build);

        AemAnalyseMojo mojo = new TestAnalyseMojo(prj);

        try (ArtifactManager am = mojo.getArtifactManager()) {
            URL url2 = am.provide(ArtifactId.fromMvnId("d.e:f:456"));
            assertEquals(def456.toUri().toURL(), url2);
        }
    }

    @Test
    public void testGetAnalyserTasks() {
        MavenProject prj = Mockito.mock(MavenProject.class);
        AemAnalyseMojo mojo = new TestAnalyseMojo(prj);
        
        mojo.analyserTasks = ImmutableList.of("task1","task2","task3","task4");
        mojo.analyserUserTasks = ImmutableList.of("utask1","utask2","utask3");

        assertEquals(ImmutableSet.of("task1","task2","task3","task4"), mojo.getAnalyserTasks());
        assertEquals(ImmutableSet.of("utask1","utask2","utask3"), mojo.getAnalyserUserTasks());
    }

    @Test
    public void testGetAnalyserTasksWithSkip() {
        MavenProject prj = Mockito.mock(MavenProject.class);
        AemAnalyseMojo mojo = new TestAnalyseMojo(prj);
        
        mojo.analyserTasks = ImmutableList.of("task1","task2","task3","task4");
        mojo.skipAnalyserTasks = ImmutableList.of("task3","task2");
        mojo.analyserUserTasks = ImmutableList.of("utask1","utask2","utask3");
        mojo.skipAnalyserUserTasks = ImmutableList.of("utask1","utask5");

        assertEquals(ImmutableSet.of("task1","task4"), mojo.getAnalyserTasks());
        assertEquals(ImmutableSet.of("utask2","utask3"), mojo.getAnalyserUserTasks());
    }

    @Test
    public void testGetContentPackagesWithClassifier() throws MojoExecutionException {
        MavenProject prj = Mockito.mock(MavenProject.class);
        Artifact packageArtifact = new DefaultArtifact("group", "artifact", "1.0", null, Constants.PACKAGING_CONTENT_PACKAGE, "myclassifier", new ContentPackageArtifactHandler());
        Artifact signatureForPackageArtifact = new DefaultArtifact("group", "artifact", "1.0", null, Constants.PACKAGING_ZIP, "myclassifier", new DefaultArtifactHandler("zip.asc"));
        Mockito.when(prj.getAttachedArtifacts()).thenReturn(
            List.of(
                packageArtifact,
                signatureForPackageArtifact
            )
        );
        AemAnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.classifier = "myclassifier";

        assertEquals(List.of(packageArtifact), mojo.getContentPackages());
    }

    // copied from https://github.com/apache/jackrabbit-filevault-package-maven-plugin/blob/filevault-package-maven-plugin-1.4.0/src/main/java/org/apache/jackrabbit/filevault/maven/packaging/impl/extensions/ContentPackageArtifactHandler.java
    private static final class ContentPackageArtifactHandler extends DefaultArtifactHandler {
        public ContentPackageArtifactHandler() {
            super(Constants.PACKAGING_CONTENT_PACKAGE);
            setIncludesDependencies(true);
            setExtension("zip");
            setLanguage("java");
            setAddedToClasspath(true);
        }
    }

    private static class TestAnalyseMojo extends AemAnalyseMojo {
        private TestAnalyseMojo(final MavenProject prj) {
            this.project = prj;
        }
    }

}
