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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.Feature;

import com.adobe.aem.project.ServiceType;

/**
 * Generates product aggregates
 */
public interface ProductFeatureGenerator {

    /**
     * Returns a mapping of product aggregates
     *
     * @param serviceTypes the service types to consider
     * @return a mapping of product aggregates
     * @throws IOException in case of IO errors
     */
    Map<ProductVariation, List<Feature>> getProductAggregates(EnumSet<ServiceType> serviceTypes) throws IOException;

    /**
     * Returns a variation matching the specified user aggregate name
     *
     * @param name the user aggregate name
     * @return the matching variation
     */
    ProductVariation getVariation(String name);
}