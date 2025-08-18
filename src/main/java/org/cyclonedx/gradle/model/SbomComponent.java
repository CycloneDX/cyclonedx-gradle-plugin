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
package org.cyclonedx.gradle.model;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.model.License;
import org.jspecify.annotations.Nullable;

public final class SbomComponent implements Serializable {

    private final SbomComponentId id;
    private final Set<ConfigurationScope> inScopeConfigurations;
    private final Set<SbomComponentId> dependencyComponents;

    @Nullable private final File artifactFile;

    @Nullable private final SbomMetaData metaData;

    private final List<License> licenses;

    private SbomComponent(
            final SbomComponentId id,
            final Set<ConfigurationScope> inScopeConfigurations,
            final Set<SbomComponentId> dependencyComponents,
            @Nullable final File artifactFile,
            @Nullable final SbomMetaData metaData,
            final List<License> licenses) {
        this.id = id;
        this.inScopeConfigurations = inScopeConfigurations;
        this.dependencyComponents = dependencyComponents;
        this.artifactFile = artifactFile;
        this.metaData = metaData;
        this.licenses = licenses;
    }

    public SbomComponentId getId() {
        return id;
    }

    public Set<ConfigurationScope> getInScopeConfigurations() {
        return inScopeConfigurations;
    }

    public Set<SbomComponentId> getDependencyComponents() {
        return dependencyComponents;
    }

    public Optional<File> getArtifactFile() {
        return Optional.ofNullable(artifactFile);
    }

    public Optional<SbomMetaData> getSbomMetaData() {
        return Optional.ofNullable(metaData);
    }

    public List<License> getLicenses() {
        return licenses;
    }

    public static class Builder {

        private @Nullable SbomComponentId id;
        private Set<ConfigurationScope> inScopeConfigurations = Collections.emptySet();
        private Set<SbomComponentId> dependencyComponents = Collections.emptySet();
        private @Nullable File artifactFile;
        private @Nullable SbomMetaData metaData;
        private List<License> licenses = Collections.emptyList();

        public Builder() {}

        public Builder withId(final SbomComponentId id) {
            this.id = id;
            return this;
        }

        public Builder withInScopeConfigurations(final Set<ConfigurationScope> inScopeConfigurations) {
            this.inScopeConfigurations = inScopeConfigurations;
            return this;
        }

        public Builder withDependencyComponents(final Set<SbomComponentId> dependencyComponents) {
            this.dependencyComponents = dependencyComponents;
            return this;
        }

        public Builder withArtifactFile(@Nullable final File artifactFile) {
            this.artifactFile = artifactFile;
            return this;
        }

        public Builder withMetaData(@Nullable final SbomMetaData metaData) {
            this.metaData = metaData;
            return this;
        }

        public Builder withLicenses(final List<License> licenses) {
            this.licenses = licenses;
            return this;
        }

        public SbomComponent build() {
            return new SbomComponent(
                    Objects.requireNonNull(id),
                    inScopeConfigurations,
                    dependencyComponents,
                    artifactFile,
                    metaData,
                    licenses);
        }
    }
}
