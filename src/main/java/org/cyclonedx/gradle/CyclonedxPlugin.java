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

import com.google.common.collect.ImmutableMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Entrypoint of the plugin which simply configures one task
 */
public class CyclonedxPlugin implements Plugin<Project> {

    private static final Logger LOGGER = Logging.getLogger(CyclonedxPlugin.class);

    public static final String LOG_PREFIX = "[CycloneDX]";
    protected final String cyclonedxDirectTaskName;
    protected final String cyclonedxDirectConfigurationName;
    protected final String cyclonedxAggregateTaskName;
    protected final String cyclonedxAggregateConfigurationName;
    protected final String cyclonedxDirectReportDir;
    protected final String cyclonedxAggregateReportDir;

    @Inject
    public CyclonedxPlugin() {
        this(
                "cyclonedxDirectBom",
                "cyclonedxDirectBom",
                "cyclonedxBom",
                "cyclonedxBom",
                "reports/cyclonedx-direct",
                "reports/cyclonedx");
    }

    protected CyclonedxPlugin(
            final String cyclonedxDirectTaskName,
            final String cyclonedxDirectConfigurationName,
            final String cyclonedxAggregateTaskName,
            final String cyclonedxAggregateConfigurationName,
            final String cyclonedxDirectReportDir,
            final String cyclonedxAggregateReportDir) {
        this.cyclonedxDirectTaskName = cyclonedxDirectTaskName;
        this.cyclonedxDirectConfigurationName = cyclonedxDirectConfigurationName;
        this.cyclonedxAggregateTaskName = cyclonedxAggregateTaskName;
        this.cyclonedxAggregateConfigurationName = cyclonedxAggregateConfigurationName;
        this.cyclonedxDirectReportDir = cyclonedxDirectReportDir;
        this.cyclonedxAggregateReportDir = cyclonedxAggregateReportDir;
    }

    @Override
    public void apply(final Project project) {
        if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
            LOGGER.warn(
                    "warning: {} Support of Java versions prior to 17 is deprecated and will be removed in a future release.",
                    LOG_PREFIX);
        }
        getProjectAndSubprojects(project).forEach(this::configureProject);

        // Incoming configuration at root to collect subproject SBOMs
        final Configuration cyclonedxBomAggregateConfiguration =
                project.getConfigurations().maybeCreate(cyclonedxAggregateConfigurationName);
        cyclonedxBomAggregateConfiguration.setCanBeResolved(true);
        cyclonedxBomAggregateConfiguration.setCanBeConsumed(false);

        // Aggregate task
        registerCyclonedxAggregateBomTask(project, cyclonedxBomAggregateConfiguration);

        // Deferred: evaluated on resolution of the aggregate configuration, when all direct BOM tasks are configured
        cyclonedxBomAggregateConfiguration
                .getDependencies()
                .addAllLater(project.provider(() -> getProjectAndSubprojects(project)
                        .filter(this::hasEnabledDirectBomTask)
                        .map(subProject -> createDirectBomDependency(project.getDependencies(), subProject))
                        .collect(Collectors.toList())));
    }

    private static Stream<Project> getProjectAndSubprojects(final Project project) {
        return Stream.concat(Stream.of(project), project.getSubprojects().stream());
    }

    private void configureProject(final Project project) {
        // Outgoing configuration to publish SBOMs as artifacts
        final Configuration cyclonedxBomConfiguration =
                project.getConfigurations().maybeCreate(cyclonedxDirectConfigurationName);
        cyclonedxBomConfiguration.setCanBeConsumed(true);
        cyclonedxBomConfiguration.setCanBeResolved(false);
        registerCyclonedxDirectBomTask(project);
    }

    private void registerCyclonedxAggregateBomTask(
            final Project project, final Configuration cyclonedxBomAggregateConfiguration) {
        final TaskProvider<CyclonedxAggregateTask> aggregateTaskProvider = project.getTasks()
                .register(cyclonedxAggregateTaskName, CyclonedxAggregateTask.class, task -> {
                    task.dependsOn(getProjectAndSubprojects(project)
                        .map(p -> p.getTasks().named(cyclonedxDirectTaskName))
                        .toArray(Object[]::new));
                    final Provider<Directory> aggregateReportDir =
                            project.getLayout().getBuildDirectory().dir(cyclonedxAggregateReportDir);
                    task.getXmlOutput().convention(aggregateReportDir.get().file("bom.xml"));
                    task.getJsonOutput().convention(aggregateReportDir.get().file("bom.json"));
                    // Wire inputs from configuration files
                    final Provider<ConfigurableFileCollection> files = project.getProviders()
                            .provider(() ->
                                    project.getObjects().fileCollection().from(cyclonedxBomAggregateConfiguration));
                    task.getInputSboms().from(files);
                });

        getProjectAndSubprojects(project).forEach(p -> {
            p.getTasks().withType(CyclonedxDirectTask.class).configureEach(directTask -> {
                directTask
                        .getExcludeArtifacts()
                        .addAll(aggregateTaskProvider.flatMap(CyclonedxAggregateTask::getExcludeArtifacts));
            });
        });
    }

    private void registerCyclonedxDirectBomTask(final Project project) {
        if (project.getTasks().getNames().contains(cyclonedxDirectTaskName)) {
            LOGGER.info(
                    "{} Task [{}] already exists in project [{}], skipping creation",
                    LOG_PREFIX,
                    cyclonedxDirectTaskName,
                    project.getDisplayName());
            return;
        }
        final TaskProvider<CyclonedxDirectTask> taskProvider = project.getTasks()
                .register(cyclonedxDirectTaskName, CyclonedxDirectTask.class, task -> {
                    final Provider<Directory> dir =
                            project.getLayout().getBuildDirectory().dir(cyclonedxDirectReportDir);
                    task.getXmlOutput().convention(dir.get().file("bom.xml"));
                    task.getJsonOutput().convention(dir.get().file("bom.json"));
                    task.getAggregateConfigurationName().convention(cyclonedxAggregateConfigurationName);
                });

        project.getConfigurations()
                .getByName(cyclonedxDirectConfigurationName)
                .getOutgoing()
                .artifacts(taskProvider.map(CyclonedxDirectTask::getOutputFiles), a -> a.builtBy(taskProvider));
    }

    private boolean hasEnabledDirectBomTask(final Project project) {
        final Task directBomTask = project.getTasks().findByName(cyclonedxDirectTaskName);
        if (directBomTask == null) {
            LOGGER.info(
                    "{} Project [{}] skipped because direct BOM task [{}] not found",
                    LOG_PREFIX,
                    project.getDisplayName(),
                    cyclonedxDirectTaskName);
            return false;
        }
        if (!directBomTask.getEnabled()) {
            LOGGER.info(
                    "{} Project [{}] skipped because direct BOM task [{}] is disabled",
                    LOG_PREFIX,
                    project.getDisplayName(),
                    cyclonedxDirectTaskName);
            return false;
        }
        return true;
    }

    private Dependency createDirectBomDependency(final DependencyHandler dependencies, final Project directBomProject) {
        final ImmutableMap<String, String> notation =
                ImmutableMap.of("path", directBomProject.getPath(), "configuration", cyclonedxDirectConfigurationName);
        return dependencies.project(notation);
    }
}
