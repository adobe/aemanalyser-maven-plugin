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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.LoggerFactory;

public class AemAnalyserUtil {

    /** The map of invalid runmodes and the valid one to use. */
    private static final Map<String, String> INVALID_MODES = new HashMap<>();
    static {
        INVALID_MODES.put("dev.author", "author.dev");
        INVALID_MODES.put("stage.author", "author.stage");
        INVALID_MODES.put("prod.author", "author.dev");
        INVALID_MODES.put("dev.publish", "publish.dev");
        INVALID_MODES.put("stage.publish", "publish.stage");
        INVALID_MODES.put("prod.publish", "publish.prod");
    }

    /** Used run modes */
    static final List<String> USED_MODES = Arrays.asList("author", "author.dev", "author.stage",
        "author.prod", "publish", "publish.dev", "publish.stage", "publish.prod");

    /** Default runmode */
    private static final String DEFAULT_MODE = "(default)";

    /**
     * Check if a runmode is invalid
     * @return {@code null} if is valid, the correct runmode if invalid
     */
    static String getValidRunMode(final String mode) {
        return INVALID_MODES.get(mode);
    }

    /**
     * Check if run mode is used
     * @param mode The runmode
     * @return {@code true} if mode is used
     */
    public static boolean isRunModeUsed(final String mode) {
        return USED_MODES.contains(mode);
    }

    /**
     * Calculate the aggregates based on the runmode mappings
     * @param runmodes The runmode mappings
     * @return The aggregates
     * @throws IllegalArgumentException If an invalid runmode is used
     */
    public static Map<String, Set<String>> getAggregates(final Properties runmodes) {
        final Map<String, Set<String>> allModels = new HashMap<>();
        for(final String mode : USED_MODES) {
            allModels.put(mode, new HashSet<>());
        }

        final Object defaultFm = runmodes.remove(DEFAULT_MODE);
        if (defaultFm != null ) {
            for(final String pck : defaultFm.toString().split(",")) {
                allModels.values().stream().forEach(s -> s.add(pck));
            }
        }

        for (final String mode : runmodes.stringPropertyNames()) {
            Set<String> models = allModels.get(mode);
            boolean valid = false;
            if ( models != null ) {
                for(final String pck : runmodes.getProperty(mode).split(",")) {
                    models.add(pck);
                }
                for (final String ap : new String [] {".dev", ".stage", ".prod"}) {
                    final String key = mode.concat(ap);
                    models = allModels.get(key);
                    if ( models != null ) {
                        for(final String pck : runmodes.getProperty(mode).split(",")) {
                            models.add(pck);
                        }
                    }
                }
                valid = true;                
            } else {
                for (final String ap : new String [] {"author.", "publish."}) {
                    final String key = ap.concat(mode);
                    models = allModels.get(key);
                    if ( models != null ) {
                        for(final String pck : runmodes.getProperty(mode).split(",")) {
                            models.add(pck);
                        }
                        valid = true;                
                    }
                }
            }
            if ( !valid ) {
                final String validMode = getValidRunMode(mode);
                if ( validMode != null ) {
                    throw new IllegalArgumentException("Invalid runmode " + mode + ". Please use this runmode instead: " + validMode);
                }
                LoggerFactory.getLogger(AemAnalyser.class.getName()).info("Ignoring unused runmode " + mode);
            }
        }

        pruneModels(allModels);

        return allModels;
    }

    static void pruneModels(final Map<String, Set<String>> allModels) {
        // Remove specialised models that don't add anything
        for (String ap : new String [] {"author", "publish"}) {
            for (String env : new String [] {".dev", ".stage", ".prod"}) {
                String mode = ap + env;
                if (allModels.get(ap).equals(allModels.get(mode))) {
                    allModels.remove(mode);
                }
            }
        }

        // If specialised models exist for all environments, remove the generic model, as
        // a specialised model is then always used
        publish:
        for (String ap : new String [] {"author", "publish"}) {
            for (String env : new String [] {".dev", ".stage", ".prod"}) {
                if (!allModels.containsKey(ap + env)) {
                    continue publish;
                }
            }

            // Found specialised models for all, remove the generic one
            allModels.remove(ap);
        }
    }
}
