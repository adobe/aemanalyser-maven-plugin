/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.felix.cm.json.io.Configurations;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.apache.sling.feature.scanner.BundleDescriptor;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.ClassFile;

/**
 * Feature model analyser task that checks if a class extends or implements a provider type.
 */
public class ProviderTypeAnalyserTask implements AnalyserTask {

    /** Configuration property to enable strict checking */
    private static final String CFG_STRICT = "strict";

    private static final String PROVIDER_TYPES_FILE = "META-INF/api-info.json";
    private static final String PROVIDER_TYPES_KEY = "providerTypes";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderTypeAnalyserTask.class);

    private static final List<String> PROVIDER_TYPES = new ArrayList<>();

    public static boolean initializeProviderTypeInfo(final ArtifactId sdkId, final File apiFile) {
        try (final JarFile jarFile = new JarFile(apiFile)) {
            final JarEntry entry = jarFile.getJarEntry(PROVIDER_TYPES_FILE);
            if (entry != null) {
                try (final InputStream is = jarFile.getInputStream(entry)) {
                    final JsonObject providerTypeInfo = Json.createReader(is).readObject();
                    if (providerTypeInfo.containsKey(PROVIDER_TYPES_KEY)) {
                        for(final JsonValue v : providerTypeInfo.getJsonArray(PROVIDER_TYPES_KEY)) {
                            PROVIDER_TYPES.add(Configurations.convertToObject(v).toString());
                        }
                        LOGGER.debug("Found {} provider types in {}", PROVIDER_TYPES.size(), sdkId.toMvnId());
                        return true;
                    }
                }
            } else {
                LOGGER.error("API info not found in {}. Please update to a more recent version of the API. ", sdkId.toMvnId());
            }
        } catch ( final IOException ioe) {
            LOGGER.error("Error while reading API info from {}", sdkId.toMvnId());
        }
        return false;
    }

    /** We cache the result to avoid rescanning classes files for bundles used in more than one feature. */
    private static final Map<String, String> CHECKED_CLASSES = new HashMap<>();

    @Override
    public String getId() {
        return "aem-provider-type";
    }

    @Override
    public String getName() {
        return "AEM Provider Type Analyser";
    }

    @Override
    public void execute(final AnalyserTaskContext context) throws Exception {
        if ( PROVIDER_TYPES.isEmpty() ) {
            context.reportError("No provider types found.");
            return;
        }
        final boolean strict = Boolean.parseBoolean(context.getConfiguration().get(CFG_STRICT));
        for(final BundleDescriptor bundle : context.getFeatureDescriptor().getBundleDescriptors()) {
            analyse(context, bundle, strict);
        }
    }

    private void analyse(final AnalyserTaskContext context, final BundleDescriptor bundle, final boolean strict) {
        try ( final JarInputStream jis = new JarInputStream(bundle.getArtifactFile().openStream())) {
            JarEntry entry = null;
            while ( (entry = jis.getNextJarEntry()) != null ) {
                if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
                    final String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                    this.checkClass(context, bundle, className, jis, strict);
                } else if (entry.getName().endsWith(".jar")) {
                    // embedded jar?
                    final String cp = bundle.getManifest().getMainAttributes().getValue(Constants.BUNDLE_CLASSPATH);
                    if (cp != null) {
                        for(final String path : cp.split(",")) {
                            if (path.trim().equals(entry.getName())) {
                                try (final JarInputStream ejis = new JarInputStream(jis)) {
                                    JarEntry inner = null;
                                    while ( (inner = ejis.getNextJarEntry()) != null ) {
                                        if (inner.getName().endsWith(".class") && !inner.getName().startsWith("META-INF/")) {
                                            final String className = inner.getName().substring(0, inner.getName().length() - 6).replace('/', '.');
                                            this.checkClass(context, bundle, className, ejis, strict);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (final IOException e) {
            context.reportError("Error while analysing bundle ".concat(bundle.getArtifact().getId().toMvnId()).concat(" : ").concat(e.getMessage()));
        }
    }

    private void checkClass(final AnalyserTaskContext context, final BundleDescriptor bundle, final String className, final InputStream clazzStream, final boolean strict) throws IOException, RuntimeException {
        final String key = bundle.getArtifact().getId().toMvnId().concat(":").concat(className);
        String known = CHECKED_CLASSES.get(key);
        if (known != null) {
            this.reportProviderTypeUsage(context, bundle, className, known, strict);
            return;
        }
        final CtClass cc = ClassPool.getDefault().makeClass(clazzStream);
        cc.setName(className);

        final ClassFile cfile = cc.getClassFile();
        String result = "";
        for(final String name : cfile.getInterfaces()) {
            result = this.checkClassForProviderType(result, name);
        }
        result = this.checkClassForProviderType(result, cfile.getSuperclass());
        this.reportProviderTypeUsage(context, bundle, className, result, strict);
        CHECKED_CLASSES.put(key, result);
    }

    private void reportProviderTypeUsage(final AnalyserTaskContext context, final BundleDescriptor bundle, final String className, final String providerType, final boolean strict) {
        if (!providerType.isEmpty()) {
            final String msg = "Class ".concat(className).concat(" implements or extends an AEM provider type : ").concat(providerType);
            if (strict) {
                context.reportArtifactError(bundle.getArtifact().getId(), msg);
            } else {
                context.reportArtifactWarning(bundle.getArtifact().getId(), msg);
            }
        }
    }

    private String checkClassForProviderType(final String result, final String name) {
        if (PROVIDER_TYPES.contains(name)) {
            if (!result.isEmpty()) {
                return result.concat(", ");
            }
            return result.concat(name);
        }
        return result;
    }
}