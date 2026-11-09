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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The description of the Resolved Dependency Graph is part of the build cache key, and for a project component, which
 * has no artifact among the inputs, it is the only part that tells one graph from another. Two graphs that yield
 * different documents must therefore never be described alike, and one graph must be described alike however its
 * sets happen to be ordered.
 */
class SbomGraphResolvedDependencyGraphTest {

    private static final SbomComponentId APP = new SbomComponentId("com.example", "app", "1.0", null, ":app");
    private static final SbomComponentId LIB = new SbomComponentId("com.example", "lib", "1.0", null, ":lib");
    private static final SbomComponentId EXTERNAL = new SbomComponentId("com.test", "external", "1.0.0", "jar", null);

    @Test
    void aComponentWhoseVersionChangesIsDescribedDifferently() {
        SbomComponentId bumped = new SbomComponentId("com.example", "lib", "2.0", null, ":lib");

        assertNotEquals(
                describe(component(APP, ids(LIB)), component(LIB, ids())),
                describe(component(APP, ids(bumped)), component(bumped, ids())));
    }

    @Test
    void aProjectThatMovesToAnotherPathIsDescribedDifferently() {
        // The coordinates are the same, and the path is what the package URL of a project component carries
        SbomComponentId moved = new SbomComponentId("com.example", "lib", "1.0", null, ":libs:lib");

        assertNotEquals(
                describe(component(APP, ids(LIB)), component(LIB, ids())),
                describe(component(APP, ids(moved)), component(moved, ids())));
    }

    @Test
    void aDependencyOnAProjectAtAnotherPathIsDescribedDifferently() {
        // Two projects can share their coordinates and so share one component, while the reference a dependent
        // writes into the document still carries the path of the project it was resolved to
        SbomComponentId elsewhere = new SbomComponentId("com.example", "lib", "1.0", null, ":other:lib");

        assertNotEquals(
                describe(component(APP, ids(LIB)), component(LIB, ids())),
                describe(component(APP, ids(elsewhere)), component(LIB, ids())));
    }

    @Test
    void aRelationshipBetweenTwoComponentsAlreadyPresentIsPartOfTheDescription() {
        assertNotEquals(
                describe(component(APP, ids(LIB, EXTERNAL)), component(LIB, ids()), component(EXTERNAL, ids())),
                describe(
                        component(APP, ids(LIB, EXTERNAL)), component(LIB, ids(EXTERNAL)), component(EXTERNAL, ids())));
    }

    @Test
    void aConfigurationThatStopsContributingAComponentIsPartOfTheDescription() {
        assertNotEquals(
                describe(
                        component(APP, ids(EXTERNAL)),
                        component(EXTERNAL, ids(), "runtimeClasspath", "testRuntimeClasspath")),
                describe(component(APP, ids(EXTERNAL)), component(EXTERNAL, ids(), "testRuntimeClasspath")));
    }

    @Test
    void theOrderOfDependenciesAndConfigurationsDoesNotMatter() {
        assertEquals(
                describe(
                        component(APP, ids(LIB, EXTERNAL), "compileClasspath", "runtimeClasspath"),
                        component(LIB, ids()),
                        component(EXTERNAL, ids())),
                describe(
                        component(APP, ids(EXTERNAL, LIB), "runtimeClasspath", "compileClasspath"),
                        component(EXTERNAL, ids()),
                        component(LIB, ids())));
    }

    @Test
    void aConfigurationContributingAComponentTwiceIsDescribedOnce() {
        // A project configuration and a build script configuration can share a name, and both can be in scope
        assertEquals(
                describe(component(APP, ids(), "runtimeClasspath", "runtimeClasspath")),
                describe(component(APP, ids(), "runtimeClasspath")));
    }

    @Test
    void aSeparatorInsideAConfigurationNameCannotPoseAsAnotherConfiguration() {
        assertNotEquals(
                describe(component(APP, ids(), "a\nconfiguration=b")), describe(component(APP, ids(), "a", "b")));
    }

    @Test
    void aSeparatorInsideAProjectPathCannotPoseAsAConfiguration() {
        SbomComponentId pathCarriesConfiguration =
                new SbomComponentId("com.example", "app", "1.0", null, ":app\nconfiguration=a");

        assertNotEquals(describe(component(pathCarriesConfiguration, ids())), describe(component(APP, ids(), "a")));
    }

    @Test
    void aComponentThatIsNotAProjectDiffersFromAProjectWithAnEmptyPath() {
        assertNotEquals(
                describe(component(new SbomComponentId("com.example", "app", "1.0", null, null), ids())),
                describe(component(new SbomComponentId("com.example", "app", "1.0", null, ""), ids())));
    }

    @Test
    void everyComponentHasAnEntryEvenWithNothingToSayAboutIt() {
        Map<String, String> description = describe(component(APP, ids(EXTERNAL)), component(EXTERNAL, ids()));

        assertEquals(2, description.size());
    }

    @Test
    void aRootComponentThatIsNotInTheGraphHasAnEntry() {
        // A project whose own component the traversal did not find gets a root built from the task configuration
        Map<SbomComponentId, SbomComponent> graph = new HashMap<>();
        graph.put(LIB, component(LIB, ids()));

        Map<String, String> atOnePath = new SbomGraph(graph, component(APP, ids())).getResolvedDependencyGraph();
        Map<String, String> atAnotherPath = new SbomGraph(
                        graph, component(new SbomComponentId("com.example", "app", "1.0", null, ":other"), ids()))
                .getResolvedDependencyGraph();

        assertEquals(2, atOnePath.size());
        assertNotEquals(atOnePath, atAnotherPath);
    }

    /** Describes a graph whose root is the first of the given components. */
    private static Map<String, String> describe(final SbomComponent... components) {
        Map<SbomComponentId, SbomComponent> graph = new HashMap<>();
        for (final SbomComponent component : components) {
            graph.put(component.getId(), component);
        }
        return new SbomGraph(graph, components[0]).getResolvedDependencyGraph();
    }

    private static SbomComponent component(
            final SbomComponentId id, final Set<SbomComponentId> dependencies, final String... configurations) {
        return new SbomComponent.Builder()
                .withId(id)
                .withDependencyComponents(dependencies)
                .withInScopeConfigurations(Arrays.stream(configurations)
                        .map(configuration -> new ConfigurationScope("app", configuration))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .build();
    }

    private static Set<SbomComponentId> ids(final SbomComponentId... ids) {
        return ids.length == 0 ? Collections.emptySet() : new LinkedHashSet<>(Arrays.asList(ids));
    }
}
