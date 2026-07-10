/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.aem.analyser;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ApiRegionsMergeHandlerTest {

    @Test
    public void testMergeMergesExportsByPackageNameWithPrereleasePrecedence() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[{\"name\":\"ch.qos.logback.classic\",\"deprecated\":{\"msg\":\"stable-msg\",\"since\":\"2022-01-27\",\"for-removal\":\"2025-08-31\"}},{\"name\":\"org.apache.abdera.ext.opensearch\",\"deprecated\":{\"msg\":\"legacy-stable\",\"since\":\"2019-04-08\",\"for-removal\":\"2025-08-31\"}}]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[{\"name\":\"ch.qos.logback.classic\",\"deprecated\":{\"msg\":\"prerelease-msg\",\"since\":\"2022-01-27\",\"for-removal\":\"2025-08-31\"}},{\"name\":\"com.example.new.api\",\"deprecated\":{\"msg\":\"new-prerelease\",\"since\":\"2026-01-01\",\"for-removal\":\"2028-01-01\"}}]}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));
        final JsonArray exports = mergedRegion.getJsonArray("exports");

        assertEquals(3, exports.size());
        assertEquals("prerelease-msg", deprecatedMessageByExportName(exports, "ch.qos.logback.classic"));
        assertEquals("legacy-stable", deprecatedMessageByExportName(exports, "org.apache.abdera.ext.opensearch"));
        assertEquals("new-prerelease", deprecatedMessageByExportName(exports, "com.example.new.api"));
    }

    @Test
    public void testMergeKeepsPrereleaseRegionProperties() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"stable.api\"],\"kind\":\"stable-kind\"}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"],\"kind\":\"prerelease-kind\"}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));

        assertEquals("prerelease-kind", mergedRegion.getString("kind"));
    }

    //?
    @Test
    public void testMergeKeepsOnlyPrereleaseRegionSet() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"stable.api\"]},{\"name\":\"internal\",\"exports\":[\"internal.api\"]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]";

        String merge = ApiRegionsMergeHandler.merge(stableJson, prereleaseJson);
        try (final JsonReader reader = Json.createReader(new StringReader(merge))) {
            final JsonArray regions = reader.readArray();

            assertEquals(1, regions.size());
            assertEquals("global", regions.getJsonObject(0).getString("name"));
        }
    }

    @Test
    public void testMergeReturnsPrereleaseWhenStableJsonContainsNonModelFields() {
        final String stableJson = "[{\"name\":\"global\",\"imports\":[\"x.y\"],\"exports\":[\"stable.api\"]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]";

        assertEquals(prereleaseJson, ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));
    }

    @Test
    public void testMergeReturnsPrereleaseWhenStableJsonBlank() {
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]";

        assertEquals(prereleaseJson, ApiRegionsMergeHandler.merge("   ", prereleaseJson));
    }

    @Test
    public void testMergeReturnsPrereleaseWhenStableJsonIsNotArray() {
        final String stableJson = "{\"aa\":\"aa\"}";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]";

        assertEquals(prereleaseJson, ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));
    }

    @Test
    public void testMergeAddsStableExportsWhenPrereleaseRegionsHaveNoExports() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[{\"name\":\"ch.qos.logback.classic\",\"deprecated\":{\"msg\":\"This internal logback API is not supported by AEM as a Cloud Service.\",\"since\":\"2022-01-27\",\"for-removal\":\"2025-08-31\"}}]},{\"name\":\"com.adobe.aem.deprecated\",\"exports\":[{\"name\":\"org.apache.abdera.ext.opensearch\",\"deprecated\":{\"msg\":\"Legacy AEM 6.x API.\",\"since\":\"2019-04-08\",\"for-removal\":\"2025-08-31\"}}]}]";
        final String prereleaseJson = "[{\"name\":\"global\"},{\"name\":\"com.adobe.aem.deprecated\"}]";

        try (final JsonReader reader = Json.createReader(new StringReader(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson)))) {
            final JsonArray regions = reader.readArray();
            assertEquals(2, regions.size());

            final JsonObject global = regionByName(regions, "global");
            final JsonObject deprecated = regionByName(regions, "com.adobe.aem.deprecated");
            assertNotNull(global);
            assertNotNull(deprecated);

            final JsonArray globalExports = global.getJsonArray("exports");
            final JsonArray deprecatedExports = deprecated.getJsonArray("exports");
            assertNotNull(globalExports);
            assertNotNull(deprecatedExports);
            assertEquals(1, globalExports.size());
            assertEquals(1, deprecatedExports.size());
            assertEquals("This internal logback API is not supported by AEM as a Cloud Service.", deprecatedMessageByExportName(globalExports, "ch.qos.logback.classic"));
            assertEquals("Legacy AEM 6.x API.", deprecatedMessageByExportName(deprecatedExports, "org.apache.abdera.ext.opensearch"));
        }
    }

    private String deprecatedMessageByExportName(final JsonArray exports, final String packageName) {
        for (final JsonValue export : exports) {
            if (export.getValueType() == JsonValue.ValueType.OBJECT) {
                final JsonObject exportObject = export.asJsonObject();
                if (packageName.equals(exportObject.getString("name", null))) {
                    final JsonObject deprecated = exportObject.getJsonObject("deprecated");
                    return deprecated != null ? deprecated.getString("msg", null) : null;
                }
            }
        }
        return null;
    }

    private JsonObject firstObjectRegion(final String json) {
        try (final JsonReader reader = Json.createReader(new StringReader(json))) {
            final JsonArray result = reader.readArray();
            assertEquals(1, result.size());
            final JsonValue value = result.get(0);
            assertEquals(JsonValue.ValueType.OBJECT, value.getValueType());
            final JsonObject region = value.asJsonObject();
            assertNotNull(region);
            return region;
        }
    }

    private JsonObject regionByName(final JsonArray regions, final String name) {
        for (final JsonValue regionValue : regions) {
            if (regionValue.getValueType() == JsonValue.ValueType.OBJECT) {
                final JsonObject region = regionValue.asJsonObject();
                if (name.equals(region.getString("name", null))) {
                    return region;
                }
            }
        }
        return null;
    }
}
