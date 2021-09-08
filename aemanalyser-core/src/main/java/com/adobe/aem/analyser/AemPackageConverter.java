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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemPackageConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String FILTER = ".*/(apps|libs)/(.*)/install\\.(((author|publish)\\.(dev|stage|prod))|((dev|stage|prod)\\.(author|publish))|(dev|stage|prod))/(.*)(?<=\\.(zip|jar)$)";

    private File featureOutputDirectory;

    private File bundlesOutputDirectory;

    private File converterOutputDirectory;

    private String artifactIdOverride;

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

    public void convert(final Map<String, File> contentPackages) throws IOException, ConverterException {
        final Map<String, String> properties = new HashMap<>();

        final AclManager aclManager = new DefaultAclManager(null, "system");
        final DefaultFeaturesManager featuresManager = new DefaultFeaturesManager(
            false,
            20,
            featureOutputDirectory,
            artifactIdOverride,
            null,
            properties,
            aclManager
        );

        featuresManager.setExportToAPIRegion("global");

        File bundlesOutputDir = this.bundlesOutputDirectory != null
                ? this.bundlesOutputDirectory : this.converterOutputDirectory;
        ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter(false,
                SlingInitialContentPolicy.KEEP)
                .setFeaturesManager(featuresManager)
                .setBundlesDeployer(
                        new LocalMavenRepositoryArtifactsDeployer(
                            bundlesOutputDir
                        )
                    )
                    .setEntryHandlersManager(
                        new DefaultEntryHandlersManager(Collections.emptyMap(), true,
                                SlingInitialContentPolicy.KEEP, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT)
                        )
                        .setAclManager(
                            new DefaultAclManager()
                            )
                            .setEmitter(DefaultPackagesEventsEmitter.open(this.featureOutputDirectory))
                            .setResourceFilter(getResourceFilter());

        try {
            for(Map.Entry<String, File> entry : contentPackages.entrySet()) {
                logger.info("Converting package {}", entry.getKey());
                try {
                    converter.convert(entry.getValue());
                } catch (final Throwable t) {
                    throw new IOException("Content Package Converter Exception " + t.getMessage(), t);
                }
            }
        } finally {
            // make sure to remove the temp folders
            converter.cleanup();
        }
    }

    private ResourceFilter getResourceFilter() {
        RegexBasedResourceFilter filter = new RegexBasedResourceFilter();
        filter.addFilteringPattern(FILTER);

        return filter;
    }
}
