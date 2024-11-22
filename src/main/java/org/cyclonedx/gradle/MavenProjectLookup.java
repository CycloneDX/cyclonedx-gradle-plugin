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
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

/**
 * Finds the pom.xml of a maven project in the gradle repositories and, if exists, instantiates a MavenProject object
 */
class MavenProjectLookup {

    private final Project project;
    private final Map<ComponentIdentifier, MavenProject> cache;

    MavenProjectLookup(final Project project) {
        this.project = project;
        this.cache = new HashMap<>();
    }

    /**
     * Retrieve the MavenProject instance for the provided component
     *
     * @param result the resolved component for which to find the maven project,
     *               or null if the pom.xml is not found
     *
     * @return a MavenProject instance for this component
     */
    @Nullable MavenProject getResolvedMavenProject(@Nullable final ResolvedComponentResult result) {

        if (result == null) {
            return null;
        }

        if (cache.containsKey(result.getId())) {
            return cache.get(result.getId());
        }

        try {
            final File pomFile = buildMavenProject(result.getId());
            final MavenProject mavenProject = MavenHelper.readPom(pomFile);
            if (mavenProject != null) {
                project.getLogger().debug("CycloneDX: parse queried pom file for component {}", result.getId());
                final Model model = MavenHelper.resolveEffectivePom(pomFile, project);
                if (model != null) {
                    mavenProject.setLicenses(model.getLicenses());
                }

                cache.put(result.getId(), mavenProject);
                return mavenProject;
            }
        } catch (Exception err) {
            project.getLogger().error("Unable to resolve POM for {}", result.getId(), err);
        }
        return null;
    }

    @Nullable File buildMavenProject(final ComponentIdentifier id) {

        final ArtifactResolutionResult result = project.getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(id)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute();

        final Iterator<ComponentArtifactsResult> componentIt =
                result.getResolvedComponents().iterator();
        if (!componentIt.hasNext()) {
            return null;
        }

        final Iterator<ArtifactResult> artifactIt =
                componentIt.next().getArtifacts(MavenPomArtifact.class).iterator();
        if (!artifactIt.hasNext()) {
            return null;
        }

        final ArtifactResult artifact = artifactIt.next();
        if (artifact instanceof ResolvedArtifactResult) {
            project.getLogger().debug("CycloneDX: found pom file for component {}", id);
            final ResolvedArtifactResult resolvedArtifact = (ResolvedArtifactResult) artifact;
            return resolvedArtifact.getFile();
        }

        return null;
    }
}
