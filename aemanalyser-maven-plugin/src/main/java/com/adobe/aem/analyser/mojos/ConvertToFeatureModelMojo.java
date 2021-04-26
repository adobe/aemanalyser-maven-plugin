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
package com.adobe.aem.analyser.mojos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.adobe.aem.analyser.AemPackageConverter;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;

@Mojo(name = "convert", 
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class ConvertToFeatureModelMojo extends AbstractMojo {
    static final String AEM_ANALYSE_PACKAGING = "aem-analyse";

    @Parameter(defaultValue = MojoUtils.DEFAULT_SKIP_ENV_VAR, property = MojoUtils.PROPERTY_SKIP_VAR)
    String skipEnvVarName;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (MojoUtils.skipRun(skipEnvVarName)) {
            getLog().info("Skipping AEM analyser plugin as variable " + skipEnvVarName + " is set.");
            return;
        }

        final AemPackageConverter converter = new AemPackageConverter();
        converter.setArtifactIdOverride(new ArtifactId(project.getGroupId(), project.getArtifactId(), project.getVersion(), null, "slingosgifeature").toMvnId());
        converter.setConverterOutputDirectory(MojoUtils.getConversionOutputDir(project));
        converter.setFeatureOutputDirectory(MojoUtils.getGeneratedFeaturesDir(project));
    
        for(Artifact contentPackage: getContentPackages()) {
            final File source = contentPackage.getFile();
            try {
                converter.convert(Collections.singletonMap(contentPackage.toString(), source));
            } catch (IOException t) {
                throw new MojoExecutionException("Content Package Converter Exception " + t.getMessage(), t);        
            }
        }
    }

    private List<Artifact> getContentPackages() throws MojoExecutionException {
        final List<Artifact> result = new ArrayList<>();
        
        if (!AEM_ANALYSE_PACKAGING.equals(project.getPackaging())) {
            // Use the current project artifact as the content package
            getLog().info("Using current project as content package: " + project.getArtifact());
            result.add(project.getArtifact());
        } else {
            for (final Artifact d : project.getDependencyArtifacts()) {
                if ("zip".equals(d.getType()) || "content-package".equals(d.getType())) {
                    // If a dependency is of type 'zip' it is assumed to be a content package
                    result.add(d);
                }
            }    
            getLog().info("Found content packages from dependencies: " + result);
        }

        if (result.isEmpty()) {
            throw new MojoExecutionException("No content packages found for project.");
        }
        return result;
    }
}
