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
package com.adobe.aem.analyser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class AemAnalyserTest {
    
    @Test public void testDefaultIncludedTasks() {
        final AemAnalyser analyser = new AemAnalyser();
        assertNotNull(analyser.getIncludedTasks());
        assertEquals(AemAnalyser.DEFAULT_TASKS.split(",").length, analyser.getIncludedTasks().size());
        for(final String t : AemAnalyser.DEFAULT_TASKS.split(",")) {
            assertTrue(analyser.getIncludedTasks().contains(t));
        }
    }

    @Test public void testUserIncludedTasks() {
        final AemAnalyser analyser = new AemAnalyser();
        assertNotNull(analyser.getIncludedUserTasks());
        assertEquals(AemAnalyser.DEFAULT_USER_TASKS.split(",").length, analyser.getIncludedUserTasks().size());
        for(final String t : AemAnalyser.DEFAULT_USER_TASKS.split(",")) {
            assertTrue(analyser.getIncludedUserTasks().contains(t));
        }
    }

    @Test public void testDefaultTaskConfigurations() {
        final AemAnalyser analyser = new AemAnalyser();
        assertNotNull(analyser.getTaskConfigurations());

        // 3 configs by default
        assertEquals(3, analyser.getTaskConfigurations().size());
       
        // check first config
        Map<String, String> config = analyser.getTaskConfigurations().get("api-regions-crossfeature-dups");
        assertNotNull(config);
        assertEquals(3, config.size());
        assertEquals("global,com.adobe.aem.deprecated", config.get("regions"));
        assertEquals("com.adobe.aem:aem-sdk-api:slingosgifeature:*", config.get("definingFeatures"));
        assertEquals("*", config.get("warningPackages"));

        // check second config
        config = analyser.getTaskConfigurations().get("api-regions-check-order");
        assertNotNull(config);
        assertEquals(1, config.size());
        assertEquals("global,com.adobe.aem.deprecated,com.adobe.aem.internal", config.get("order"));
        
        // check validation config
        config = analyser.getTaskConfigurations().get("content-packages-validation");
        assertNotNull(config);
        assertEquals(1, config.size());
        assertEquals("jackrabbit-nodetypes", config.get("disabled-validators"));
    }

    @Test public void testSetTaskConfigurations() throws Exception {
        final Map<String, String> myTaskConfig = new HashMap<>();
        myTaskConfig.put("x", "y");
        final Map<String, String> overriddenConfig = new HashMap<>();
        overriddenConfig.put("traa", "laa");

        final Map<String, Map<String, String>> taskConfigurations = new HashMap<>();
        taskConfigurations.put("mytask", myTaskConfig);
        taskConfigurations.put("api-regions-crossfeature-dups", overriddenConfig);

        final AemAnalyser analyser = new AemAnalyser();
        analyser.setTaskConfigurations(taskConfigurations);

        // 4 configurations (3 default + 1 custom)
        assertEquals(4, analyser.getTaskConfigurations().size());

        // check overridden default config
        Map<String, String> config = analyser.getTaskConfigurations().get("api-regions-crossfeature-dups");
        assertNotNull(config);
        assertEquals(1, config.size());
        assertEquals("laa", config.get("traa"));

        // check custom config
        config = analyser.getTaskConfigurations().get("mytask");
        assertNotNull(config);
        assertEquals(1, config.size());
        assertEquals("y", config.get("x"));

        // check second default config - unchanged
        config = analyser.getTaskConfigurations().get("api-regions-check-order");
        assertNotNull(config);
        assertEquals(1, config.size());
        assertEquals("global,com.adobe.aem.deprecated,com.adobe.aem.internal", config.get("order"));
    }

    @Test public void testSetIncludedTasks() throws Exception {
        final AemAnalyser analyser = new AemAnalyser();
        analyser.setIncludedTasks(Collections.singleton("mytask"));

        assertEquals(1, analyser.getIncludedTasks().size());
        assertTrue(analyser.getIncludedTasks().contains("mytask"));
    }

    @Test public void testSetIncludedUserTasks() throws Exception {
        final AemAnalyser analyser = new AemAnalyser();
        analyser.setIncludedUserTasks(Collections.singleton("mytask"));

        assertEquals(1, analyser.getIncludedUserTasks().size());
        assertTrue(analyser.getIncludedUserTasks().contains("mytask"));
    }
}
