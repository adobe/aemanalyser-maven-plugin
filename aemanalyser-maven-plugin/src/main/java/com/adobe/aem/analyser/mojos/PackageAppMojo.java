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
package com.adobe.aem.analyser.mojos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.jar.Manifest.Attribute;

import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.SDKType;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.Application;
import com.adobe.aem.project.model.Module;
import com.adobe.aem.project.model.Project;

@Mojo(name = "package-app", 
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class PackageAppMojo extends AbstractAemMojo {

    /**
     * The Jar archiver.
     */
    @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("*********************************************************************************************");
        getLog().warn("THIS MOJO IS IN ALPHA STATE. USE WITH CAUTION AT YOUR OWN RISK");
        getLog().warn("THE FUNCTIONALITY MIGHT CHANGE OR BREAK WITHOUT PRIOR NOTICE");
        getLog().warn("*********************************************************************************************");
        new File(this.project.getBuild().getDirectory()).mkdirs();

        Project pr = DependencyLifecycleParticipant.getProject(this.project);
        if ( pr == null ) {
            pr = new Project(this.project.getBasedir().getParentFile());
            pr.scan();
        }
        final Application app;
        if ( pr.getApplication() == null || !pr.getApplication().getDirectory().getAbsolutePath().equals(this.project.getBasedir().getAbsolutePath())) {
            app = new Application(this.project.getBasedir());
        } else {
            app = pr.getApplication();
        }

        final File buildDirectory = new File(this.project.getBuild().getDirectory());
        final File buildFile = new File(buildDirectory, this.project.getBuild().getFinalName().concat(".zip"));

        final File filterFile = new File(buildDirectory, "filter.xml");
        final File defFile = new File(buildDirectory, ".content.xml");
        final File propsFile = new File(buildDirectory, "properties.xml");
        try {
            Files.writeString(filterFile.toPath(), this.getFilterContents());
            Files.writeString(defFile.toPath(), this.getDefinitionContents());
            try (final FileOutputStream w = new FileOutputStream(propsFile)) {
                this.getPackageProperties().storeToXML(w, null);
            }

            this.jarArchiver.setDestFile(buildFile);
            this.createManifest();
    
            this.addFile(filterFile, "META-INF/vault/" + filterFile.getName());
            this.addFile(defFile, "META-INF/vault/definition/" + defFile.getName());
            this.addFile(propsFile, "META-INF/vault/" + propsFile.getName());
    
            final Map<ArtifactId, ServiceType> moduleIds = new HashMap<>();
            for(final Module m : pr.getModules()) {
                if ( m.getMvnId() != null) {
                    final ArtifactId id = ArtifactId.fromMvnId(m.getMvnId());
                    final Artifact a = new Artifact(id);
                    this.processArtifact(moduleIds, a, m.getServiceType());
                }
            }
            this.processArtifacts(moduleIds, app.getBundles(null), null);
            this.processArtifacts(moduleIds, app.getBundles(ServiceType.AUTHOR), ServiceType.AUTHOR);
            this.processArtifacts(moduleIds, app.getBundles(ServiceType.PUBLISH), ServiceType.PUBLISH);

            this.processArtifacts(moduleIds, app.getContentPackages(null), null);
            this.processArtifacts(moduleIds, app.getContentPackages(ServiceType.AUTHOR), ServiceType.AUTHOR);
            this.processArtifacts(moduleIds, app.getContentPackages(ServiceType.PUBLISH), ServiceType.PUBLISH);

            this.processConfigurations(app.getConfigurations(null, null, null), null);
            this.processConfigurations(app.getConfigurations(null, SDKType.RDE, null), SDKType.RDE.asString());
            this.processConfigurations(app.getConfigurations(null, null, EnvironmentType.DEV), EnvironmentType.DEV.asString());
            this.processConfigurations(app.getConfigurations(null, null, EnvironmentType.STAGE), EnvironmentType.STAGE.asString());
            this.processConfigurations(app.getConfigurations(null, null, EnvironmentType.PROD), EnvironmentType.PROD.asString());

            this.processConfigurations(app.getConfigurations(ServiceType.AUTHOR, null, null), ServiceType.AUTHOR.asString());
            this.processConfigurations(app.getConfigurations(ServiceType.AUTHOR, null, EnvironmentType.DEV), ServiceType.AUTHOR.asString().concat(".").concat(EnvironmentType.DEV.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.AUTHOR, null, EnvironmentType.STAGE), ServiceType.AUTHOR.asString().concat(".").concat(EnvironmentType.STAGE.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.AUTHOR, null, EnvironmentType.PROD), ServiceType.AUTHOR.asString().concat(".").concat(EnvironmentType.PROD.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.AUTHOR, SDKType.RDE, null), ServiceType.AUTHOR.asString().concat(".").concat(SDKType.RDE.asString()));

            this.processConfigurations(app.getConfigurations(ServiceType.PUBLISH, null, null), ServiceType.PUBLISH.asString());
            this.processConfigurations(app.getConfigurations(ServiceType.PUBLISH, null, EnvironmentType.DEV), ServiceType.PUBLISH.asString().concat(".").concat(EnvironmentType.DEV.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.PUBLISH, null, EnvironmentType.STAGE), ServiceType.PUBLISH.asString().concat(".").concat(EnvironmentType.STAGE.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.PUBLISH, null, EnvironmentType.PROD), ServiceType.PUBLISH.asString().concat(".").concat(EnvironmentType.PROD.asString()));
            this.processConfigurations(app.getConfigurations(ServiceType.PUBLISH, SDKType.RDE, null), ServiceType.PUBLISH.asString().concat(".").concat(SDKType.RDE.asString()));

            this.processRepoinit(app.getRepoInit(null), null);
            this.processRepoinit(app.getRepoInit(ServiceType.AUTHOR), ServiceType.AUTHOR.asString());
            this.processRepoinit(app.getRepoInit(ServiceType.PUBLISH), ServiceType.PUBLISH.asString());
        } catch ( final IOException ioe) {
            throw new MojoExecutionException(ioe.getMessage(), ioe);
        }


        this.createArchive();
        project.getArtifact().setFile(buildFile);
    }

    private void processRepoinit(final String repoinit, final String runmode) throws IOException, MojoExecutionException {
        if ( repoinit != null ) {
            final Configurations cfgs = new Configurations();
            final Configuration c = new Configuration(Application.REPOINIT_FACTORY_PID.concat("~").concat(runmode == null ? "global" : runmode));
            c.getProperties().put("scripts", repoinit);
            cfgs.add(c);
            this.processConfigurations(cfgs, runmode);
        }
    }

    private void processConfigurations(final Configurations configs, final String runmode) throws MojoExecutionException, IOException {
        if ( configs != null ) {
            for(final Configuration c : configs) {
                final String path = this.getConfigurationPath(runmode, c);
                final String source = (String)c.getProperties().get(Application.CFG_SOURCE);
                final File file;
                if ( source != null ) {
                    file = new File(source);
                } else {
                    file = new File(this.project.getBuild().getDirectory(), c.getPid().concat(".cfg.json"));
                    try ( final FileWriter writer = new FileWriter(file)) {
                        org.apache.felix.cm.json.Configurations
                            .buildWriter().build(writer)
                            .writeConfiguration(c.getProperties());
                    }
                }
                this.addFile(file, path);
            }
        }
    }

    private void processArtifacts(final Map<ArtifactId, ServiceType> moduleIds, final Artifacts artifacts, final ServiceType serviceType) throws MojoExecutionException {
        if ( artifacts != null ) {
            for(final Artifact a : artifacts) {
                processArtifact(moduleIds, a, serviceType);
            }
        }
    }

    private void processArtifact(final Map<ArtifactId, ServiceType> moduleIds, final Artifact a, ServiceType serviceType) throws MojoExecutionException {
        if ( moduleIds.containsKey(a.getId()) ) {
            if ( moduleIds.get(a.getId()) == serviceType ) {
                return;
            }
            if ( moduleIds.get(a.getId()) == null ) {
                return;
            }
            if ( serviceType != null ) {
                serviceType = null;
            }
        }
        moduleIds.put(a.getId(), serviceType);
        final String path = this.getArtifactPath(serviceType == null ? null : serviceType.asString(), a);
        final File file = this.getOrResolveArtifact(a.getId()).getFile();
        this.addFile(file, path);
    }

    private String getPackageId() {
        return new ArtifactId(this.project.getGroupId(), this.project.getArtifactId(), this.project.getVersion(), null, null).toMvnId();
    }

    private String getRoot() {
        return "/jcr_root/apps/feature-".concat(this.project.getArtifactId()).concat("-").concat(this.project.getVersion()).concat("/application");
    }

    private String getArtifactPath(final String runmode, final Artifact a) {
        String base = getRoot().concat("/install");
        if ( runmode != null ) {
            base = base.concat(".").concat(runmode);
        }
        if ( a.getStartOrder() > 0 && a.getStartOrder() != 20 ) {
            base = base.concat("/").concat(String.valueOf(a.getStartOrder()));
        }
        return base.concat("/").concat(a.getId().toMvnName());
    }

    private String getConfigurationPath(final String runmode, final Configuration c) {
        String base = getRoot().concat("/config");
        if ( runmode != null ) {
            base = base.concat(".").concat(runmode);
        }
        return base.concat("/").concat(c.getPid()).concat(".cfg.json");
    }

    private Properties getPackageProperties() {
        final Properties p = new Properties();
        p.put("allowIndexDefinitions", "false");
        p.put("created", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(System.currentTimeMillis()));
        p.put("groupId", this.project.getGroupId());
        p.put("name", this.project.getArtifactId());
        if ( this.project.getDescription() != null ) {
            p.put("description", this.project.getDescription());
        }
        p.put("artifactId", this.project.getArtifactId());
        p.put("version", this.project.getVersion());
        p.put("packageType", "container");
        p.put("requiresRoot", "false");
        p.put("group", this.project.getGroupId());
        return p;
    }
    private String getFilterContents() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<workspaceFilter version=\"1.0\">\n" +
            "  <filter root=\"" + this.getRoot() + "\"/>\n" +
            "</workspaceFilter>\n";
    }

    private String getValue(final String val) {
        if ( val != null ) {
            return val;
        }
        return "";
    }

    private String getDefinitionContents() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n" +
            "  jcr:primaryType=\"vlt:PackageDefinition\"\n" +
            "  providerLink=\"" + getValue(this.project.getOrganization() != null ? this.project.getOrganization().getUrl() : null) + "\"\n" +
            "  providerName=\"" + getValue(this.project.getOrganization() != null ? this.project.getOrganization().getName() : null) + "\"\n" +
            "  providerUrl=\"" + getValue(this.project.getOrganization() != null ? this.project.getOrganization().getUrl() : null) + "\"\n" +
            "  testedWith=\"AEM Cloud Service\">\n" +
            "</jcr:root>\n";
    }
    /**
     * Create a manifest
     */
    private void createManifest() throws MojoExecutionException {
        // create a new manifest
        final Manifest outManifest = new Manifest();

        try {
            outManifest.addConfiguredAttribute(new Attribute("Created-By", "AEM Project Plugin"));
            outManifest.addConfiguredAttribute(new Attribute("Implementation-Title", this.project.getName()));
            outManifest.addConfiguredAttribute(new Attribute("Implementation-Version", this.project.getVersion()));
            outManifest.addConfiguredAttribute(new Attribute("Content-Package-Type", "container"));
            outManifest.addConfiguredAttribute(new Attribute("Content-Package-Id", this.getPackageId()));
            outManifest.addConfiguredAttribute(new Attribute("Content-Package-Roots", this.getRoot()));
            if ( this.project.getDescription() != null ) {
                outManifest.addConfiguredAttribute(new Attribute("Content-Package-Description", this.project.getDescription()));
            }

            this.jarArchiver.addConfiguredManifest(outManifest);
        } catch (final ManifestException e) {
            throw new MojoExecutionException("Unable to create manifest for " + this.jarArchiver.getDestFile(), e);
        }
    }

    public void addFile(File inputFile, String destFileName) throws MojoExecutionException {
        try {
            jarArchiver.addFile(inputFile, destFileName);
        } catch (final ArchiverException ae) {
            throw new MojoExecutionException("Unable to create archive for " + this.jarArchiver.getDestFile(), ae);
        }
    }

    public void createArchive() throws MojoExecutionException {
        try {
            jarArchiver.createArchive();
        } catch (final IOException | ArchiverException ae) {
            throw new MojoExecutionException("Unable to create archive for " + this.jarArchiver.getDestFile(), ae);
        }
    }
}
