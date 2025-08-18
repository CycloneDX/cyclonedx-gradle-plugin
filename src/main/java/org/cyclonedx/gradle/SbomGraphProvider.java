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

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Provider that lazily calculates the aggregated dependency graph. The usage of a provider is essential to support
 * configuration cache and also to ensure that all dependencies have been resolved when the CycloneDxTask is executed.
 */
class SbomGraphProvider implements Callable<SbomGraph> {

    private static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    private static final ResolvedArtifactResult[] ARTIFACT_TYPE = new ResolvedArtifactResult[0];
    private static final Logger LOGGER = Logging.getLogger(SbomGraphProvider.class);

    private final Project project;
    private final CycloneDxTask task;
    private final MavenProjectLookup mavenLookup;

    SbomGraphProvider(final Project project, final CycloneDxTask task) {
        this.project = project;
        this.task = task;
        this.mavenLookup = new MavenProjectLookup(project);
    }

    /**
     * Calculates the aggregated dependency graph across all the configurations of both the current project and
     * child projects. The steps are as follows:
     *  1) generate dependency graphs for the current project, one for each configuration
     *  2) if child projects exist, generate dependency graphs across all the child projects
     *  3) merge all generated graphs from the step 1) and 2)
     *
     * @return the aggregated dependency graph
     */
    @Override
    public SbomGraph call() throws Exception {

        if (project.getGroup().equals("") || project.getVersion().equals("")) {
            LOGGER.warn(
                    "Project group or version are not set for project [{}], will use \"unspecified\"",
                    project.getName());
        }

        LOGGER.info(MESSAGE_RESOLVING_DEPS);

        final Map<SbomComponentId, SbomComponent> graph = Stream.concat(
                        Stream.of(project), project.getSubprojects().stream())
                .filter(this::filterProjects)
                .flatMap(this::traverseProject)
                .reduce(new HashMap<>(), DependencyUtils::mergeGraphs);

        return buildSbomGraph(graph);
    }

    private SbomGraph buildSbomGraph(final Map<SbomComponentId, SbomComponent> graph) {
        final Optional<SbomComponent> rootProject =
                findRootComponent(graph, task.getComponentVersion().get());
        if (!rootProject.isPresent()) {
            LOGGER.debug("CycloneDX: root project not found. Constructing it.");
            final SbomComponentId rootProjectId = new SbomComponentId(
                    project.getGroup().toString(),
                    project.getName(),
                    task.getComponentVersion().get(),
                    null,
                    project.getPath());
            final SbomComponent sbomComponent = new SbomComponent.Builder()
                    .withId(rootProjectId)
                    .withDependencyComponents(new HashSet<>())
                    .withInScopeConfigurations(new HashSet<>())
                    .withLicenses(new ArrayList<>())
                    .build();
            graph.put(rootProjectId, sbomComponent);
            connectRootWithSubProjects(rootProjectId, graph);
            return new SbomGraph(graph, sbomComponent);
        } else {
            connectRootWithSubProjects(rootProject.get().getId(), graph);
            return new SbomGraph(graph, rootProject.get());
        }
    }

    private Stream<Map<SbomComponentId, SbomComponent>> traverseProject(final Project project) {

        final DependencyGraphTraverser traverser = new DependencyGraphTraverser(getArtifacts(), mavenLookup, task);
        return getInScopeConfigurations(project)
                .map(config -> traverser.traverseGraph(
                        config.getIncoming().getResolutionResult().getRoot(), project.getName(), config.getName()));
    }

    private Map<ComponentIdentifier, File> getArtifacts() {
        return Stream.concat(Stream.of(project), project.getSubprojects().stream())
                .filter(this::filterProjects)
                .flatMap(this::getInScopeConfigurations)
                .flatMap(config -> {
                    final ResolvedArtifactResult[] resolvedArtifacts = config.getIncoming()
                            .artifactView(view -> {
                                view.lenient(true);
                            })
                            .getArtifacts()
                            .getArtifacts()
                            .toArray(ARTIFACT_TYPE);
                    this.project
                            .getLogger()
                            .debug(
                                    "For project {} following artifacts have been resolved: {}",
                                    project.getName(),
                                    summarize(resolvedArtifacts, v -> v.getId().getDisplayName()));
                    return Arrays.stream(resolvedArtifacts);
                })
                .collect(Collectors.toMap(
                        artifact -> artifact.getId().getComponentIdentifier(),
                        ResolvedArtifactResult::getFile,
                        (v1, v2) -> v1));
    }

    private <T> String summarize(T[] data, final Function<T, String> extractor) {
        return Arrays.stream(data).map(extractor).collect(Collectors.joining(","));
    }

    private boolean shouldSkipConfiguration(final Configuration configuration) {
        return task.getSkipConfigs().get().stream().anyMatch(configuration.getName()::matches);
    }

    private boolean shouldIncludeConfiguration(final Configuration configuration) {
        return task.getIncludeConfigs().get().isEmpty()
                || task.getIncludeConfigs().get().stream().anyMatch(configuration.getName()::matches);
    }

    private boolean shouldSkipProject(final Project project) {
        return task.getSkipProjects().get().contains(project.getName());
    }

    private boolean filterConfigurations(final Project project, final Configuration configuration) {
        final boolean include = shouldIncludeConfiguration(configuration);
        final boolean skip = shouldSkipConfiguration(configuration);
        final boolean resolvable = configuration.isCanBeResolved();
        if (!include || skip || !resolvable) {
            LOGGER.debug(
                    "Skipping configuration '{}' (project: {}, include: {}, skip: {}, canBeResolved: {})",
                    configuration.getName(),
                    project,
                    include,
                    skip,
                    resolvable);
        }
        return include && !skip && resolvable;
    }

    private boolean filterProjects(final Project project) {
        final boolean skip = shouldSkipProject(project);
        if (skip) {
            LOGGER.debug("Skipping project '{}'", project.getName());
        }
        return !skip;
    }

    private Stream<Configuration> getInScopeConfigurations(final Project project) {
        final Configuration[] configs = project.getConfigurations().stream()
                .filter(configuration -> filterConfigurations(project, configuration))
                .toArray(Configuration[]::new);
        LOGGER.info(
                "For project {} following configurations are in scope to build the dependency graph: {}",
                project.getName(),
                summarize(configs, Configuration::getName));
        return Arrays.stream(configs);
    }

    private void connectRootWithSubProjects(
            final SbomComponentId rootProjectId, final Map<SbomComponentId, SbomComponent> graph) {
        if (project.getSubprojects().isEmpty()) {
            return;
        }
        final Set<SbomComponentId> dependencyComponentIds = project.getSubprojects().stream()
                .map(subProject -> new SbomComponentId(
                        subProject.getGroup().toString(),
                        subProject.getName(),
                        subProject.getVersion().toString(),
                        null,
                        subProject.getPath()))
                .filter(id -> {
                    final boolean exists = graph.containsKey(id);
                    if (!exists) {
                        LOGGER.debug("Subproject not found in graph: {}", id);
                    }
                    return exists;
                })
                .collect(Collectors.toSet());
        LOGGER.debug("Connecting root project {} with subprojects {}", rootProjectId, dependencyComponentIds);

        graph.get(rootProjectId).getDependencyComponents().addAll(dependencyComponentIds);
    }

    private Optional<SbomComponent> findRootComponent(
            final Map<SbomComponentId, SbomComponent> graph, final String configuredComponentVersion) {
        final SbomComponentId rootProjectId = new SbomComponentId(
                project.getGroup().toString(), project.getName(), configuredComponentVersion, null, project.getPath());

        if (!graph.containsKey(rootProjectId)) {
            return Optional.empty();
        } else {
            return Optional.of(graph.get(rootProjectId));
        }
    }
}
