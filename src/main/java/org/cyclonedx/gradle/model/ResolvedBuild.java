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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ResolvedBuild {
    private final String projectName;
    private final Set<ResolvedConfiguration> projectConfigurations;
    private final Map<String, Set<ResolvedConfiguration>> subProjectsConfigurations;

    public ResolvedBuild(final String projectName) {
        this.projectName = projectName;
        this.projectConfigurations = new HashSet<>();
        this.subProjectsConfigurations = new HashMap<>();
    }

    public String getProjectName() {
        return projectName;
    }

    public void addProjectConfiguration(final ResolvedConfiguration configuration) {
        projectConfigurations.add(configuration);
    }

    public Set<ResolvedConfiguration> getProjectConfigurations() {
        return projectConfigurations;
    }

    public void addSubProjectConfiguration(final String projectName, final ResolvedConfiguration configuration) {
        if (subProjectsConfigurations.containsKey(projectName)) {
            subProjectsConfigurations.get(projectName).add(configuration);
        } else {
            final Set<ResolvedConfiguration> subProjectConfigurations = new HashSet<>();
            subProjectConfigurations.add(configuration);
            subProjectsConfigurations.put(projectName, subProjectConfigurations);
        }
    }

    public Map<String, Set<ResolvedConfiguration>> getSubProjectsConfigurations() {
        return subProjectsConfigurations;
    }
}
