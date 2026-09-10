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
package com.adobe.aem.analyser.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public class AemAnalyserResultJsonWriterTest {

    private JsonObject write(final AemAnalyserResult result) throws Exception {
        final StringWriter sw = new StringWriter();
        AemAnalyserResultJsonWriter.write(result, sw);
        try (final JsonReader reader = Json.createReader(new StringReader(sw.toString()))) {
            return reader.readObject();
        }
    }

    @Test
    public void testEmptyResult() throws Exception {
        final JsonObject json = write(new AemAnalyserResult());
        assertEquals(AemAnalyserResultJsonWriter.SCHEMA_VERSION, json.getString("schemaVersion"));
        assertEquals(0, json.getInt("errors"));
        assertEquals(0, json.getInt("warnings"));
        assertTrue(json.getJsonArray("findings").isEmpty());
        assertTrue(json.containsKey("generatedAt"));
    }

    @Test
    public void testCountsAndLevels() throws Exception {
        final AemAnalyserResult result = new AemAnalyserResult();
        result.getErrors().add(new AemAnalyserAnnotation("some error"));
        result.getWarnings().add(new AemAnalyserAnnotation("some warning"));
        result.getWarnings().add(new AemAnalyserAnnotation("another warning"));

        final JsonObject json = write(result);
        assertEquals(1, json.getInt("errors"));
        assertEquals(2, json.getInt("warnings"));

        final JsonArray findings = json.getJsonArray("findings");
        assertEquals(3, findings.size());
        assertEquals("error", findings.getJsonObject(0).getString("level"));
        assertEquals("some error", findings.getJsonObject(0).getString("message"));
        assertEquals("warning", findings.getJsonObject(1).getString("level"));
        // plain messages carry no structured deprecation object
        assertFalse(findings.getJsonObject(0).containsKey("deprecation"));
    }

    @Test
    public void testHeaderBecomesTierAndIsNotAFinding() throws Exception {
        final AemAnalyserResult result = new AemAnalyserResult();
        result.getErrors().add(new AemAnalyserAnnotation("The analyser found the following errors for author : "));
        result.getErrors().add(new AemAnalyserAnnotation("real finding"));

        final JsonObject json = write(result);
        final JsonArray findings = json.getJsonArray("findings");
        assertEquals(1, findings.size());
        assertEquals("author", findings.getJsonObject(0).getString("tier"));
        assertEquals("real finding", findings.getJsonObject(0).getString("message"));
    }

    @Test
    public void testDeprecatedPackageParsing() {
        final Map<String, Object> d = AemAnalyserResultJsonWriter.parseDeprecation(
            "Usage of deprecated package found : com.foo.bar : Use com.foo.baz instead"
            + " Deprecated since 2023.1 For removal : 2025-01-01");

        assertEquals("package", d.get("kind"));
        assertEquals(List.of("com.foo.bar"), d.get("packages"));
        assertEquals("Use com.foo.baz instead", d.get("hint"));
        assertEquals("2023.1", d.get("since"));
        assertEquals("2025-01-01", d.get("forRemoval"));
    }

    @Test
    public void testDeprecatedPackageWithoutSinceOrRemoval() {
        final Map<String, Object> d = AemAnalyserResultJsonWriter.parseDeprecation(
            "Usage of deprecated package found : com.foo.bar : Use something else");

        assertEquals("package", d.get("kind"));
        assertEquals(List.of("com.foo.bar"), d.get("packages"));
        assertEquals("Use something else", d.get("hint"));
        assertNull(d.get("since"));
        assertNull(d.get("forRemoval"));
    }

    @Test
    public void testDeprecatedPackageScheduledForRemoval() {
        final Map<String, Object> d = AemAnalyserResultJsonWriter.parseDeprecation(
            "Usage of deprecated package found : com.foo.bar : Use com.foo.baz instead"
            + " Deprecated since 2023.1 The package is scheduled to be removed in less than 90 days by 2025-01-01");

        assertEquals("Use com.foo.baz instead", d.get("hint"));
        assertEquals("2023.1", d.get("since"));
        assertEquals("2025-01-01", d.get("forRemoval"));
    }

    @Test
    public void testDeprecatedLibraryParsing() {
        final Map<String, Object> d = AemAnalyserResultJsonWriter.parseDeprecation(
            "Usage of deprecated library found : mylib, package(s) : com.foo, com.bar : Use newlib"
            + " Deprecated since 2022 For removal : 2024-06-30");

        assertEquals("library", d.get("kind"));
        assertEquals("mylib", d.get("library"));
        assertEquals(List.of("com.foo", "com.bar"), d.get("packages"));
        assertEquals("Use newlib", d.get("hint"));
        assertEquals("2022", d.get("since"));
        assertEquals("2024-06-30", d.get("forRemoval"));
    }

    @Test
    public void testDeprecationWithOriginSuffix() {
        final Map<String, Object> d = AemAnalyserResultJsonWriter.parseDeprecation(
            "Usage of deprecated package found : com.foo.bar : Use com.foo.baz instead"
            + " For removal : 2025-01-01 (mvn:com.example:my-content:1.0.0)");

        assertEquals("2025-01-01", d.get("forRemoval"));
        assertEquals("mvn:com.example:my-content:1.0.0", d.get("origin"));
    }

    @Test
    public void testNonDeprecationMessageIsNotParsed() {
        assertNull(AemAnalyserResultJsonWriter.parseDeprecation("Some unrelated analyser error"));
    }

    @Test
    public void testDeprecationEmbeddedInJson() throws Exception {
        final AemAnalyserResult result = new AemAnalyserResult();
        result.getWarnings().add(new AemAnalyserAnnotation(
            "Usage of deprecated package found : com.foo.bar : Use com.foo.baz instead For removal : 2025-01-01"));

        final JsonObject json = write(result);
        final JsonObject finding = json.getJsonArray("findings").getJsonObject(0);
        final JsonObject deprecation = finding.getJsonObject("deprecation");
        assertEquals("package", deprecation.getString("kind"));
        assertEquals("com.foo.bar", deprecation.getJsonArray("packages").getString(0));
        assertEquals("2025-01-01", deprecation.getString("forRemoval"));
    }
}
