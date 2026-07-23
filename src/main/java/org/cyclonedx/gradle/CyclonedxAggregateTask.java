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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CyclonedxUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.BomReference;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.parsers.BomParserFactory;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class CyclonedxAggregateTask extends BaseCyclonedxTask {

    private static final Logger LOGGER = Logging.getLogger(CyclonedxAggregateTask.class);

    /**
     * Resolved BOM files, contributed by declared aggregation members.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputSbomFiles();

    /**
     * Resolved artifacts with their component identifiers.
     */
    @Internal
    public abstract SetProperty<ResolvedArtifactResult> getResolvedMemberArtifacts();

    /**
     * Path of the project that owns this aggregate task. Used to build the root component of the
     * aggregate BOM.
     */
    @Input
    public abstract Property<String> getProjectPath();

    /**
     * Snapshot of the project paths declared on {@code cyclonedxAggregation}.
     */
    @Input
    public abstract ListProperty<String> getDeclaredMemberPaths();

    @TaskAction
    public void aggregate() throws Exception {
        logParameters();

        final Map<String, List<File>> filesByMember = new TreeMap<>();
        for (final ResolvedArtifactResult a : getResolvedMemberArtifacts().get()) {
            if (a.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                final String path = ((ProjectComponentIdentifier) a.getId().getComponentIdentifier()).getProjectPath();
                filesByMember.computeIfAbsent(path, k -> new ArrayList<>()).add(a.getFile());
            }
        }

        final List<String> declared = getDeclaredMemberPaths().get();
        checkDeclaredMembersPublishedArtifacts(declared, filesByMember);
        checkForMissingInputSboms(filesByMember);

        final Bom merged;
        try {
            merged = mergeAll(filesByMember);
        } catch (ParseException e) {
            throw new GradleException(
                    LOG_PREFIX + " Failed to parse a member BOM. Aggregation aborted to avoid "
                            + "silent under-reporting of the SBOM (a security-consumed artifact). "
                            + "Declared members: " + declared,
                    e);
        }

        LOGGER.info("{} Writing aggregate BOM (members: {})", LOG_PREFIX, declared);
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

    private static void checkDeclaredMembersPublishedArtifacts(
            final List<String> declared, final Map<String, List<File>> filesByMember) {
        final List<String> membersWithoutArtifacts = declared.stream()
                .filter(p ->
                        !filesByMember.containsKey(p) || filesByMember.get(p).isEmpty())
                .collect(Collectors.toList());
        if (!membersWithoutArtifacts.isEmpty()) {
            throw new GradleException(LOG_PREFIX + " Declared aggregation members produced no Direct BOM artifacts: "
                    + membersWithoutArtifacts
                    + ". Each member must apply the cyclonedx plugin with an enabled cyclonedxDirectBom task "
                    + "and publish at least one output format (xmlOutput or jsonOutput). "
                    + "Aggregation aborted to avoid silent under-reporting of the SBOM.");
        }
    }

    private void checkForMissingInputSboms(final Map<String, List<File>> filesByMember) {
        final Map<String, List<File>> missingByMember = new TreeMap<>();
        for (final Map.Entry<String, List<File>> entry : filesByMember.entrySet()) {
            for (final File file : entry.getValue()) {
                if (!file.exists()) {
                    missingByMember
                            .computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .add(file);
                }
            }
        }

        if (missingByMember.isEmpty()) {
            return;
        }

        final StringBuilder message = new StringBuilder();
        message.append(LOG_PREFIX)
                .append(" Aggregate BOM task '")
                .append(getPath())
                .append("' expected Direct BOM files that do not exist:\n\n");
        for (final Map.Entry<String, List<File>> entry : missingByMember.entrySet()) {
            for (final File file : entry.getValue()) {
                message.append("  - project '")
                        .append(entry.getKey())
                        .append("': ")
                        .append(file.getPath())
                        .append("\n");
            }
        }
        message.append("\nA Direct BOM can be missing when its producing cyclonedxDirectBom task was skipped ")
                .append("at execution time. Aggregation is aborted to avoid silent under-reporting of the SBOM. ")
                .append("If this project should not contribute to the Aggregate BOM, ")
                .append("disable its producing task with `enabled = false` or remove it from cyclonedxAggregation.");

        throw new GradleException(message.toString());
    }

    private Bom mergeAll(final Map<String, List<File>> filesByMember) throws ParseException {
        LOGGER.info("{} Merging BOMs from members: {}", LOG_PREFIX, filesByMember.keySet());
        final Bom aggregateBom = getRootProjectBom();
        final String rootRef = aggregateBom.getMetadata().getComponent().getBomRef();
        final Map<String, Component> componentsByBomRef = new TreeMap<>();
        final Map<String, Set<String>> dependenciesByBomRef = new TreeMap<>();

        final Set<String> rootEdges = dependenciesByBomRef.computeIfAbsent(rootRef, k -> new TreeSet<>());

        for (final Map.Entry<String, List<File>> entry : filesByMember.entrySet()) {
            final File chosen = pickPreferredFile(entry.getValue());
            final Bom subProjectBom = BomParserFactory.createParser(chosen).parse(chosen);
            final String memberRef = subProjectBom.getMetadata().getComponent().getBomRef();
            if (!memberRef.equals(rootRef)) {
                rootEdges.add(memberRef);
            }
            mergeInto(aggregateBom, subProjectBom, componentsByBomRef, dependenciesByBomRef);
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

    private static File pickPreferredFile(final List<File> files) {
        return files.stream()
                .filter(f -> f.getName().endsWith(".json"))
                .findFirst()
                .orElse(files.get(0));
    }

    private void mergeInto(
            final Bom aggregateBom,
            final Bom subProjectBom,
            final Map<String, Component> componentsByBomRef,
            final Map<String, Set<String>> dependenciesByBomRef) {

        // Merge main component of sub-project unless it duplicates the aggregate root.
        if (!aggregateBom
                .getMetadata()
                .getComponent()
                .getBomRef()
                .equals(subProjectBom.getMetadata().getComponent().getBomRef())) {
            LOGGER.info(
                    "{} Adding sub-project component: root=[{}] sub=[{}]",
                    LOG_PREFIX,
                    aggregateBom.getMetadata().getComponent().getBomRef(),
                    subProjectBom.getMetadata().getComponent().getBomRef());
            componentsByBomRef.putIfAbsent(
                    subProjectBom.getMetadata().getComponent().getBomRef(),
                    subProjectBom.getMetadata().getComponent());
        }

        if (subProjectBom.getComponents() != null) {
            for (final Component component : subProjectBom.getComponents()) {
                componentsByBomRef.putIfAbsent(component.getBomRef(), component);
            }
        }

        if (subProjectBom.getDependencies() == null) {
            return;
        }
        for (final Dependency dependency : subProjectBom.getDependencies()) {
            final String bomRef = dependency.getRef();
            if (dependency.getDependencies() == null || bomRef == null) {
                continue;
            }
            dependenciesByBomRef.compute(bomRef, (key, existingDeps) -> {
                final List<String> nextLevelDeps = dependency.getDependencies().stream()
                        .map(BomReference::getRef)
                        .filter(ref -> !ref.equals(key))
                        .collect(Collectors.toList());
                if (existingDeps == null) {
                    return new TreeSet<>(nextLevelDeps);
                }
                existingDeps.addAll(nextLevelDeps);
                return existingDeps;
            });
        }
    }

    private Bom getRootProjectBom() {
        final SbomComponent updatedRootComponent = new SbomComponent.Builder()
                .withId(new SbomComponentId(
                        getComponentGroup().get(),
                        getComponentName().get(),
                        getComponentVersion().get(),
                        null,
                        getProjectPath().get()))
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
            LOGGER.info(
                    "declaredMembers           : {}", getDeclaredMemberPaths().get());
            LOGGER.info("------------------------------------------------------------------------");
        }
    }
}
