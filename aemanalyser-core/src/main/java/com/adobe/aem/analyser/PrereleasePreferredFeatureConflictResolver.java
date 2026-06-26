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

import org.apache.sling.feature.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Feature conflict resolver that prefers prerelease artifacts, configurations and extensions.
 * Merges two features with the same version, giving precedence to prerelease over stable.
 */
class PrereleasePreferredFeatureConflictResolver implements FeatureConflictResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrereleasePreferredFeatureConflictResolver.class);

    @Override
    public Feature resolveVersionConflict(Feature stable, Feature prerelease,
                                          SdkProductVariation variation) {
        final Feature merged = new Feature(prerelease.getId());

        // Merge bundles: prerelease takes precedence, ignore version
        final Set<String> bundleKeys = new HashSet<>();
        for (final Artifact bundle : prerelease.getBundles()) {
            merged.getBundles().add(bundle);
            bundleKeys.add(getBundleKey(bundle.getId()));
        }
        for (final Artifact bundle : stable.getBundles()) {
            final String key = getBundleKey(bundle.getId());
            if (!bundleKeys.contains(key)) {
                merged.getBundles().add(bundle);
                bundleKeys.add(key);
            } else {
                LOGGER.info("Keeping {} bundle {} for {} and dropping duplicate {} from {}.",
                        prerelease.getId().toMvnId(), bundle.getId().toMvnId(), variation,
                        bundle.getId().toMvnId(), stable.getId().toMvnId());
            }
        }

        // Merge configurations: prerelease takes precedence, deduplicate by PID
        final Set<String> configPids = new HashSet<>();
        for (final Configuration cfg : prerelease.getConfigurations()) {
            merged.getConfigurations().add(cfg);
            configPids.add(cfg.getPid());
        }
        for (final Configuration cfg : stable.getConfigurations()) {
            if (!configPids.contains(cfg.getPid())) {
                merged.getConfigurations().add(cfg);
                configPids.add(cfg.getPid());
            } else {
                LOGGER.info("Keeping {} configuration {} for {} and dropping duplicate from {}.",
                        prerelease.getId().toMvnId(), cfg.getPid(), variation,
                        stable.getId().toMvnId());
            }
        }

        // Merge extensions: prerelease takes precedence
        for (final Extension extension : prerelease.getExtensions()) {
            merged.getExtensions().add(extension);
        }
        for (final Extension extension : stable.getExtensions()) {
            if (merged.getExtensions().getByName(extension.getName()) == null) {
                merged.getExtensions().add(extension);
            } else {
                LOGGER.info("Keeping {} extension {} for {} and dropping duplicate from {}.",
                        prerelease.getId().toMvnId(), extension.getName(), variation,
                        stable.getId().toMvnId());
            }
        }

        return merged;
    }

    private String getBundleKey(ArtifactId id) {
        return id.getGroupId() + ":" + id.getArtifactId() + ":" + id.getClassifier();
    }
}