package com.adobe.aem.analyser;

import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.apache.sling.repoinit.parser.impl.ParseException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.operations.PathSegmentDefinition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AemPackageConverterTest {

    public @Rule TemporaryFolder tempDir = new TemporaryFolder();

    private AemPackageConverter systemUnderTest = new AemPackageConverter();

    private PackageManager packageManager = new PackageManagerImpl();
    private Map<String, File> packages = new LinkedHashMap<>(1);

    File outDir;
    File featureDir;


    @Before
    public void setUp() throws IOException {

        outDir = tempDir.newFolder("out");
        featureDir = tempDir.newFolder("features");

        systemUnderTest.setFeatureOutputDirectory(featureDir);
        systemUnderTest.setConverterOutputDirectory(outDir);
        systemUnderTest.setBundlesOutputDirectory(outDir);


    }

    @Test
    public void testSlingInitialContent() throws ConverterException, IOException, ParseException {
        URL url = AemPackageConverterTest.class.getClassLoader().getResource("packages/aem-guides-wknd-wcmio.all.zip");
        packages.put("wcmio-all", new File(url.getFile()));

        systemUnderTest.convert(packages);

        File featureFile = new File(featureDir, "aem-guides-wknd-wcmio.all.json");

        try(FileReader reader=  new FileReader(featureFile)){
            Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());
            assertNotNull(feature);

            Extension repoinit = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);

            assertNotNull(repoinit);

            RepoInitParserImpl parser = new RepoInitParserImpl(
                  new StringReader(  repoinit.getText())
            );

            List<Operation> operations = parser.parse();

            List<CreatePath> createPaths = operations.stream()
                    .filter(op -> op instanceof CreatePath)
                    .map(op -> (CreatePath) op)
                    .collect(Collectors.toList());

            assertEquals(45, createPaths.size());

            Optional<CreatePath> cp  =createPaths.stream().filter((CreatePath createPath) -> {
                List<PathSegmentDefinition> paths = createPath.getDefinitions();

                if(paths.size() == 7){

                    return  paths.get(0).getSegment().equals("apps") &&
                            paths.get(0).getPrimaryType().equals("sling:Folder") &&
                            paths.get(1).getSegment().equals("wcm-io") &&
                            paths.get(1).getPrimaryType().equals("sling:Folder") &&
                            paths.get(2).getSegment().equals("handler") &&
                            paths.get(2).getPrimaryType().equals("sling:Folder") &&
                            paths.get(3).getSegment().equals("link") &&
                            paths.get(3).getPrimaryType().equals("sling:Folder") &&
                            paths.get(4).getSegment().equals("components") &&
                            paths.get(4).getPrimaryType().equals("sling:Folder") &&
                            paths.get(5).getSegment().equals("global") &&
                            paths.get(5).getPrimaryType().equals("sling:Folder") &&
                            paths.get(6).getSegment().equals("include") &&
                            paths.get(6).getPrimaryType().equals("sling:Folder");
                }

                return false;

            }).findAny();

            assertTrue("does not have desired create path statement of /apps/wcm-io/handler/link/components/global/include", cp.isPresent());

            assertExtractedInitialContentFromBundle("io.wcm.handler.link", "2.1.0");
            assertExtractedInitialContentFromBundle("io.wcm.handler.media", "2.0.4");
            assertExtractedInitialContentFromBundle("io.wcm.handler.url", "2.1.0");
            assertExtractedInitialContentFromBundle("io.wcm.wcm.commons", "1.10.0");
            assertExtractedInitialContentFromBundle("io.wcm.wcm.ui.granite", "1.10.4");
        }


    }

    private void assertExtractedInitialContentFromBundle(String module, String version) throws IOException {
        File appsPackageFile = new File(outDir, String.format("io/wcm/%1$s-apps/%2$s/%1$s-apps-%2$s-cp2fm-converted.zip", module, version));
        VaultPackage appsVltPackage = packageManager.open(appsPackageFile);
        appsVltPackage.getPackageType().equals(PackageType.APPLICATION);
        assertNotNull(appsVltPackage);

        try(JarFile jarFile = new JarFile(new File(outDir,  String.format("io/wcm/%1$s/%2$s-cp2fm-converted/%1$s-%2$s-cp2fm-converted.jar", module, version)))){
            assertNotNull(jarFile);
            assertNotNull(jarFile.getManifest());
        }
    }


}
