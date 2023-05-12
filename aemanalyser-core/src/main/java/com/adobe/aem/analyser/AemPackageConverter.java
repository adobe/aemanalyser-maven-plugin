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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactWriter;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.index.DefaultIndexManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemPackageConverter {

    private static final Map<String, String> DEFAULT_NAMESPACE_MAPPINGS = Map.of(
        "cq", "http://www.day.com/jcr/cq/1.0",
        "granite", "http://www.adobe.com/jcr/granite/1.0"
    );

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String FILTER = ".*/(apps|libs)/(.*)/install\\.(((author|publish)\\.(dev|stage|prod))|((dev|stage|prod)\\.(author|publish))|(dev|stage|prod))/(.*)(?<=\\.(zip|jar)$)";

    private File featureOutputDirectory;

    private File bundlesOutputDirectory;

    private File mutableContentOutputDirectory;

    private File converterOutputDirectory;

    private String artifactIdOverride;

    private final List<String> apiRegions = Arrays.asList("com.adobe.aem.deprecated");
    /**
     * @return the featureOutputDirectory
     */
    public File getFeatureOutputDirectory() {
        return featureOutputDirectory;
    }

    /**
     * @param featureOutputDirectory the featureOutputDirectory to set
     */
    public void setFeatureOutputDirectory(File featureOutputDirectory) {
        this.featureOutputDirectory = featureOutputDirectory;
    }

    public void setMutableContentOutputDirectory(File mutableContentOutputDirectory) {
        this.mutableContentOutputDirectory = mutableContentOutputDirectory;
    }
    
    public void addToApiRegions(Collection<String> apiRegions){
        this.apiRegions.addAll(apiRegions);
    }

    /**
     * @return the bundlesOutputDirectory
     */
    public File getBundlesOutputDirectory() {
        return bundlesOutputDirectory;
    }

    /**
     * @param bundlesOutputDirectory the featureOutputDirectory to set
     */
    public void setBundlesOutputDirectory(File bundlesOutputDirectory) {
        this.bundlesOutputDirectory = bundlesOutputDirectory;
    }

    /**
     * @return the converterOutputDirectory
     */
    public File getConverterOutputDirectory() {
        return converterOutputDirectory;
    }

    /**
     * @param converterOutputDirectory the converterOutputDirectory to set
     */
    public void setConverterOutputDirectory(File converterOutputDirectory) {
        this.converterOutputDirectory = converterOutputDirectory;
    }

    /**
     * @return the artifactIdOverride
     */
    public String getArtifactIdOverride() {
        return artifactIdOverride;
    }

    /**
     * @param artifactIdOverride the artifactIdOverride to set
     */
    public void setArtifactIdOverride(String artifactIdOverride) {
        this.artifactIdOverride = artifactIdOverride;
    }

    /**
     * Convert the packages
     *
     * @param contentPackages The map of packages
     * @throws IOException When a problem happens with IO
     * @throws ConverterException When a problem happens during the CP Converter execution
     */
    public void convert(final Map<String, File> contentPackages) throws IOException, ConverterException {
        final Map<String, String> properties = new HashMap<>();

        final AclManager aclManager = new DefaultAclManager(null, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT);
        final DefaultFeaturesManager featuresManager = new DefaultFeaturesManager(
            false,
            20,
            featureOutputDirectory,
            artifactIdOverride,
            null,
            properties,
            aclManager
        );

        featuresManager.setAPIRegions(apiRegions);
        featuresManager.setExportToAPIRegion("global");
        // populate with namespace mapping defaults, they can still be overridden from the bundle metadata
        featuresManager.getNamespaceUriByPrefix().putAll(DEFAULT_NAMESPACE_MAPPINGS);

        final File bundlesOutputDir = this.bundlesOutputDirectory != null
                ? this.bundlesOutputDirectory : this.converterOutputDirectory;

        File unreferencedArtifactsOutputDirectory = mutableContentOutputDirectory != null? mutableContentOutputDirectory : new File(converterOutputDirectory, "mutable-content");
        MutableContentPackageDeployer mutableContentPackagesDeployer = new MutableContentPackageDeployer(unreferencedArtifactsOutputDirectory);
                
        try (final ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter(false,
                SlingInitialContentPolicy.EXTRACT_AND_REMOVE, true) ) {
            final BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor = new BundleSlingInitialContentExtractor();
            converter.setFeaturesManager(featuresManager)
                    .setBundlesDeployer(
                        new LocalMavenRepositoryArtifactsDeployer(
                            bundlesOutputDir
                        )
                    )
                    .setBundleSlingInitialContentExtractor(bundleSlingInitialContentExtractor)
                    .setEntryHandlersManager(
                        new DefaultEntryHandlersManager(Collections.emptyMap(), true,
                                SlingInitialContentPolicy.EXTRACT_AND_REMOVE, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT)
                        )
                    .setAclManager(
                            new DefaultAclManager()
                            )
                    .setIndexManager(
                            new DefaultIndexManager()
                            )
                    .setEmitter(DefaultPackagesEventsEmitter.open(this.featureOutputDirectory))
                    .setContentTypePackagePolicy(ContentPackage2FeatureModelConverter.PackagePolicy.PUT_IN_DEDICATED_FOLDER)
                    .setUnreferencedArtifactsDeployer(mutableContentPackagesDeployer)
                    .setIndexManager(new DefaultIndexManager())
                    .setResourceFilter(getResourceFilter());
            logger.info("Converting packages {}", contentPackages.keySet());
            converter.convert(contentPackages.values().toArray(new File[contentPackages.size()]));
        } catch ( final IOException | ConverterException e) {
            throw e;
        } catch (final Throwable t) {
            throw new IOException("Content Package Converter exception " + t.getMessage(), t);
        }
        mutableContentPackagesDeployer.logMutableContentPackages();
    }

    private ResourceFilter getResourceFilter() {
        RegexBasedResourceFilter filter = new RegexBasedResourceFilter();
        filter.addFilteringPattern(FILTER);

        return filter;
    }


    class MutableContentPackageDeployer extends LocalMavenRepositoryArtifactsDeployer {

        final Map<ArtifactId, String> mutableContentPackagesWithRunMode = new HashMap<>();
        
        public MutableContentPackageDeployer(File outputDirectory) {
            super(outputDirectory);
        }

        @Override
        public String deploy(ArtifactWriter artifactWriter, String runmode,
                             ArtifactId id) throws IOException {
            if (runmode != null) {
                mutableContentPackagesWithRunMode.put(id, runmode);
            }
            return super.deploy(artifactWriter, runmode, id);
        }
        
        public void logMutableContentPackages() {
            for(final Map.Entry<ArtifactId, String> entry : mutableContentPackagesWithRunMode.entrySet()) {
                logger.info("Mutable content package {} uses runmode {}", entry.getKey().toMvnId(), entry.getValue());
            }
        }
        
        
    }
}
