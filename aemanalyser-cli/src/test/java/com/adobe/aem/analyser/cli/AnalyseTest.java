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
package com.adobe.aem.analyser.cli;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.Feature;
import org.junit.Test;

public class AnalyseTest {
    @Test
    public void testInterpolatePlaceholders() {
        Analyse a = new Analyse();

        Feature f = new Feature(ArtifactId.fromMvnId("g:a:1"));
        Configurations configs = f.getConfigurations();

        Configuration cfg = new Configuration("org.foo.pid");
        cfg.getProperties().put("abc", "def");
        cfg.getProperties().put("propvar", "my$[prop:some.prop]val");
        configs.add(cfg);

        f.getFrameworkProperties().put("some.prop", "999");
        f.getFrameworkProperties().put("some.other.prop", "888");

        a.interpolatePlaceholders(Collections.singletonList(f));

        Configuration procCfg = f.getConfigurations().getConfiguration("org.foo.pid");
        assertEquals("def", procCfg.getConfigurationProperties().get("abc"));
        assertEquals("my999val", procCfg.getConfigurationProperties().get("propvar"));
        assertEquals("def", procCfg.getProperties().get("abc"));
        assertEquals("my999val", procCfg.getProperties().get("propvar"));
    }
}
