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

import java.util.List;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.feature.ArtifactId;

import com.adobe.aem.analyser.impl.ProviderTypeAnalyserTask;
import com.adobe.aem.analyser.result.AemAnalyserAnnotation;
import com.adobe.aem.analyser.result.AemAnalyserResult;

/**
 * Abstract base class for all analyse mojos
 */
public abstract class AbstractAnalyseMojo extends AbstractAemMojo {

    /**
     * The artifact id of the sdk api jar. The artifact id is automatically detected by this plugin,
     * by using this configuration the auto detection can be disabled
     */
    @Parameter(property = "sdkArtifactId")
    String sdkArtifactId;
    
    /**
     * The version of the sdk api. Can be used to specify the exact version to be used. Otherwise the
     * plugin detects the version to use.
     */
    @Parameter(required = false, property = "sdkVersion")
    String sdkVersion;

    /**
     * Use dependency versions. If this is enabled, the version for the SDK and the Add-ons is taken
     * from the project dependencies. By default, the latest version is used.
     */
    @Parameter(required = false, defaultValue = "false", property = "sdkUseDependency")
    boolean useDependencyVersions;

    /**
     * The list of add ons.
     */
    @Parameter
    List<Addon> addons;

    /**
     * Skip the execution
     */
    @Parameter(defaultValue = "false", property = "aem.analyser.skip")
    boolean skip;

    /**
     * Fail on analyser errors?
     */
    @Parameter(defaultValue = "true", property = "failon.analyser.errors")
    private boolean failOnAnalyserErrors;

    /**
     * If enabled, warnings for maven plugin version or SDK API version are turned
     * into errors and fail the build.
     * @since 1.6.2
     */
    @Parameter(defaultValue = "false", property = "aem.analyser.strict.version")
    protected boolean strictVersionValidation;

    /**
     * The maven session
     */
    @Parameter(property = "session", readonly = true, required = true)
    protected MavenSession mavenSession;

    @Parameter( defaultValue = "${plugin}", readonly = true ) // Maven 3 only
    protected PluginDescriptor plugin;

    /**
     * Detect if the execution should be skipped
     * @return {@code true} if execution should be skipped
     */
    boolean skipRun() {
        // check env var
        final String skipVar = System.getenv(Constants.SKIP_ENV_VAR);
        boolean skipExecution = skipVar != null && skipVar.length() > 0;
        if ( skipExecution ) {
            getLog().info("Skipping AEM analyser plugin as variable " + Constants.SKIP_ENV_VAR + " is set.");
        } else if ( this.skip ) {
            skipExecution = true;
            getLog().info("Skipping AEM analyser plugin as configured.");
        }

        return skipExecution;
    }

    /**
     * Execute the plugin
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skipRun()) {
            return;
        }

        final VersionUtil versionUtil = new VersionUtil(this.getLog(), this.project, artifactHandlerManager,
                this.repoSystem, this.repoSession,
                this.mavenSession.isOffline());

        versionUtil.checkPluginVersion(this.plugin.getGroupId(), this.plugin.getArtifactId(), this.plugin.getVersion());

        final ArtifactId sdkId = versionUtil.getSDKArtifactId(this.sdkArtifactId, this.sdkVersion, this.useDependencyVersions);
        final List<ArtifactId> addons = versionUtil.discoverAddons(this.addons, this.useDependencyVersions);

        // initialize the provider types analyser
        if ( !ProviderTypeAnalyserTask.initializeProviderTypeInfo(sdkId, this.getOrResolveArtifact(sdkId).getFile()) ) {
            throw new MojoFailureException("Provider types not found in " + sdkId.toMvnId() + ". Please update to a more recent version of the API.");
        }

        final AemAnalyserResult result = this.doExecute(sdkId, addons);

        // Fail build with errors, or depending on configuration with warnings
        final boolean hasErrors = result.hasErrors()
            || (strictValidation && result.hasWarnings())
            || (strictVersionValidation && !versionUtil.getVersionWarnings().isEmpty());

        // add version util warnings
        for(final String warn : versionUtil.getVersionWarnings()) {
            result.getWarnings().add(new AemAnalyserAnnotation(warn));
        }

        this.printResult(result);

        if (hasErrors) {
            if ( failOnAnalyserErrors ) {
                throw new MojoFailureException(
                    "One or more feature analyser(s) detected feature error(s), please read the plugin log for more details");
            }
            getLog().warn("Errors found during analyser run, but this plugin is configured to ignore errors and continue the build!");
        }            
    }

    protected abstract AemAnalyserResult doExecute(final ArtifactId sdkId, 
        final List<ArtifactId> addons) 
        throws MojoExecutionException, MojoFailureException;
}
