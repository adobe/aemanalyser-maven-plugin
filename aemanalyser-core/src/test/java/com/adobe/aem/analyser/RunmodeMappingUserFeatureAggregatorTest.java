/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RunmodeMappingUserFeatureAggregatorTest {
    public @Rule TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testGetUserAggregatesPublish() throws IOException {
        writeRunmodeMappings(tempDir.getRoot());

        RunmodeMappingUserFeatureAggregator fa = new RunmodeMappingUserFeatureAggregator(tempDir.getRoot());

        Map<String, Feature> pf = new HashMap<>();
        ArtifactId fid = ArtifactId.fromMvnId("blah:blah:1");
        pf.put("blah", new Feature(fid));
        Map<String, List<Feature>> agg = fa.getUserAggregates(pf, new ServiceType[] {ServiceType.PUBLISH});

        assertEquals(1, agg.size());
        List<Feature> f = agg.get("user-aggregated-publish");
        assertEquals(1, f.size());
        assertEquals(fid, f.get(0).getId());
    }

    private void writeRunmodeMappings(File root) throws IOException {
        Properties p = new Properties();
        p.put("(default)", "blah");

        try (OutputStream fos = new FileOutputStream(new File(root, "runmode.mapping"))) {
            p.store(fos, "test");
        }
    }
}
