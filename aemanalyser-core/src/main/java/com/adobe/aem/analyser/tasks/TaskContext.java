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
package com.adobe.aem.analyser.tasks;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureBuilder;
import org.apache.sling.feature.builder.FeatureProvider;

import com.adobe.aem.analyser.AemSdkProductFeatureGenerator;
import com.adobe.aem.analyser.ProductVariation;
import com.adobe.aem.project.ServiceType;

public class TaskContext {

    private final FeatureProvider provider;

    private final ArtifactId sdkId;

    private final List<ArtifactId> addons;

    private final File projectDirectory;

    private final ArtifactId projectId;
    private Map<ServiceType, Feature> productFeatures;

    /**
     * Create a new context
     * @param projectDirectory The project directory
     * @param projectId The project ID
     * @param sdkId The SDK ID
     * @param addons The addons
     * @param provider The feature provider
     * @throws NullPointerException If any of the arguments is {@code null}
     */
    public TaskContext(
              final File projectDirectory,
              final ArtifactId projectId,
              final ArtifactId sdkId, 
              final List<ArtifactId> addons, 
              final FeatureProvider provider) {
        Objects.requireNonNull(projectDirectory);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(sdkId);
        Objects.requireNonNull(addons);
        Objects.requireNonNull(provider);
        this.projectDirectory = projectDirectory;
        this.provider = provider;
        this.sdkId = sdkId;
        this.addons = addons;
        this.projectId = projectId;
    }

    /**
     * @return the projectId
     */
    public ArtifactId getProjectId() {
        return projectId;
    }

    /**
     * Check if the file is a project file
     * @param file The file
     * @throws IOException If the file is not a project file
     */
    public void checkProjectFile(final File file) throws IOException {
        if (!file.getAbsolutePath().startsWith(this.projectDirectory.getAbsolutePath().concat(File.separator))) {
            throw new IOException("File is outside of project directory " + file);
        }
    }

    /**
     * Get the relative path for a project file
     * @param file The file
     * @return The relative path
     * @throws IOException If the file is not inside the project directory
     */
    public String getRelativePath(final File file) throws IOException {
        checkProjectFile(file);
        return file.getAbsolutePath().substring(this.projectDirectory.getAbsolutePath().length() + 1);
    }

    /**
     * Get the aggregated author and publish feature
     * @return A map with the author and publish feature
     * @throws IOException If something goes wrong
     */
    public Map<ServiceType, Feature> getProductFeatures() 
    throws IOException {
        if ( this.productFeatures == null ) {
            this.productFeatures = this.loadProductFeatures();
        }
        return this.productFeatures;
    }

    private Map<ServiceType, Feature> loadProductFeatures() throws IOException {
        final Map<ServiceType, Feature> result = new HashMap<>();
        final AemSdkProductFeatureGenerator generator = new AemSdkProductFeatureGenerator(this.provider, sdkId, addons);

        final BuilderContext context = new BuilderContext(this.provider);
        for(final ServiceType serviceType : ServiceType.values()) {
            final Map<ProductVariation, List<Feature>> featuresMap = generator.getProductAggregates(EnumSet.of(serviceType));
            if ( featuresMap.size() != 1 ) {
                throw new IOException("Unable to resolve product feature for servive " + serviceType);
            }
            final List<Feature> features = featuresMap.values().iterator().next();
            final Feature finalFeature = FeatureBuilder.assemble(sdkId.changeClassifier(serviceType.name()),
                context, features.toArray(new Feature[features.size()]));
            result.put(serviceType, finalFeature);
        }
        return result;
    }
}
