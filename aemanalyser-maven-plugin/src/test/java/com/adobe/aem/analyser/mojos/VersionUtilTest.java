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

import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.junit.Test;
import org.mockito.Mockito;

public class VersionUtilTest {

    @Test
    public void testGetArtifactIdFromDependencies() throws Exception {
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

        final VersionUtil util = new TestVersionUtil(prj);

        assertEquals(new ArtifactId(sdkDep.getGroupId(), sdkDep.getArtifactId(), sdkDep.getVersion(), sdkDep.getClassifier(), sdkDep.getType()), 
                util.getArtifactIdFromDependencies("com.adobe.aem", "aem-sdk-api"));
    }

    @Test
    public void testDiscoverAddons() throws Exception {
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

        final VersionUtil util = new TestVersionUtil(prj);
        final List<ArtifactId> addons = util.discoverAddons(null, true);
        assertEquals(1, addons.size());
        assertEquals(new ArtifactId(sdkDep.getGroupId(), sdkDep.getArtifactId(), sdkDep.getVersion(), sdkDep.getClassifier(), sdkDep.getType()), 
                addons.get(0));
    }
    
    @Test
    public void testGetSDKArtifactIdWithConfiguredVersionAndArtifactId() throws Exception {
        final MavenProject prj = Mockito.mock(MavenProject.class);
        final VersionUtil util = new TestVersionUtil(prj);

        final ArtifactId id = util.getSDKArtifactId("my-artifact", "5.0", false);
        assertEquals(Constants.SDK_GROUP_ID, id.getGroupId());
        assertEquals("my-artifact", id.getArtifactId());
        assertEquals("5.0", id.getVersion());
    }

    @Test(expected = MojoExecutionException.class)
    public void testGetSDKArtifactIdWithoutDependency() throws Exception {
        final MavenProject prj = Mockito.mock(MavenProject.class);
        final VersionUtil util = new TestVersionUtil(prj);

        util.getSDKArtifactId(Constants.SDK_ARTIFACT_ID, null, false);
    }

    private static class TestVersionUtil extends VersionUtil {
        private TestVersionUtil(final MavenProject prj) {
            super(Mockito.mock(Log.class), prj, null, null, null, null, false);
        }

        String getLatestVersion(final Dependency dependencies) throws MojoExecutionException {
            return null;
        }
    }
}
