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

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureBuilder;
import org.apache.sling.feature.builder.HandlerContext;
import org.apache.sling.feature.builder.MergeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * Feature conflict resolver that uses {@link FeatureBuilder#assemble} to merge two features,
 * preferring prerelease artifacts, configurations, variables, framework properties and extensions
 * over stable ones.
 *
 * <p>The assembly order is {@code stable} first, {@code prerelease} last. Override rules and a
 * custom {@link MergeHandler} ensure that prerelease values win on every type of conflict.
 * Data present only in stable is still carried over to the result.</p>
 */
class AssemblyBasedFeatureConflictResolver implements FeatureConflictResolver {

    private static final String API_REGIONS_EXTENSION_NAME = "api-regions";
    private static final Logger LOGGER = LoggerFactory.getLogger(AssemblyBasedFeatureConflictResolver.class);

    @Override
    public Feature resolveVersionConflict(final Feature stable, final Feature prerelease,
                                          final SdkProductVariation variation) {
        LOGGER.info("Resolving version conflict between {} and {} for {} using FeatureBuilder.assemble().",
                stable.getId().toMvnId(), prerelease.getId().toMvnId(), variation);

        final BuilderContext builderContext = new BuilderContext(id -> {
            if (id.equals(stable.getId())) {
                return stable;
            }
            if (id.equals(prerelease.getId())) {
                return prerelease;
            }
            return null;
        });

        // Extensions: custom handler runs first and replaces target with source (prerelease wins).
        // It handles all extension types so subsequent ServiceLoader handlers are not invoked.
        builderContext.addMergeExtensions(new PrereleaseWinsMergeHandler());
        builderContext.addMergeExtensions(StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                ServiceLoader.load(MergeHandler.class).iterator(), Spliterator.ORDERED),
                        false)
                .toArray(MergeHandler[]::new));

        // Artifacts: LATEST picks the last feature assembled (prerelease).
        builderContext.addArtifactsOverride(ArtifactId.parse("*:*:LATEST"));
        builderContext.addArtifactsOverride(ArtifactId.parse("*:*:*:*:LATEST"));

        // Configurations: last assembled (prerelease) wins on property conflicts.
        builderContext.addConfigsOverrides(Collections.singletonMap("*", "MERGE_LATEST"));

        // Variables: merge stable and prerelease, with prerelease overriding on conflicts.
        Map<String, String> variablesOverrides = new HashMap<>(stable.getVariables());
        variablesOverrides.putAll(prerelease.getVariables());
        builderContext.addVariablesOverrides(variablesOverrides);

        // Framework properties: same treatment as variables.
        Map<String, String> frameworkPropertiesOverrides = new HashMap<>(stable.getFrameworkProperties());
        frameworkPropertiesOverrides.putAll(prerelease.getFrameworkProperties());
        builderContext.addFrameworkPropertiesOverrides(frameworkPropertiesOverrides);

        // stable first → prerelease last: consistent with all override rules above.
        return FeatureBuilder.assemble(prerelease.getId(), builderContext, stable, prerelease);
    }

    /**
     * {@link MergeHandler} that handles every extension type by replacing the target extension
     * content with the source extension content. When no conflict exists (targetEx is null),
     * the source extension is simply copied into the target feature.
     *
     * <p>Because this handler returns {@code true} for all extensions, it prevents any
     * subsequently registered handler from running, giving it full control over extension merging.
     */
    private static class PrereleaseWinsMergeHandler implements MergeHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(PrereleaseWinsMergeHandler.class);

        @Override
        public boolean canMerge(final Extension extension) {
            return true;
        }

        @Override
        public void merge(final HandlerContext context, final Feature target, final Feature source,
                          final Extension targetEx, final Extension sourceEx) {
            if (targetEx == null) {
                target.getExtensions().add(sourceEx.copy());
            } else {
                // Source (prerelease) replaces target (stable).
                switch (sourceEx.getType()) {
                    case TEXT:
                        targetEx.setText(sourceEx.getText());
                        break;
                    case JSON:
                        if (API_REGIONS_EXTENSION_NAME.equals(sourceEx.getName())) {
                            targetEx.setJSON(ApiRegionsMergeHandler.merge(targetEx.getJSON(), sourceEx.getJSON()));
                        } else {
                            LOGGER.warn("Unsupported JSON extension {}. Using source JSON as fallback.", sourceEx.getName());
                            targetEx.setJSON(sourceEx.getJSON());
                        }
                        break;
                    case ARTIFACTS:
                        final Map<String, Artifact> mergedArtifactsByKey = new LinkedHashMap<>();
                        targetEx.getArtifacts().forEach(artifact ->
                                mergedArtifactsByKey.put(toVersionlessKey(artifact.getId()), artifact));
                        sourceEx.getArtifacts().forEach(artifact ->
                                mergedArtifactsByKey.put(toVersionlessKey(artifact.getId()), artifact.copy(artifact.getId())));

                        targetEx.getArtifacts().clear();
                        targetEx.getArtifacts().addAll(mergedArtifactsByKey.values());
                        break;
                }
            }
        }

        private static String toVersionlessKey(final ArtifactId artifactId) {
            return String.join(":",
                    artifactId.getGroupId(),
                    artifactId.getArtifactId(),
                    Objects.toString(artifactId.getType(), ""),
                    Objects.toString(artifactId.getClassifier(), ""));
        }
    }
}
