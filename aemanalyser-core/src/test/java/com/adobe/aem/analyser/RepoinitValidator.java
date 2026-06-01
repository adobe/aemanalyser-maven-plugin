package com.adobe.aem.analyser;

import org.apache.commons.collections4.EnumerationUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.Test;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jackrabbit.oak.security.user.UserConfigurationImpl;

public class RepoinitValidator {

    private static final String NODETYPES_RESOURCE = "/nodetypes";

    /** Base Sling types that other CND files in nodetypes/ depend on. */
    private static final List<String> NODETYPE_LOAD_ORDER = Arrays.asList(
            "org.apache.sling.jcr.resource-3.3.6.jar-resource.cnd",
            "org.apache.sling.jcr.resource-3.3.6.jar-folder.cnd"
    );

    @Test
    public void validate() throws Exception {

        ConfigurationParameters params = ConfigurationParameters.of(Map.of(
                "groupsPath", "/home/groups",
                "usersPath", "/home/users"
        ));
        
        Repository repository = new Jcr(new Oak(new MemoryNodeStore()))
                .with(SecurityProviderBuilder.newBuilder()
                        .with(
                                new AuthenticationConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new PrivilegeConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new UserConfigurationImpl(), params,
                                new AuthorizationConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new PrincipalConfigurationImpl(), ConfigurationParameters.EMPTY,
                                new TokenConfigurationImpl(), ConfigurationParameters.EMPTY
                        )
                        .build())
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

    private static class NamedByteArrayInputStream extends ByteArrayInputStream {

        public String getName() {
            return name;
        }

        private final String name;

        public NamedByteArrayInputStream(byte[] buf, String name) {
            super(buf);
            this.name = name;
        }
    }
    
    private void registerNodeTypes(Session session) throws Exception {
        Enumeration<URL> resources = getClass().getClassLoader().getResources("SLING-INF/nodetypes/");
        List<URL> cdnFiles = EnumerationUtils.toList(resources);
        LinkedList<NamedByteArrayInputStream> nodeTypeInputStreams = new LinkedList<>();

        for (URL url : cdnFiles) {
            String jarLocation = url.toURI().getSchemeSpecificPart();
            URI fileURI = new URI(StringUtils.substringBefore(jarLocation, "!/"));
            Path jarFilePath = Path.of(fileURI);
            try (InputStream inputStream = Files.newInputStream(jarFilePath);
                 JarInputStream jarInputStream = new JarInputStream(inputStream)) {
                JarEntry nextJarEntry;
                while ((nextJarEntry = jarInputStream.getNextJarEntry()) != null) {
                    String name = nextJarEntry.getName();
                    if (name.startsWith("SLING-INF/nodetypes") && name.endsWith(".cnd")) {
                        nodeTypeInputStreams.add(new NamedByteArrayInputStream(jarInputStream.readAllBytes(), name));
                    }
                    jarInputStream.closeEntry();
                }
            }
        }

        int maxSize = nodeTypeInputStreams.size() * 4;
        int counter = 0;
        
        Map<String,Throwable> exceptions = new HashMap<>();
        
        while(counter < maxSize && !nodeTypeInputStreams.isEmpty()){
            
            counter++;
            NamedByteArrayInputStream stream = nodeTypeInputStreams.pop();
            try{
                registerNodeTypes(session, stream);
                exceptions.remove(stream.name);
            }catch(Exception ex){
                nodeTypeInputStreams.addLast(stream);
                exceptions.put(stream.name, ex);
            }
        }
        
        if(!nodeTypeInputStreams.isEmpty()){
            IllegalStateException illegalStateException = new IllegalStateException("Exception installing nodetype definitions");
            exceptions.forEach( (key, value) -> illegalStateException.addSuppressed(new Exception("Exception installing nodetype definition file " + key, value)));
            throw illegalStateException;
        }
        
    }

    private void registerNodeTypes(Session session, InputStream nodetypesUrl) throws Exception {
        try(Reader reader = new InputStreamReader(nodetypesUrl, StandardCharsets.UTF_8)) {
            CndImporter.registerNodeTypes(reader, session, true);
        }
    }

}
