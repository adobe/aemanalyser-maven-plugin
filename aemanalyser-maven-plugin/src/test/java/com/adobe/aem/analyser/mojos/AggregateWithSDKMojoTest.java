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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AggregateWithSDKMojoTest {
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

        MavenProject prj = Mockito.mock(MavenProject.class);
        Mockito.when(prj.getDependencies())
            .thenReturn(List.of(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement())
            .thenReturn(Mockito.mock(DependencyManagement.class));

        MojoUtils.setParameter(mojo, "project", prj);

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("aggregated", agg.classifier);
        assertTrue(agg.markAsComplete);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem-author-sdk:9.9.1",
                artSelInstr);

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
            .thenReturn(List.of(dep1, dep2, sdk));
        Mockito.when(prj.getDependencyManagement()).thenReturn(dm);

        MojoUtils.setParameter(mojo, "project", prj);

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("aggregated", agg.classifier);
        assertTrue(agg.markAsComplete);

        // Note getSelections() returns a private type...
        List<?> sels = agg.getSelections();
        String artSelInstr = getSelectionInstruction(sels, "ARTIFACT");
        assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:aem-author-sdk:9.9.1",
                artSelInstr);

        String filesInclInstr = getSelectionInstruction(sels, "FILES_INCLUDE");
        assertEquals("**/*.json", filesInclInstr);
    }

    @Test
    public void testSDKIDOverrides() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;
        mojo.sdkGroupId = "foo";
        mojo.sdkArtifactId = "bar";
        mojo.sdkVersion = "42";

        Dependency sdk = new Dependency();
        sdk.setGroupId("com.adobe.aem");
        sdk.setArtifactId("aem-sdk-api");
        sdk.setVersion("9.9.1");

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
