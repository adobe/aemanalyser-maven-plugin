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

import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AggregateWithSDKMojoTest {
    @Test
    public void testExecute() throws Exception {
        AggregateWithSDKMojo mojo = new AggregateWithSDKMojo();
        mojo.unitTestMode = true;

        mojo.execute();

        @SuppressWarnings("unchecked")
        List<Aggregate> aggregates =
                (List<Aggregate>) TestUtil.getField(mojo,
                        AggregateFeaturesMojo.class, "aggregates");

        assertEquals(1, aggregates.size());
        Aggregate agg = aggregates.get(0);
        assertEquals("aggregated", agg.classifier);
        assertTrue(agg.markAsComplete);
    }
}
