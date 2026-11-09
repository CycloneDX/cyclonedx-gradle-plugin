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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.gradle.model.SbomGraph;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parsed Maven models must not remain reachable from the provider after graph construction. */
class SbomGraphProviderTest {

    private Project project;
    private CyclonedxDirectTask task;
    private MavenProjectLookup lookup;

    @BeforeEach
    void setUp() {
        assumeTrue(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17));
        project = ProjectBuilder.builder().withName("test-project").build();
        project.setGroup("com.test");
        project.setVersion("1.0.0");
        task = project.getTasks().create("testSbom", CyclonedxDirectTask.class);
        task.getComponentGroup().set("com.test");
        task.getComponentName().set("test-project");
        task.getComponentVersion().set("1.0.0");
        lookup = new MavenProjectLookup(project);
    }

    @Test
    void releasesParsedModelsButPreservesPomEvidence() throws Exception {
        final Map<ComponentIdentifier, MavenMetadata> cache = seedModelCache();
        final File pom = new File(project.getProjectDir(), "dependency.pom");
        final File parentPom = new File(project.getProjectDir(), "parent.pom");
        lookup.recordPomFile(pom);
        lookup.recordPomFile(parentPom);
        assertFalse(cache.isEmpty());

        final SbomGraph graph = provider(Collections.emptyList()).call();

        assertTrue(cache.isEmpty(), "Parsed Maven models must be released before the SBOM task executes");
        assertEquals(lookup.getPomFiles(), graph.getPomFiles());
        assertEquals(2, graph.getPomFiles().size());
        lookup.recordPomFile(new File(project.getProjectDir(), "later.pom"));
        assertEquals(2, graph.getPomFiles().size(), "The cached graph must retain its own evidence snapshot");
    }

    @Test
    void reusesTheCompletedGraphWithoutResolvingAgain() throws Exception {
        final Map<ComponentIdentifier, MavenMetadata> cache = seedModelCache();
        final AtomicInteger traversals = new AtomicInteger();
        final Iterable<Configuration> configurations = () -> {
            traversals.incrementAndGet();
            return Collections.<Configuration>emptyList().iterator();
        };
        final SbomGraphProvider provider = provider(configurations);

        final SbomGraph first = provider.call();
        final int traversalsAfterConstruction = traversals.get();
        final SbomGraph second = provider.call();

        assertSame(first, second);
        assertTrue(traversalsAfterConstruction > 0);
        assertEquals(traversalsAfterConstruction, traversals.get(), "The completed graph must not be rebuilt");
        assertTrue(cache.isEmpty());
    }

    @Test
    void releasesParsedModelsWhenGraphConstructionFails() throws Exception {
        final Map<ComponentIdentifier, MavenMetadata> cache = seedModelCache();
        final IllegalStateException failure = new IllegalStateException("Dependency resolution failed");
        final Iterable<Configuration> configurations = () -> {
            throw failure;
        };
        final ClassLoader original = Thread.currentThread().getContextClassLoader();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> provider(configurations)
                .call()));

        assertTrue(cache.isEmpty(), "A failed graph must not leave parsed Maven models attached to the task");
        assertSame(original, Thread.currentThread().getContextClassLoader());
    }

    private SbomGraphProvider provider(final Iterable<Configuration> configurations) {
        return new SbomGraphProvider(
                () -> "com.test",
                project.getName(),
                () -> "1.0.0",
                project.getPath(),
                project.getDisplayName(),
                configurations,
                Collections.emptyList(),
                lookup,
                task);
    }

    @SuppressWarnings("unchecked")
    private Map<ComponentIdentifier, MavenMetadata> seedModelCache() throws Exception {
        // Inspect the owning cache directly: GC timing would make a reachability regression test nondeterministic.
        final Field cacheField = MavenProjectLookup.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        final Map<ComponentIdentifier, MavenMetadata> cache =
                (Map<ComponentIdentifier, MavenMetadata>) cacheField.get(lookup);
        final ComponentIdentifier id = () -> "com.test:dependency:1.0.0";
        cache.put(id, MavenMetadata.of(new MavenProject(new Model())));
        return cache;
    }
}
