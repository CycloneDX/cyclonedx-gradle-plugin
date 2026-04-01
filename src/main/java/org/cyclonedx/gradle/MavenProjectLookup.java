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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.jspecify.annotations.Nullable;

/**
 * Finds the pom.xml of a maven project in the gradle repositories and, if exists, instantiates a MavenProject object
 */
class MavenProjectLookup {

    private static final Logger LOGGER = Logging.getLogger(MavenProjectLookup.class);
    private final Project project;
    private final Map<ComponentIdentifier, MavenProject> cache;
    private final Map<ComponentIdentifier, File> pomFileCache;
    private final GradleAssistedMavenModelResolverImpl modelResolver;

    MavenProjectLookup(final Project project) {
        this.project = project;
        this.cache = new HashMap<>();
        this.pomFileCache = new HashMap<>();
        this.modelResolver = new GradleAssistedMavenModelResolverImpl(project);
    }

    /**
     * Resolves POM files for all provided component identifiers in a single batch query.
     * This is significantly faster than resolving them one at a time, as it avoids
     * N individual Gradle artifact resolution API calls.
     *
     * @param componentIds the component identifiers to resolve POM files for
     */
    void batchResolvePomFiles(final Collection<ComponentIdentifier> componentIds) {
        if (componentIds.isEmpty()) {
            return;
        }

        LOGGER.info("CycloneDX: Batch resolving {} POM files", componentIds.size());
        resolvePomFiles(componentIds);
        LOGGER.info(
                "CycloneDX: Batch resolved {} POM files out of {} requested", pomFileCache.size(), componentIds.size());
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
                LOGGER.debug("CycloneDX: parse queried pom file for component {}", result.getId());
                final Model model = MavenHelper.resolveEffectivePom(pomFile, modelResolver);
                if (model != null) {
                    mavenProject.setLicenses(model.getLicenses());
                }

                cache.put(result.getId(), mavenProject);
                return mavenProject;
            }
        } catch (Exception err) {
            LOGGER.error("Unable to resolve POM for {}", result.getId(), err);
        }
        return null;
    }

    @Nullable File buildMavenProject(final ComponentIdentifier id) {
        if (!pomFileCache.containsKey(id)) {
            resolvePomFiles(Collections.singletonList(id));
        }
        return pomFileCache.get(id);
    }

    private void resolvePomFiles(final Collection<ComponentIdentifier> componentIds) {
        for (final ComponentArtifactsResult componentResult : project.getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute()
                .getResolvedComponents()) {
            for (final ArtifactResult artifact : componentResult.getArtifacts(MavenPomArtifact.class)) {
                if (artifact instanceof ResolvedArtifactResult) {
                    pomFileCache.put(componentResult.getId(), ((ResolvedArtifactResult) artifact).getFile());
                    LOGGER.debug("CycloneDX: found pom file for component {}", componentResult.getId());
                }
            }
        }
    }
}
