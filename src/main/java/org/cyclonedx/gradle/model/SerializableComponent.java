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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SerializableComponent implements Serializable {

    private final String group;
    private final String name;
    private final String version;
    private final File artifactFile;
    private final Set<ConfigurationScope> inScopeConfigurations;

    public SerializableComponent(
            final String group,
            final String name,
            final String version,
            final Set<ConfigurationScope> inScopeConfigurations) {
        this(group, name, version, inScopeConfigurations, null);
    }

    public SerializableComponent(
            final String group,
            final String name,
            final String version,
            final Set<ConfigurationScope> inScopeConfigurations,
            final File artifactFile) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.artifactFile = artifactFile;
        this.inScopeConfigurations = inScopeConfigurations;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Set<ConfigurationScope> getInScopeConfigurations() {
        return inScopeConfigurations;
    }

    public Optional<File> getArtifactFile() {
        return Optional.ofNullable(artifactFile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializableComponent that = (SerializableComponent) o;
        return Objects.equals(group, that.group)
                && Objects.equals(name, that.name)
                && Objects.equals(version, that.version)
                && Objects.equals(artifactFile, that.artifactFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version, artifactFile);
    }
}
