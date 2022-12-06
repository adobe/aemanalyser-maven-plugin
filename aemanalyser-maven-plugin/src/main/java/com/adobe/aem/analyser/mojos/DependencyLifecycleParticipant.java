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

import java.io.IOException;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import com.adobe.aem.project.ServiceType;
import com.adobe.aem.project.model.Application;
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

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        logger.debug("Searching for project using plugin '" + PLUGIN_ID + "'...");

        for (final MavenProject project : session.getProjects()) {
            final Plugin plugin = project.getPlugin(PLUGIN_ID);
            if (plugin != null) {
                logger.debug("Found project " + project.getId() + " using " + PLUGIN_ID);
                if ( "aemapp".equals(project.getPackaging()) ) {
                    logger.info("Found application project " + project.getId() + " using " + PLUGIN_ID);
                    final Project p = new Project(project.getBasedir().getParentFile());
                    p.scan();
                    final Application app = p.getApplication();

                    logger.info("Adding dependencies...");
                    try {
                        addArtifacts(project, app.getBundles(null));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    try {
                        addArtifacts(project, app.getBundles(ServiceType.AUTHOR));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    try {
                        addArtifacts(project, app.getBundles(ServiceType.PUBLISH));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    try {
                        addArtifacts(project, app.getContentPackages(null));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    try {
                        addArtifacts(project, app.getContentPackages(ServiceType.AUTHOR));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    try {
                        addArtifacts(project, app.getContentPackages(ServiceType.PUBLISH));
                    } catch ( final IOException ioe) {
                        // we ignore this
                    }
                    logger.debug("Done adding dependencies");
                }
            }
        }
    }

    private void addArtifacts(final MavenProject project, final Artifacts artifacts) {
        if ( artifacts != null ) {
            for(final Artifact a : artifacts) {
                this.addDependency(project, a.getId(), "provided");
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

    private void addDependency(final MavenProject project, final ArtifactId id, final String scope) {
        if ( id.getGroupId().equals(project.getGroupId())
             && id.getArtifactId().equals(project.getArtifactId())
             && id.getVersion().equals(project.getVersion()) ) {
            // skip artifact from the same project
            logger.info("- skipping dependency " + id.toMvnId());
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
				logger.info("- adding dependency " + id.toMvnId());
				final Dependency dep = this.toDependency(id, scope);

				// Exclude all transitive dependencies coming from the feature model deps
				final Exclusion exclusion = new Exclusion();
				exclusion.setGroupId("*");
				exclusion.setArtifactId("*");
				dep.addExclusion(exclusion);

				project.getDependencies().add(dep);
			} else {
                logger.info("- skipping duplicate dependency " + id.toMvnId());
            }
        }
    }
}
