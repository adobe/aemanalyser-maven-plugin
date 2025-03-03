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

import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Warn user about CORS settings blocking build in features
 */
public class CorsConfigurationValidator implements AnalyserTask {

    static String ALLOW_ORIGIN = "alloworigin";
    static String ALLOW_ORIGIN_REGEXP = "alloworiginregexp";
    static Pattern COM_URL_PATTERN = Pattern.compile("https://.*\\.adobe\\.com");
    static Pattern NET_URL_PATTERN = Pattern.compile("https://.*\\.adobe\\.net");
    static String SAMPLE_COM_URL = "https://experience.adobe.com";
    static String SAMPLE_NET_URL = "https://static.adobe.net";

    @Override
    public String getName() {
        return "CORS Configuration Validator";
    }

    @Override
    public String getId() {
        return "cors-configuration-validator";
    }
    @Override
    public void execute(final AnalyserTaskContext ctx) throws IOException {

        List<Configuration> projectCorsConfigurations = ctx.getFeature().getConfigurations().stream()
                .filter(c -> isCorsPolicyConfiguration(c, ctx))
                .filter(c -> hasInvalidAllowOrigin(c, ctx) || hasInvalidAllowOriginRegex(c, ctx))
                .collect(Collectors.toList());

        for (final Configuration cfg : projectCorsConfigurations) {
            ctx.reportConfigurationWarning(cfg,
                     " overrides the default CORS Configuration for adobe.com/adobe.net and will block Headless UIs." +
                             " Please reconfigure your project to avoid blocking these features."
            );
        }
    }

    private static boolean isCorsPolicyConfiguration(Configuration configuration, AnalyserTaskContext ctx) {
        return "com.adobe.granite.cors.impl.CORSPolicyImpl".equals(configuration.getFactoryPid());
    }

    private static boolean hasInvalidAllowOrigin(Configuration configuration, AnalyserTaskContext ctx) {
        String[] property = getConfigurationProperty(configuration, ALLOW_ORIGIN);

        return property != null
                && Arrays.stream(property).anyMatch(regex -> COM_URL_PATTERN.matcher(regex).matches()
                || NET_URL_PATTERN.matcher(regex).matches());
    }
    private static boolean hasInvalidAllowOriginRegex(Configuration configuration, AnalyserTaskContext ctx) {
        String[] property = getConfigurationProperty(configuration, ALLOW_ORIGIN_REGEXP);

        return property != null && Arrays.stream(property).anyMatch(regex -> Pattern.compile(regex).matcher(SAMPLE_COM_URL).matches()
                || Pattern.compile(regex).matcher(SAMPLE_NET_URL).matches());

    }

    private static String[] getConfigurationProperty(Configuration configuration, String propertyName) {
        Object property = configuration.getConfigurationProperties().get(propertyName);

        if (property instanceof String) {
            return new String[]{(String) property};
        } else {
            return (String[]) property;

        }
    }

}
