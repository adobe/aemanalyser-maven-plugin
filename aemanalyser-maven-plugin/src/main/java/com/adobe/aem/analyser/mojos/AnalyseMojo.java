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

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.sling.cpconverter.maven.mojos.ContentPackage;
import org.apache.sling.cpconverter.maven.mojos.ConvertCPMojo;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.VERIFY)
public class AnalyseMojo extends AbstractMojo {
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue="${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repoSystem;

    public void execute() throws MojoExecutionException, MojoFailureException {
        // First convert content package to feature model
        convertContentPackageToFeatureModel();
    }

    private void convertContentPackageToFeatureModel() throws MojoExecutionException, MojoFailureException {
        ConvertCPMojo mojo = new ConvertCPMojo();
        try {
            prepareMojo(mojo);

            setParameter(mojo, "artifactIdOverride",
                    project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
            setParameter(mojo, "isContentPackage", true);
            setParameter(mojo, "installConvertedCP", false);

            ContentPackage cp = new ContentPackage();
            cp.setGroupId(project.getGroupId());
            cp.setArtifactId("aem-bosschae6-project.ui.apps"); // TODO
            setParameter(mojo, "contentPackages", Collections.singletonList(cp));

            File f = new File(project.getBuild().getDirectory() + "/cp-conversion");
            setParameter(mojo, "convertedCPOutput", f);
            setParameter(mojo, "fmOutput", new File(f, "fm.out"));

            mojo.execute();
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }

    private void prepareMojo(ConvertCPMojo mojo) throws ReflectiveOperationException {
        setParameter(mojo, "project", project);
        setParameter(mojo, "repoSystem", repoSystem);
        setParameter(mojo, "repoSession", repoSession);
        setParameter(mojo, "artifactHandlerManager", artifactHandlerManager);
        setParameter(mojo, "projectHelper", projectHelper);
    }

    private void setParameter(Object mojo, String field, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        setParameter(mojo, mojo.getClass(), field, value);
    }

    private void setParameter(Object mojo, Class<?> cls, String field, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            Field f = cls.getDeclaredField(field);

            f.setAccessible(true);
            f.set(mojo, value);
        } catch (NoSuchFieldException e) {
            Class<?> sc = cls.getSuperclass();
            if (!sc.equals(Object.class)) {
                // Try the superclass
                setParameter(mojo, sc, field, value);
            } else {
                throw e;
            }
        }
    }
}
