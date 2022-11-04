/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RunModesTest {

    @Test public void testINVALID_MODES() {
        assertEquals(10, RunModes.INVALID_MODES.size());
        assertEquals("author.dev", RunModes.INVALID_MODES.get("dev.author"));
        assertEquals("author.stage", RunModes.INVALID_MODES.get("stage.author"));
        assertEquals("author.prod", RunModes.INVALID_MODES.get("prod.author"));
        assertEquals("publish.dev", RunModes.INVALID_MODES.get("dev.publish"));
        assertEquals("publish.stage", RunModes.INVALID_MODES.get("stage.publish"));
        assertEquals("publish.prod", RunModes.INVALID_MODES.get("prod.publish"));
        assertEquals("author.sdk", RunModes.INVALID_MODES.get("sdk.author"));
        assertEquals("author.rde", RunModes.INVALID_MODES.get("rde.author"));
        assertEquals("publish.sdk", RunModes.INVALID_MODES.get("sdk.publish"));
        assertEquals("publish.rde", RunModes.INVALID_MODES.get("rde.publish"));
    }

    @Test public void testGLOBAL_RUN_MODES() {
        assertEquals(3, RunModes.GLOBAL_RUN_MODES.size());
        assertTrue(RunModes.GLOBAL_RUN_MODES.contains("dev"));
        assertTrue(RunModes.GLOBAL_RUN_MODES.contains("stage"));
        assertTrue(RunModes.GLOBAL_RUN_MODES.contains("prod"));
    }

    @Test public void testAUTHOR_ONLY_MODES() {
        assertEquals(4, RunModes.AUTHOR_ONLY_MODES.size());
        assertTrue(RunModes.AUTHOR_ONLY_MODES.contains("author"));
        assertTrue(RunModes.AUTHOR_ONLY_MODES.contains("author.dev"));
        assertTrue(RunModes.AUTHOR_ONLY_MODES.contains("author.prod"));
        assertTrue(RunModes.AUTHOR_ONLY_MODES.contains("author.stage"));
    }

    @Test public void testPUBLISH_ONLY_MODES() {
        assertEquals(4, RunModes.PUBLISH_ONLY_MODES.size());
        assertTrue(RunModes.PUBLISH_ONLY_MODES.contains("publish"));
        assertTrue(RunModes.PUBLISH_ONLY_MODES.contains("publish.dev"));
        assertTrue(RunModes.PUBLISH_ONLY_MODES.contains("publish.prod"));
        assertTrue(RunModes.PUBLISH_ONLY_MODES.contains("publish.stage"));
    }

    @Test public void testAUTHOR_RUN_MODES() {
        assertEquals(7, RunModes.AUTHOR_RUN_MODES.size());
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("dev"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("stage"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("prod"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("author"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("author.dev"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("author.prod"));
        assertTrue(RunModes.AUTHOR_RUN_MODES.contains("author.stage"));
    }

    @Test public void testPUBLISH_RUN_MODES() {
        assertEquals(7, RunModes.PUBLISH_RUN_MODES.size());
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("dev"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("stage"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("prod"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("publish"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("publish.dev"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("publish.prod"));
        assertTrue(RunModes.PUBLISH_RUN_MODES.contains("publish.stage"));
    }

    @Test public void testALL_RUN_MODES() {
        assertEquals(11, RunModes.ALL_RUN_MODES.size());
        assertTrue(RunModes.ALL_RUN_MODES.contains("dev"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("stage"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("prod"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("author"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("author.dev"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("author.prod"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("author.stage"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("publish"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("publish.dev"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("publish.prod"));
        assertTrue(RunModes.ALL_RUN_MODES.contains("publish.stage"));
    }

    @Test public void testIsRunModeAllowed() {
        assertTrue(RunModes.isRunModeAllowed("dev"));
        assertTrue(RunModes.isRunModeAllowed("author.dev"));
        assertTrue(RunModes.isRunModeAllowed("publish.stage"));
        assertFalse(RunModes.isRunModeAllowed("foo"));
        assertFalse(RunModes.isRunModeAllowed("sdk"));
    }

    @Test public void testIsRunModeAllowedIncludingSDK() {
        assertTrue(RunModes.isRunModeAllowedIncludingSDK("dev"));
        assertTrue(RunModes.isRunModeAllowedIncludingSDK("author.dev"));
        assertTrue(RunModes.isRunModeAllowedIncludingSDK("publish.stage"));
        assertFalse(RunModes.isRunModeAllowedIncludingSDK("foo"));
        assertTrue(RunModes.isRunModeAllowedIncludingSDK("sdk"));
    }

    @Test public void testCheckIfRunModeIsSpecifiedInWrongOrder() {
        assertNull(RunModes.checkIfRunModeIsSpecifiedInWrongOrder("author.dev"));
        assertNull(RunModes.checkIfRunModeIsSpecifiedInWrongOrder("foo"));
        assertEquals("author.dev", RunModes.checkIfRunModeIsSpecifiedInWrongOrder("dev.author"));
    }

    @Test public void testMatchesRunMode() {
        assertTrue(RunModes.matchesRunMode(ServiceType.AUTHOR, null));
        assertTrue(RunModes.matchesRunMode(ServiceType.PUBLISH, null));
        assertFalse(RunModes.matchesRunMode(ServiceType.AUTHOR, "foo"));
        assertFalse(RunModes.matchesRunMode(ServiceType.PUBLISH, "foo"));
    }

    @Test(expected = NullPointerException.class) public void testMatchesRunModeThrows() {
        RunModes.matchesRunMode(null, "runmode");
    }
}
