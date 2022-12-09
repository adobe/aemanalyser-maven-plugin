/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.adobe.aem.project.model.Application;
import com.adobe.aem.project.model.ArtifactsFile;
import com.adobe.aem.project.model.Module;
import com.adobe.aem.project.model.ModuleType;
import com.adobe.aem.project.model.Project;

/**
 * Maven lifecycle participant which adds the artifacts of the model to the dependencies.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "aemappparticipant")
public class DependencyLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    /**
     * The plugin ID consists of <code>groupId:artifactId</code>, see {@link Plugin#constructKey(String, String)}
     */
    private static final String PLUGIN_ID = "com.adobe.aem:aemanalyser-maven-plugin";

    private static final String KEY_PROJECT = PLUGIN_ID.concat("/project");

    private static final String KEY_PROJECT_SERIALIZED = KEY_PROJECT.concat("-ser");

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        logger.debug("Searching for project using plugin '" + PLUGIN_ID + "'...");

        final List<MavenProject> apps = new ArrayList<>();
        for (final MavenProject project : session.getProjects()) {
            final Plugin plugin = project.getPlugin(PLUGIN_ID);
            if (plugin != null && Constants.PACKAGING_AEMAPP.equals(project.getPackaging()) ) {
                apps.add(project);
            }
        }
        for(final MavenProject project : apps) {
            processProject(project, session);
        }
    }

    private void processProject(final MavenProject mavenProject, final MavenSession session) {
        logger.debug("Found application project " + mavenProject.getId());
        final Project project = new Project(mavenProject.getBasedir().getParentFile());
        project.scan();
        final Application app = project.getApplication();
        // sanity check
        if ( app == null || !app.getDirectory().getAbsolutePath().equals(mavenProject.getBasedir().getAbsolutePath())) {
            logger.debug("Skipping project due to setup mismatch");
            return;
        }
        app.setId(new ArtifactId(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion(), null, null));
        for(final Module m : project.getModules()) {
            if ( m.getType() != ModuleType.BUNDLE && m.getType() != ModuleType.CONTENT ) {
                continue;
            }
            // check whether module is in the current build
            MavenProject found = null;
            for(final MavenProject p : session.getProjects()) {
                if ( p.getBasedir().getAbsolutePath().equals(m.getDirectory().getAbsolutePath()) ) {
                     found = p;
                     break;
                }
            }
            if ( found == null ) {
                final File pomFile = new File(m.getDirectory(), "pom.xml");
                if ( pomFile.exists() ) {
                    final MavenXpp3Reader reader = new MavenXpp3Reader();
                    try ( final Reader r = new FileReader(pomFile) ) {
                        final Model model = reader.read(r);
                        String groupId = model.getGroupId();
                        if ( groupId == null && model.getParent() != null ) {
                            groupId = model.getParent().getGroupId();
                        }
                        String version = model.getVersion();
                        if ( version == null && model.getParent() != null ) {
                            version = model.getParent().getVersion();
                        }
                        if ( groupId != null && model.getArtifactId() != null && version != null ) {
                            final ArtifactId id = new ArtifactId(groupId, model.getArtifactId(), version, null, 
                                m.getType() == ModuleType.BUNDLE ? null : "zip");
                            m.setId(id);
                            addDependency(mavenProject, id);    
                        } 
                    } catch ( IOException | XmlPullParserException ignore) {
                        // ignore this
                    }
                }
            } else {
                final ArtifactId id = new ArtifactId(found.getGroupId(), found.getArtifactId(), found.getVersion(), null, 
                    m.getType() == ModuleType.BUNDLE ? null : "zip");
                    m.setId(id);
                    addDependency(mavenProject, id);
            }
        }
        try ( final ByteArrayOutputStream baos = new ByteArrayOutputStream();
              final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(project);
            oos.flush();
            mavenProject.setContextValue(KEY_PROJECT_SERIALIZED, baos.toByteArray());
        } catch ( final IOException ignore) {
            // we ignore this
            ignore.printStackTrace();
        }
        logger.debug("Adding dependencies...");
        addArtifacts(mavenProject, app.getBundleFiles());
        addArtifacts(mavenProject, app.getContentPackageFiles());
        logger.debug("Done adding dependencies");
    }

    public static Project getProject(final MavenProject mavenProject) {
        Project project = (Project) mavenProject.getContextValue(KEY_PROJECT);
        if ( project == null ) {
            final byte[] data = (byte[]) mavenProject.getContextValue(KEY_PROJECT_SERIALIZED);
            if ( data != null ) {
                try ( final ByteArrayInputStream bais = new ByteArrayInputStream(data);
                      final ObjectInputStream ois = new ObjectInputStream(bais)) {
                    project = (Project) ois.readObject();
                    mavenProject.setContextValue(KEY_PROJECT, project);
                } catch ( final IOException | ClassNotFoundException ignore) {
                    // we ignore this
                    ignore.printStackTrace();
                }
            }
        }
        return project;
    }

    public static void setProject(final MavenProject mavenProject, final Project project) {
        mavenProject.setContextValue(KEY_PROJECT, project);
    }

    private void addArtifacts(final MavenProject project, final List<ArtifactsFile> artifactsFiles) {
        for(final ArtifactsFile file : artifactsFiles) {
            try {
                for(final Artifact a : file.readArtifacts()) {
                    this.addDependency(project, a.getId());
                }
            } catch ( final IOException io) {
                // ignore
            }
        }
    }

    private Dependency toDependency(final ArtifactId id, final String scope) {
        final Dependency dep = new Dependency();
        dep.setGroupId(id.getGroupId());
        dep.setArtifactId(id.getArtifactId());
        dep.setVersion(id.getVersion());
        dep.setType(id.getType());
        dep.setClassifier(id.getClassifier());
        dep.setScope(scope);

        return dep;
    }

    private void addDependency(final MavenProject project, final ArtifactId id) {
        if ( id.getGroupId().equals(project.getGroupId())
             && id.getArtifactId().equals(project.getArtifactId())
             && id.getVersion().equals(project.getVersion()) ) {
            // skip artifact from the same project
            logger.debug("- skipping dependency " + id.toMvnId());
        } else {

			boolean found = false;
			for(final Dependency d : project.getDependencies()) {
				if ( d.getGroupId().equals(id.getGroupId()) && d.getArtifactId().equals(id.getArtifactId())) {
					if ( d.getVersion().equals(id.getVersion()) && d.getType().equals(id.getType())) {
						if ( d.getClassifier() == null && id.getClassifier() == null ) {
							found = true;
							break;
						}
						if ( d.getClassifier() != null && d.getClassifier().equals(id.getClassifier())) {
							found = true;
							break;
						}
					}
				}
			}
			if ( !found ) {
				logger.debug("- adding dependency " + id.toMvnId());
				final Dependency dep = this.toDependency(id, org.apache.maven.artifact.Artifact.SCOPE_PROVIDED);

				// Exclude all transitive dependencies coming from the feature model deps
				final Exclusion exclusion = new Exclusion();
				exclusion.setGroupId("*");
				exclusion.setArtifactId("*");
				dep.addExclusion(exclusion);

				project.getDependencies().add(dep);
			} else {
                logger.debug("- skipping duplicate dependency " + id.toMvnId());
            }
        }
    }
}
