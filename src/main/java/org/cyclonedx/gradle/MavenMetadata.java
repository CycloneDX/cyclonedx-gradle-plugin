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

import org.apache.maven.project.MavenProject;
import org.cyclonedx.gradle.model.UnresolvedMetadata;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of looking up a component's Maven metadata: what was read, why it is incomplete, or both.
 *
 * <p>The reason travels with the project because an empty or partial result on its own cannot say whether
 * the component declares no metadata or whether it could not be read.
 */
final class MavenMetadata {

    @Nullable private final MavenProject project;

    @Nullable private final UnresolvedMetadata unresolved;

    private MavenMetadata(@Nullable final MavenProject project, @Nullable final UnresolvedMetadata unresolved) {
        this.project = project;
        this.unresolved = unresolved;
    }

    static MavenMetadata of(final MavenProject project) {
        return new MavenMetadata(project, null);
    }

    static MavenMetadata unresolved(final UnresolvedMetadata unresolved) {
        return new MavenMetadata(null, unresolved);
    }

    /** What was read, along with why it is not the whole story. */
    static MavenMetadata incomplete(final MavenProject project, final UnresolvedMetadata unresolved) {
        return new MavenMetadata(project, unresolved);
    }

    @Nullable MavenProject getProject() {
        return project;
    }

    @Nullable UnresolvedMetadata getUnresolved() {
        return unresolved;
    }
}
