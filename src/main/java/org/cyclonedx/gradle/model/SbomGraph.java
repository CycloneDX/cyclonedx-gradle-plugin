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
import java.util.Map;

public class SbomGraph implements Serializable {

    private final Map<SbomComponentId, SbomComponent> graph;
    private final SbomComponent rootComponent;

    public SbomGraph(final Map<SbomComponentId, SbomComponent> graph, final SbomComponent rootComponent) {
        this.graph = graph;
        this.rootComponent = rootComponent;
    }

    public Map<SbomComponentId, SbomComponent> getGraph() {
        return graph;
    }

    public SbomComponent getRootComponent() {
        return rootComponent;
    }
}
