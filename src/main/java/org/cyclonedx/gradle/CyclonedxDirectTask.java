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

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CyclonedxUtils;
import org.cyclonedx.model.Bom;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * This task mainly acts a container for the user configurations (includeConfigs, projectType, schemaVersion, ...)
 * and orchestrating the calls between the core objects (SbomGraphProvider and SbomBuilder)
 */
@CacheableTask
public abstract class CyclonedxDirectTask extends BaseCyclonedxTask {

    private static final Logger LOGGER = Logging.getLogger(CyclonedxDirectTask.class);

    /**
     * The list of configuration names to include in the BOM.
     * If not set, all configurations will be included.
     * Regex patterns can be used to match multiple configurations.
     *
     * @return the list of configuration names to include
     */
    @Input
    public abstract ListProperty<String> getIncludeConfigs();

    /**
     * The list of configuration names to skip in the BOM.
     * If not set, no configurations will be skipped.
     * Regex patterns can be used to match multiple configurations.
     *
     * @return the list of configuration names to skip
     */
    @Input
    public abstract ListProperty<String> getSkipConfigs();

    /**
     * Whether to include metadata resolution in the BOM. For example, license information.
     * If not set, it defaults to true.
     *
     * @return true if metadata resolution should be included, false otherwise
     */
    @Input
    public abstract Property<Boolean> getIncludeMetadataResolution();

    /**
     * Whether to include the build environment dependencies (e.g. from buildscript) in the BOM.
     * If not set, it defaults to false.
     *
     * @return true if build environment dependencies should be included, false otherwise
     */
    @Input
    public abstract Property<Boolean> getIncludeBuildEnvironment();

    /**
     * The resolved dependency files from all in-scope configurations.
     * This is used for up-to-date checking and caching - when dependencies change
     * (new dependency, version change, or dynamic version resolution), the task
     * will be re-executed instead of being marked UP-TO-DATE.
     *
     * @return the resolved dependency files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResolvedDependencies();

    private final Provider<SbomGraph> componentsProvider;

    public CyclonedxDirectTask() {
        getIncludeConfigs().convention(new ArrayList<>());
        getSkipConfigs().convention(new ArrayList<>());
        getIncludeMetadataResolution().convention(true);
        getIncludeBuildEnvironment().convention(false);
        this.componentsProvider = getProject()
                .getProviders()
                .provider(new SbomGraphProvider(
                        () -> getProject().getGroup().toString(),
                        getProject().getName(),
                        () -> getProject().getVersion().toString(),
                        getProject().getPath(),
                        getProject().getDisplayName(),
                        getProject().getConfigurations(),
                        getProject().getBuildscript().getConfigurations(),
                        new MavenProjectLookup(getProject()),
                        this));

        // Wire resolved dependencies for cache invalidation
        // Derives input files from the same SbomGraph used for BOM generation,
        // avoiding a second dependency resolution pass
        getResolvedDependencies().from(componentsProvider.map(graph -> graph.getGraph().values().stream()
                .map(SbomComponent::getArtifactFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet())));
    }

    /**
     * The name of the aggregate configuration to exclude from the BOM.
     * This is internal and set by the plugin.
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getAggregateConfigurationName();

    /**
     * Executes the main logic of the plugin by loading the dependency graph (SbomGraphProvider.get())
     * and providing the result to SbomBuilder
     */
    @TaskAction
    public void createBom() {
        logParameters();
        final Bom bom = new SbomBuilder<>(this).buildBom(componentsProvider.get());
        LOGGER.info("{} Writing BOM", LOG_PREFIX);
        if (getJsonOutput().isPresent()) {
            CyclonedxUtils.writeJsonBom(
                    getSchemaVersion().get(), bom, getJsonOutput().getAsFile().get());
        }
        if (getXmlOutput().isPresent()) {
            CyclonedxUtils.writeXmlBom(
                    getSchemaVersion().get(), bom, getXmlOutput().getAsFile().get());
        }
    }

    private void logParameters() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("CycloneDX: Parameters");
            LOGGER.info("------------------------------------------------------------------------");
            LOGGER.info("schemaVersion             : {}", getSchemaVersion().get());
            LOGGER.info(
                    "includeLicenseText        : {}", getIncludeLicenseText().get());
            LOGGER.info(
                    "includeBomSerialNumber    : {}",
                    getIncludeBomSerialNumber().get());
            LOGGER.info("includeConfigs            : {}", getIncludeConfigs().get());
            LOGGER.info("skipConfigs               : {}", getSkipConfigs().get());
            LOGGER.info(
                    "includeMetadataResolution : {}",
                    getIncludeMetadataResolution().get());
            LOGGER.info(
                    "includeBuildEnvironment   : {}",
                    getIncludeBuildEnvironment().get());
            LOGGER.info("jsonOutput                : {}", getJsonOutput().getOrNull());
            LOGGER.info("xmlOutput                 : {}", getXmlOutput().getOrNull());
            LOGGER.info("componentGroup            : {}", getComponentGroup().get());
            LOGGER.info("componentName             : {}", getComponentName().get());
            LOGGER.info("componentVersion          : {}", getComponentVersion().get());
            LOGGER.info("projectType               : {}", getProjectType().get());
            LOGGER.info("------------------------------------------------------------------------");
        }
    }
}
