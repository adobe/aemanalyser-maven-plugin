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
package com.adobe.aem.analyser;

import org.apache.jackrabbit.oak.plugins.tree.RootProvider;
import org.apache.jackrabbit.oak.plugins.tree.TreeProvider;
import org.apache.jackrabbit.oak.plugins.tree.impl.RootProviderService;
import org.apache.jackrabbit.oak.plugins.tree.impl.TreeProviderService;
import org.apache.jackrabbit.oak.security.authentication.AuthenticationConfigurationImpl;
import org.apache.jackrabbit.oak.security.authentication.token.TokenConfigurationImpl;
import org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl;
import org.apache.jackrabbit.oak.security.principal.PrincipalConfigurationImpl;
import org.apache.jackrabbit.oak.security.privilege.PrivilegeConfigurationImpl;
import org.apache.jackrabbit.oak.security.user.UserConfigurationImpl;
import org.apache.jackrabbit.oak.spi.security.ConfigurationBase;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityConfiguration;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authentication.AuthenticationConfiguration;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalConfiguration;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;

/**
 * Minimal {@link SecurityProvider} for repoinit validation using only public Oak APIs.
 */
final class RepoinitSecurityProvider implements SecurityProvider {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final AuthorizationConfiguration authorizationConfiguration;
    private final UserConfiguration userConfiguration;
    private final PrivilegeConfiguration privilegeConfiguration;
    private final PrincipalConfiguration principalConfiguration;
    private final TokenConfiguration tokenConfiguration;

    RepoinitSecurityProvider() {
        final RootProvider rootProvider = new RootProviderService();
        final TreeProvider treeProvider = new TreeProviderService();
        final ConfigurationParameters userParams = ConfigurationParameters.of(
                UserConstants.PARAM_GROUP_PATH, "/home/groups",
                UserConstants.PARAM_USER_PATH, "/home/users");

        this.authenticationConfiguration = initialize(new AuthenticationConfigurationImpl(),
                ConfigurationParameters.EMPTY, rootProvider, treeProvider);
        this.privilegeConfiguration = initialize(new PrivilegeConfigurationImpl(),
                ConfigurationParameters.EMPTY, rootProvider, treeProvider);
        this.userConfiguration = initialize(new UserConfigurationImpl(), userParams, rootProvider, treeProvider);
        this.authorizationConfiguration = initialize(new AuthorizationConfigurationImpl(),
                ConfigurationParameters.EMPTY, rootProvider, treeProvider);
        this.principalConfiguration = initialize(new PrincipalConfigurationImpl(),
                ConfigurationParameters.EMPTY, rootProvider, treeProvider);
        this.tokenConfiguration = initialize(new TokenConfigurationImpl(),
                ConfigurationParameters.EMPTY, rootProvider, treeProvider);
    }

    private <T extends SecurityConfiguration> T initialize(final T configuration,
            final ConfigurationParameters parameters, final RootProvider rootProvider,
            final TreeProvider treeProvider) {
        if (configuration instanceof ConfigurationBase) {
            final ConfigurationBase base = (ConfigurationBase) configuration;
            base.setSecurityProvider(this);
            base.setRootProvider(rootProvider);
            base.setTreeProvider(treeProvider);
            base.setParameters(ConfigurationParameters.of(base.getParameters(), parameters));
        }
        return configuration;
    }

    @Override
    public ConfigurationParameters getParameters(final String name) {
        final SecurityConfiguration configuration = getSecurityConfigurationByName(name);
        return configuration != null ? configuration.getParameters() : ConfigurationParameters.EMPTY;
    }

    @Override
    public Iterable<? extends SecurityConfiguration> getConfigurations() {
        return java.util.Set.of(
                this.authenticationConfiguration,
                this.authorizationConfiguration,
                this.userConfiguration,
                this.privilegeConfiguration,
                this.principalConfiguration,
                this.tokenConfiguration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(final Class<T> configurationClass) {
        if (AuthenticationConfiguration.class == configurationClass) {
            return (T) this.authenticationConfiguration;
        }
        if (AuthorizationConfiguration.class == configurationClass) {
            return (T) this.authorizationConfiguration;
        }
        if (UserConfiguration.class == configurationClass) {
            return (T) this.userConfiguration;
        }
        if (PrivilegeConfiguration.class == configurationClass) {
            return (T) this.privilegeConfiguration;
        }
        if (PrincipalConfiguration.class == configurationClass) {
            return (T) this.principalConfiguration;
        }
        if (TokenConfiguration.class == configurationClass) {
            return (T) this.tokenConfiguration;
        }
        return null;
    }

    private SecurityConfiguration getSecurityConfigurationByName(final String name) {
        for (final SecurityConfiguration configuration : getConfigurations()) {
            if (name.equals(configuration.getName())) {
                return configuration;
            }
        }
        return null;
    }

}
