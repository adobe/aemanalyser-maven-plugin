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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.junit.Test;

public class AemSdkProductFeatureGeneratorTest {

    @Test
    public void testGetProductAggregatesAuthor() throws IOException {
        FeatureProvider fp = id -> new Feature(id);

        ArtifactId sdkID = ArtifactId.fromMvnId("foo:bar:123");
        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, sdkID, Collections.emptyList());

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(new ServiceType[] {ServiceType.AUTHOR});
        assertEquals(1, res.size());
        List<Feature> fl = res.get(SdkProductVariation.AUTHOR);
        assertEquals(1, fl.size());
        Feature f = fl.get(0);
        assertEquals("bar", f.getId().getArtifactId());
    }

    @Test
    public void testGetProductAggregates() throws IOException {
        FeatureProvider fp = id -> new Feature(id);

        ArtifactId sdkID = ArtifactId.fromMvnId("foo:bar:123");
        ArtifactId addonID = ArtifactId.fromMvnId("my:testaddon:999");
        AemSdkProductFeatureGenerator pg = new AemSdkProductFeatureGenerator(fp, sdkID, Collections.singletonList(addonID));

        Map<ProductVariation, List<Feature>> res = pg.getProductAggregates(ServiceType.values());
        assertEquals(2, res.size());
        List<Feature> fla = res.get(SdkProductVariation.AUTHOR);
        assertEquals(2, fla.size());
        assertEquals("bar", fla.get(0).getId().getArtifactId());
        assertEquals("testaddon", fla.get(1).getId().getArtifactId());

        List<Feature> flp = res.get(SdkProductVariation.PUBLISH);
        assertEquals(2, flp.size());
        assertEquals("bar", flp.get(0).getId().getArtifactId());
        assertEquals("testaddon", flp.get(1).getId().getArtifactId());
    }
}
