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
package org.cyclonedx.gradle.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.cyclonedx.gradle.model.ConfigurationScope;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.junit.jupiter.api.Test;

class DependencyUtilsTest {

    @Test
    void testShouldMergeSimpleGraphs() {

        final SbomComponent componentA = buildDefaultComponent("A", "B");
        final SbomComponent componentC = buildDefaultComponent("C", "D");

        final Map<SbomComponentId, SbomComponent> graphA = new HashMap<>();
        graphA.put(componentA.getId(), componentA);

        final Map<SbomComponentId, SbomComponent> graphC = new HashMap<>();
        graphC.put(componentC.getId(), componentC);

        final Map<SbomComponentId, SbomComponent> resultGraph = DependencyUtils.mergeGraphs(graphA, graphC);

        final Map<SbomComponentId, SbomComponent> expectedGraph = new HashMap<>();
        expectedGraph.put(componentA.getId(), componentA);
        expectedGraph.put(componentC.getId(), componentC);

        assertEquals(expectedGraph, resultGraph);
    }

    private SbomComponent buildDefaultComponent(final String componentSuffix, final String dependencySuffix) {
        return new SbomComponent.Builder()
                .withId(new SbomComponentId("group" + componentSuffix, "component" + componentSuffix, "1.0.0", "jar"))
                .withDependencyComponents(buildDependencyComponents(new SbomComponentId(
                        "group" + dependencySuffix, "component" + dependencySuffix, "1.0.0", "jar")))
                .withInScopeConfigurations(buildInScopeConfigurations(new ConfigurationScope("projectA", "configA")))
                .withLicenses(Collections.EMPTY_LIST)
                .build();
    }

    private Set<SbomComponentId> buildDependencyComponents(final SbomComponentId... ids) {
        final Set<SbomComponentId> componentIds = new HashSet<>();
        Collections.addAll(componentIds, ids);
        return componentIds;
    }

    private Set<ConfigurationScope> buildInScopeConfigurations(final ConfigurationScope... configs) {
        final Set<ConfigurationScope> componentConfigs = new HashSet<>();
        Collections.addAll(componentConfigs, configs);
        return componentConfigs;
    }
}
