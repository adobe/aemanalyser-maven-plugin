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

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.Feature;

import com.adobe.aem.project.ServiceType;

/**
 * Creates aggregates of the user features
 *
 */
public interface UserFeatureAggregator {
    /**
     * Generates a mapping of all user aggregates discovered
     *
     * <p>The key of the returned mapping is of the form <code>user-aggregated-<em>mode</em></code>,
     * where the mode represents a valid runmode entry, such as {@code author} or {@code publish.dev}.
     * </p>
     *
     * @param projectFeatures the project features
     * @param serviceTypes the service types to consider
     * @return a mapping of user aggregates
     * @throws IOException in case of IO errors
     */
    default Map<String, List<Feature>> getUserAggregates(final Map<String, Feature> projectFeatures, final EnumSet<ServiceType> serviceTypes)
            throws IOException {
        return getUserAggregates(projectFeatures, serviceTypes, Collections.emptyMap());
    }

    /**
     * Generates a mapping of all user aggregates discovered
     *
     * <p>The key of the returned mapping is of the form <code>user-aggregated-<em>mode</em></code>,
     * where the mode represents a valid runmode entry, such as {@code author} or {@code publish.dev}.
     * </p>
     *
     * @param projectFeatures the project features
     * @param serviceTypes the service types to consider
     * @param additionalRunmodes a map of additional runmodes to support
     * @return a mapping of user aggregates
     * @throws IOException in case of IO errors
     */
    Map<String, List<Feature>> getUserAggregates(final Map<String, Feature> projectFeatures, final EnumSet<ServiceType> serviceTypes,
            Map<String,String> additionalRunmodes) throws IOException;
}