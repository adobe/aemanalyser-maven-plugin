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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.Operation;

public class RepoInitValidator {

    public static final String SLING_INF_NODE_TYPES = "SLING-INF/nodetypes";

    private ArtifactProvider artifactProvider;

    public void setArtifactProvider(final ArtifactProvider artifactProvider) {
        this.artifactProvider = artifactProvider;
    }

    public void validate(final Feature feature) throws Exception {
        final Extension repoinit = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        if (repoinit == null) {
            return;
        }
        final String repoinitText = repoinit.getText();
        if (repoinitText == null || repoinitText.isBlank()) {
            return;
        }
        if (this.artifactProvider == null) {
            throw new IllegalStateException("ArtifactProvider must be set before validating repoinit");
        }

        final Repository repository = new Jcr(new Oak(new MemoryNodeStore()))
                .with(new RepoinitSecurityProvider())
                .createRepository();
        final Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        try {
            registerNodeTypes(session, feature);

            final RepoInitParserImpl repoInitParser = new RepoInitParserImpl(new StringReader(repoinitText));
            final List<Operation> operations = repoInitParser.parse();
            new JcrRepoInitOpsProcessorImpl().apply(session, operations);
            session.save();
        } finally {
            session.logout();
        }
    }

    private static class NamedByteArrayInputStream extends ByteArrayInputStream {

        private final String name;

        NamedByteArrayInputStream(final byte[] buf, final String name) {
            super(buf);
            this.name = name;
        }

    }

    private void registerNodeTypes(final Session session, final Feature feature) throws Exception {
        final LinkedList<NamedByteArrayInputStream> nodeTypeInputStreams = new LinkedList<>();
        for (final Artifact artifact : feature.getBundles()) {
            collectNodeTypeStreams(artifact, nodeTypeInputStreams);
        }

        final int maxSize = nodeTypeInputStreams.size() * 4;
        int counter = 0;

        final Map<String, Throwable> exceptions = new HashMap<>();

        while (counter < maxSize && !nodeTypeInputStreams.isEmpty()) {
            counter++;
            final NamedByteArrayInputStream stream = nodeTypeInputStreams.pop();
            try {
                registerNodeTypes(session, stream);
                exceptions.remove(stream.name);
            } catch (final Exception ex) {
                stream.reset();
                nodeTypeInputStreams.addLast(stream);
                exceptions.put(stream.name, ex);
            }
        }

        if (!nodeTypeInputStreams.isEmpty()) {
            final IllegalStateException illegalStateException = new IllegalStateException("Exception installing nodetype definitions");
            exceptions.forEach((key, value) -> illegalStateException.addSuppressed(
                    new Exception("Exception installing nodetype definition file " + key, value)));
            throw illegalStateException;
        }

        session.save();
    }

    private void collectNodeTypeStreams(final Artifact artifact, final LinkedList<NamedByteArrayInputStream> nodeTypeInputStreams)
            throws IOException {
        final URL url = this.artifactProvider.provide(artifact.getId());
        if (url == null) {
            return;
        }
        try (InputStream inputStream = url.openStream();
             JarInputStream jarInputStream = new JarInputStream(inputStream)) {
            JarEntry nextJarEntry;
            while ((nextJarEntry = jarInputStream.getNextJarEntry()) != null) {
                final String name = nextJarEntry.getName();
                if (name.startsWith(SLING_INF_NODE_TYPES) && name.endsWith(".cnd")) {
                    nodeTypeInputStreams.add(new NamedByteArrayInputStream(jarInputStream.readAllBytes(), name));
                }
                jarInputStream.closeEntry();
            }
        }
    }

    private void registerNodeTypes(final Session session, final NamedByteArrayInputStream nodeTypeDefinition) throws Exception {
        try (Reader reader = new InputStreamReader(nodeTypeDefinition, StandardCharsets.UTF_8)) {
            CndImporter.registerNodeTypes(reader, session, true);
        }
    }

}
