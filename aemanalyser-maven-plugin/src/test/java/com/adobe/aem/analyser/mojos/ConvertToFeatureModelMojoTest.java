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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

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
    public void testExecute() throws Exception {
        ConvertToFeatureModelMojo mojo = new ConvertToFeatureModelMojo();
        mojo.unitTestMode = true;

        Dependency dep = new Dependency();
        dep.setType("zip");
        dep.setGroupId("dg");
        dep.setArtifactId("da");

        Build build = new Build();
        build.setDirectory(tempDir.toString());

        MavenProject prj = new MavenProject();
        prj.setGroupId("g");
        prj.setArtifactId("a");
        prj.setVersion("7");
        prj.setBuild(build);
        prj.setDependencies(Collections.singletonList(dep));

        setParameter(mojo, "project", prj);

        mojo.execute();
        // what to assert?
    }
}
