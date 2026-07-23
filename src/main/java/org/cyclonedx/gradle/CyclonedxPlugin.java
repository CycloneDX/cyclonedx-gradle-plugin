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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Entrypoint of the plugin.
 *
 * Needs to be applied per project: Each project that should contribute to an
 * aggregate BOM applies this plugin itself and publishes its Direct BOM as a consumable variant
 * via the {@code cyclonedxDirectBom} configuration.
 *
 * A project that wants to aggregate BOMs of other projects declares its members on its own
 * resolvable {@code cyclonedxAggregation} configuration, e.g.:
 *
 * dependencies {
 *     cyclonedxAggregation project(":app-a")
 *     cyclonedxAggregation project(":app-b")
 * }
 *
 * This mirrors Gradle's own {@code jacoco-report-aggregation} /
 * {@code test-report-aggregation} plugins and is both configuration-cache-safe and
 * Project-Isolation-safe.
 *
 * Format selection: a member disables an output format by calling
 * {@code xmlOutput.unsetConvention()} or {@code jsonOutput.unsetConvention()} on its own
 * {@code cyclonedxDirectBom} task.
 */
public class CyclonedxPlugin implements Plugin<Project> {

    private static final Logger LOGGER = Logging.getLogger(CyclonedxPlugin.class);

    public static final String LOG_PREFIX = "[CycloneDX]";

    protected final String cyclonedxDirectTaskName;
    protected final String cyclonedxDirectConfigurationName;
    protected final String cyclonedxAggregateTaskName;
    protected final String cyclonedxAggregationConfigurationName;
    protected final String cyclonedxDirectReportDir;
    protected final String cyclonedxAggregateReportDir;

    @Inject
    public CyclonedxPlugin() {
        this(
                "cyclonedxDirectBom",
                "cyclonedxDirectBom",
                "cyclonedxBom",
                "cyclonedxAggregation",
                "reports/cyclonedx-direct",
                "reports/cyclonedx");
    }

    protected CyclonedxPlugin(
            final String cyclonedxDirectTaskName,
            final String cyclonedxDirectConfigurationName,
            final String cyclonedxAggregateTaskName,
            final String cyclonedxAggregationConfigurationName,
            final String cyclonedxDirectReportDir,
            final String cyclonedxAggregateReportDir) {
        this.cyclonedxDirectTaskName = cyclonedxDirectTaskName;
        this.cyclonedxDirectConfigurationName = cyclonedxDirectConfigurationName;
        this.cyclonedxAggregateTaskName = cyclonedxAggregateTaskName;
        this.cyclonedxAggregationConfigurationName = cyclonedxAggregationConfigurationName;
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

        // Per-project Direct BOM publishing
        configureDirectBomPublishing(project);

        configureAggregator(project);
    }

    /**
     * creates the Direct BOM task and the consumable
     * {@code cyclonedxDirectBom} configuration, and wires the task outputs as artifacts.
     */
    private Configuration configureDirectBomPublishing(final Project project) {
        final Configuration outgoing = project.getConfigurations().maybeCreate(cyclonedxDirectConfigurationName);
        outgoing.setCanBeConsumed(true);
        outgoing.setCanBeResolved(false);
        outgoing.setDescription("CycloneDX Direct BOM published by this project");

        final TaskProvider<CyclonedxDirectTask> directTask = registerCyclonedxDirectBomTask(project);

        // Lazy, conditional artifact registration.
        outgoing.getArtifacts().addAllLater(project.provider(() -> buildArtifacts(directTask, "xml", "bom")));
        outgoing.getArtifacts().addAllLater(project.provider(() -> buildArtifacts(directTask, "json", "bom")));

        return outgoing;
    }

    /**
     * Returns either an empty list (when the corresponding output property is absent) or a
     * one-element list containing a {@link PublishArtifact} for the direct task's output file.
     */
    private static List<PublishArtifact> buildArtifacts(
            final TaskProvider<CyclonedxDirectTask> directTask, final String extension, final String artifactName) {
        final Provider<RegularFile> output = "xml".equals(extension)
                ? directTask.flatMap(CyclonedxDirectTask::getXmlOutput)
                : directTask.flatMap(CyclonedxDirectTask::getJsonOutput);
        if (output.getOrNull() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ConditionalPublishArtifact(artifactName, extension, directTask, output));
    }

    /**
     * Sets up the resolvable {@code cyclonedxAggregation} configuration and the aggregate task.
     */
    private void configureAggregator(final Project project) {
        final Configuration aggregation =
                project.getConfigurations().maybeCreate(cyclonedxAggregationConfigurationName);
        aggregation.setCanBeResolved(true);
        aggregation.setCanBeConsumed(false);
        aggregation.setTransitive(false);
        aggregation.setDescription("Declares the projects whose CycloneDX Direct BOMs should be aggregated by "
                + cyclonedxAggregateTaskName);

        // For the user's convenience, any project dependency added without an explicit target configuration is
        // resolved against the ${cyclonedxDirectConfigurationName}-variant.
        aggregation.getDependencies().withType(ProjectDependency.class).configureEach(dep -> {
            if (dep.getTargetConfiguration() == null) {
                dep.setTargetConfiguration(cyclonedxDirectConfigurationName);
            }
        });
        project.getDependencies()
                .add(
                        cyclonedxAggregationConfigurationName,
                        project.getDependencies()
                                .project(ImmutableMap.of(
                                        "path", project.getPath(), "configuration", cyclonedxDirectConfigurationName)));

        // Fail-guard: cyclonedxAggregation accepts only project(...) members. External module
        // dependencies would silently resolve to unrelated artifacts.
        aggregation.getDependencies().whenObjectAdded(dep -> {
            if (!(dep instanceof ProjectDependency)) {
                throw new InvalidUserDataException(
                        LOG_PREFIX + " cyclonedxAggregation only accepts project(...) dependencies, got: " + dep);
            }
        });

        registerCyclonedxAggregateBomTask(project, aggregation);
    }

    private TaskProvider<CyclonedxDirectTask> registerCyclonedxDirectBomTask(final Project project) {
        if (project.getTasks().getNames().contains(cyclonedxDirectTaskName)) {
            LOGGER.info(
                    "{} Task [{}] already exists in project [{}], reusing",
                    LOG_PREFIX,
                    cyclonedxDirectTaskName,
                    project.getDisplayName());
            return project.getTasks().named(cyclonedxDirectTaskName, CyclonedxDirectTask.class);
        }
        return project.getTasks().register(cyclonedxDirectTaskName, CyclonedxDirectTask.class, task -> {
            final Provider<Directory> dir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxDirectReportDir);
            task.getXmlOutput().convention(dir.map(d -> d.file("bom.xml")));
            task.getJsonOutput().convention(dir.map(d -> d.file("bom.json")));
            task.getAggregateConfigurationName().convention(cyclonedxAggregationConfigurationName);
        });
    }

    private void registerCyclonedxAggregateBomTask(
            final Project project, final Configuration aggregationConfiguration) {

        project.getTasks().register(cyclonedxAggregateTaskName, CyclonedxAggregateTask.class, task -> {
            final Provider<Directory> aggregateReportDir =
                    project.getLayout().getBuildDirectory().dir(cyclonedxAggregateReportDir);
            task.getXmlOutput().convention(aggregateReportDir.map(d -> d.file("bom.xml")));
            task.getJsonOutput().convention(aggregateReportDir.map(d -> d.file("bom.json")));
            task.getProjectPath().set(project.getPath());

            final ArtifactCollection artifacts =
                    aggregationConfiguration.getIncoming().getArtifacts();
            task.getInputSbomFiles().from(artifacts.getArtifactFiles());
            task.getResolvedMemberArtifacts().set(artifacts.getResolvedArtifacts());

            task.getDeclaredMemberPaths()
                    .set(project.provider(() -> aggregationConfiguration.getAllDependencies().stream()
                            .filter(ProjectDependency.class::isInstance)
                            .map(ProjectDependency.class::cast)
                            .map(CyclonedxPlugin::getProjectPath)
                            .sorted()
                            .collect(Collectors.toList())));
        });
    }

    /**
     * Unfortunately, getDependencyProject() is deprecated in Gradle 8.11 and removed in Gradle 9.
     * While on the other hand org.gradle.api.artifacts.ProjectDependency.getPath() was introduced in Gradle 8.11
     */
    private static String getProjectPath(ProjectDependency dependency) {
        try {
            // Gradle 8.11+
            return (String) ProjectDependency.class.getMethod("getPath").invoke(dependency);
        } catch (ReflectiveOperationException ignored) {
            try {
                // Gradle < 8.11
                Object project = ProjectDependency.class
                        .getMethod("getDependencyProject")
                        .invoke(dependency);

                return (String) project.getClass().getMethod("getPath").invoke(project);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to determine project dependency path", exception);
            }
        }
    }
}
