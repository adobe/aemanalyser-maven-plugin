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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ApiRegionsMergeHandlerTest {

    @Test
    public void testMergeUsesStableExportsWhenPrereleaseExportsMissing() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"a.b\"]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"imports\":[\"x.y\"]}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));

        assertEquals("a.b", mergedRegion.getJsonArray("exports").getString(0));
        assertEquals("x.y", mergedRegion.getJsonArray("imports").getString(0));
    }

    @Test
    public void testMergeUsesStableExportsWhenPrereleaseExportsEmpty() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"a.b\"]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[]}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));

        assertEquals("a.b", mergedRegion.getJsonArray("exports").getString(0));
    }

    @Test
    public void testMergeKeepsPrereleaseExportsWhenNonEmpty() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"stable.api\"]}]";
        final String prereleaseJson = "[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));

        assertEquals("prerelease.api", mergedRegion.getJsonArray("exports").getString(0));
    }

    @Test
    public void testMergeKeepsUnnamedRegionUnchanged() {
        final String stableJson = "[{\"name\":\"global\",\"exports\":[\"stable.api\"]}]";
        final String prereleaseJson = "[{\"exports\":[]}]";

        final JsonObject mergedRegion = firstObjectRegion(ApiRegionsMergeHandler.merge(stableJson, prereleaseJson));

        assertFalse(mergedRegion.containsKey("name"));
        assertEquals(0, mergedRegion.getJsonArray("exports").size());
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
}
