package com.adobe.aem.analyser;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.Test;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepoinitValidator {

    private static final String NODETYPES_RESOURCE = "/nodetypes";

    @Test
    public void validate() throws Exception {
        Repository repository = new Jcr(new Oak(new MemoryNodeStore()))
                .with(SecurityProviderBuilder.newBuilder().build())
                .createRepository();
        Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        try {
            registerNodeTypes(session);

            try (InputStream inputStream = getClass().getResource("/repoinit.txt").openStream()) {
                RepoInitParserImpl repoInitParser = new RepoInitParserImpl(inputStream);
                List<Operation> operations = repoInitParser.parse();
                new JcrRepoInitOpsProcessorImpl().apply(session, operations);
                session.save();
            }
        } finally {
            session.logout();
        }
    }

    private void registerNodeTypes(Session session) throws Exception {
        URL nodetypesUrl = getClass().getResource(NODETYPES_RESOURCE);
        if (nodetypesUrl == null) {
            throw new IllegalStateException("Resource directory not found: " + NODETYPES_RESOURCE);
        }
        Path nodetypesDir = Paths.get(nodetypesUrl.toURI());
        try (Stream<Path> cndFiles = Files.list(nodetypesDir)
                .filter(path -> path.getFileName().toString().endsWith(".cnd"))
                .sorted()) {
            for (Path cndFile : cndFiles.collect(Collectors.toList())) {
                try (InputStream in = Files.newInputStream(cndFile)) {
                    CndImporter.registerNodeTypes(in, session, true);
                }
            }
        }
    }

}
