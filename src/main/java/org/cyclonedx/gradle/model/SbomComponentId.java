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

import java.io.Serializable;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public class SbomComponentId implements Serializable {

    private final String group;
    private final String name;
    private final String version;

    @Nullable private final String type;

    @Nullable private final String gradleProjectPath;

    public SbomComponentId(
            final String group,
            final String name,
            final String version,
            @Nullable final String type,
            @Nullable final String gradleProjectPath) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.type = type;
        this.gradleProjectPath = gradleProjectPath;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getGroup() {
        return group;
    }

    @Nullable public String getType() {
        return type;
    }

    @Nullable public String getGradleProjectPath() {
        return gradleProjectPath;
    }

    public TreeMap<String, String> getQualifiers() {
        final TreeMap<String, String> result = new TreeMap<>();
        if (gradleProjectPath != null) {
            result.put("project_path", gradleProjectPath);
        }
        if (type != null) {
            result.put("type", type);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SbomComponentId that = (SbomComponentId) o;
        return Objects.equals(group, that.group)
                && Objects.equals(name, that.name)
                && Objects.equals(version, that.version)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version, type);
    }

    @Override
    public String toString() {
        return "SbomComponentId{" + "group='"
                + group + '\'' + ", name='"
                + name + '\'' + ", version='"
                + version + '\'' + ", type='"
                + type + '\'' + ", gradleProjectPath='"
                + gradleProjectPath + '\'' + '}';
    }
}
