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

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * Serialises an {@link AemAnalyserResult} into a stable, machine readable JSON
 * document. The intent is to let third party tools consume the analyser output
 * &ndash; in particular the deprecated API findings &ndash; from a well defined
 * file instead of scraping the Maven build log.
 *
 * <p>The document has the following shape:</p>
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "generatedAt": "2026-07-28T10:15:30Z",
 *   "errors": 1,
 *   "warnings": 2,
 *   "findings": [
 *     {
 *       "level": "error",
 *       "tier": "author",
 *       "message": "Usage of deprecated package found : com.foo : Use com.bar instead Deprecated since 2023.1 For removal : 2025-01-01",
 *       "deprecation": {
 *         "kind": "package",
 *         "packages": [ "com.foo" ],
 *         "hint": "Use com.bar instead",
 *         "since": "2023.1",
 *         "forRemoval": "2025-01-01"
 *       }
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>The {@code message} field is always the verbatim analyser message so that
 * consumers are never forced to rely on the parsed {@code deprecation} object.
 * The {@code deprecation} object is a best effort structured view that is only
 * emitted when the message matches the {@code region-deprecated-api} format.</p>
 */
public class AemAnalyserResultJsonWriter {

    /** Schema version of the emitted document. Bump on incompatible changes. */
    public static final String SCHEMA_VERSION = "1.0";

    private static final String LEVEL_ERROR = "error";

    private static final String LEVEL_WARNING = "warning";

    /**
     * Matches the section header lines that {@code AemAnalyser} inserts into the
     * flat result lists, e.g. "The analyser found the following errors for author : ".
     * Group 1 is the finding type, group 2 the tier.
     */
    private static final Pattern HEADER =
        Pattern.compile("^The analyser found the following (errors|warnings) for (.+) : $");

    private static final String PKG_PREFIX = "Usage of deprecated package found : ";

    private static final String LIB_PREFIX = "Usage of deprecated library found : ";

    private static final String LIB_PACKAGES_SEP = ", package(s) : ";

    private static final String MARK_SINCE = " Deprecated since ";

    private static final String MARK_FOR_REMOVAL = " For removal : ";

    private static final String MARK_SCHEDULED = " The package is scheduled to be removed in less than ";

    private static final String MARK_SCHEDULED_BY = " days by ";

    private AemAnalyserResultJsonWriter() {
        // static helper
    }

    /**
     * Write the given result as JSON to the provided writer.
     *
     * @param result the analyser result, must not be {@code null}
     * @param out the target writer, must not be {@code null}
     * @throws IOException if writing fails
     */
    public static void write(final AemAnalyserResult result, final Writer out) throws IOException {
        final JsonObjectBuilder root = Json.createObjectBuilder();
        root.add("schemaVersion", SCHEMA_VERSION);
        root.add("generatedAt", Instant.now().toString());
        root.add("errors", result.getErrors().size());
        root.add("warnings", result.getWarnings().size());

        final JsonArrayBuilder findings = Json.createArrayBuilder();
        appendFindings(findings, result.getErrors(), LEVEL_ERROR);
        appendFindings(findings, result.getWarnings(), LEVEL_WARNING);
        root.add("findings", findings);

        final Map<String, Object> config =
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        final JsonWriterFactory factory = Json.createWriterFactory(config);
        try (final JsonWriter jsonWriter = factory.createWriter(out)) {
            jsonWriter.writeObject(root.build());
        }
    }

    private static void appendFindings(final JsonArrayBuilder findings,
            final List<AemAnalyserAnnotation> annotations, final String level) {
        String tier = null;
        for (final AemAnalyserAnnotation ann : annotations) {
            final String message = ann.getMessage();
            final Matcher headerMatcher = HEADER.matcher(message);
            if (headerMatcher.matches()) {
                // Section header - remember the tier, do not emit it as a finding.
                tier = headerMatcher.group(2);
                continue;
            }

            final JsonObjectBuilder finding = Json.createObjectBuilder();
            finding.add("level", level);
            if (tier != null) {
                finding.add("tier", tier);
            }
            finding.add("message", message);

            final Map<String, Object> deprecation = parseDeprecation(message);
            if (deprecation != null) {
                finding.add("deprecation", toJson(deprecation));
            }
            findings.add(finding);
        }
    }

    /**
     * Best effort parse of a {@code region-deprecated-api} message into a
     * structured map. Returns {@code null} if the message is not a deprecation
     * finding.
     */
    static Map<String, Object> parseDeprecation(final String rawMessage) {
        String message = rawMessage;
        // The message may carry a trailing " (origin...)" content package origin
        // suffix appended by AemAnalyser. Keep it out of the parsed fields.
        String origin = null;
        if (message.endsWith(")")) {
            final int open = message.lastIndexOf(" (");
            if (open > 0) {
                origin = message.substring(open + 2, message.length() - 1);
                message = message.substring(0, open);
            }
        }

        final Map<String, Object> result = new HashMap<>();
        final List<String> packages = new ArrayList<>();
        final String remainder;

        if (message.startsWith(PKG_PREFIX)) {
            result.put("kind", "package");
            final String body = message.substring(PKG_PREFIX.length());
            final int sep = body.indexOf(" : ");
            if (sep < 0) {
                return null;
            }
            packages.add(body.substring(0, sep).trim());
            remainder = body.substring(sep + 3);
        } else if (message.startsWith(LIB_PREFIX)) {
            result.put("kind", "library");
            final String body = message.substring(LIB_PREFIX.length());
            final int libSep = body.indexOf(LIB_PACKAGES_SEP);
            if (libSep < 0) {
                return null;
            }
            result.put("library", body.substring(0, libSep).trim());
            final String afterLib = body.substring(libSep + LIB_PACKAGES_SEP.length());
            final int sep = afterLib.indexOf(" : ");
            if (sep < 0) {
                return null;
            }
            for (final String pkg : afterLib.substring(0, sep).split(",")) {
                final String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    packages.add(trimmed);
                }
            }
            remainder = afterLib.substring(sep + 3);
        } else {
            return null;
        }

        result.put("packages", packages);
        parseHintSinceRemoval(remainder, result);
        if (origin != null) {
            result.put("origin", origin);
        }
        return result;
    }

    private static void parseHintSinceRemoval(final String remainder, final Map<String, Object> result) {
        // Find the earliest trailer marker; everything before it is the hint.
        final int sinceIdx = remainder.indexOf(MARK_SINCE);
        final int removalIdx = remainder.indexOf(MARK_FOR_REMOVAL);
        final int scheduledIdx = remainder.indexOf(MARK_SCHEDULED);

        final int hintEnd = firstNonNegative(sinceIdx, removalIdx, scheduledIdx);
        final String hint = (hintEnd < 0 ? remainder : remainder.substring(0, hintEnd)).trim();
        if (!hint.isEmpty()) {
            result.put("hint", hint);
        }

        if (sinceIdx >= 0) {
            final int sinceStart = sinceIdx + MARK_SINCE.length();
            final int sinceEnd = firstNonNegativeFrom(sinceStart, removalIdx, scheduledIdx);
            final String since = (sinceEnd < 0 ? remainder.substring(sinceStart)
                : remainder.substring(sinceStart, sinceEnd)).trim();
            if (!since.isEmpty()) {
                result.put("since", since);
            }
        }

        if (removalIdx >= 0) {
            final String forRemoval = remainder.substring(removalIdx + MARK_FOR_REMOVAL.length()).trim();
            if (!forRemoval.isEmpty()) {
                result.put("forRemoval", forRemoval);
            }
        } else if (scheduledIdx >= 0) {
            final int byIdx = remainder.indexOf(MARK_SCHEDULED_BY, scheduledIdx);
            if (byIdx >= 0) {
                final String forRemoval = remainder.substring(byIdx + MARK_SCHEDULED_BY.length()).trim();
                if (!forRemoval.isEmpty()) {
                    result.put("forRemoval", forRemoval);
                }
            }
        }
    }

    /** @return the smallest non-negative value, or -1 if all are negative. */
    private static int firstNonNegative(final int... values) {
        int min = -1;
        for (final int v : values) {
            if (v >= 0 && (min < 0 || v < min)) {
                min = v;
            }
        }
        return min;
    }

    /** As {@link #firstNonNegative} but only considers values greater than {@code from}. */
    private static int firstNonNegativeFrom(final int from, final int... values) {
        int min = -1;
        for (final int v : values) {
            if (v >= from && (min < 0 || v < min)) {
                min = v;
            }
        }
        return min;
    }

    @SuppressWarnings("unchecked")
    private static JsonObjectBuilder toJson(final Map<String, Object> map) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final Object value = entry.getValue();
            if (value instanceof List) {
                final JsonArrayBuilder array = Json.createArrayBuilder();
                for (final String item : (List<String>) value) {
                    array.add(item);
                }
                builder.add(entry.getKey(), array);
            } else {
                builder.add(entry.getKey(), value.toString());
            }
        }
        return builder;
    }
}
