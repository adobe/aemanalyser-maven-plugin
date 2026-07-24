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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.adobe.aem.project.RunModes;
import com.adobe.aem.project.ServiceType;

public class AemAnalyserUtilTest {
    @Test
    public void testInvalidModes() {
        assertEquals("author.dev", AemAnalyserUtil.getValidRunMode("dev.author"));
        assertNull(AemAnalyserUtil.getValidRunMode("author.foo"));
        assertNull(AemAnalyserUtil.getValidRunMode("dev.foo"));
    }

    @Test
    public void testPruneModels() {
        Map<String, List<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singletonList("x"));
        allModels.put("publish", Collections.singletonList("x"));

        Map<String, List<String>> expected = new HashMap<>(allModels);
        AemAnalyserUtil.pruneModels(allModels, Collections.emptySet());
        assertEquals(expected, allModels);
    }

    @Test
    public void testPruneModels2() {
        Map<String, List<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singletonList("x"));
        allModels.put("author.dev", Arrays.asList("x", "x1"));
        allModels.put("author.stage", Arrays.asList("x", "x2"));
        allModels.put("author.prod", Arrays.asList("x", "x3"));
        allModels.put("publish", Collections.singletonList("y"));
        allModels.put("publish.dev", Arrays.asList("y", "y1", "y2"));
        allModels.put("publish.stage", Arrays.asList("y"));
        allModels.put("publish.prod", Arrays.asList("y", "y3"));

        Map<String, List<String>> expected = new HashMap<>();
        expected.put("author", Arrays.asList("x"));
        expected.put("author.dev", Arrays.asList("x", "x1"));
        expected.put("author.stage", Arrays.asList("x", "x2"));
        expected.put("author.prod", Arrays.asList("x", "x3"));
        expected.put("publish", Collections.singletonList("y"));
        expected.put("publish.dev", Arrays.asList("y", "y1", "y2"));
        expected.put("publish.prod", Arrays.asList("y", "y3"));

        AemAnalyserUtil.pruneModels(allModels, Collections.emptySet());
        assertEquals(expected, allModels);
    }

    @Test
    public void testPruneModels3() {
        Map<String, List<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singletonList("x"));
        allModels.put("author.xtra", Collections.singletonList("x"));
        allModels.put("publish", Collections.singletonList("x"));
        allModels.put("publish.xtra", Collections.singletonList("x"));

        Map<String, List<String>> expected = new HashMap<>();
        expected.put("author", Collections.singletonList("x"));
        expected.put("publish", Collections.singletonList("x"));

        AemAnalyserUtil.pruneModels(allModels, Set.of("abc", "xtra", "zzz"));
        assertEquals(expected, allModels);
    }

    @Test
    public void testGetAggregatesValid() throws Exception {
        Map<String, String> p = new HashMap<>();

        p.put("prod", "myproj.all-prod.json");
        p.put("stage", "myproj.all-stage.json");
        p.put("publish.stage", "myproj.all-publish.stage.json");
        p.put("publish", "myproj.all-publish.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("(default)", "myproj.all.json");
        p.put("author.foo", "myproj.foo-author.json");
        p.put("foo.author", "myproj.author-foo.json");
        p.put("dev.foo", "myproj.dev-foo.json");
        p.put("foo.dev", "myproj.foo-dev.json");

        Map<String, List<String>> aggs = AemAnalyserUtil.getAggregates(p, EnumSet.allOf(ServiceType.class));

        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json"),
                aggs.get("author"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-prod.json"),
                aggs.get("author.prod"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-stage.json"),
                aggs.get("author.stage"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-publish.json"),
                aggs.get("publish"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-publish.dev.json"),
                aggs.get("publish.dev"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-stage.json", "myproj.all-publish.stage.json"),
                aggs.get("publish.stage"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-prod.json", "myproj.all-publish.prod.json"),
                aggs.get("publish.prod"));
        assertEquals(7, aggs.size());
    }

    @Test
    public void testGetAggregatesAuthorOnly() throws Exception {
        Map<String, String> p = new HashMap<>();

        p.put("prod", "myproj.all-prod.json");
        p.put("stage", "myproj.all-stage.json");
        p.put("publish.stage", "myproj.all-publish.stage.json");
        p.put("publish", "myproj.all-publish.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("(default)", "myproj.all.json");
        p.put("author.foo", "myproj.foo-author.json");
        p.put("foo.author", "myproj.author-foo.json");
        p.put("dev.foo", "myproj.dev-foo.json");
        p.put("foo.dev", "myproj.foo-dev.json");

        Map<String, List<String>> aggs = AemAnalyserUtil.getAggregates(p, EnumSet.of(ServiceType.AUTHOR));

        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json"),
                aggs.get("author"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-prod.json"),
                aggs.get("author.prod"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-stage.json"),
                aggs.get("author.stage"));
        assertEquals(3, aggs.size());
    }

    @Test
    public void testGetAdditionalAggregates() throws Exception {
        Map<String, String> p = new HashMap<>();

        p.put("prod", "myproj.all-prod.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("rde", "myproj.all-rde.json");
        p.put("author.rde", "myproj.all-author.rde.json");
        p.put("(default)", "myproj.all.json");

        Map<String, List<String>> aggs = AemAnalyserUtil.getAggregates(p, EnumSet.allOf(ServiceType.class),
            Collections.singletonMap("rde", "dev"));

        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json"),
            aggs.get("author"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-author.json",
            "myproj.all-rde.json", "myproj.all-author.rde.json"), aggs.get("author.rde"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-publish.dev.json",
            "myproj.all-rde.json"), aggs.get("publish.rde"));
    }

    @Test
    public void testGetAdditionalAggregates2() throws Exception {
        Map<String, String> p = new HashMap<>();

        p.put("dev", "myproj.all-dev.json");
        p.put("rde", "myproj.all-rde.json");
        p.put("publish.rde", "myproj.all-publish.rde.json");
        p.put("(default)", "myproj.all.json");

        Map<String, List<String>> aggs = AemAnalyserUtil.getAggregates(p, EnumSet.allOf(ServiceType.class),
            Collections.singletonMap("rde", "dev"));

        assertEquals(Arrays.asList("myproj.all.json"),
            aggs.get("author"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-dev.json",
            "myproj.all-rde.json"), aggs.get("author.rde"));
        assertEquals(Arrays.asList("myproj.all.json", "myproj.all-dev.json",
            "myproj.all-rde.json", "myproj.all-publish.rde.json"), aggs.get("publish.rde"));
    }


    @Test(expected = IllegalArgumentException.class)
    public void testGetAggregatesInvalid() throws Exception {
        Map<String, String> p = new HashMap<>();

        p.put("prod", "myproj.all-prod.json");
        p.put("stage", "myproj.all-stage.json");
        p.put("publish.stage", "myproj.all-publish.stage.json");
        p.put("publish", "myproj.all-publish.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("(default)", "myproj.all.json");
        p.put("dev.author", "dev-author.json");

        AemAnalyserUtil.getAggregates(p, EnumSet.allOf(ServiceType.class));
    }

    @Test
    public void testGetUsedModes() {

        assertEquals(RunModes.AUTHOR_ONLY_MODES,
            AemAnalyserUtil.getUsedModes(EnumSet.of(ServiceType.AUTHOR)));
        assertEquals(RunModes.PUBLISH_ONLY_MODES,
            AemAnalyserUtil.getUsedModes(EnumSet.of(ServiceType.PUBLISH)));
        assertEquals(AemAnalyserUtil.ALL_USED_MODES,
            AemAnalyserUtil.getUsedModes(EnumSet.of(ServiceType.PUBLISH, ServiceType.AUTHOR)));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetUsedModesNone() {
        AemAnalyserUtil.getUsedModes(EnumSet.noneOf(ServiceType.class));
    }

    @Test
    public void testIsRunmodeUsed() {
        assertTrue(AemAnalyserUtil.isRunModeUsed("author", EnumSet.of(ServiceType.PUBLISH, ServiceType.AUTHOR)));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author", EnumSet.of(ServiceType.AUTHOR)));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.dev", EnumSet.of(ServiceType.AUTHOR)));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.stage", EnumSet.of(ServiceType.AUTHOR)));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.prod", EnumSet.of(ServiceType.AUTHOR)));
        assertFalse(AemAnalyserUtil.isRunModeUsed("publish", EnumSet.of(ServiceType.AUTHOR)));
        assertTrue(AemAnalyserUtil.isRunModeUsed("publish", EnumSet.of(ServiceType.PUBLISH, ServiceType.AUTHOR)));
    }

    @Test(expected = IllegalStateException.class)
    public void testNoRunModeUsed() {
        AemAnalyserUtil.isRunModeUsed("author", EnumSet.noneOf(ServiceType.class));
    }
}
