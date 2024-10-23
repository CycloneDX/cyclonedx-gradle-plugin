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

import java.util.HashSet;
import java.util.Set;

public class ArtifactInfoSet {

    private final Set<ArtifactInfo> infoSet;

    public ArtifactInfoSet() {
        this.infoSet = new HashSet<>();
    }

    public void addInfo(final ArtifactInfo info) {
        this.infoSet.add(info);
    }

    public Set<ArtifactInfo> getInfoSet() {
        return infoSet;
    }
}
