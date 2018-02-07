/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.service;

import com.thoughtworks.go.config.ConfigTag;
import com.thoughtworks.go.config.PluginProfile;
import com.thoughtworks.go.config.PluginProfiles;
import com.thoughtworks.go.config.commands.EntityConfigUpdateCommand;
import com.thoughtworks.go.config.exceptions.GoConfigInvalidException;
import com.thoughtworks.go.config.update.PluginProfileCommand;
import com.thoughtworks.go.domain.config.ConfigurationProperty;
import com.thoughtworks.go.i18n.LocalizedMessage;
import com.thoughtworks.go.plugin.access.PluginNotFoundException;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.result.LocalizedOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class PluginProfilesService<M extends PluginProfile> {
    protected final GoConfigService goConfigService;
    protected final EntityHashingService hashingService;
    protected Logger LOGGER = LoggerFactory.getLogger(getClass());

    public PluginProfilesService(GoConfigService goConfigService, EntityHashingService hashingService) {
        this.goConfigService = goConfigService;
        this.hashingService = hashingService;
    }

    protected abstract PluginProfiles<M> getPluginProfiles();

    public M findProfile(String id) {
        return getPluginProfiles().find(id);
    }

    public Map<String, M> listAll() {
        Map<String, M> result = new LinkedHashMap<>();
        for (M profile : getPluginProfiles()) {
            result.put(profile.getId(), profile);
        }
        return result;
    }

    private void validatePluginProperties(PluginProfileCommand command, PluginProfile newPluginProfile) {
        try {
            ValidationResult result = command.validateUsingExtension(newPluginProfile.getPluginId(), newPluginProfile.getConfigurationAsMap(true));
            addErrorsToConfiguration(result, newPluginProfile);
        }catch (PluginNotFoundException e) {
            newPluginProfile.addError("pluginId", String.format("Plugin with id `%s` is not found.", newPluginProfile.getPluginId()));
        }catch (Exception e) {
            //Ignore - it will be the invalid cipher text exception for an encrypted value. This will be validated later during entity update
        }
    }

    private void addErrorsToConfiguration(ValidationResult result, PluginProfile newSecurityAuthConfig) {
        if (!result.isSuccessful()) {
            for (com.thoughtworks.go.plugin.api.response.validation.ValidationError validationError : result.getErrors()) {
                ConfigurationProperty property = newSecurityAuthConfig.getProperty(validationError.getKey());

                if (property != null) {
                    property.addError(validationError.getKey(), validationError.getMessage());
                } else {
                    newSecurityAuthConfig.addError(validationError.getKey(), validationError.getMessage());
                }
            }
        }
    }

    protected void update(Username currentUser, M pluginProfile, LocalizedOperationResult result, PluginProfileCommand command) {
        try {
            validatePluginProperties(command, pluginProfile);
            goConfigService.updateConfig(command, currentUser);
        } catch (Exception e) {
            if (e instanceof GoConfigInvalidException) {
                result.unprocessableEntity(LocalizedMessage.string("ENTITY_CONFIG_VALIDATION_FAILED", pluginProfile.getClass().getAnnotation(ConfigTag.class).value(), pluginProfile.getId(), e.getMessage()));
            } else {
                if (!result.hasMessage()) {
                    LOGGER.error(e.getMessage(), e);
                    result.internalServerError(LocalizedMessage.string("SAVE_FAILED_WITH_REASON", "An error occurred while saving the profile config. Please check the logs for more information."));
                }
            }
        }
    }
}