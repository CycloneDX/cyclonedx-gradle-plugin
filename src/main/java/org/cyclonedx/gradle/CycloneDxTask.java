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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.cyclonedx.gradle.model.ArtifactInfo;
import org.cyclonedx.gradle.model.ResolvedBuild;
import org.cyclonedx.gradle.model.ResolvedConfiguration;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class CycloneDxTask extends DefaultTask {

    private final CycloneDxParser parser;

    public CycloneDxTask() {
        this.parser = new CycloneDxParser(getLogger());
    }

    @Input
    public abstract Property<ResolvedBuild> getResolvedBuild();

    @Input
    public abstract Property<File> getDestination();

    @Input
    public abstract SetProperty<ArtifactInfo> getArtifacts();

    @TaskAction
    public void createBom() {

        final ResolvedBuild resolvedBuild = getResolvedBuild().get();
        final Map<String, Set<ResolvedConfiguration>> configurations = new HashMap<>();
        configurations.put(resolvedBuild.getProjectName(), resolvedBuild.getProjectConfigurations());
        configurations.putAll(resolvedBuild.getSubProjectsConfigurations());

        registerArtifacts();
        buildDependencies(configurations);

        File destination = new File(getDestination().get(), "bom.json");
        CycloneDxUtils.writeBom(parser.getResultingBom(), destination);
    }

    private void buildDependencies(final Map<String, Set<ResolvedConfiguration>> configurations) {
        configurations.entrySet().forEach(project -> project.getValue()
                .forEach(config -> parser.visitGraph(
                        config.getDependencyGraph().get(), project.getKey(), config.getConfigurationName())));
    }

    private void registerArtifacts() {
        getArtifacts().get().forEach(parser::registerArtifact);
    }
}
