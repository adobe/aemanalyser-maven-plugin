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

import org.apache.sling.feature.Feature;

/**
 * Resolves conflicts when merging two features with the same version.
 * The resolver decides how to handle duplicate bundles, configurations, and extensions.
 */
public interface FeatureConflictResolver {

    /**
     * Merge two features that have the same version.
     * This method is called only when both features are available and have the same version.
     * The resolver is responsible for deciding which artifacts take precedence in case of conflicts.
     *
     * @param stable the stable feature
     * @param prerelease the prerelease feature (same version as stable)
     * @param variation the product variation
     * @return merged feature with conflicts resolved
     */
    Feature resolveVersionConflict(Feature stable, Feature prerelease,
                                   SdkProductVariation variation);
}
