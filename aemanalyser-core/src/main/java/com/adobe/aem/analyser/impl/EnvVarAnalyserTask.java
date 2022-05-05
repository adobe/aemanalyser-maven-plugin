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

import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.felix.configadmin.plugin.interpolation.Interpolator;
import org.apache.felix.configadmin.plugin.interpolation.Interpolator.Provider;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;

/**
 * This analyser checks the usage of env vars and secrets within OSGi Configurations
 */
public class EnvVarAnalyserTask implements AnalyserTask {

    private static final String TYPE_ENV = "env";

    private static final String TYPE_SECRET = "secret";

    private static final List<String> PREFIXES = Arrays.asList("INTERNAL_", "ADOBE_");

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*");

    private static final String SECRETS_PATH = "customer-secrets/";

    @Override
    public String getId() {
        return "aem-env-var";
    }

    @Override
    public String getName() {
        return "AEM Env Var Analyser";
    }

    @Override
    public void execute(final AnalyserTaskContext context) throws Exception {
        for(final Configuration cfg : context.getFeature().getConfigurations()) {
            check(context, cfg);
        }
    }

    /**
     * Enumeration for the violations
     */
    private enum Usage {
        ENV("Value for property '{}' must not use env vars prefixed with INTERNAL_ or ADOBE_"),
        ENV_PATTERN("Value for property '{}' uses env var not following the required naming scheme of " + NAME_PATTERN.toString()),
        ENV_SIZE("Value for property '{}' uses env var not following naming length restrictions (>= 2 and <= 100)"),
        SECRET("Value for property '{}' must not use secrets prefixed with INTERNAL_ or ADOBE_"),
        SECRET_PATH("Value for property '{}' must not use prefix " + SECRETS_PATH + ". Please remove the prefix", false),
        SECRET_PATTERN("Value for property '{}' uses env var not following the required naming scheme of " + NAME_PATTERN.toString()),
        SECRET_SIZE("Value for property '{}' uses env var not following naming length restrictions (>= 2 and <= 100)");

        private final String message;

        private final boolean error;

        private Usage(final String msg) {
            this(msg, true);
        }

        private Usage(final String msg, final boolean error) {
            this.message = msg;
            this.error = error;
        }

        /**
         * Is this an error?
         * @return {@code true} if error
         */
        public boolean isError() {
            return this.error;
        }
        /**
         * Return a message for that property
         * @param propertyName The property name
         * @return The message
         */
        public String getMessageFor(final String propertyName) {
            return message.replace("{}", propertyName);
        }
    }

    /**
     * Check if a configuration is using unwanted prefixes in env vars.
     * @param context The context
     * @param cfg The configuration
     */
    private void check(final AnalyserTaskContext context, final Configuration cfg) {
        final Dictionary<String, Object> properties = cfg.getConfigurationProperties();
        for(final String propName : Collections.list(properties.keys())) {
            final Object value = properties.get(propName);
            EnumSet<Usage> usage = EnumSet.noneOf(Usage.class);
            if (value instanceof String) {
                usage = checkValue(value.toString());
            } else if (value instanceof String[]) {
                final String[] array = (String[]) value;
                for(final String val : array) {
                    usage.addAll(checkValue(val));
                }
            }

            for(final Usage u : usage) {
                if ( u.isError() ) {
                    context.reportConfigurationError(cfg, u.getMessageFor(propName));                
                } else {
                    context.reportConfigurationWarning(cfg, u.getMessageFor(propName));
                }
            }
        }
    }

    /**
     * Check if a value is using unwanted prefixes.
     * @param value The value
     * @return The set of violations, might be empty
     */
    private EnumSet<Usage> checkValue(final String value) {
        final EnumSet<Usage> result = EnumSet.noneOf(Usage.class);
        Interpolator.replace(value, new Provider() {

            @Override
            public Object provide(final String type, final String name, final Map<String, String> directives) {
                if ( TYPE_ENV.equals(type) ) {
                    for(final String prefix : PREFIXES) {
                        if ( name.startsWith(prefix) ) {
                            result.add(Usage.ENV);
                        }
                    }
                    if ( !NAME_PATTERN.matcher(name).matches() ) {
                        result.add(Usage.ENV_PATTERN);
                    }
                    if ( name.length() < 2 || name.length() > 100 ) {
                        result.add(Usage.ENV_SIZE);
                    }
                } else if ( TYPE_SECRET.equals(type) ) {
                    String value = name;
                    if ( value.startsWith(SECRETS_PATH) ) {
                        value = value.substring(SECRETS_PATH.length());
                        result.add(Usage.SECRET_PATH);
                    }
                    for(final String prefix : PREFIXES) {
                        if ( value.startsWith(prefix) ) {
                            result.add(Usage.SECRET);
                        }
                    }    
                    if ( !NAME_PATTERN.matcher(value).matches() ) {
                        result.add(Usage.SECRET_PATTERN);
                    }
                    if ( value.length() < 2 || value.length() > 100 ) {
                        result.add(Usage.SECRET_SIZE);
                    }
                }
                return "";
            }
            
        });
        return result;
    }
}
