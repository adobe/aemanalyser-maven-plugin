package com.adobe.aem.analyser;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.junit.Assert;
import org.junit.Test;

public class FeatureUtilTest {

    @Test
    public void testGetFeatureFromResources() {
        Feature featureFromResources = FeatureUtil.getFeatureFromResources();

        ArtifactId artifactId = new ArtifactId("group-id", "artifact-id", "1", "unsupported_libraries_java21", "slingosgifeature");
        Assert.assertEquals(artifactId, featureFromResources.getId());

        Assert.assertEquals(1, featureFromResources.getExtensions().size());

        Extension extension = featureFromResources.getExtensions().get(0);
        Assert.assertEquals(ExtensionType.JSON, extension.getType());
        Assert.assertEquals("artifact-rules", extension.getName());
        Assert.assertEquals(ExtensionState.OPTIONAL, extension.getState());
        Assert.assertEquals("\"LENIENT\"", extension.getJSONStructure().asJsonObject().get("mode").toString());
        Assert.assertEquals("\"org.cid15.aem.groovy.console:aem-groovy-console-bundle:17.0.0\"", extension.getJSONStructure().asJsonObject().getJsonArray("bundle-version-rules").get(0).asJsonObject().get("artifact-id").toString());
        Assert.assertEquals("\"This library is unsupported with Java 21\"", extension.getJSONStructure().asJsonObject().getJsonArray("bundle-version-rules").get(0).asJsonObject().get("message").toString());
        Assert.assertEquals("\"[17.0.0,17.0.0]\"", extension.getJSONStructure().asJsonObject().getJsonArray("bundle-version-rules").get(0).asJsonObject().getJsonArray("denied-version-ranges").get(0).toString());
    }
}