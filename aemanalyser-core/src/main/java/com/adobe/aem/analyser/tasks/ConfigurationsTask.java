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
package com.adobe.aem.analyser.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.extension.apiregions.api.config.ConfigurableEntity;
import org.apache.sling.feature.extension.apiregions.api.config.ConfigurationApi;
import org.apache.sling.feature.extension.apiregions.api.config.Region;
import org.apache.sling.feature.extension.apiregions.api.config.validation.ConfigurationValidationResult;
import org.apache.sling.feature.extension.apiregions.api.config.validation.FeatureValidationResult;
import org.apache.sling.feature.extension.apiregions.api.config.validation.FeatureValidator;
import org.apache.sling.feature.extension.apiregions.api.config.validation.PropertyValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.analyser.tasks.ConfigurationFile.Location;
import com.adobe.aem.project.RunModes;
import com.adobe.aem.project.ServiceType;

public class ConfigurationsTask {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ConfigurationsTaskConfig taskConfig;

    private final TaskContext context;

    /**
     * Create new task
     * @param context The task context
     * @param config The task config
     * @throws NullPointerException If any of the arguments is {@code null}
     */
    public ConfigurationsTask(final TaskContext context, final ConfigurationsTaskConfig config) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(config);
        this.taskConfig = config;
        this.context = context;
    }

    /**
     * Scan the repository and return a list of configuration files
     * @param directory The directory
     * @return The list of files
     * @throws IOException If the directory is not inside the project directory
     */
    public List<ConfigurationFile> scanRepositoryDirectory(final File directory) throws IOException {
        final List<ConfigurationFile> result = new ArrayList<>();

        if ( directory.exists() ) {
            this.context.checkProjectFile(directory);
            logger.debug("Scanning {}", this.context.getRelativePath(directory));
            for(final String rootDir : new String[] {"libs", "apps"}) {
                final File dir = new File(directory, rootDir);
                if ( dir.exists() ) {
                    scanRepositoryForConfigFolders(result, dir, "apps".equals(rootDir) ? Location.APPS : Location.LIBS);
                }    
            }
        } else {
            logger.debug("Configured repository directory does not exist: {}", directory.getAbsolutePath());
        }

        return result;
    }

    public TaskResult analyseConfigurations(final List<ConfigurationFile> configFiles) throws IOException {
        final TaskResult result = new TaskResult();

        if ( configFiles.isEmpty() ) {
            logger.info("No configurations found in project");
        } else {
            logger.debug("{} configurations found in project", configFiles.size());

            final ArtifactId customFeatureId = this.context.getProjectId().changeClassifier("custom").changeType("slingosgifeature");
            final Feature customFeature = new Feature(customFeatureId);
            final ArtifactId productFeatureId = customFeatureId.changeClassifier("product");
            final Map<ServiceType, ConfigurationApi> productApis = this.loadConfigurationApis(customFeatureId, productFeatureId);
    
            final ConfigurationApi customApi = new ConfigurationApi();
            customApi.getFeatureToRegionCache().put(customFeatureId, Region.GLOBAL);
            customApi.getFeatureToRegionCache().put(productFeatureId, Region.INTERNAL);
            customApi.setRegion(Region.GLOBAL);
            ConfigurationApi.setConfigurationApi(customFeature, customApi);
    
            for(final ConfigurationFile file : configFiles) {
                this.analyseConfigFile(file, result, productApis, customFeature, productFeatureId);
            }
        }
        return result;
    }

    private Map<ServiceType, ConfigurationApi> loadConfigurationApis(final ArtifactId customFeatureId, final ArtifactId productFeatureId) throws IOException {
        final Map<ServiceType, ConfigurationApi> result = new HashMap<>();

        for(final Map.Entry<ServiceType, Feature> entry : this.context.getProductFeatures().entrySet()) {
            final ConfigurationApi api = ConfigurationApi.getConfigurationApi(entry.getValue());
            if ( api != null ) {
                api.setRegion(Region.GLOBAL);
                api.getFeatureToRegionCache().put(customFeatureId, Region.GLOBAL);
                api.getFeatureToRegionCache().put(productFeatureId, Region.INTERNAL);
                result.put(entry.getKey(), api);
            }
        }
 
        return result;
    }

    /**
     * Scan the repository for config folders
     * @param configFiles The list of files
     * @param dir Directory
     * @param location The location
     * @throws IOException
     */
    private void scanRepositoryForConfigFolders(final List<ConfigurationFile> configFiles, final File dir, final Location location) throws IOException {
        logger.debug("Scanning for configuration folders in {}", this.context.getRelativePath(dir));
        for(final File f : dir.listFiles()) {
            if ( f.isDirectory() ) {
                final String runMode = isConfigFolder(f);
                if ( runMode != null ) {
                    scanConfigurationFolder(configFiles, f, runMode, location, 1);
                } else {
                    scanRepositoryForConfigFolders(configFiles, f, location);
                }
            }
        }
    }
 
     /**
     * Scan the configuration folder
     * @param configFiles The configurations
     * @param dir The folder
     * @param runMode The run mode
     * @param location The location
     * @param level The level
     * @throws IOException
     */
    private void scanConfigurationFolder(final List<ConfigurationFile> configFiles, final File dir, final String runMode, final Location location, final int level) throws IOException {
        logger.debug("Scanning for configurations in {}", this.context.getRelativePath(dir));
        for(final File f : dir.listFiles()) {
            if ( f.isDirectory() ) {
                scanConfigurationFolder(configFiles, f, runMode, location, level+1);
            } else {
                if ( !f.getName().startsWith(".") ) {
                    final ConfigurationFileType type = ConfigurationFileType.fromFileName(f.getName());
                    if ( type != null ) {
                        final ConfigurationFile file = new ConfigurationFile(location, f, type);
                        file.setLevel(level);
                        file.setRunMode(runMode.isEmpty() ? null : runMode);
                        configFiles.add(file);
                    }    
                }
            }
        }
    }

    /**
     * Is the folder a configuration folder
     * @param dir The folder
     * @return The run mode if it is a configuration folder otherwise {@code null}
     */
    private String isConfigFolder(final File dir) {
        final String name = dir.getName();
        if ( "config".equals(name) || "install".equals(name) ) {
            return "";
        }
        if ( name.startsWith("install.")) {
            return name.substring(8);
        }
        if ( name.startsWith("config.")) {
            return name.substring(7);
        }
        return null;
    }

    private boolean checkRunMode(final ConfigurationFile file, final TaskResult result) throws IOException {
        final boolean validRunMode = RunModes.isRunModeAllowedIncludingSDK(file.getRunMode());
        if ( !validRunMode ) {
            final String validMode = RunModes.checkIfRunModeIsSpecifiedInWrongOrder(file.getRunMode());
            if ( validMode != null ) {
                result.getErrors().add(this.context.newAnnotation(file.getSource(), 
                    "Configuration has invalid runmode: ".concat(file.getRunMode()).concat(". Please use this runmode instead: ").concat(validMode)));
            } else {
                result.getErrors().add(this.context.newAnnotation(file.getSource(), "Configuration has unused runmode: ".concat(file.getRunMode())));
            }
        }
        return validRunMode;
    }

    /**
     * Analyse all configuration files
     * @param configFiles The configuration files
     * @param result The result to augment
     * @param features The features map for author and publish
     * @throws IOException
     */
    private void analyseConfigFile(final ConfigurationFile file, 
            final TaskResult result,
            final Map<ServiceType, ConfigurationApi> productApis,
            final Feature customFeature,
            final ArtifactId productFeatureId) throws IOException {
        logger.debug("Analysing file ".concat(context.getRelativePath(file.getSource())));

        if ( taskConfig.isEnforceRepositoryConfigurationBelowConfigFolder() && file.getLevel() > 1 ) {
            result.getErrors().add(context.newAnnotation(file.getSource(), 
                "Configuration must be directly inside a configuration folder and not in a sub folder."));
        }
        if ( file.getLocation() == Location.LIBS) {
            result.getErrors().add(context.newAnnotation(file.getSource(), 
                "Configuration must be inside the apps folder (not libs)."));
        }
        if ( checkRunMode(file, result) ) {
            final Dictionary<String, Object> properties = file.readConfiguration();
            if ( properties != null ) {
                if ( file.getType() != ConfigurationFileType.JSON ) {
                    result.getWarnings().add(context.newAnnotation(file.getSource(), "Configuration is not in JSON format. Please convert."));
                }
                for(final ServiceType serviceType : this.context.getProductFeatures().keySet()) {
                    if ( RunModes.matchesRunMode(serviceType, file.getRunMode()) ) {
                        final Feature feature = this.context.getProductFeatures().get(serviceType);
                        final ConfigurationApi api = productApis.get(serviceType);

                        if ( api != null ) {
                            final Configuration cfg = new Configuration(file.getPid());
                            cfg.setFeatureOrigins(Collections.singletonList(customFeature.getId()));
                            final ConfigurableEntity entity;
                            if ( cfg.isFactoryConfiguration() ) {
                                entity = api.getFactoryConfigurationDescriptions().get(cfg.getFactoryPid());
                            } else {
                                entity = api.getConfigurationDescriptions().get(cfg.getPid());
                            }
                            if ( entity != null ) {

                                final Configuration featureCfg = feature.getConfigurations().getConfiguration(file.getPid());
                                if ( featureCfg != null ) {
                                    for( final String propName : Collections.list(featureCfg.getConfigurationProperties().keys()) ) {
                                        cfg.getProperties().put(propName, featureCfg.getProperties().get(propName));
                                        cfg.setFeatureOrigins(propName, Collections.singletonList(productFeatureId));
                                    }
                                }
                                for(final String propName : Collections.list(properties.keys()) ) {
                                    cfg.getProperties().put(propName, properties.get(propName));
                                    cfg.setFeatureOrigins(propName, Collections.singletonList(customFeature.getId()));
                                }
                                customFeature.getConfigurations().add(cfg);

                                validateConfiguration(file, customFeature, api, result);

                                customFeature.getConfigurations().remove(cfg);
                            }
                        }
                    }
                }
            }
        }
    }

    private void validateConfiguration(final ConfigurationFile file, final Feature customFeature, 
        final ConfigurationApi api, final TaskResult result) throws IOException {

        final FeatureValidator validator = new FeatureValidator();
        final FeatureValidationResult featureResult = validator.validate(customFeature, api);

        final ConfigurationValidationResult cfgResult = featureResult.getConfigurationResults().get(file.getPid());
        for(final String warn : cfgResult.getWarnings()) {
            result.getWarnings().add(context.newAnnotation(file.getSource(), warn));
        }
        for(final String err : cfgResult.getErrors()) {
            result.getErrors().add(context.newAnnotation(file.getSource(), err));
        }
        for(final Map.Entry<String, PropertyValidationResult> propEntry : cfgResult.getPropertyResults().entrySet()) {
            for(final String warn : propEntry.getValue().getWarnings()) {
                result.getWarnings().add(context.newAnnotation(file.getSource(), "Property ".concat(propEntry.getKey()).concat(" - ").concat(warn)));
            }
            for(final String err : propEntry.getValue().getErrors()) {
                result.getErrors().add(context.newAnnotation(file.getSource(), "Property ".concat(propEntry.getKey()).concat(" - ").concat(err)));
            }
        }
    }
}
