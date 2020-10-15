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
import org.apache.maven.project.MavenProject;
import org.apache.sling.cpconverter.maven.mojos.ContentPackage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConvertToFeatureModelMojoTest {
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
    public void testExecuteAEMAnalyseProject() throws Exception {
        ConvertToFeatureModelMojo mojo = new ConvertToFeatureModelMojo();
        mojo.unitTestMode = true;

        Dependency dep = new Dependency();
        dep.setType("zip");
        dep.setGroupId("dg");
        dep.setArtifactId("da");
        Dependency dep2 = new Dependency();
        dep2.setType("pom");
        dep2.setGroupId("dg2");
        dep2.setArtifactId("da2");

        Build build = new Build();
        build.setDirectory(tempDir.toString());

        MavenProject prj = new MavenProject();
        prj.setGroupId("g");
        prj.setArtifactId("a");
        prj.setVersion("7");
        prj.setBuild(build);
        prj.setDependencies(List.of(dep, dep2));
        prj.setPackaging(ConvertToFeatureModelMojo.AEM_ANALYSE_PACKAGING);

        setParameter(mojo, "project", prj);

        mojo.execute();

        assertFalse((Boolean) TestUtil.getField(mojo, "installConvertedCP"));
        File cpOutputDir = (File) TestUtil.getField(mojo, "convertedCPOutput");
        Path cpP = cpOutputDir.toPath();
        assertEquals(tempDir, cpP.getParent());
        File fmOutDir = (File) TestUtil.getField(mojo, "fmOutput");
        assertEquals(cpP, fmOutDir.toPath().getParent());

        @SuppressWarnings("unchecked")
        List<ContentPackage> cpl = (List<ContentPackage>) TestUtil.getField(
                mojo, "contentPackages");
        assertEquals(1, cpl.size());
        ContentPackage cp = cpl.get(0);
        assertEquals("dg", TestUtil.getField(cp, "groupId"));
        assertEquals("da", TestUtil.getField(cp, "artifactId"));
    }

    @Test
    public void testExecuteCurrentProject() throws Exception {
        ConvertToFeatureModelMojo mojo = new ConvertToFeatureModelMojo();
        mojo.unitTestMode = true;

        Build build = new Build();
        build.setDirectory(tempDir.toString());

        MavenProject prj = new MavenProject();
        prj.setGroupId("g");
        prj.setArtifactId("a");
        prj.setVersion("7");
        prj.setBuild(build);
        // Note project has no packaging set

        setParameter(mojo, "project", prj);

        mojo.execute();

        assertFalse((Boolean) TestUtil.getField(mojo, "installConvertedCP"));
        File cpOutputDir = (File) TestUtil.getField(mojo, "convertedCPOutput");
        Path cpP = cpOutputDir.toPath();
        assertEquals(tempDir, cpP.getParent());
        File fmOutDir = (File) TestUtil.getField(mojo, "fmOutput");
        assertEquals(cpP, fmOutDir.toPath().getParent());

        @SuppressWarnings("unchecked")
        List<ContentPackage> cpl = (List<ContentPackage>) TestUtil.getField(
                mojo, "contentPackages");
        assertEquals(1, cpl.size());
        ContentPackage cp = cpl.get(0);
        assertEquals("g", TestUtil.getField(cp, "groupId"));
        assertEquals("a", TestUtil.getField(cp, "artifactId"));
    }
}
