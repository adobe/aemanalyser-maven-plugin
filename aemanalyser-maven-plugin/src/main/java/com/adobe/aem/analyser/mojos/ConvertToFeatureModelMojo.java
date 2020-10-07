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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.sling.cpconverter.maven.mojos.ContentPackage;
import org.apache.sling.cpconverter.maven.mojos.ConvertCPMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "convert", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ConvertToFeatureModelMojo extends ConvertCPMojo {
    private static final String AEM_ANALYSE_PACKAGING = "aem-analyse";

    boolean unitTestMode = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        setParameter(this, "artifactIdOverride",
            project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
        setParameter(this, "isContentPackage", false);
        setParameter(this, "installConvertedCP", true);
        setParameter(this, "contentPackages", getContentPackages());

        setParameter(this, "convertedCPOutput", getCPConversionDir());
        setParameter(this, "fmOutput", getGeneratedFeaturesDir());

        if (unitTestMode)
            return;

        super.execute();
    }

    private List<ContentPackage> getContentPackages() throws MojoExecutionException {
        if (!AEM_ANALYSE_PACKAGING.equals(project.getPackaging())) {
            // Take the current project artifact as the content package
            ContentPackage cp = new ContentPackage();
            cp.setGroupId(project.getGroupId());
            cp.setArtifactId(project.getArtifactId());

            getLog().info("Taking current project as content package: " + cp);

            return Collections.singletonList(cp);
        }

        List<ContentPackage> l = new ArrayList<>();

        for (Dependency d : project.getDependencies()) {
            if ("zip".equals(d.getType())) {
                // If a dependency is of type 'zip' it is assumed to be a content package. TODO find a better way...
                ContentPackage cp = new ContentPackage();
                cp.setGroupId(d.getGroupId());
                cp.setArtifactId(d.getArtifactId());
                l.add(cp);
            }
        }

        if (l.isEmpty())
            throw new MojoExecutionException("No content packages found for project.");

        getLog().info("Found content packages from dependencies: " + l);
        return l;
    }

    private File getCPConversionDir() {
        return new File(project.getBuild().getDirectory() + "/cp-conversion");
    }

    private File getGeneratedFeaturesDir() {
        return new File(getCPConversionDir(), "fm.out");
    }
}
