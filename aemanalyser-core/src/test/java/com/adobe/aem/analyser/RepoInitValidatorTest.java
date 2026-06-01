/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.aem.analyser;

import static org.junit.Assert.assertNotNull;

import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.jcr.repoinit.impl.RepoInitException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RepoInitValidatorTest {

    private static final ArtifactId NODETYPES_BUNDLE_ID = new ArtifactId("test.group", "nodetypes", "1.0.0", null, "jar");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path nodetypesJar;

    @Before
    public void setUp() throws Exception {
        this.nodetypesJar = createNodeTypesJar();
    }

    @Test
    public void testSuccess() throws Exception {
        final URL repoinitUrl = getClass().getResource("/repoinit/success.txt");
        testRepoInitFile(repoinitUrl);
    }

    @Test(expected = RepoInitException.class)
    public void testFailureMissingCreatePath() throws Exception {
        final URL repoinitUrl = getClass().getResource("/repoinit/fail.txt");
        testRepoInitFile(repoinitUrl);
    }

    private void testRepoInitFile(URL repoinitUrl) throws Exception {
        assertNotNull("repoinit.txt test resource must exist", repoinitUrl);
        final String repoinitText = Files.readString(Path.of(repoinitUrl.toURI()), StandardCharsets.UTF_8);

        final Feature feature = new Feature(new ArtifactId("test.group", "feature", "1.0.0", "aggregated-author", "slingosgifeature"));
        final Extension repoinitExtension = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.REQUIRED);
        repoinitExtension.setText(repoinitText);
        feature.getExtensions().add(repoinitExtension);
        feature.getBundles().add(new Artifact(NODETYPES_BUNDLE_ID));

        final RepoInitValidator validator = new RepoInitValidator();
        validator.setArtifactProvider(id -> {
            if (!NODETYPES_BUNDLE_ID.equals(id)) {
                return null;
            }
            try {
                return this.nodetypesJar.toUri().toURL();
            } catch (final java.net.MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });

        validator.validate(feature);
    }

    private Path createNodeTypesJar() throws Exception {
        final URL nodetypesUrl = getClass().getResource("/nodetypes");
        assertNotNull("nodetypes test resources must exist", nodetypesUrl);

        final Path jarPath = this.temporaryFolder.newFile("nodetypes.jar").toPath();
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            try (Stream<Path> cndFiles = Files.list(Path.of(nodetypesUrl.toURI()))) {
                for (final Path cndFile : cndFiles.filter(path -> path.toString().endsWith(".cnd")).collect(Collectors.toList())) {
                    final String entryName = RepoInitValidator.SLING_INF_NODE_TYPES + "/" + cndFile.getFileName();
                    jarOutputStream.putNextEntry(new JarEntry(entryName));
                    jarOutputStream.write(Files.readAllBytes(cndFile));
                    jarOutputStream.closeEntry();
                }
            }
        }
        return jarPath;
    }

}
