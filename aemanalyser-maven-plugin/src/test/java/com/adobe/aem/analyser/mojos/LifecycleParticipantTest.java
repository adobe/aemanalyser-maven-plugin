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

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.maven.Environment;
import org.apache.sling.feature.maven.FeatureProjectInfo;
import org.codehaus.plexus.logging.Logger;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LifecycleParticipantTest {
    @Test
    public void testAfterProjectsRead() throws Exception {
        List<Environment> preprocessed = new ArrayList<>();
        LifecycleParticipant lp = new LifecycleParticipant() {
            @Override
            void preProcess(Environment env) {
                preprocessed.add(env);
            }
        };

        Logger logger = Mockito.mock(Logger.class);
        setParameter(lp, "logger", logger);
        ArtifactHandlerManager ahm = Mockito.mock(ArtifactHandlerManager.class);
        setParameter(lp, "artifactHandlerManager", ahm);
        ArtifactResolver resolver = Mockito.mock(ArtifactResolver.class);
        setParameter(lp, "resolver", resolver);

        Plugin thisPlugin = Mockito.mock(Plugin.class);

        MavenProject p1 = Mockito.mock(MavenProject.class);
        Mockito.when(p1.getPlugin("com.adobe.aem:aemanalyser-maven-plugin"))
            .thenReturn(thisPlugin);
        Mockito.when(p1.getGroupId()).thenReturn("grp1");
        Mockito.when(p1.getArtifactId()).thenReturn("art1");

        MavenProject p2 = Mockito.mock(MavenProject.class);

        List<MavenProject> projects = Arrays.asList(p1, p2);

        MavenSession ms = Mockito.mock(MavenSession.class);
        Mockito.when(ms.getProjects()).thenReturn(projects);

        assertEquals("Precondition", 0, preprocessed.size());
        lp.afterProjectsRead(ms);
        assertEquals(1, preprocessed.size());

        Environment env = preprocessed.get(0);
        assertSame(ahm, env.artifactHandlerManager);
        assertSame(resolver, env.resolver);
        assertSame(logger, env.logger);
        assertSame(ms, env.session);
        assertEquals(1, env.modelProjects.size());

        FeatureProjectInfo info = env.modelProjects.get("grp1:art1");
        assertSame(thisPlugin, info.plugin);
        assertSame(p1, info.project);
    }
}
