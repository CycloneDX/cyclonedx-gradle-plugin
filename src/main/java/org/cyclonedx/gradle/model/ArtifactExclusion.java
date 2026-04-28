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
import java.util.regex.Pattern;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class ArtifactExclusion implements Serializable {

    private final Pattern groupPattern;
    private final Pattern namePattern;
    private final Pattern versionPattern;

    public ArtifactExclusion(final String exclusion) {
        final String[] parts = exclusion.split(":");
        this.groupPattern = createPattern(parts.length > 0 ? parts[0] : "*");
        this.namePattern = createPattern(parts.length > 1 ? parts[1] : "*");
        this.versionPattern = createPattern(parts.length > 2 ? parts[2] : "*");
    }

    private Pattern createPattern(final String part) {
        return Pattern.compile(part);
    }

    public boolean matches(final String group, final String name, final String version) {
        return groupPattern.matcher(Objects.toString(group, "")).matches()
                && namePattern.matcher(Objects.toString(name, "")).matches()
                && versionPattern.matcher(Objects.toString(version, "")).matches();
    }

    public boolean matches(final ComponentIdentifier componentIdentifier) {
        String group = "";
        String name = componentIdentifier.getDisplayName();
        String version = "";

        if (componentIdentifier instanceof ModuleComponentIdentifier) {
            final ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) componentIdentifier;
            group = moduleComponentIdentifier.getGroup();
            name = moduleComponentIdentifier.getModule();
            version = moduleComponentIdentifier.getVersion();
        }

        return matches(group, name, version);
    }
}
