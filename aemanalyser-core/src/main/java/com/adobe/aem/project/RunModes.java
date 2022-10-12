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
package com.adobe.aem.project;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to handle run modes
 */
public class RunModes {

    /** The map of invalid runmodes and the valid one to use. */
    static final Map<String, String> INVALID_MODES = new HashMap<>();
    static {
        for(final EnvironmentType env : EnvironmentType.values()) {
            for(final ServiceType service : ServiceType.values()) {
                INVALID_MODES.put(env.asString().concat(".").concat(service.asString()), service.asString().concat(".").concat(env.asString()));
            }
        }
        final Set<String> sdk = new HashSet<>();
        for(final ServiceType service : ServiceType.values()) {
           sdk.add(service.asString().concat(".sdk"));
        }
        sdk.add("sdk");
        SDK_ONLY_MODES = Collections.unmodifiableSet(sdk);
    }

    /** 
     * Allowed global run modes  - all environment types
     */
    public static final Set<String> GLOBAL_RUN_MODES = Arrays.stream(EnvironmentType.values())
        .map(e -> e.asString())
        .collect(Collectors.toSet());
    
    /** 
     * Author only run modes
     */
    public static final Set<String> AUTHOR_ONLY_MODES;
    
    /** 
     * Publish only run modes
     */
    public static final Set<String> PUBLISH_ONLY_MODES;
    
    static {
        final Set<String> authors = new HashSet<>();
        final Set<String> publish = new HashSet<>();
        authors.add(ServiceType.AUTHOR.asString());
        publish.add(ServiceType.PUBLISH.asString());
        for(final String mode : GLOBAL_RUN_MODES) {
            authors.add(ServiceType.AUTHOR.asString().concat(".").concat(mode));
            publish.add(ServiceType.PUBLISH.asString().concat(".").concat(mode));
        }
        AUTHOR_ONLY_MODES = Collections.unmodifiableSet(authors);
        PUBLISH_ONLY_MODES = Collections.unmodifiableSet(publish);
    }

    /**
     * All run modes applying to author
     */    
    public static final Set<String> AUTHOR_RUN_MODES = Stream.concat(
        GLOBAL_RUN_MODES.stream(), AUTHOR_ONLY_MODES.stream()).collect(Collectors.toSet());

    /**
     * All run modes applying to publish
     */    
    public static final Set<String> PUBLISH_RUN_MODES = Stream.concat(
        GLOBAL_RUN_MODES.stream(), PUBLISH_ONLY_MODES.stream()).collect(Collectors.toSet());

    /** 
     * All alowed run modes
     */
    public static final Set<String> ALL_RUN_MODES = Stream.concat(
        AUTHOR_RUN_MODES.stream(), PUBLISH_RUN_MODES.stream()).collect(Collectors.toSet());

    /** 
     * Run modes for SDK only
     */
    public static final Set<String> SDK_ONLY_MODES;

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
     * Matches the run modes of the specified service
     * @param serviceType The service type
     * @param runMode The run mode
     * @return {@code null} if it matches
     * @throws NullPointerException if service type is null
     */
    public static boolean matchesRunMode(final ServiceType serviceType, final String runMode) {
        Objects.requireNonNull(serviceType);
        if ( serviceType == ServiceType.AUTHOR ) {
            return runMode == null || AUTHOR_RUN_MODES.contains(runMode);
        }
        return runMode == null || PUBLISH_RUN_MODES.contains(runMode);
    }

    /**
     * Matches the run modes of the specified service including the SDK
     * @param serviceType The service type
     * @param runMode The run mode
     * @return {@code null} if it matches
     * @throws NullPointerException if service type is null
     */
    public static boolean matchesRunModeIncludingSDK(final ServiceType serviceType, final String runMode) {
        if ( matchesRunMode(serviceType, runMode) ) {
            return true;
        }
        return SDK_ONLY_MODES.contains(runMode);
    }

    /**
     * Check whether the run mode is allowed including the SDK
     * @param runMode The run mode
     * @return {@code true} if allowed
     */
    public static boolean isRunModeAllowedIncludingSDK(final String runMode) {
        boolean result = isRunModeAllowed(runMode);
        if ( !result ) {
            result = SDK_ONLY_MODES.contains(runMode);
        }
        return result;
    }
}
