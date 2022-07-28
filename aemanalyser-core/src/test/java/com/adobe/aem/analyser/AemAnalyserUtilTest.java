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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

public class AemAnalyserUtilTest {

    @Test
    public void testInvalidModes() {
        assertEquals("author.dev", AemAnalyserUtil.getValidRunMode("dev.author"));
        assertNull(AemAnalyserUtil.getValidRunMode("author.foo"));
        assertNull(AemAnalyserUtil.getValidRunMode("dev.foo"));
    }

    @Test
    public void testPruneModels() {
        Map<String, Set<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singleton("x"));
        allModels.put("publish", Collections.singleton("x"));

        Map<String, Set<String>> expected = new HashMap<>(allModels);
        AemAnalyserUtil.pruneModels(allModels);
        assertEquals(expected, allModels);
    }

    @Test
    public void testPruneModels2() {
        Map<String, Set<String>> allModels = new HashMap<>();

        allModels.put("author", Collections.singleton("x"));
        allModels.put("author.dev", new HashSet<>(Arrays.asList("x", "x1")));
        allModels.put("author.stage", new HashSet<>(Arrays.asList("x", "x2")));
        allModels.put("author.prod", new HashSet<>(Arrays.asList("x", "x3")));
        allModels.put("publish", Collections.singleton("y"));
        allModels.put("publish.dev", new HashSet<>(Arrays.asList("y", "y1", "y2")));
        allModels.put("publish.stage", new HashSet<>(Arrays.asList("y")));
        allModels.put("publish.prod", new HashSet<>(Arrays.asList("y", "y3")));

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("author.dev", new HashSet<>(Arrays.asList("x", "x1")));
        expected.put("author.stage", new HashSet<>(Arrays.asList("x", "x2")));
        expected.put("author.prod", new HashSet<>(Arrays.asList("x", "x3")));
        expected.put("publish", Collections.singleton("y"));
        expected.put("publish.dev", new HashSet<>(Arrays.asList("y", "y1", "y2")));
        expected.put("publish.prod", new HashSet<>(Arrays.asList("y", "y3")));

        AemAnalyserUtil.pruneModels(allModels);
        assertEquals(expected, allModels);
    }

    @Test
    public void testGetAggregatesValid() throws Exception {
        Properties p = new Properties();

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

        Map<String, Set<String>> aggs = AemAnalyserUtil.getAggregates(p, ServiceType.values());

        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json")),
                aggs.get("author"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-prod.json")),
                aggs.get("author.prod"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-stage.json")),
                aggs.get("author.stage"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-publish.dev.json")),
                aggs.get("publish.dev"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-stage.json", "myproj.all-publish.stage.json")),
                aggs.get("publish.stage"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-publish.json", "myproj.all-prod.json", "myproj.all-publish.prod.json")),
                aggs.get("publish.prod"));
        assertEquals(6, aggs.size());
    }

    @Test
    public void testGetAggregatesAuthorOnly() throws Exception {
        Properties p = new Properties();

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

        Map<String, Set<String>> aggs = AemAnalyserUtil.getAggregates(p, new ServiceType[] {ServiceType.AUTHOR});

        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json")),
                aggs.get("author"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-prod.json")),
                aggs.get("author.prod"));
        assertEquals(new HashSet<>(Arrays.asList("myproj.all.json", "myproj.all-author.json", "myproj.all-stage.json")),
                aggs.get("author.stage"));
        assertEquals(3, aggs.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetAggregatesInvalid() throws Exception {
        Properties p = new Properties();

        p.put("prod", "myproj.all-prod.json");
        p.put("stage", "myproj.all-stage.json");
        p.put("publish.stage", "myproj.all-publish.stage.json");
        p.put("publish", "myproj.all-publish.json");
        p.put("author", "myproj.all-author.json");
        p.put("publish.prod", "myproj.all-publish.prod.json");
        p.put("publish.dev", "myproj.all-publish.dev.json");
        p.put("(default)", "myproj.all.json");
        p.put("dev.author", "dev-author.json");

        AemAnalyserUtil.getAggregates(p, ServiceType.values());
    }

    @Test
    public void testGetUsedModes() {
        assertEquals(0, AemAnalyserUtil.getUsedModes(new ServiceType[] {}).size());

        assertEquals(AemAnalyserUtil.AUTHOR_USED_MODES,
            AemAnalyserUtil.getUsedModes(new ServiceType[] {ServiceType.AUTHOR}));
        assertEquals(AemAnalyserUtil.PUBLISH_USED_MODES,
            AemAnalyserUtil.getUsedModes(new ServiceType[] {ServiceType.PUBLISH}));
        assertEquals(AemAnalyserUtil.ALL_USED_MODES,
            AemAnalyserUtil.getUsedModes(new ServiceType[] {ServiceType.AUTHOR, ServiceType.PUBLISH}));
        assertEquals(AemAnalyserUtil.ALL_USED_MODES,
            AemAnalyserUtil.getUsedModes(new ServiceType[] {ServiceType.PUBLISH, ServiceType.AUTHOR}));
    }

    @Test
    public void testIsRunmodeUsed() {
        assertTrue(AemAnalyserUtil.isRunModeUsed("author", new ServiceType[] {ServiceType.PUBLISH, ServiceType.AUTHOR}));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author", new ServiceType[] {ServiceType.AUTHOR}));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.dev", new ServiceType[] {ServiceType.AUTHOR}));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.stage", new ServiceType[] {ServiceType.AUTHOR}));
        assertTrue(AemAnalyserUtil.isRunModeUsed("author.prod", new ServiceType[] {ServiceType.AUTHOR}));
        assertFalse(AemAnalyserUtil.isRunModeUsed("publish", new ServiceType[] {ServiceType.AUTHOR}));
        assertFalse(AemAnalyserUtil.isRunModeUsed("author", new ServiceType[] {}));
        assertTrue(AemAnalyserUtil.isRunModeUsed("publish", new ServiceType[] {ServiceType.PUBLISH, ServiceType.AUTHOR}));
    }
}
