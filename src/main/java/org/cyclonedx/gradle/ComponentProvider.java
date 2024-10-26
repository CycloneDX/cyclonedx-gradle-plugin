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

import java.util.concurrent.Callable;
import org.cyclonedx.gradle.model.SerializableComponents;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class ComponentProvider implements Callable<SerializableComponents> {

    private final Project project;

    public ComponentProvider(final Project project) {
        this.project = project;
    }

    @Override
    public SerializableComponents call() throws Exception {

        final CycloneDxDependencyTraverser traverser =
                new CycloneDxDependencyTraverser(project.getLogger(), new CycloneDxBomBuilder(project.getLogger()));

        traverseParentProject(traverser);
        traverseChildProjects(traverser);
        registerArtifacts(traverser);

        return traverser.serializableComponents();
    }

    private void traverseParentProject(final CycloneDxDependencyTraverser traverser) {
        project.getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .forEach(config -> traverser.traverseParentGraph(
                        config.getIncoming().getResolutionResult().getRoot(), project.getName(), config.getName()));
    }

    private void traverseChildProjects(final CycloneDxDependencyTraverser traverser) {
        project.getChildProjects().forEach((k, v) -> v.getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .forEach(config -> traverser.traverseChildGraph(
                        config.getIncoming().getResolutionResult().getRoot(), k, config.getName())));
    }

    private void registerArtifacts(final CycloneDxDependencyTraverser traverser) {
        project.getAllprojects().stream()
                .flatMap(project -> project.getConfigurations().stream())
                .filter(Configuration::isCanBeResolved)
                .forEach(config -> config.getIncoming().getArtifacts().getArtifacts().stream()
                        .forEach(artifact -> traverser.registerArtifact(
                                artifact.getId().getComponentIdentifier(), artifact.getFile())));
    }
}
