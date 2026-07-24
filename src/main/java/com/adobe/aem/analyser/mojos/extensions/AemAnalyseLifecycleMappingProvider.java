/*
  Copyright 2024 Cognizant Netcentric. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos.extensions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

import com.adobe.aem.analyser.mojos.Constants;

@Singleton
@Named(Constants.PACKAGING_AEM_ANALYSE)
public class AemAnalyseLifecycleMappingProvider implements Provider<LifecycleMapping> {

    private static final String DEFAULT_LIFECYCLE_KEY = "default";
    private static final Map<String, LifecyclePhase> BINDINGS;
    static {
        BINDINGS = new HashMap<>();
        BINDINGS.put("test", new LifecyclePhase("com.adobe.aem:aemanalyser-maven-plugin:analyse"));
    }

    private final Lifecycle defaultLifecycle;

    private final LifecycleMapping lifecycleMapping;

    public AemAnalyseLifecycleMappingProvider() {
        this.defaultLifecycle = new Lifecycle();
        this.defaultLifecycle.setId(DEFAULT_LIFECYCLE_KEY);
        this.defaultLifecycle.setLifecyclePhases(BINDINGS);

        this.lifecycleMapping = new LifecycleMapping() {
            @Override
            public Map<String, Lifecycle> getLifecycles() {
                return Collections.singletonMap(DEFAULT_LIFECYCLE_KEY, defaultLifecycle);
            }

            @Override
            public List<String> getOptionalMojos(String lifecycle) {
                return null;
            }

            @Override
            public Map<String, String> getPhases(String lifecycle) {
                if (DEFAULT_LIFECYCLE_KEY.equals(lifecycle)) {
                    return defaultLifecycle.getPhases();
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public LifecycleMapping get() {
        return lifecycleMapping;
    }

}
