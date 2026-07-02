package com.adobe.aem.analyser;

import jakarta.json.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility responsible for merging the {@code api-regions} JSON extension content.
 *
 * <p>The merge iterates over prerelease regions and keeps prerelease data by default.
 * For matching regions, stable {@code exports} are reused only when prerelease does not
 * provide exports (missing or empty array).</p>
 */
class ApiRegionsMergeHandler {

    private ApiRegionsMergeHandler() {
    }

    /**
     * Merges two {@code api-regions} JSON arrays and returns the resulting JSON text.
     *
     * <p>Prerelease content is the baseline. Stable {@code exports} are copied only for
     * matching regions where prerelease exports are missing or empty. If either JSON input
     * is blank, null, or cannot be parsed as an array, the method falls back to
     * {@code prereleaseJson} unchanged.</p>
     *
     * @param stableJson stable {@code api-regions} JSON array text
     * @param prereleaseJson prerelease {@code api-regions} JSON array text
     * @return merged {@code api-regions} JSON array text, or {@code prereleaseJson} on fallback
     */
    static String merge(final String stableJson, final String prereleaseJson) {
        final JsonArray stableRegions = parseJsonArray(stableJson);
        final JsonArray prereleaseRegions = parseJsonArray(prereleaseJson);
        if (stableRegions == null || prereleaseRegions == null) {
            return prereleaseJson;
        }

        final Map<String, JsonObject> stableRegionsByName = new LinkedHashMap<>();
        for (final JsonValue regionVal : stableRegions) {
            if (regionVal.getValueType() == JsonValue.ValueType.OBJECT) {
                final JsonObject region = regionVal.asJsonObject();
                final String regionName = region.getString("name", null);
                if (regionName != null) {
                    stableRegionsByName.put(regionName, region);
                }
            }
        }

        final JsonArrayBuilder mergedRegions = Json.createArrayBuilder();
        for (final JsonValue regionVal : prereleaseRegions) {
            if (regionVal.getValueType() != JsonValue.ValueType.OBJECT) {
                mergedRegions.add(regionVal);
                continue;
            }

            final JsonObject prereleaseRegion = regionVal.asJsonObject();
            final String regionName = prereleaseRegion.getString("name", null);
            final JsonObject stableRegion = stableRegionsByName.get(regionName);
            if (regionName == null || stableRegion == null || !shouldUseStableExports(prereleaseRegion, stableRegion)) {
                mergedRegions.add(prereleaseRegion);
                continue;
            }

            final JsonObjectBuilder mergedRegion = Json.createObjectBuilder();
            for (final Map.Entry<String, JsonValue> entry : prereleaseRegion.entrySet()) {
                if (!"exports".equals(entry.getKey())) {
                    mergedRegion.add(entry.getKey(), entry.getValue());
                }
            }
            mergedRegion.add("exports", stableRegion.getJsonArray("exports"));
            mergedRegions.add(mergedRegion);
        }

        final StringWriter writer = new StringWriter();
        try (final JsonWriter jsonWriter = Json.createWriter(writer)) {
            jsonWriter.writeArray(mergedRegions.build());
        }
        return writer.toString();
    }

    private static boolean shouldUseStableExports(final JsonObject prereleaseRegion, final JsonObject stableRegion) {
        final JsonArray stableExports = stableRegion.getJsonArray("exports");
        if (stableExports == null || stableExports.isEmpty()) {
            return false;
        }

        if (!prereleaseRegion.containsKey("exports")) {
            return true;
        }
        final JsonValue prereleaseExports = prereleaseRegion.get("exports");
        return prereleaseExports.getValueType() == JsonValue.ValueType.ARRAY
                && prereleaseExports.asJsonArray().isEmpty();
    }

    private static JsonArray parseJsonArray(final String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try (final JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            return jsonReader.readArray();
        } catch (final JsonException e) {
            return null;
        }
    }
}
