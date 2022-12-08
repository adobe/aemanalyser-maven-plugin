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
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.jar.Manifest.Attribute;

import com.adobe.aem.analyser.tasks.ConfigurationFile;
import com.adobe.aem.analyser.tasks.TaskResult;
import com.adobe.aem.analyser.tasks.ConfigurationFile.Location;
import com.adobe.aem.analyser.tasks.TaskResult.Annotation;
import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.SDKType;
import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.Application;
import com.adobe.aem.project.model.ArtifactsFile;
import com.adobe.aem.project.model.Module;
import com.adobe.aem.project.model.Project;
import com.adobe.aem.project.model.RepoinitFile;

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
        if ( !DependencyLifecycleParticipant.isExperimentalEnabled() ) {
            return;
        }
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

            final List<ConfigurationFile> configs = app.getConfigurationFiles();
            final List<RepoinitFile> repoinit = app.getRepoInitFiles();
            final List<ArtifactsFile> bundles = app.getBundleFiles();
            final List<ArtifactsFile> contentPackages = app.getContentPackageFiles();
            final TaskResult result = app.verify(configs, repoinit, bundles, contentPackages);
            this.processResult(result);

            this.processConfigurations(configs);
            this.processRepoinit(repoinit);

            final Set<ArtifactId> moduleIds = new HashSet<>();
            this.processArtifacts(moduleIds, bundles);
            this.processArtifacts(moduleIds, contentPackages);

            for(final Module m : pr.getModules()) {
                if ( m.getMvnId() != null) {
                    final ArtifactId id = ArtifactId.fromMvnId(m.getMvnId());
                    if ( moduleIds.add(id) ) {
                        this.processArtifact(new Artifact(id), m.getServiceType());    
                    }
                }
            }
        } catch ( final IOException ioe) {
            throw new MojoExecutionException(ioe.getMessage(), ioe);
        }


        this.createArchive();
        project.getArtifact().setFile(buildFile);
    }

    private void processResult(final TaskResult result) throws MojoExecutionException {
        for(final Annotation ann : result.getWarnings()) {
            if ( this.strictValidation ) {
                getLog().error(ann.toActionString("error"));
            } else {
                getLog().warn(ann.toActionString("warning"));
            }
        }
        for(final Annotation ann : result.getErrors()) {
            getLog().error(ann.toActionString("error"));
        }
        if ( result.hasErrors() || (this.strictValidation && result.hasWarnings()) ) {
            throw new MojoExecutionException("Configurations are not valid. Please check log");
        }
    }

    private void processRepoinit(final List<RepoinitFile> repoinit) throws IOException, MojoExecutionException {
        for(final RepoinitFile file : repoinit) {
            final Dictionary<String, Object> props = new Hashtable<>();
            props.put("scripts", file.getContents());
            final File outFile = new File(this.project.getBuild().getDirectory(), file.getPid().concat(this.project.getArtifactId()).concat(".cfg.json"));
            try ( final FileWriter writer = new FileWriter(outFile)) {
                org.apache.felix.cm.json.Configurations
                    .buildWriter().build(writer)
                    .writeConfiguration(props);
            }
            final ConfigurationFile cfgFile = new ConfigurationFile(Location.APPS, outFile);
            cfgFile.setServiceType(file.getServiceType());
            this.processConfiguration(cfgFile);
        }
    }

    private void processConfigurations(final List<ConfigurationFile> configs) throws MojoExecutionException {
        for(final ConfigurationFile file : configs) {
            this.processConfiguration(file);
        }
    }

    private void processConfiguration(final ConfigurationFile file) throws MojoExecutionException {
        final String runmode = getRunMode(file.getServiceType(), file.getEnvType(), file.getSdkType());
        final String path = this.getConfigurationPath(runmode, file.getPid());
        this.addFile(file.getSource(), path);
    }

    private String getRunMode(ServiceType serviceType, EnvironmentType envType, SDKType sdkType) {
        if ( serviceType != null ) {
            if ( envType != null ) {
                return serviceType.asString().concat(".").concat(envType.asString());
            } else if ( sdkType != null ) {
                return serviceType.asString().concat(".").concat(sdkType.asString());
            }
            return serviceType.asString();
        } else if ( envType != null ) {
            return envType.asString();
        } else if ( sdkType != null ) {
            return sdkType.asString();
        }
        return null;
    }

    private void processArtifacts(final Set<ArtifactId> moduleIds, final List<ArtifactsFile> files) throws MojoExecutionException {
        for(final ArtifactsFile f : files) {
            for(final Artifact a : f.getArtifacts()) {
                moduleIds.add(a.getId());
                processArtifact(a, f.getServiceType());
            }
        }
    }

    private void processArtifact(final Artifact a, ServiceType serviceType) throws MojoExecutionException {
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

    private String getConfigurationPath(final String runmode, final String pid) {
        String base = getRoot().concat("/config");
        if ( runmode != null ) {
            base = base.concat(".").concat(runmode);
        }
        return base.concat("/").concat(pid).concat(".cfg.json");
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
