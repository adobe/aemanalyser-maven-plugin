package com.adobe.aem.analyser;

import org.apache.sling.feature.extension.apiregions.api.ApiRegion;
import org.apache.sling.feature.extension.apiregions.api.ApiExport;
import org.apache.sling.feature.extension.apiregions.api.ApiRegions;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility responsible for merging the {@code api-regions} JSON extension content.
 *
 * <p>The merge iterates over prerelease regions and keeps prerelease region-level data.
 * For matching regions, {@code exports} are merged by package name, with prerelease
 * exports taking precedence on conflicts.</p>
 */
class ApiRegionsMergeHandler {

    private ApiRegionsMergeHandler() {
    }

    /**
     * Merges two {@code api-regions} JSON arrays and returns the resulting JSON text.
     *
     * <p>Prerelease content is the baseline. For each prerelease region, exports from matching
     * stable region are added first and then prerelease exports override by package name.
     * If input cannot be parsed by Sling {@link ApiRegions} model, the method falls back to
     * {@code prereleaseJson} unchanged.</p>
     *
     * @param stableJson stable {@code api-regions} JSON array text
     * @param prereleaseJson prerelease {@code api-regions} JSON array text
     * @return merged {@code api-regions} JSON array text, or {@code prereleaseJson} on fallback
     */
    static String merge(final String stableJson, final String prereleaseJson) {
        try {
            final ApiRegions stableRegions = ApiRegions.parse(stableJson);
            final ApiRegions prereleaseRegions = ApiRegions.parse(prereleaseJson);

            final Map<String, ApiRegion> stableByName = new LinkedHashMap<>();
            for (final ApiRegion stableRegion : stableRegions.listRegions()) {
                stableByName.put(stableRegion.getName(), stableRegion);
            }

            final ApiRegions merged = new ApiRegions();
            for (final ApiRegion prereleaseRegion : prereleaseRegions.listRegions()) {
                final ApiRegion stableRegion = stableByName.get(prereleaseRegion.getName());
                merged.add(mergeRegionExports(stableRegion, prereleaseRegion));
            }

            return merged.toJSON();
        } catch (final IOException | RuntimeException e) {
            return prereleaseJson;
        }
    }

    private static ApiRegion mergeRegionExports(final ApiRegion stableRegion, final ApiRegion prereleaseRegion) {
        final ApiRegion mergedRegion = new ApiRegion(prereleaseRegion.getName());
        mergedRegion.setFeatureOrigins(prereleaseRegion.getFeatureOrigins());
        mergedRegion.getProperties().putAll(prereleaseRegion.getProperties());

        final Map<String, ApiExport> exportsByPackageName = new LinkedHashMap<>();
        if (stableRegion != null) {
            stableRegion.listExports().forEach(export -> exportsByPackageName.put(export.getName(), export));
        }
        prereleaseRegion.listExports().forEach(export -> exportsByPackageName.put(export.getName(), export));

        exportsByPackageName.values().forEach(mergedRegion::add);
        return mergedRegion;
    }
}
