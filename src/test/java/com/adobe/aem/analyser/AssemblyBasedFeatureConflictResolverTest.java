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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.junit.Test;

public class AssemblyBasedFeatureConflictResolverTest {

    private final AssemblyBasedFeatureConflictResolver resolver = new AssemblyBasedFeatureConflictResolver();

    @Test
    public void testResolveVersionConflictCopiesExtensionWhenOnlyInPrerelease() {
        Feature stable = createStableFeature();
        Feature prerelease = createPrereleaseFeature();

        Extension prereleaseOnly = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.OPTIONAL);
        prereleaseOnly.setText("prerelease-content");
        prerelease.getExtensions().add(prereleaseOnly);

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        Extension mergedExt = merged.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(mergedExt);
        assertEquals(ExtensionType.TEXT, mergedExt.getType());
        assertEquals("prerelease-content", mergedExt.getText());
    }

    @Test
    public void testResolveVersionConflictReplacesTextExtensionWithPrerelease() {
        Feature stable = createStableFeature();
        Feature prerelease = createPrereleaseFeature();

        Extension stableText = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.OPTIONAL);
        stableText.setText("stable-text");
        stable.getExtensions().add(stableText);

        Extension prereleaseText = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.OPTIONAL);
        prereleaseText.setText("prerelease-text");
        prerelease.getExtensions().add(prereleaseText);

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        Extension mergedText = merged.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(mergedText);
        assertEquals("prerelease-text", mergedText.getText());
    }

    @Test
    public void testResolveVersionConflictNotReplacesTextExtensionWithEmptyPrerelease() {
        Feature stable = createStableFeature();
        Feature prerelease = createPrereleaseFeature();

        Extension stableText = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.OPTIONAL);
        stableText.setText("stable-text");
        stable.getExtensions().add(stableText);

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        Extension mergedText = merged.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(mergedText);
        assertEquals("stable-text", mergedText.getText());
    }

    @Test
    public void testResolveVersionConflictMergesApiRegionsJsonHappyPath() {
        Feature stable = createStableFeature();
        Feature prerelease = createPrereleaseFeature();

        Extension stableApiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
        stableApiRegions.setJSON("[{\"name\":\"global\",\"exports\":[\"stable.api\"]}]");
        stable.getExtensions().add(stableApiRegions);

        Extension prereleaseApiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
        prereleaseApiRegions.setJSON("[{\"name\":\"global\",\"exports\":[\"prerelease.api\"]}]");
        prerelease.getExtensions().add(prereleaseApiRegions);

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        Extension mergedApiRegions = merged.getExtensions().getByName("api-regions");
        assertNotNull(mergedApiRegions);
        assertEquals("[{\"name\":\"global\",\"exports\":[\"stable.api\",\"prerelease.api\"]}]", mergedApiRegions.getJSON());
    }


    @Test
    public void testResolveVersionConflictReplacesArtifactsExtensionWithPrerelease() {
        Feature stable = createStableFeature();
        Feature prerelease = createPrereleaseFeature();

        Extension stableArtifacts = new Extension(ExtensionType.ARTIFACTS, "artifacts-ext", ExtensionState.OPTIONAL);
        stableArtifacts.getArtifacts().add(new Artifact(ArtifactId.fromMvnId("g:stable.bundle:1.0.0")));
        stableArtifacts.getArtifacts().add(new Artifact(ArtifactId.fromMvnId("g:shared.bundle:1.0.1")));
        stable.getExtensions().add(stableArtifacts);

        Extension prereleaseArtifacts = new Extension(ExtensionType.ARTIFACTS, "artifacts-ext", ExtensionState.OPTIONAL);
        prereleaseArtifacts.getArtifacts().add(new Artifact(ArtifactId.fromMvnId("g:prerelease.bundle:2.0.0")));
        prereleaseArtifacts.getArtifacts().add(new Artifact(ArtifactId.fromMvnId("g:new.bundle:3.0.0")));
        prereleaseArtifacts.getArtifacts().add(new Artifact(ArtifactId.fromMvnId("g:shared.bundle:1.0.0")));
        prerelease.getExtensions().add(prereleaseArtifacts);

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        Extension mergedArtifacts = merged.getExtensions().getByName("artifacts-ext");
        assertNotNull(mergedArtifacts);
        assertEquals(4, mergedArtifacts.getArtifacts().size());
        assertEquals("1.0.0", findArtifactVersion(mergedArtifacts, "stable.bundle"));
        assertEquals("2.0.0", findArtifactVersion(mergedArtifacts, "prerelease.bundle"));
        assertEquals("3.0.0", findArtifactVersion(mergedArtifacts, "new.bundle"));
        assertEquals("1.0.0", findArtifactVersion(mergedArtifacts, "shared.bundle"));
    }

    @Test
    public void testResolveVersionConflictMergesVariablesAndFrameworkProperties() {
        Feature stable = createStableFeature();
        stable.getVariables().put("stable.only", "stable-value");
        stable.getVariables().put("common.var", "stable-common");
        stable.getFrameworkProperties().put("stable.fw.only", "stable-fw-value");
        stable.getFrameworkProperties().put("common.fw", "stable-fw-common");

        Feature prerelease = createPrereleaseFeature();
        prerelease.getVariables().put("prerelease.only", "prerelease-value");
        prerelease.getVariables().put("common.var", "prerelease-common");
        prerelease.getFrameworkProperties().put("prerelease.fw.only", "prerelease-fw-value");
        prerelease.getFrameworkProperties().put("common.fw", "prerelease-fw-common");

        Feature merged = resolver.resolveVersionConflict(stable, prerelease, SdkProductVariation.AUTHOR);

        assertEquals(3, merged.getVariables().size());
        assertEquals("stable-value", merged.getVariables().get("stable.only"));
        assertEquals("prerelease-value", merged.getVariables().get("prerelease.only"));
        assertEquals("prerelease-common", merged.getVariables().get("common.var"));

        assertEquals(3, merged.getFrameworkProperties().size());
        assertEquals("stable-fw-value", merged.getFrameworkProperties().get("stable.fw.only"));
        assertEquals("prerelease-fw-value", merged.getFrameworkProperties().get("prerelease.fw.only"));
        assertEquals("prerelease-fw-common", merged.getFrameworkProperties().get("common.fw"));
    }

    private static Feature createStableFeature() {
        return new Feature(ArtifactId.fromMvnId(
                "com.adobe.aem:aem-sdk-api:slingosgifeature:aem-author-sdk:1.0.0"));
    }

    private static Feature createPrereleaseFeature() {
        return new Feature(ArtifactId.fromMvnId(
                "com.adobe.aem:aem-prerelease-sdk-api:slingosgifeature:aem-author-sdk:1.0.0"));
    }

    private static String findArtifactVersion(final Extension artifactsExtension, final String artifactId) {
        return artifactsExtension.getArtifacts().stream()
                .filter(artifact -> artifactId.equals(artifact.getId().getArtifactId()))
                .findFirst()
                .map(artifact -> artifact.getId().getVersion())
                .orElse(null);
    }
}
