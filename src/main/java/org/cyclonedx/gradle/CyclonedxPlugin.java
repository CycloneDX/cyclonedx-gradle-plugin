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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
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
    protected final String cyclonedxBomExtensionName;
    protected final String cyclonedxDirectTaskName;
    protected final String cyclonedxDirectConfigurationName;
    protected final String cyclonedxAggregateTaskName;
    protected final String cyclonedxAggregateConfigurationName;
    protected final String cyclonedxDirectReportDir;
    protected final String cyclonedxAggregateReportDir;

    @Inject
    public CyclonedxPlugin() {
        this(
                "cyclonedxBom",
                "cyclonedxDirectBom",
                "cyclonedxDirectBom",
                "cyclonedxBom",
                "cyclonedxBom",
                "reports/cyclonedx-direct",
                "reports/cyclonedx");
    }

    protected CyclonedxPlugin(
            final String cyclonedxBomExtensionName,
            final String cyclonedxDirectTaskName,
            final String cyclonedxDirectConfigurationName,
            final String cyclonedxAggregateTaskName,
            final String cyclonedxAggregateConfigurationName,
            final String cyclonedxDirectReportDir,
            final String cyclonedxAggregateReportDir) {
        this.cyclonedxBomExtensionName = cyclonedxBomExtensionName;
        this.cyclonedxDirectTaskName = cyclonedxDirectTaskName;
        this.cyclonedxDirectConfigurationName = cyclonedxDirectConfigurationName;
        this.cyclonedxAggregateTaskName = cyclonedxAggregateTaskName;
        this.cyclonedxAggregateConfigurationName = cyclonedxAggregateConfigurationName;
        this.cyclonedxDirectReportDir = cyclonedxDirectReportDir;
        this.cyclonedxAggregateReportDir = cyclonedxAggregateReportDir;
    }

    @Override
    public void apply(final Project project) {
        final CyclonedxBomExtension ext = project.getExtensions()
                .create(cyclonedxBomExtensionName, CyclonedxBomExtension.class, project.getObjects());

        // Register per-project SBOM generation task and outgoing configuration
        Stream.concat(Stream.of(project), project.getSubprojects().stream())
                .forEach(subProject -> configureProject(subProject, ext));

        // Incoming configuration at root to collect subproject SBOMs
        final Configuration cyclonedxBomAggregateConfiguration =
                project.getConfigurations().maybeCreate(cyclonedxAggregateConfigurationName);
        cyclonedxBomAggregateConfiguration.setCanBeResolved(true);
        cyclonedxBomAggregateConfiguration.setCanBeConsumed(false);

        // Aggregate task
        registerCyclonedxAggregateBomTask(project, ext, cyclonedxBomAggregateConfiguration);

        project.afterEvaluate(evaluatedProject -> Stream.concat(
                        Stream.of(evaluatedProject), evaluatedProject.getSubprojects().stream())
                .forEach(subProject -> {
                    // Attach produced files lazily
                    subProject.getTasksByName(cyclonedxDirectTaskName, false).forEach(task -> task.getOutputs()
                            .getFiles()
                            .getFiles()
                            .forEach(file -> subProject
                                    .getArtifacts()
                                    .add(cyclonedxDirectConfigurationName, file, a -> a.builtBy(task))));
                    // Extend from all subprojects' cyclonedxBom dynamically
                    evaluatedProject
                            .getDependencies()
                            .add(
                                    cyclonedxAggregateConfigurationName,
                                    evaluatedProject
                                            .getDependencies()
                                            .project(ImmutableMap.of(
                                                    "path",
                                                    subProject.getPath(),
                                                    "configuration",
                                                    cyclonedxDirectConfigurationName)));
                }));
    }

    private void configureProject(final Project project, final CyclonedxBomExtension ext) {
        if (!shouldIncludeProject(project, ext)) {
            LOGGER.lifecycle(
                    "{} Project [{}] excluded by user for CycloneDX BOM generation",
                    LOG_PREFIX,
                    project.getDisplayName());
            return;
        }
        // Outgoing configuration to publish SBOMs as artifacts
        final Configuration cyclonedxBomConfiguration =
                project.getConfigurations().maybeCreate(cyclonedxDirectConfigurationName);
        cyclonedxBomConfiguration.setCanBeConsumed(true);
        cyclonedxBomConfiguration.setCanBeResolved(false);
        registerCyclonedxBomTask(project, ext);
    }

    private void registerCyclonedxAggregateBomTask(
            final Project project,
            final CyclonedxBomExtension ext,
            final Configuration cyclonedxBomAggregateConfiguration) {
        project.getTasks().register(cyclonedxAggregateTaskName, CyclonedxAggregateTask.class, task -> {
            task.dependsOn(Stream.concat(Stream.of(project), project.getSubprojects().stream())
                    .filter(p -> shouldIncludeProject(p, ext))
                    .map(p -> p.getPath() + ":"
                            + p.getTasks().named(cyclonedxDirectTaskName).getName())
                    .toArray(Object[]::new));

            task.getComponentGroup()
                    .set(ext.getComponentGroup().orElse(project.getProviders().provider(() -> project.getGroup()
                            .toString())));
            task.getComponentName().set(ext.getComponentName().orElse(project.getName()));
            task.getComponentVersion()
                    .set(ext.getComponentVersion().orElse(project.getProviders().provider(() -> project.getVersion()
                            .toString())));
            task.getSchemaVersion().set(ext.getSchemaVersion());
            final Provider<Directory> aggregateReportDir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxAggregateReportDir);
            task.getXmlOutput().convention(aggregateReportDir.get().file("bom.xml"));
            task.getJsonOutput().set(aggregateReportDir.get().file("bom.json"));
            task.getProjectType().set(ext.getProjectType());
            task.getIncludeBomSerialNumber().set(ext.getIncludeBomSerialNumber());
            task.getLicenseChoice().convention(ext.getLicenseChoice());
            task.getIncludeBomSerialNumber().set(ext.getIncludeBomSerialNumber());
            task.getIncludeBuildSystem().set(ext.getIncludeBuildSystem());
            task.getBuildSystemEnvironmentVariable().set(ext.getBuildSystemEnvironmentVariable());
            task.getOrganizationalEntity().convention(ext.getOrganizationalEntity());
            task.getExternalReferences().convention(ext.getExternalReferences());
            task.getIncludeLicenseText().set(ext.getIncludeLicenseText());
            // Wire inputs from configuration files
            Provider<ConfigurableFileCollection> files = project.getProviders()
                    .provider(() -> project.getObjects().fileCollection().from(cyclonedxBomAggregateConfiguration));
            task.getInputSboms().from(files);
        });
    }

    private TaskProvider<CyclonedxDirectTask> registerCyclonedxBomTask(
            final Project project, final CyclonedxBomExtension ext) {
        return project.getTasks().register(cyclonedxDirectTaskName, CyclonedxDirectTask.class, task -> {
            task.getComponentGroup()
                    .set(ext.getComponentGroup().orElse(project.getProviders().provider(() -> project.getGroup()
                            .toString())));
            task.getComponentName().set(ext.getComponentName().orElse(project.getName()));
            task.getComponentVersion()
                    .set(ext.getComponentVersion().orElse(project.getProviders().provider(() -> project.getVersion()
                            .toString())));
            task.getIncludeConfigs().set(ext.getIncludeConfigs());
            task.getSkipConfigs().set(ext.getSkipConfigs());
            task.getSchemaVersion().set(ext.getSchemaVersion());
            task.getIncludeLicenseText().set(ext.getIncludeLicenseText());
            task.getIncludeMetadataResolution().set(ext.getIncludeMetadataResolution());
            final Provider<Directory> dir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxDirectReportDir);
            task.getXmlOutput().convention(dir.get().file("bom.xml"));
            task.getJsonOutput().convention(dir.get().file("bom.json"));
            task.getBuildSystemEnvironmentVariable().set(ext.getBuildSystemEnvironmentVariable());
            task.getOrganizationalEntity().convention(ext.getOrganizationalEntity());
            task.getExternalReferences().convention(ext.getExternalReferences());
            task.getProjectType().set(ext.getProjectType());
            task.getLicenseChoice().convention(ext.getLicenseChoice());
            task.getIncludeBomSerialNumber().set(ext.getIncludeBomSerialNumber());
            task.getIncludeBuildSystem().set(ext.getIncludeBuildSystem());
        });
    }

    private boolean shouldIncludeProject(final Project project, final CyclonedxBomExtension ext) {
        if (!ext.getIncludeProjects().get().isEmpty()
                && !ext.getIncludeProjects().get().contains(project.getPath())
                && !ext.getIncludeProjects().get().contains(project.getName())) {
            return false;
        }
        return !ext.getSkipProjects().get().contains(project.getPath())
                && !ext.getSkipProjects().get().contains(project.getName());
    }
}
