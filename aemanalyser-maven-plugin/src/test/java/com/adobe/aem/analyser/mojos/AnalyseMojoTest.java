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

import org.apache.sling.feature.maven.mojos.Scan;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AnalyseMojoTest {
    @Test
    public void testExecute() throws Exception {
        AnalyseMojo mojo = new AnalyseMojo();
        mojo.unitTestMode = true;

        mojo.execute();

        assertNotNull(TestUtil.getField(
                mojo, mojo.getClass(), "framework"));

        @SuppressWarnings("unchecked")
        List<Scan> scans = (List<Scan>) TestUtil.getField(
                mojo, mojo.getClass(), "scans");
        assertEquals(1, scans.size());
        Scan scan = scans.get(0);
        assertEquals(1, scan.getSelections().size());

        // The following doesn't work because getSelections() returns a private type
//        Selection sel = scan.getSelections().get(0);
//        assertEquals("aggregated", scan.getSelections());
    }
}
