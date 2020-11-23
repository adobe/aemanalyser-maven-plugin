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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.maven.mojos.Scan;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    public void testExecute() throws Exception {
        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.toString());

        List<ArtifactRepository> repos = new ArrayList<>();

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getBuild()).thenReturn(build);
        Mockito.when(prj.getRemoteArtifactRepositories()).thenReturn(repos);
        Mockito.when(prj.getContextValue(AggregateWithSDKMojo.class.getName() + "-aggregates"))
            .thenReturn(new HashSet<>(Arrays.asList("agg1", "agg2")));

        AnalyseMojo mojo = new TestAnalyseMojo(prj);
        mojo.unitTestMode = true;
        mojo.includeTasks = Collections.emptyList();

        assertEquals("Precondition", 0, repos.size());
        mojo.execute();
        assertEquals(1, repos.size());
        MavenArtifactRepository repo = (MavenArtifactRepository) repos.iterator().next();
        assertEquals(tempDir.toFile().toURI().toURL() + "cp-conversion", repo.getUrl());

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
