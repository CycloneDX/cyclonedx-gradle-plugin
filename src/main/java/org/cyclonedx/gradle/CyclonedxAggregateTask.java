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
import java.util.*;
import java.util.stream.Collectors;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.gradle.model.ArtifactExclusion;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CyclonedxUtils;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.BomReference;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.parsers.BomParserFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

@CacheableTask
public abstract class CyclonedxAggregateTask extends BaseCyclonedxTask {

    private static final Logger LOGGER = Logging.getLogger(CyclonedxAggregateTask.class);

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getInputSboms();

    protected final Provider<String> getProjectPath =
            getProject().getProviders().provider(() -> getProject().getPath());

    @TaskAction
    public void aggregate() throws Exception {
        logParameters();
        final List<ArtifactExclusion> exclusions =
                getExcludeArtifacts().get().stream().map(ArtifactExclusion::new).collect(Collectors.toList());
        final Bom merged = mergeAll(getInputSboms().getFiles(), exclusions);
        LOGGER.info("{} Writing BOM", LOG_PREFIX);
        if (getJsonOutput().isPresent()) {
            CyclonedxUtils.writeJsonBom(
                    getSchemaVersion().get(),
                    merged,
                    getJsonOutput().getAsFile().get());
        }
        if (getXmlOutput().isPresent()) {
            CyclonedxUtils.writeXmlBom(
                    getSchemaVersion().get(), merged, getXmlOutput().getAsFile().get());
        }
    }

    private Bom mergeAll(final Set<File> files, final List<ArtifactExclusion> exclusions) throws ParseException {
        LOGGER.info("{} Received files: {}", LOG_PREFIX, files);
        final Bom aggregateBom = getRootProjectBom();
        final Map<String, Component> componentsByBomRef = new TreeMap<>();
        final Map<String, Set<String>> dependenciesByBomRef = new TreeMap<>();
        for (final File subProjectBomFile : files) {
            final Bom subProjectBom =
                    BomParserFactory.createParser(subProjectBomFile).parse(subProjectBomFile);
            // merge components of all BOMs
            if (!aggregateBom
                    .getMetadata()
                    .getComponent()
                    .getBomRef()
                    .equals(subProjectBom.getMetadata().getComponent().getBomRef())) {
                // if the root project BOM main component is not the same as the sub-project BOM main component,
                // add the sub-project main component to the map
                // to avoid duplicating the root project component
                if (exclusions.stream()
                        .noneMatch(exclusion -> exclusion.matches(DependencyUtils.toComponentIdentifier(
                                subProjectBom.getMetadata().getComponent())))) {
                    LOGGER.info(
                            "{} Adding sub-project component:[{}] {}",
                            LOG_PREFIX,
                            aggregateBom.getMetadata().getComponent().getBomRef(),
                            subProjectBom.getMetadata().getComponent().getBomRef());
                    componentsByBomRef.putIfAbsent(
                            subProjectBom.getMetadata().getComponent().getBomRef(),
                            subProjectBom.getMetadata().getComponent());
                }
            }
            if (subProjectBom.getComponents() == null) {
                continue; // no components in this BOM
            }
            for (final Component component : subProjectBom.getComponents()) {
                if (exclusions.stream()
                        .noneMatch(exclusion -> exclusion.matches(DependencyUtils.toComponentIdentifier(component)))) {
                    componentsByBomRef.putIfAbsent(component.getBomRef(), component);
                }
            }
            // merge dependencies of all BOMs
            if (subProjectBom.getDependencies() == null) {
                continue; // no dependencies in this BOM
            }
            for (final Dependency dependency : subProjectBom.getDependencies()) {
                final String bomRef = dependency.getRef();
                if (dependency.getDependencies() == null || bomRef == null) {
                    continue;
                }
                // Only keep dependency if the ref is not excluded
                if (componentsByBomRef.containsKey(bomRef)
                        || aggregateBom.getMetadata().getComponent().getBomRef().equals(bomRef)) {
                    dependenciesByBomRef.compute(bomRef, (key, existingDeps) -> {
                        final List<String> nextLevelDeps = dependency.getDependencies().stream()
                                .map(BomReference::getRef)
                                .filter(ref -> !ref.equals(key))
                                .filter(ref -> componentsByBomRef.containsKey(ref)
                                        || aggregateBom
                                                .getMetadata()
                                                .getComponent()
                                                .getBomRef()
                                                .equals(ref))
                                .collect(Collectors.toList());
                        if (existingDeps == null) {
                            return new TreeSet<>(nextLevelDeps);
                        } else {
                            existingDeps.addAll(nextLevelDeps);
                            return existingDeps;
                        }
                    });
                }
            }
        }
        aggregateBom.setComponents(new ArrayList<>(componentsByBomRef.values()));
        aggregateBom.setDependencies(dependenciesByBomRef.entrySet().stream()
                .map(entry -> {
                    final Dependency dependency = new Dependency(entry.getKey());
                    dependency.setDependencies(
                            entry.getValue().stream().map(Dependency::new).collect(Collectors.toList()));
                    return dependency;
                })
                .collect(Collectors.toList()));
        return aggregateBom;
    }

    private Bom getRootProjectBom() {
        final SbomComponent updatedRootComponent = new SbomComponent.Builder()
                .withId(new SbomComponentId(
                        getComponentGroup().get(),
                        getComponentName().get(),
                        getComponentVersion().get(),
                        null,
                        getProjectPath.get()))
                .build();
        return new SbomBuilder<>(this).buildBom(new SbomGraph(Collections.emptyMap(), updatedRootComponent));
    }

    private void logParameters() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("CycloneDX Aggregate: Parameters");
            LOGGER.info("------------------------------------------------------------------------");
            LOGGER.info("schemaVersion             : {}", getSchemaVersion().get());
            LOGGER.info(
                    "includeLicenseText        : {}", getIncludeLicenseText().get());
            LOGGER.info(
                    "includeBomSerialNumber    : {}",
                    getIncludeBomSerialNumber().get());
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
