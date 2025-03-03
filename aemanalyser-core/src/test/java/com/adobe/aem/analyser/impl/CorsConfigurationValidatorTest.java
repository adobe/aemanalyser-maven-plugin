/*
  Copyright 2025 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.impl;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CorsConfigurationValidatorTest {

    @Mock
    AnalyserTaskContext ctx;

    private AutoCloseable closeable;

    @Before
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    public void testTaskIdentifiers() {
        AnalyserTask task = new CorsConfigurationValidator();
        assertEquals("cors-configuration-validator", task.getId());
        assertEquals("CORS Configuration Validator", task.getName());
    }

    @Test
    public void testValidCorsConfigurations() throws Exception {
        List<Configuration> configurations = List.of(
                createConfiguration("regexp-array", null, new String[]{"https://.*\\.something\\.com"}),
                createConfiguration("regexp-string", null, "https://.*\\.something\\.com"),
                createConfiguration("singleorigin-array", new String[]{"https://something.com"}, null),
                createConfiguration("singleorigin-string", "https://something.com", null),
                createConfiguration("no-config", null, null)
        );
        initializeFeatureWithConfigurations(configurations);

        AnalyserTask task = new CorsConfigurationValidator();
        task.execute(ctx);

        verify(ctx).getFeature();
        verify(ctx, times(0)).reportConfigurationWarning(any(), anyString());
        verifyNoMoreInteractions(ctx);
    }

    @Test
    public void testNonCorsConfigurations() throws Exception {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getFactoryPid()).thenReturn("not.CORSPolicyImpl");
        List<Configuration> configurations = List.of(configuration);
        initializeFeatureWithConfigurations(configurations);

        AnalyserTask task = new CorsConfigurationValidator();
        task.execute(ctx);

        verify(configuration, times(0)).getConfigurationProperties();
    }

    @Test
    public void testInvalidCorsConfigurations() throws Exception {
        List<Configuration> configurations = List.of(
                createConfiguration("regexp-com", null, new String[]{"https://.*\\.adobe\\.com"}),
                createConfiguration("regexp-net", null, new String[]{"https://.*\\.adobe\\.net"}),
                createConfiguration("regexp-string", null, "https://.*\\.adobe\\.net"),
                createConfiguration("singleorigin-com", new String[]{"https://any.adobe.com"}, null),
                createConfiguration("singleorigin-net", new String[]{"https://any.adobe.net"}, null),
                createConfiguration("singleorigin-string", "https://any.adobe.net", null)
        );
        initializeFeatureWithConfigurations(configurations);

        AnalyserTask task = new CorsConfigurationValidator();
        task.execute(ctx);

        verify(ctx).getFeature();
        for (Configuration c : configurations) {
            verify(ctx, times(1)).reportConfigurationWarning(eq(c), anyString());
        }
        verifyNoMoreInteractions(ctx);
    }

    private void initializeFeatureWithConfigurations(List<Configuration> configurations) {
        Feature feature = new Feature(ArtifactId.parse("g:a:1"));
        when(ctx.getFeature()).thenReturn(feature);
        feature.getConfigurations().addAll(configurations);
    }

    private Configuration createConfiguration(String name, Object alloworigin, Object alloworiginregexp) {
        Configuration configuration = new Configuration("com.adobe.granite.cors.impl.CORSPolicyImpl~" + name);
        if (alloworigin != null) configuration.getProperties().put("alloworigin", alloworigin);
        if (alloworiginregexp != null) configuration.getProperties().put("alloworiginregexp", alloworiginregexp);
        return configuration;
    }
}
