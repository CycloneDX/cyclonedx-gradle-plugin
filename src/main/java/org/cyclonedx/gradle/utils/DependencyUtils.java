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
package org.cyclonedx.gradle.utils;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;

public class DependencyUtils {

    public static Map<SbomComponentId, SbomComponent> mergeGraphs(
            final Map<SbomComponentId, SbomComponent> firstGraph,
            final Map<SbomComponentId, SbomComponent> secondGraph) {

        final Map<SbomComponentId, SbomComponent> mergedGraph = new HashMap<>(firstGraph);
        secondGraph.keySet().stream().forEach(id -> {
            if (firstGraph.containsKey(id)) {
                SbomComponent resultComponent = mergedGraph.get(id);
                SbomComponent targetComponent = secondGraph.get(id);
                resultComponent.getDependencyComponents().addAll(targetComponent.getDependencyComponents());
                resultComponent.getInScopeConfigurations().addAll(targetComponent.getInScopeConfigurations());
            } else {
                mergedGraph.put(id, secondGraph.get(id));
            }
        });

        return mergedGraph;
    }

    public static void connectRootWithSubProjects(
            final Project project,
            final SbomComponentId rootProjectId,
            final Map<SbomComponentId, SbomComponent> graph) {

        if (project.getSubprojects().isEmpty()) {
            return;
        }

        final Set<SbomComponentId> dependencyComponentIds = project.getSubprojects().stream()
                .map(subProject -> new SbomComponentId(
                        subProject.getGroup().toString(),
                        subProject.getName(),
                        subProject.getVersion().toString(),
                        ""))
                .filter(graph::containsKey)
                .collect(Collectors.toSet());

        graph.get(rootProjectId).getDependencyComponents().addAll(dependencyComponentIds);
    }

    public static Optional<SbomComponent> findRootComponent(
            final Project project, final Map<SbomComponentId, SbomComponent> graph) {

        final SbomComponentId rootProjectId = new SbomComponentId(
                project.getGroup().toString(),
                project.getName(),
                project.getVersion().toString(),
                "");

        if (!graph.containsKey(rootProjectId)) {
            return Optional.empty();
        } else {
            return Optional.of(graph.get(rootProjectId));
        }
    }

    public static SbomComponentId toComponentId(final ResolvedComponentResult node, final File file) {

        String type = "";
        if (node.getId() instanceof ModuleComponentIdentifier) {
            if (file != null) {
                type = getType(file);
            } else {
                type = "pom";
            }
        }

        if (node.getModuleVersion() != null) {
            return new SbomComponentId(
                    node.getModuleVersion().getGroup(),
                    node.getModuleVersion().getName(),
                    node.getModuleVersion().getVersion(),
                    type);
        } else {
            return new SbomComponentId("N/A", node.getId().getDisplayName(), "N/A", type);
        }
    }

    private static String getType(final File file) {

        final String fileExtension = FilenameUtils.getExtension(file.getName());
        if (StringUtils.isBlank(fileExtension)) {
            return "pom";
        }

        return fileExtension;
    }

    public static String generatePackageUrl(final SbomComponentId componentId, final TreeMap<String, String> qualifiers)
            throws MalformedPackageURLException {
        return new PackageURL(
                        PackageURL.StandardTypes.MAVEN,
                        componentId.getGroup(),
                        componentId.getName(),
                        componentId.getVersion(),
                        qualifiers,
                        null)
                .canonicalize();
    }
}
