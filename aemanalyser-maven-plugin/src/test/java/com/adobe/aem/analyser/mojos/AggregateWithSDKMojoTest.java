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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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

    @Test(expected = MojoExecutionException.class)
    public void testGetSDKFromDependencies() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        MavenProject prj = Mockito.mock(MavenProject.class);
        MojoUtils.setParameter(mojo, "project", prj);

        mojo.getSDKFromDependencies("com.adobe.aem", "aem-sdk-api", true);
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
        assertEquals(new ArtifactId(sdkDep.getGroupId(), sdkDep.getArtifactId(), sdkDep.getVersion(), sdkDep.getClassifier(), sdkDep.getType()), 
                mojo.getSDKFromDependencies("com.adobe.aem", "aem-sdk-api", true));
    }

    @Test
    public void testGetSDKFromDedpendencies2() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();

        Dependency dep = new Dependency();
        Dependency sdkDep = new Dependency();
        sdkDep.setGroupId("com.adobe.aem");
        sdkDep.setArtifactId("aem-cif-sdk-api");
        sdkDep.setClassifier("aem-cif-sdk");
        sdkDep.setVersion("1.3.2");

        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(dep);
        depMgmt.addDependency(sdkDep);

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencyManagement()).thenReturn(depMgmt);

        MojoUtils.setParameter(mojo, "project", prj);
        final List<ArtifactId> addons = mojo.discoverAddons(null);
        assertEquals(1, addons.size());
        assertEquals(new ArtifactId(sdkDep.getGroupId(), sdkDep.getArtifactId(), sdkDep.getVersion(), sdkDep.getClassifier(), sdkDep.getType()), 
                addons.get(0));
    }
}
