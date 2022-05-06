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
package com.adobe.aem.analyser.impl;

import static org.junit.Assert.assertEquals;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.junit.Test;
import org.mockito.Mockito;

public class EnvVarAnalyserTaskTest {

    @Test public void testConstants() {
        final AnalyserTask task = new EnvVarAnalyserTask();
        assertEquals("aem-env-var", task.getId());
        assertEquals("AEM Env Var Analyser", task.getName());
    }

    @Test public void testEnvPrefixes() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[env:MY_VAR]");
        cfg1.getProperties().put("key2", "$[env:CONST_AEM_VAR]");
        f.getConfigurations().add(cfg1);

        final Configuration cfg2 = new Configuration("c2");
        cfg2.getProperties().put("key", "$[env:INTERNAL_VAR]");
        f.getConfigurations().add(cfg2);

        final Configuration cfg3 = new Configuration("c3");
        cfg3.getProperties().put("key", new String[] {"$[env:ADOBE_VAR]"});
        f.getConfigurations().add(cfg3);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg2), Mockito.anyString());
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg3), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testEnvPattern() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[env:1MY_VAR]");
        f.getConfigurations().add(cfg1);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg1), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testEnvSize() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[env:M]");
        f.getConfigurations().add(cfg1);

        final Configuration cfg2 = new Configuration("c2");
        cfg2.getProperties().put("key1", "$[env:M2]");
        f.getConfigurations().add(cfg2);

        final Configuration cfg3 = new Configuration("c3");
        // 100 characters - allowed
        cfg3.getProperties().put("key", "$[env:M123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789]");
        f.getConfigurations().add(cfg3);

        final Configuration cfg4 = new Configuration("c4");
        // 101 characters - not allowed
        cfg4.getProperties().put("key", "$[env:M1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890]");
        f.getConfigurations().add(cfg4);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg1), Mockito.anyString());
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg4), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testSecretPrefixes() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[secret:MY_VAR]");
        cfg1.getProperties().put("key2", "$[secret:CONST_AEM_VAR]");
        f.getConfigurations().add(cfg1);

        final Configuration cfg2 = new Configuration("c2");
        cfg2.getProperties().put("key", "$[secret:INTERNAL_VAR]");
        f.getConfigurations().add(cfg2);

        final Configuration cfg3 = new Configuration("c3");
        cfg3.getProperties().put("key", new String[] {"$[secret:ADOBE_VAR]"});
        f.getConfigurations().add(cfg3);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg2), Mockito.anyString());
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg3), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testSecretPattern() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[secret:1MY_VAR]");
        f.getConfigurations().add(cfg1);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg1), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testSecretSize() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[secret:M]");
        f.getConfigurations().add(cfg1);

        final Configuration cfg2 = new Configuration("c2");
        cfg2.getProperties().put("key1", "$[secret:M2]");
        f.getConfigurations().add(cfg2);

        final Configuration cfg3 = new Configuration("c3");
        // 100 characters - allowed
        cfg3.getProperties().put("key", "$[secret:M123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789]");
        f.getConfigurations().add(cfg3);

        final Configuration cfg4 = new Configuration("c4");
        // 101 characters - not allowed
        cfg4.getProperties().put("key", "$[secret:M1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890]");
        f.getConfigurations().add(cfg4);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg1), Mockito.anyString());
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationError(Mockito.eq(cfg4), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test public void testSecretPath() throws Exception {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);
        final Feature f = new Feature(ArtifactId.parse("g:a:1"));
        Mockito.when(ctx.getFeature()).thenReturn(f);

        final Configuration cfg1 = new Configuration("c1");
        cfg1.getProperties().put("key1", "$[secret:customer-secrets/FOO]");
        f.getConfigurations().add(cfg1);

        final AnalyserTask task = new EnvVarAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx, Mockito.times(1)).reportConfigurationWarning(Mockito.eq(cfg1), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(ctx);
    }
}
