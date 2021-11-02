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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.configadmin.plugin.interpolation.Interpolator;
import org.apache.felix.configadmin.plugin.interpolation.Interpolator.Provider;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;

public class EnvVarAnalyserTask implements AnalyserTask {

    private static final String TYPE_ENV = "env";

    private static final List<String> PREFIXES = Arrays.asList("INTERNAL_", "ADOBE_");

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
     * Check if a configuration is using unwanted prefixes in env vars.
     * @param context The context
     * @param cfg The configuration
     */
    private void check(final AnalyserTaskContext context, final Configuration cfg) {
        final Dictionary<String, Object> properties = cfg.getConfigurationProperties();
        for(final String propName : Collections.list(properties.keys())) {
            final Object value = properties.get(propName);
            boolean valid = true;
            if (value instanceof String) {
                valid = checkValue(value.toString());
            } else if (value instanceof String[]) {
                final String[] array = (String[]) value;
                for(final String val : array) {
                    if ( !checkValue(val) ) {
                        valid = false;
                        break;
                    };
                }
            }
           
            if ( !valid ) {
                context.reportConfigurationError(cfg, "Value for property '" + propName 
                    + "' must not use env vars prefixed with INTERNAL_ or ADOBE_");
            }
        }
    }

    /**
     * Check if a value is using unwanted prefixes.
     * @param context The context
     * @param cfg The configuration
     */
    private boolean checkValue(final String value) {
        final AtomicBoolean result = new AtomicBoolean(true);
        Interpolator.replace(value, new Provider() {

            @Override
            public Object provide(final String type, final String name, final Map<String, String> directives) {
                if ( TYPE_ENV.equals(type) ) {
                    for(final String prefix : PREFIXES) {
                        if ( name.startsWith(prefix) ) {
                            result.set(false);
                        }
                    }
                }
                return "";
            }
            
        });
        return result.get();
    }
}
