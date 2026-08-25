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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.gradle.model.UnresolvedMetadata;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.UnresolvedArtifactResult;
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
    private final Map<ComponentIdentifier, MavenMetadata> cache;

    MavenProjectLookup(final Project project) {
        this.project = project;
        this.cache = new HashMap<>();
    }

    /**
     * Retrieve the Maven metadata for the provided component: the MavenProject, the reason there is none,
     * or a project along with the reason it is incomplete. Every path that falls short names itself, because
     * the result on its own cannot say whether the component declares no metadata or whether it was unreadable.
     *
     * @param result the resolved component to find the maven project for
     *
     * @return the metadata for this component
     */
    MavenMetadata lookup(final ResolvedComponentResult result) {

        final MavenMetadata cached = cache.get(result.getId());
        if (cached != null) {
            return cached;
        }

        final MavenMetadata metadata = resolve(result);
        cache.put(result.getId(), metadata);
        return metadata;
    }

    private MavenMetadata resolve(final ResolvedComponentResult result) {

        final File pomFile = findPomFile(result.getId());
        if (pomFile == null) {
            return MavenMetadata.unresolved(UnresolvedMetadata.POM_UNRESOLVED);
        }

        final MavenProject mavenProject;
        try {
            mavenProject = MavenHelper.readPom(pomFile);
        } catch (Exception err) {
            LOGGER.warn("{} Unable to read POM {} of component {}", LOG_PREFIX, pomFile, result.getId(), err);
            return MavenMetadata.unresolved(UnresolvedMetadata.POM_UNPARSEABLE);
        }
        if (mavenProject == null) {
            LOGGER.warn("{} POM {} of component {} is not readable", LOG_PREFIX, pomFile, result.getId());
            return MavenMetadata.unresolved(UnresolvedMetadata.POM_UNPARSEABLE);
        }

        LOGGER.debug("{} Parse queried pom file for component {}", LOG_PREFIX, result.getId());
        final Model model;
        try {
            model = MavenHelper.resolveEffectivePom(pomFile, project);
        } catch (Exception err) {
            LOGGER.warn(
                    "{} Unable to build the effective model of component {}, reporting what its own POM declares",
                    LOG_PREFIX,
                    result.getId(),
                    err);
            // What was read stands: a component's own declarations replace what it would inherit instead of
            // merging with it, so they are complete even where the rest of the model is not
            return MavenMetadata.incomplete(mavenProject, unresolvedModel(mavenProject, err));
        }
        if (model != null) {
            mavenProject.setLicenses(model.getLicenses());
        }

        return MavenMetadata.of(mavenProject);
    }

    /**
     * The POM the repositories hold for this component, or null when they hold none this build can use.
     * Gradle answers the query with the reason, which the caller cannot see, so it is logged here.
     */
    @Nullable File findPomFile(final ComponentIdentifier id) {

        final ArtifactResolutionResult result = project.getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(id)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute();

        final Iterator<ComponentArtifactsResult> componentIt =
                result.getResolvedComponents().iterator();
        if (!componentIt.hasNext()) {
            LOGGER.warn("{} No Maven component resolved for {}, its metadata is unavailable", LOG_PREFIX, id);
            return null;
        }

        final Iterator<ArtifactResult> artifactIt =
                componentIt.next().getArtifacts(MavenPomArtifact.class).iterator();
        if (!artifactIt.hasNext()) {
            LOGGER.warn("{} Component {} carries no POM artifact, its metadata is unavailable", LOG_PREFIX, id);
            return null;
        }

        final ArtifactResult artifact = artifactIt.next();
        if (artifact instanceof ResolvedArtifactResult) {
            LOGGER.debug("{} Found pom file for component {}", LOG_PREFIX, id);
            final ResolvedArtifactResult resolvedArtifact = (ResolvedArtifactResult) artifact;
            return resolvedArtifact.getFile();
        }

        LOGGER.warn(
                "{} Unable to resolve the POM of component {}, its metadata is unavailable, because {}",
                LOG_PREFIX,
                id,
                describe(artifact));
        return null;
    }

    /**
     * Which input the incomplete model is attributed to. Maven does not report which of the model's inputs
     * failed, so the parent is named only where the reported failure carries the coordinates the POM declares
     * for it. Anything else, an import among them, is reported as a model that could not be completed.
     */
    private static UnresolvedMetadata unresolvedModel(final MavenProject mavenProject, final Exception err) {
        final Parent parent = mavenProject.getModel().getParent();
        if (parent != null && namesParent(err, parent)) {
            return UnresolvedMetadata.PARENT_UNRESOLVED;
        }
        return UnresolvedMetadata.MODEL_INCOMPLETE;
    }

    private static boolean namesParent(@Nullable final Throwable err, final Parent parent) {
        final String coordinates = parent.getGroupId() + ":" + parent.getArtifactId() + ":" + parent.getVersion();
        for (Throwable cause = err; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(coordinates)) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }

    /**
     * What Gradle reported about an artifact it could not resolve. Kept out of the SBOM: the reason is a
     * free-form message that can carry local paths, while the document records only that it happened.
     */
    private static String describe(final ArtifactResult artifact) {
        if (artifact instanceof UnresolvedArtifactResult) {
            final Throwable failure = ((UnresolvedArtifactResult) artifact).getFailure();
            return failure.getMessage() != null ? failure.getMessage() : failure.toString();
        }
        return artifact.getClass().getSimpleName();
    }
}
