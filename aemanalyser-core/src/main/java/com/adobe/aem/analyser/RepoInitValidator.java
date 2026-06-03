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
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.apache.jackrabbit.oak.security.authentication.AuthenticationConfigurationImpl;
import org.apache.jackrabbit.oak.security.authentication.token.TokenConfigurationImpl;
import org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.principal.PrincipalConfigurationImpl;
import org.apache.jackrabbit.oak.security.privilege.PrivilegeConfigurationImpl;
import org.apache.jackrabbit.oak.security.user.UserConfigurationImpl;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ArtifactProvider;
import org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoInitValidator {

    public static final String SLING_INF_NODE_TYPES = "SLING-INF/nodetypes";

    private static final ConfigurationParameters CONFIGURATION_PARAMETERS = ConfigurationParameters.of(Map.of(
            "groupsPath", "/home/groups",
            "usersPath", "/home/users"
    ));
    private static final Logger LOGGER = LoggerFactory.getLogger(RepoInitValidator.class);
    private static final int RETRY_UPPER_LIMIT_MULTIPLICATION_FACTOR = 4;

    private final ArtifactProvider artifactProvider;

    public RepoInitValidator(ArtifactProvider artifactProvider ) {
        this.artifactProvider = artifactProvider;
      
        if (this.artifactProvider == null) {
            throw new IllegalStateException("ArtifactProvider must be set before validating repoinit");
        }
    }

    public void validate(final Feature feature) throws Exception {
        final String repoinitText = getRepoInitText(feature);
        if (repoinitText == null || repoinitText.isBlank()) {
            return;
        }

        final JackrabbitRepository repository = (JackrabbitRepository) new Jcr(new Oak(new MemoryNodeStore()))
                .with(SecurityProviderBuilder.newBuilder()
                        .with(
                                new AuthenticationConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new PrivilegeConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new UserConfigurationImpl(), CONFIGURATION_PARAMETERS,
                                new AuthorizationConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new PrincipalConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new TokenConfigurationImpl(), ConfigurationParameters.EMPTY
                        )
                        .build())
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
            repository.shutdown();
        }
    }

    private static String getRepoInitText(Feature feature) {
        final Extension repoinit = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        if (repoinit == null) {
            return null;
        }
        final String repoinitText = repoinit.getText();
        return repoinitText;
    }

    private static class NamedByteArrayInputStream extends ByteArrayInputStream {

        private final String name;

        NamedByteArrayInputStream(final byte[] buf, final String name) {
            super(buf);
            this.name = name;
        }

    }

    private void registerNodeTypes(final Session session, final Feature feature) throws Exception {
        final Deque<NamedByteArrayInputStream> nodeTypeInputStreamsDequeue = collectRegisterNodeTypeDequeue(feature);

        final int retryCountUpperLimit = nodeTypeInputStreamsDequeue.size() * RETRY_UPPER_LIMIT_MULTIPLICATION_FACTOR;
        int counter = 0;

        final Map<String, Throwable> exceptions = new HashMap<>();

        while (counter < retryCountUpperLimit && !nodeTypeInputStreamsDequeue.isEmpty()) {
            counter++;
            final NamedByteArrayInputStream stream = nodeTypeInputStreamsDequeue.pop();
            try {
                registerNodeTypes(session, stream);
                exceptions.remove(stream.name);
            } catch (final Exception ex) {
                stream.reset();
                nodeTypeInputStreamsDequeue.addLast(stream);
                exceptions.put(stream.name, ex);
            }
        }

        if (!nodeTypeInputStreamsDequeue.isEmpty()) {
            final IllegalStateException illegalStateException = new IllegalStateException("Exception installing Node Type definitions");
            exceptions.forEach((key, value) -> illegalStateException.addSuppressed(
                    new Exception("Exception installing Node Type definition file " + key, value)));
            throw illegalStateException;
        }

        session.save();
    }
    
    private Deque<NamedByteArrayInputStream> collectRegisterNodeTypeDequeue(Feature feature) throws IOException {
        final Deque<NamedByteArrayInputStream> nodeTypeInputStreamsDequeue = new LinkedList<>();
        for (final Artifact artifact : feature.getBundles()) {
            collectRegisterNodeTypeStreams(artifact, nodeTypeInputStreamsDequeue::add);
        }
        return nodeTypeInputStreamsDequeue;
    }

    private void collectRegisterNodeTypeStreams(final Artifact artifact, Consumer<NamedByteArrayInputStream> addRegisterNodeTypeInputStream)
            throws IOException {
        try{
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
                        addRegisterNodeTypeInputStream.accept(new NamedByteArrayInputStream(jarInputStream.readAllBytes(), name));
                    }
                    jarInputStream.closeEntry();
                }
            }
        } catch (RuntimeException ex){
            LOGGER.error("Error loading artifact {} : {}", artifact.getId().toString(), ex.getMessage());
        }
     
    }

    private void registerNodeTypes(final Session session, final NamedByteArrayInputStream nodeTypeDefinition) throws Exception {
        try (Reader reader = new InputStreamReader(nodeTypeDefinition, StandardCharsets.UTF_8)) {
            CndImporter.registerNodeTypes(reader, session, true);
        }
    }

}
