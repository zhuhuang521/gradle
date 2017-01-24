/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.repository;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.plugin.repository.rules.RuleBasedPluginResolution;
import org.gradle.plugin.repository.rules.RuleBasedArtifactRepositories;

/**
 * Used to describe a rule based plugin repository.
 *
 * Using this repository, the implementer is responsible to decide if the plugin can be
 * satisfied by this repository. If in doubt, then nothing should be done.
 *
 * Example usage:
 * <code>
 *      pluginRepositories {
 *          rules {
 *              description = 'Example Inc. Plugin Repo'
 *              artifactRepositories { repo ->
 *                  repo.maven { url 'http://repo.example.org' }
 *              }
 *              pluginResolution { resolution ->
 *                  if(resolution.requestedPlugin.id.namespace == 'org.example' && resolution.requestedPlugin.id.name == 'plugin') {
 *                      resolution.useModule('org.example.plugin:plugin:1.0')
 *                  }
 *              }
 *          }
 *      }
 * </code>
 *
 * @since 3.4
 */
@Incubating
public interface RuleBasedPluginRepository extends PluginRepository {

    String getDescription();

    void setDescription(String description);

    void artifactRepositories(Action<RuleBasedArtifactRepositories> action);

    void pluginResolution(RuleBasedPluginResolution resolution);
}
