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
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
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
import org.apache.sling.feature.maven.mojos.AbstractFeatureMojo;
import org.apache.sling.feature.maven.mojos.Aggregate;
import org.apache.sling.feature.maven.mojos.AggregateFeaturesMojo;
import org.apache.sling.feature.maven.mojos.AnalyseFeaturesMojo;
import org.apache.sling.feature.maven.mojos.Scan;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mojo(name = "analyse", defaultPhase = LifecyclePhase.VERIFY)
public class AnalyseMojo extends AbstractMojo {
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Parameter(defaultValue="${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    @Component
    ArtifactResolver artifactResolver;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repoSystem;

    public void execute() throws MojoExecutionException, MojoFailureException {
        // First convert content package to feature model
        convertContentPackageToFeatureModel();

        // Hack remove cp2fm-converted: from generated files. Find a better solution for this.
//        stripCp2fmConvertedFromFeatureFiles();

        // Then aggregate the features
        aggregateUserAndSDKFeatures();

        analyseFeature();
    }

    private void convertContentPackageToFeatureModel() throws MojoExecutionException, MojoFailureException {
        ConvertCPMojo mojo = new ConvertCPMojo();
        try {
            prepareMojo(mojo);

            setParameter(mojo, "repoSystem", repoSystem);
            setParameter(mojo, "repoSession", repoSession);

            setParameter(mojo, "artifactIdOverride",
                    project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
            setParameter(mojo, "isContentPackage", true);
            setParameter(mojo, "installConvertedCP", true);
            setParameter(mojo, "contentPackages", getContentPackages());

            setParameter(mojo, "convertedCPOutput", getCPConversionDir());
            setParameter(mojo, "fmOutput", getGeneratedFeaturesDir());

            mojo.execute();
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }

    private File getCPConversionDir() {
        return new File(project.getBuild().getDirectory() + "/cp-conversion");
    }

    private File getGeneratedFeaturesDir() {
        return new File(getCPConversionDir(), "fm.out");
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

    private void stripCp2fmConvertedFromFeatureFiles() throws MojoExecutionException {
        // TODO This is a hack, remove this

        try {
            File dir = getGeneratedFeaturesDir();
            for (File ff : dir.listFiles(f -> f.getName().endsWith(".json"))) {
                replaceInFile(ff);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot replace text in file", e);
        }
    }

    private void replaceInFile(File ff) throws IOException {
        List<String> newFile = new ArrayList<>();
        boolean changes = false;
        try (BufferedReader br = new BufferedReader(new FileReader(ff))) {
            String line;
            while((line = br.readLine()) != null) {
                String replaced = line.replace("cp2fm-converted:", "");
                newFile.add(replaced);
                if (!replaced.equals(line)) {
                    changes = true;
                }
            }
        }

        if (changes) {
            try (BufferedWriter wr = new BufferedWriter(new FileWriter(ff))) {
                for (String line : newFile) {
                    wr.write(line);
                    wr.newLine();
                }
            }
        }
    }

    private void aggregateUserAndSDKFeatures() throws MojoExecutionException {
        AggregateFeaturesMojo mojo = new AggregateFeaturesMojo();
        try {
            prepareSFMPMojo(mojo);
            setParameter(mojo, "aggregates", getAggregates());

            mojo.execute();
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }

    private List<Aggregate> getAggregates() {
        List<Aggregate> l = new ArrayList<>();

        Aggregate a = new Aggregate();
        a.classifier = "aggregated";
        Dependency d = new Dependency();
        d.setGroupId("com.day.cq"); // TODO
        d.setArtifactId("cq-quickstart"); // TODO
        d.setVersion("6.6.0-SNAPSHOT"); // TODO
        d.setType("slingosgifeature");
        d.setClassifier("aem-sdk");
        a.setIncludeArtifact(d);

        a.setFilesInclude("**/*.json");
        a.markAsComplete = true;
        a.artifactsOverrides = Collections.singletonList("*:*:LATEST");
        a.configurationOverrides = Collections.singletonList("*=MERGE_LATEST");
        l.add(a);

        return l;
    }

    private void analyseFeature() throws MojoExecutionException, MojoFailureException {
        AnalyseFeaturesMojo mojo = new AnalyseFeaturesMojo();
        try {
            prepareSFMPMojo(mojo);

            Dependency fwDep = new Dependency();
            fwDep.setGroupId("org.apache.felix");
            fwDep.setArtifactId("org.apache.felix.framework");
            fwDep.setVersion("6.0.1"); // TODO Where do we get this from ? Some property set in the parent pom?
            setParameter(mojo, "framework", fwDep);

            Scan s = new Scan();
            s.setIncludeClassifier("aggregated");
            s.setIncludeTask("requirements-capabilities"); // TODO maybe make this configurable
            setParameter(mojo, "scans", Collections.singletonList(s));

            mojo.execute();
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }

    private void prepareMojo(org.apache.maven.plugin.Mojo mojo) throws ReflectiveOperationException {
        setParameter(mojo, "project", project);
        setParameter(mojo, "artifactHandlerManager", artifactHandlerManager);
        setParameter(mojo, "projectHelper", projectHelper);
    }

    private void prepareSFMPMojo(AbstractFeatureMojo mojo)
            throws ReflectiveOperationException, NoSuchFieldException, IllegalAccessException {
        prepareMojo(mojo);

        setParameter(mojo, "artifactResolver", artifactResolver);
        setParameter(mojo, "features", new File("src/main/features"));
        setParameter(mojo, "mavenSession", mavenSession);
        setParameter(mojo, "generatedFeatures", getGeneratedFeaturesDir());
        setParameter(mojo, "generatedFeaturesIncludes", "**/*.json");
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
