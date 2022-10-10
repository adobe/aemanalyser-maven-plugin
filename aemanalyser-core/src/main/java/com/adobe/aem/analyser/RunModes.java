/*
  Copyright 2022 Adobe. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to handle run modes
 */
public class RunModes {

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

    /** Allowed global run modes */
    static final List<String> GLOBAL_RUN_MODES = Arrays.asList("dev", "prod", "stage");
    /** Allowed author run modes */
    static final List<String> AUTHOR_USED_MODES = Arrays.asList(
        "author", "author.dev", "author.stage", "author.prod");
    static final List<String> AUTHOR_RUN_MODES = Stream.concat(
        GLOBAL_RUN_MODES.stream(), AUTHOR_USED_MODES.stream()).collect(Collectors.toList());
    /** Allowed publish run modes */
    static final List<String> PUBLISH_USED_MODES = Arrays.asList(
        "publish", "publish.dev", "publish.stage", "publish.prod");
    static final List<String> PUBLISH_RUN_MODES = Stream.concat(
        GLOBAL_RUN_MODES.stream(), PUBLISH_USED_MODES.stream()).collect(Collectors.toList());
    /** All alowed run modes */
    static final List<String> ALL_RUN_MODES = Stream.concat(
        AUTHOR_RUN_MODES.stream(), PUBLISH_RUN_MODES.stream()).collect(Collectors.toList());

    /**
     * Check whether the run mode is allowed
     * @param runMode The run mode
     * @return {@code true} if allowed
     */
    public static boolean isRunModeAllowed(final String runMode) {
        if ( runMode != null ) {
            return ALL_RUN_MODES.contains(runMode);
        }
        return true;
    }

    /**
     * Check whether the run mode is valid but specified in the wrong order
     * @param runMode The run mode
     * @return The right run mode to use or {@code null} otherwise
     */
    public static String checkIfRunModeIsSpecifiedInWrongOrder(final String runMode) {
        return INVALID_MODES.get(runMode);
    }

    /**
     * Matches the run mode the specified service
     * @param serviceType The service type
     * @param runMode The run mode
     * @return {@code null} if it matches
     * @throws IllegalArgumentException if service type is null
     */
    public static boolean matchesRunMode(final ServiceType serviceType, final String runMode) {
        if ( serviceType == null ) {
            throw new IllegalArgumentException();
        }
        if ( serviceType == ServiceType.AUTHOR ) {
            return runMode == null || AUTHOR_RUN_MODES.contains(runMode);
        }
        return runMode == null || PUBLISH_RUN_MODES.contains(runMode);
    }
}
