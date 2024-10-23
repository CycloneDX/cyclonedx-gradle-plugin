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
import java.util.Map;
import java.util.Set;
import org.cyclonedx.gradle.model.ResolvedArtifacts;
import org.cyclonedx.gradle.model.ResolvedBuild;
import org.cyclonedx.gradle.model.ResolvedConfiguration;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class CycloneDxTask extends DefaultTask {

    private final CycloneDxDependencyTraverser traverser;

    public CycloneDxTask() {
        this.traverser = new CycloneDxDependencyTraverser(getLogger(), new CycloneDxBomBuilder(getLogger()));
    }

    @Input
    public abstract Property<ResolvedBuild> getResolvedBuild();

    @Input
    public abstract Property<File> getDestination();

    @Input
    public abstract Property<ResolvedArtifacts> getArtifacts();

    @TaskAction
    public void createBom() {

        final ResolvedBuild resolvedBuild = getResolvedBuild().get();

        registerArtifacts();
        buildParentDependencies(resolvedBuild.getProjectName(), resolvedBuild.getProjectConfigurations());
        buildChildDependencies(resolvedBuild.getSubProjectsConfigurations());

        File destination = new File(getDestination().get(), "bom.json");
        CycloneDxUtils.writeBom(traverser.toBom(), destination);
    }

    private void buildParentDependencies(final String projectName, Set<ResolvedConfiguration> configurations) {
        configurations.forEach(config -> traverser.traverseParentGraph(
                config.getDependencyGraph().get(), projectName, config.getConfigurationName()));
    }

    private void buildChildDependencies(final Map<String, Set<ResolvedConfiguration>> configurations) {
        configurations.forEach((key, value) -> value.forEach(config ->
                traverser.traverseChildGraph(config.getDependencyGraph().get(), key, config.getConfigurationName())));
    }

    private void registerArtifacts() {
        getArtifacts().get().getArtifacts().forEach(v -> v.get().getInfoSet().forEach(traverser::registerArtifact));
    }
}
