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
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
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

        getProjectAndSubprojects(project)
                .forEach(subProject -> subProject.afterEvaluate(evaluatedProject -> {
                    if (!evaluatedProject.getTasks().getNames().contains(cyclonedxDirectTaskName)) {
                        LOGGER.info(
                                "{} Project [{}] skipped because direct BOM task [{}] not found",
                                LOG_PREFIX,
                                evaluatedProject.getDisplayName(),
                                cyclonedxDirectTaskName);
                        return;
                    }
                    final TaskProvider<CyclonedxDirectTask> directTask =
                            evaluatedProject.getTasks().named(cyclonedxDirectTaskName, CyclonedxDirectTask.class);

                    // Skip tasks that aren't enabled
                    if (!filterProvider(project, directTask, DefaultTask::isEnabled)
                            .isPresent()) {
                        LOGGER.info(
                                "{} Project [{}] skipped because direct BOM task [{}] is disabled",
                                LOG_PREFIX,
                                evaluatedProject.getDisplayName(),
                                cyclonedxDirectTaskName);
                        return;
                    }

                    if (filterProvider(project, directTask, task -> task.getXmlOutput()
                                    .isPresent())
                            .isPresent()) {
                        evaluatedProject
                                .getArtifacts()
                                .add(
                                        cyclonedxDirectConfigurationName,
                                        directTask.map(BaseCyclonedxTask::getXmlOutput),
                                        a -> a.builtBy(directTask));
                    }

                    if (filterProvider(project, directTask, task -> task.getJsonOutput()
                                    .isPresent())
                            .isPresent()) {
                        evaluatedProject
                                .getArtifacts()
                                .add(
                                        cyclonedxDirectConfigurationName,
                                        directTask.map(BaseCyclonedxTask::getJsonOutput),
                                        a -> a.builtBy(directTask));
                    }

                    // Add aggregate dependency only for enabled tasks
                    project.getDependencies()
                            .add(
                                    cyclonedxAggregateConfigurationName,
                                    project.getDependencies()
                                            .project(ImmutableMap.of(
                                                    "path",
                                                    evaluatedProject.getPath(),
                                                    "configuration",
                                                    cyclonedxDirectConfigurationName)));
                }));
    }

    private static Stream<Project> getProjectAndSubprojects(final Project project) {
        return Stream.concat(Stream.of(project), project.getSubprojects().stream());
    }

    private static <T> Provider<T> filterProvider(
            final Project project, final Provider<? extends T> provider, final Spec<? super T> spec) {
        return provider.flatMap(
                task -> spec.isSatisfiedBy(task) ? project.provider(() -> task) : project.provider(() -> null));
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
        project.getTasks().register(cyclonedxAggregateTaskName, CyclonedxAggregateTask.class, task -> {
            task.dependsOn(getProjectAndSubprojects(project)
                    .map(p -> p.getTasks().named(cyclonedxDirectTaskName))
                    .toArray(Object[]::new));
            final Provider<Directory> aggregateReportDir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxAggregateReportDir);
            task.getXmlOutput().convention(aggregateReportDir.get().file("bom.xml"));
            task.getJsonOutput().convention(aggregateReportDir.get().file("bom.json"));
            // Wire inputs from configuration files
            final Provider<ConfigurableFileCollection> files = project.getProviders()
                    .provider(() -> project.getObjects().fileCollection().from(cyclonedxBomAggregateConfiguration));
            task.getInputSboms().from(files);
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
        project.getTasks().register(cyclonedxDirectTaskName, CyclonedxDirectTask.class, task -> {
            final Provider<Directory> dir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxDirectReportDir);
            task.getXmlOutput().convention(dir.get().file("bom.xml"));
            task.getJsonOutput().convention(dir.get().file("bom.json"));
            task.getAggregateConfigurationName().convention(cyclonedxAggregateConfigurationName);
        });
    }
}
