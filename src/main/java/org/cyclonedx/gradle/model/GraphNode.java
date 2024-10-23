/*
 * This file is part of CycloneDX Gradle Plugin.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.cyclonedx.gradle.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.jetbrains.annotations.NotNull;

public class GraphNode implements Comparable<GraphNode> {

    private final String ref;
    private final ResolvedComponentResult result;
    private final Set<ConfigurationScope> inScopeConfigurations;

    public GraphNode(final String ref, final ResolvedComponentResult result) {
        this.ref = ref;
        this.result = result;
        this.inScopeConfigurations = new HashSet<>();
    }

    public String getRef() {
        return ref;
    }

    public ResolvedComponentResult getResult() {
        return result;
    }

    public void inScopeConfiguration(final String projectName, final String configName) {
        inScopeConfigurations.add(new ConfigurationScope(projectName, configName));
    }

    public Set<ConfigurationScope> getInScopeConfigurations() {
        return inScopeConfigurations;
    }

    @Override
    public int compareTo(@NotNull GraphNode o) {
        return this.ref.compareTo(o.ref);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return Objects.equals(ref, graphNode.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ref);
    }

    public static class ConfigurationScope {
        private final String projectName;
        private final String configName;

        private ConfigurationScope(final String projectName, final String configName) {
            this.projectName = projectName;
            this.configName = configName;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getConfigName() {
            return configName;
        }
    }
}
