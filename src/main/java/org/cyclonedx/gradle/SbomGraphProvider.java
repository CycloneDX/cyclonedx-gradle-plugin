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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cyclonedx.gradle.model.ArtifactExclusion;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

/**
 * Provider that lazily calculates the aggregated dependency graph. The usage of a provider is essential to support
 * configuration cache and also to ensure that all dependencies have been resolved when the CycloneDxTask is executed.
 */
class SbomGraphProvider implements Callable<SbomGraph> {

    private static final Logger LOGGER = Logging.getLogger(SbomGraphProvider.class);
    private static final ResolvedArtifactResult[] ARTIFACT_TYPE = new ResolvedArtifactResult[0];

    private final Supplier<String> projectGroup;
    private final String projectName;
    private final Supplier<String> projectVersion;
    private final String projectPath;
    private final String projectDisplayName;
    private final Iterable<Configuration> projectConfigurations;
    private final Iterable<Configuration> buildScriptConfigurations;
    private final CyclonedxDirectTask task;
    private final MavenProjectLookup mavenLookup;
    private List<ArtifactExclusion> exclusions = new ArrayList<>();

    @Nullable private SbomGraph cachedResult;

    SbomGraphProvider(
            final Supplier<String> projectGroup,
            final String projectName,
            final Supplier<String> projectVersion,
            final String projectPath,
            final String projectDisplayName,
            final Iterable<Configuration> projectConfigurations,
            final Iterable<Configuration> buildScriptConfigurations,
            final MavenProjectLookup mavenLookup,
            final CyclonedxDirectTask task) {
        this.projectGroup = projectGroup;
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.projectPath = projectPath;
        this.projectDisplayName = projectDisplayName;
        this.projectConfigurations = projectConfigurations;
        this.buildScriptConfigurations = buildScriptConfigurations;
        this.mavenLookup = mavenLookup;
        this.task = task;
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
    public SbomGraph call() {
        if (cachedResult != null) {
            return cachedResult;
        }

        this.exclusions = task.getExcludeArtifacts().get().stream()
            .map(ArtifactExclusion::new)
            .collect(Collectors.toList());

        if (projectGroup.get().isEmpty() || projectVersion.get().isEmpty()) {
            LOGGER.warn(
                    "{} Project group or version are not set for project [{}], will use \"unspecified\"",
                    LOG_PREFIX,
                    projectName);
        }

        LOGGER.info("{} Resolving dependencies for project [{}]", LOG_PREFIX, projectDisplayName);
        final Map<SbomComponentId, SbomComponent> graph =
                withPluginClassLoader(() -> traverseProject().reduce(new HashMap<>(), DependencyUtils::mergeGraphs));
        cachedResult = buildSbomGraph(graph);
        return cachedResult;
    }

    private SbomGraph buildSbomGraph(final Map<SbomComponentId, SbomComponent> graph) {
        final SbomComponentId projectBasedRootComponentId =
                new SbomComponentId(projectGroup.get(), projectName, projectVersion.get(), null, projectPath);
        final SbomComponentId configurationBasedRootComponentId = new SbomComponentId(
                task.getComponentGroup().get(),
                task.getComponentName().get(),
                task.getComponentVersion().get(),
                null,
                projectPath);
        final SbomComponent sbomComponentFromGraph = graph.get(projectBasedRootComponentId);
        if (sbomComponentFromGraph == null) {
            LOGGER.warn(
                    "{} Root component [{}] not found in the graph, constructing it, but dependency graph will be disconnected",
                    LOG_PREFIX,
                    projectBasedRootComponentId);
            final SbomComponent configurationBasedSbomComponent = new SbomComponent.Builder()
                    .withId(configurationBasedRootComponentId)
                    .withDependencyComponents(new HashSet<>())
                    .withInScopeConfigurations(new HashSet<>())
                    .withLicenses(new ArrayList<>())
                    .build();
            return new SbomGraph(graph, configurationBasedSbomComponent);
        } else {
            if (projectBasedRootComponentId.equals(configurationBasedRootComponentId)) {
                return new SbomGraph(graph, sbomComponentFromGraph);
            } else {
                final SbomComponent configurationBasedSbomComponent = new SbomComponent.Builder()
                        .withId(configurationBasedRootComponentId)
                        .withArtifactFile(
                                sbomComponentFromGraph.getArtifactFile().orElse(null))
                        .withDependencyComponents(sbomComponentFromGraph.getDependencyComponents())
                        .withInScopeConfigurations(sbomComponentFromGraph.getInScopeConfigurations())
                        .withLicenses(sbomComponentFromGraph.getLicenses())
                        .withMetaData(sbomComponentFromGraph.getSbomMetaData().orElse(null))
                        .build();
                LOGGER.info(
                        "{} Replacing project based root component [{}] with configuration based [{}]",
                        LOG_PREFIX,
                        projectBasedRootComponentId,
                        configurationBasedRootComponentId);
                graph.remove(projectBasedRootComponentId);
                graph.put(configurationBasedRootComponentId, configurationBasedSbomComponent);
                return new SbomGraph(graph, configurationBasedSbomComponent);
            }
        }
    }

    private static <T> T withPluginClassLoader(final Supplier<T> action) {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(SbomGraphProvider.class.getClassLoader());
            return action.get();
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    private Stream<Map<SbomComponentId, SbomComponent>> traverseProject() {
        final DependencyGraphTraverser traverser =
                new DependencyGraphTraverser(getArtifacts(), mavenLookup, task, exclusions);
        return getInScopeConfigurations()
                .map(config -> traverser.traverseGraph(
                        config.getIncoming().getResolutionResult().getRoot(), projectName, config.getName()));
    }

    private Map<ComponentIdentifier, File> getArtifacts() {
        return getInScopeConfigurations()
                .flatMap(config -> {
                    final ResolvedArtifactResult[] resolvedArtifacts = config.getIncoming()
                            .artifactView(DependencyUtils::configureExternalArtifactView)
                            .getArtifacts()
                            .getArtifacts()
                            .toArray(ARTIFACT_TYPE);
                    LOGGER.debug(
                            "{} For project {} following artifacts have been resolved: {}",
                            LOG_PREFIX,
                            projectName,
                            summarize(resolvedArtifacts, v -> v.getId().getDisplayName()));
                    return Arrays.stream(resolvedArtifacts);
                })
                .filter(artifact -> exclusions.stream()
                        .noneMatch(
                                exclusion -> exclusion.matches(artifact.getId().getComponentIdentifier())))
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

    private boolean filterConfigurations(final Configuration configuration) {
        final boolean include = shouldIncludeConfiguration(configuration);
        final boolean skip = shouldSkipConfiguration(configuration);
        final boolean resolvable = configuration.isCanBeResolved();
        if (!include || skip || !resolvable) {
            LOGGER.debug(
                    "{}, Skipping configuration '{}' (project: {}, include: {}, skip: {}, canBeResolved: {})",
                    LOG_PREFIX,
                    configuration.getName(),
                    projectName,
                    include,
                    skip,
                    resolvable);
        }
        return include && !skip && resolvable;
    }

    private Stream<Configuration> getInScopeConfigurations() {
        final Stream<Configuration> projectConfigs =
                toStream(projectConfigurations).filter(this::filterConfigurations);

        final Stream<Configuration> buildScriptConfigs;
        if (task.getIncludeBuildEnvironment().get()) {
            buildScriptConfigs = toStream(buildScriptConfigurations).filter(this::filterConfigurations);
        } else {
            buildScriptConfigs = Stream.empty();
        }

        final Configuration[] configs =
                Stream.concat(projectConfigs, buildScriptConfigs).toArray(Configuration[]::new);

        LOGGER.info(
                "{} For project {} following configurations are in scope to build the dependency graph: {}",
                LOG_PREFIX,
                projectName,
                summarize(configs, Configuration::getName));
        return Arrays.stream(configs);
    }

    private static <T> Stream<T> toStream(final Iterable<T> iterable) {
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
    }
}
