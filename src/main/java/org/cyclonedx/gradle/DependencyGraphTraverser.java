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

import static org.cyclonedx.gradle.CyclonedxPlugin.LOG_PREFIX;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.gradle.model.ArtifactExclusion;
import org.cyclonedx.gradle.model.ConfigurationScope;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomMetaData;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.cyclonedx.model.Component;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

/**
 * Traverses the dependency graph of a configuration which returns a data model that 1) contains all the information
 * required to generate the CycloneDX Bom and 2) is fully serializable to support the build cache
 */
class DependencyGraphTraverser {

    private static final Logger LOGGER = Logging.getLogger(DependencyGraphTraverser.class);
    private final Map<ComponentIdentifier, File> resolvedArtifacts;
    private final MavenProjectLookup mavenLookup;
    private final boolean includeMetaData;
    private final MavenHelper mavenHelper;
    private final List<ArtifactExclusion> artifactExclusions;

    DependencyGraphTraverser(
            final Map<ComponentIdentifier, File> resolvedArtifacts,
            final MavenProjectLookup mavenLookup,
            final CyclonedxDirectTask task,
            final List<ArtifactExclusion> artifactExclusions) {
        this.resolvedArtifacts = resolvedArtifacts;
        this.mavenLookup = mavenLookup;
        this.includeMetaData = task.getIncludeMetadataResolution().get();
        this.mavenHelper = new MavenHelper(task.getIncludeLicenseText().get());
        this.artifactExclusions = artifactExclusions;
    }

    /**
     * Traverses the dependency graph of a configuration belonging to the specified project
     *
     * @param rootNode entry point into the graph which is typically represents a project
     * @param projectName project to which the configuration belongs to
     * @param configName name of the configuration
     *
     * @return a graph represented as map which is fully serializable. The graph nodes are instances of
     * SbomComponent which contain the necessary information to generate the Bom
     */
    Map<SbomComponentId, SbomComponent> traverseGraph(
            final ResolvedComponentResult rootNode, final String projectName, final String configName) {

        final Map<GraphNode, Set<GraphNode>> graph = new HashMap<>();
        final Queue<GraphNode> queue = new ArrayDeque<>();
        final GraphNode rootGraphNode = new GraphNode(rootNode);
        rootGraphNode.inScopeConfiguration(projectName, configName);
        queue.add(rootGraphNode);

        LOGGER.debug(
                "{} CycloneDX: Traversal of graph for configuration {} of project {}",
                LOG_PREFIX,
                configName,
                projectName);
        while (!queue.isEmpty()) {
            final GraphNode graphNode = queue.poll();
            if (isExcluded(graphNode.id)) {
                continue;
            }
            if (!graph.containsKey(graphNode)) {
                graph.put(graphNode, new HashSet<>());
                LOGGER.debug("{} Traversing node with ID {}", LOG_PREFIX, graphNode.id);
                for (final DependencyResult dep : graphNode.getResult().getDependencies()) {
                    if (dep.isConstraint()) {
                        continue; // Skip constraints as they do not represent a dependency in the graph
                    }
                    if (dep instanceof ResolvedDependencyResult) {
                        final ResolvedComponentResult dependencyComponent =
                                ((ResolvedDependencyResult) dep).getSelected();
                        if (graphNode.id.equals(dependencyComponent.getId())) {
                            continue; // Skip self-references
                        }
                        if (isExcluded(dependencyComponent.getId())) {
                            continue;
                        }
                        LOGGER.debug(
                                "{} Node with ID {} has dependency with ID {}",
                                LOG_PREFIX,
                                graphNode.id,
                                dependencyComponent);
                        final GraphNode dependencyNode = new GraphNode(dependencyComponent);
                        dependencyNode.inScopeConfiguration(projectName, configName);
                        graph.get(graphNode).add(dependencyNode);
                        queue.add(dependencyNode);
                    } else if (dep instanceof UnresolvedDependencyResult) {
                        final UnresolvedDependencyResult unresolved = (UnresolvedDependencyResult) dep;
                        LOGGER.info(
                                "{} Unable to resolve artifact {} because {}",
                                LOG_PREFIX,
                                unresolved.getAttempted().getDisplayName(),
                                unresolved.getFailure().toString());
                    }
                }
            }
        }

        return toSbomComponents(graph);
    }

    private boolean isExcluded(final ComponentIdentifier id) {
        return artifactExclusions.stream().anyMatch(exclusion -> exclusion.matches(id));
    }

    private Map<SbomComponentId, SbomComponent> toSbomComponents(final Map<GraphNode, Set<GraphNode>> graph) {
        return graph.entrySet().stream()
                .map(entry -> toSbomComponent(entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(SbomComponent::getId, v -> v));
    }

    private SbomComponent toSbomComponent(final GraphNode node, final Set<GraphNode> dependencyNodes) {
        final File artifactFile = getArtifactFile(node);
        final SbomComponentId id = DependencyUtils.toComponentId(node.getResult(), artifactFile);

        List<License> licenses = new ArrayList<>();
        SbomMetaData metaData = null;
        if (includeMetaData && node.id instanceof ModuleComponentIdentifier) {
            LOGGER.debug("{}: Including meta data for node {}", LOG_PREFIX, node.id);
            final Component component = new Component();
            extractMetaDataFromArtifactPom(artifactFile, component, node.getResult());
            licenses = extractMetaDataFromRepository(component, node.getResult());
            metaData = SbomMetaData.fromComponent(component);
        }

        return new SbomComponent.Builder()
                .withId(id)
                .withDependencyComponents(getSbomDependencies(dependencyNodes))
                .withInScopeConfigurations(node.getInScopeConfigurations())
                .withArtifactFile(artifactFile)
                .withMetaData(metaData)
                .withLicenses(licenses)
                .build();
    }

    private void extractMetaDataFromArtifactPom(
            @Nullable final File artifactFile, final Component component, final ResolvedComponentResult result) {

        if (artifactFile == null || result.getModuleVersion() == null) {
            return;
        }

        @Nullable final MavenProject mavenProject = mavenHelper.extractPom(artifactFile, result.getModuleVersion());
        if (mavenProject != null) {
            LOGGER.debug("{} Parse artifact pom file of component {}", LOG_PREFIX, result.getId());
            mavenHelper.getClosestMetadata(artifactFile, mavenProject, component, result.getModuleVersion());
        }
    }

    private List<License> extractMetaDataFromRepository(
            final Component component, final ResolvedComponentResult result) {
        final MavenProject mavenProject = mavenLookup.getResolvedMavenProject(result);
        if (mavenProject != null) {
            mavenHelper.extractMetadata(mavenProject, component);
            return mavenProject.getLicenses();
        }

        return new ArrayList<>();
    }

    private Set<SbomComponentId> getSbomDependencies(final Set<GraphNode> dependencyNodes) {
        return dependencyNodes.stream()
                .map(dependency -> DependencyUtils.toComponentId(dependency.getResult(), getArtifactFile(dependency)))
                .collect(Collectors.toSet());
    }

    private @Nullable File getArtifactFile(final GraphNode node) {
        return this.resolvedArtifacts.get(node.getResult().getId());
    }

    private static class GraphNode {

        private final ComponentIdentifier id;
        private final ResolvedComponentResult result;
        private final Set<ConfigurationScope> inScopeConfigurations;

        private GraphNode(final ResolvedComponentResult result) {
            this.id = result.getId();
            this.result = result;
            this.inScopeConfigurations = new HashSet<>();
        }

        private ResolvedComponentResult getResult() {
            return result;
        }

        private void inScopeConfiguration(final String projectName, final String configName) {
            inScopeConfigurations.add(new ConfigurationScope(projectName, configName));
        }

        private Set<ConfigurationScope> getInScopeConfigurations() {
            return inScopeConfigurations;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final GraphNode graphNode = (GraphNode) o;
            return Objects.equals(id, graphNode.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}
