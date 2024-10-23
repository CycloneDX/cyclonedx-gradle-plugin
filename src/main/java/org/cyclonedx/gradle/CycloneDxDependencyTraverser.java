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
package org.cyclonedx.gradle;

import com.networknt.schema.utils.StringUtils;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.cyclonedx.gradle.model.ArtifactInfo;
import org.cyclonedx.gradle.model.GraphNode;
import org.cyclonedx.model.*;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.logging.Logger;

public class CycloneDxDependencyTraverser {

    private final Map<GraphNode, Set<GraphNode>> resultGraph;
    private final Logger logger;
    private final Map<ComponentIdentifier, File> resolvedArtifacts;
    private final CycloneDxBomBuilder builder;
    private GraphNode parentNode;

    public CycloneDxDependencyTraverser(final Logger logger, final CycloneDxBomBuilder builder) {
        this.builder = builder;
        this.logger = logger;
        this.resolvedArtifacts = new HashMap<>();
        this.resultGraph = new HashMap<>();
    }

    public void registerArtifact(final ArtifactInfo artifact) {
        resolvedArtifacts.put(artifact.getComponentId(), artifact.getArtifactFile());
    }

    public void traverseParentGraph(
            final ResolvedComponentResult rootNode, final String projectName, final String configName) {
        final String parentRef = getRef(rootNode.getModuleVersion());
        this.parentNode = new GraphNode(parentRef, rootNode);
        traverseGraph(rootNode, projectName, configName);
    }

    public void traverseChildGraph(
            final ResolvedComponentResult rootNode, final String projectName, final String configName) {

        if (this.parentNode == null) {
            throw new GradleException("Parent graphs has to be traversed first");
        }

        final String childRef = getRef(rootNode.getModuleVersion());
        final GraphNode childNode = new GraphNode(childRef, rootNode);
        this.resultGraph.get(this.parentNode).add(childNode);
        traverseGraph(rootNode, projectName, configName);
    }

    public void traverseGraph(
            final ResolvedComponentResult rootNode, final String projectName, final String configName) {

        final Map<GraphNode, Set<GraphNode>> graph = new TreeMap<>();
        final Queue<GraphNode> queue = new ArrayDeque<>();

        final String rootRef = getRef(rootNode.getModuleVersion());
        final GraphNode rootGraphNode = new GraphNode(rootRef, rootNode);
        queue.add(rootGraphNode);

        while (!queue.isEmpty()) {
            final GraphNode graphNode = queue.poll();
            if (!graph.containsKey(graphNode)) {
                graph.put(graphNode, new TreeSet<>());
                for (DependencyResult dep : graphNode.getResult().getDependencies()) {
                    if (dep instanceof ResolvedDependencyResult) {
                        final ResolvedComponentResult dependencyComponent =
                                ((ResolvedDependencyResult) dep).getSelected();
                        String ref = getRef(dependencyComponent.getModuleVersion());
                        GraphNode dependencyNode = new GraphNode(ref, dependencyComponent);
                        graph.get(graphNode).add(dependencyNode);
                        queue.add(dependencyNode);
                    }
                }
            }
        }

        mergeIntoResultGraph(graph, projectName, configName);
    }

    private void mergeIntoResultGraph(
            final Map<GraphNode, Set<GraphNode>> graph, final String projectName, final String configName) {

        graph.keySet().forEach(node -> {
            if (resultGraph.containsKey(node)) {
                resultGraph.get(node).addAll(graph.get(node));
            } else {
                resultGraph.put(node, graph.get(node));
            }
        });

        resultGraph.keySet().stream()
                .filter(graph::containsKey)
                .forEach(v -> v.inScopeConfiguration(projectName, configName));
    }

    public Bom toBom() {
        return builder.buildBom(this.resultGraph, this.parentNode, this.resolvedArtifacts);
    }

    private String getRef(final ModuleVersionIdentifier identifier) {

        // The cause for this failure is mainly if the group/name/project of the build isn't set
        if (StringUtils.isBlank(identifier.getGroup())
                || StringUtils.isBlank(identifier.getName())
                || StringUtils.isBlank(identifier.getVersion())) {
            throw new GradleException(String.format(
                    "Invalid module identifier provided. Group: %s, Name: %s, Version: %s",
                    identifier.getGroup(), identifier.getName(), identifier.getVersion()));
        }

        return String.format("%s:%s:%s", identifier.getGroup(), identifier.getName(), identifier.getVersion());
    }
}
