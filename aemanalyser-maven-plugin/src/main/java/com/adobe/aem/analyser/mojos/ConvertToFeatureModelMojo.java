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
import java.util.List;

import static com.adobe.aem.analyser.mojos.MojoUtils.setParameter;

@Mojo(name = "convert", requiresProject = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ConvertToFeatureModelMojo extends ConvertCPMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        System.out.println("******************* execute: " + this);

        setParameter(this, "artifactIdOverride",
            project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
        setParameter(this, "isContentPackage", true);
        setParameter(this, "installConvertedCP", true);
        setParameter(this, "contentPackages", getContentPackages());

        setParameter(this, "convertedCPOutput", getCPConversionDir());
        setParameter(this, "fmOutput", getGeneratedFeaturesDir());

        super.execute();
    }

    private List<ContentPackage> getContentPackages() throws MojoExecutionException {
        List<ContentPackage> l = new ArrayList<>();

        for (Dependency d : project.getDependencies()) {
            if ("zip".equals(d.getType())) {
                // If a dependency is of type 'zip' it is assumed to be a content package. TODO find a better way...
                ContentPackage cp = new ContentPackage();
                cp.setGroupId(d.getGroupId());
                cp.setArtifactId(d.getArtifactId());
                // TODO set the version???
                l.add(cp);
            }
        }

        if (l.isEmpty())
            throw new MojoExecutionException("No content packages found for project.");

        return l;
    }

    private File getCPConversionDir() {
        return new File(project.getBuild().getDirectory() + "/cp-conversion");
    }

    private File getGeneratedFeaturesDir() {
        return new File(getCPConversionDir(), "fm.out");
    }
}
