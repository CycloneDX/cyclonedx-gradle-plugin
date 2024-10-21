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
import java.util.*;
import java.util.stream.Collectors;
import org.cyclonedx.gradle.model.ArtifactInfo;
import org.cyclonedx.gradle.model.ResolvedBuild;
import org.cyclonedx.gradle.model.ResolvedConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.Provider;

public class CycloneDxPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getTasks().register("cyclonedxBom", CycloneDxTask.class, (task) -> {
            final ResolvedBuild resolvedBuild = getResolvedBuild(project);
            final Optional<Provider<Set<ArtifactInfo>>> artifacts = getArtifacts(project);
            final File destination =
                    project.getLayout().getBuildDirectory().dir("reports").get().getAsFile();

            task.getResolvedBuild().set(resolvedBuild);
            task.getDestination().set(destination);
            task.setGroup("Reporting");
            task.setDescription("Generates a CycloneDX compliant Software Bill of Materials (SBOM)");
            artifacts.ifPresent(provider -> task.getArtifacts().set(provider));
        });
    }

    private ResolvedBuild getResolvedBuild(final Project project) {

        final ResolvedBuild resolvedBuild = new ResolvedBuild(project.getName());
        project.getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .forEach(v -> resolvedBuild.addProjectConfiguration(resolvedConfiguration(v)));

        project.getChildProjects().forEach((k, v) -> v.getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .forEach(w -> resolvedBuild.addSubProjectConfiguration(k, resolvedConfiguration(w))));

        return resolvedBuild;
    }

    private Optional<Provider<Set<ArtifactInfo>>> getArtifacts(final Project project) {

        return project.getAllprojects().stream()
                .flatMap(v -> v.getConfigurations().stream())
                .filter(Configuration::isCanBeResolved)
                .map(v -> v.getIncoming().getArtifacts().getResolvedArtifacts())
                .reduce(this::combineArtifactsProviders)
                .map(provider ->
                        provider.map(v -> v.stream().map(this::mapResult).collect(Collectors.toSet())));
    }

    private Provider<Set<ResolvedArtifactResult>> combineArtifactsProviders(
            Provider<Set<ResolvedArtifactResult>> left, Provider<Set<ResolvedArtifactResult>> right) {
        return left.flatMap(v -> right.map(w -> {
            Set<ResolvedArtifactResult> result = new HashSet<>();
            result.addAll(v);
            result.addAll(w);
            return result;
        }));
    }

    private ArtifactInfo mapResult(ResolvedArtifactResult result) {
        return new ArtifactInfo(result.getId().getComponentIdentifier().getDisplayName(), result.getFile());
    }

    private ResolvedConfiguration resolvedConfiguration(Configuration config) {
        return new ResolvedConfiguration(
                config.getName(), config.getIncoming().getResolutionResult().getRootComponent());
    }
}
