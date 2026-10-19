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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class UnresolvedDependenciesTest {

    private static final String HEADER =
            "The SBOM of project ':' cannot describe the build, these dependencies did not resolve:\n";

    @Test
    void isEmptyUntilAFailureIsRecorded() {
        UnresolvedDependencies unresolved = new UnresolvedDependencies();

        assertTrue(unresolved.isEmpty());

        unresolved.record("Could not find com.test:missing:1.0.0.", "runtimeClasspath");

        assertFalse(unresolved.isEmpty());
    }

    @Test
    void theSameFailureInSeveralConfigurationsIsOneEntryNamingAllOfThem() {
        // Gradle reports the same failure once per scanned configuration, and only the first lookup says where it
        // searched: the later ones are answered from its cache without that block
        String description = "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n"
                + "  - https://repo/missing-1.0.0.pom\nRequired by:\n    root project :";
        String fromCache = "Could not find com.test:missing:1.0.0.\nRequired by:\n    root project :";
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record(description, "runtimeClasspath");
        unresolved.record(fromCache, "compileClasspath");

        assertEquals(1, unresolved.getFailures().size());
        assertEquals(
                "Could not find com.test:missing:1.0.0.",
                unresolved.getFailures().get(0).getSummary());
        assertEquals(
                new TreeSet<>(Arrays.asList("compileClasspath", "runtimeClasspath")),
                unresolved.getFailures().get(0).getConfigurations());
        assertEquals(
                "The SBOM of project ':app' cannot describe the build, these dependencies did not resolve:\n"
                        + "  - Could not find com.test:missing:1.0.0.\n"
                        + "    Searched in the following locations:\n"
                        + "      - https://repo/missing-1.0.0.pom\n"
                        + "    Required by:\n"
                        + "        root project :\n"
                        + "    (compileClasspath, runtimeClasspath)",
                unresolved.describe(":app"));
    }

    @Test
    void theTextWithTheMostContextIsKeptWhicheverCameFirst() {
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record("Could not find com.test:missing:1.0.0.", "compileClasspath");
        unresolved.record(
                "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n  - https://repo/x.pom",
                "runtimeClasspath");

        assertEquals(
                HEADER + "  - Could not find com.test:missing:1.0.0.\n"
                        + "    Searched in the following locations:\n"
                        + "      - https://repo/x.pom\n"
                        + "    (compileClasspath, runtimeClasspath)",
                unresolved.describe(":"));
    }

    @Test
    void aFailureWithSeveralReasonsIsOneEntryListingEachReasonWithItsConfigurations() {
        // The same module can fail for different reasons in different configurations; neither reason may be lost,
        // and neither may be attributed to a configuration that did not see it
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record(
                "Could not resolve com.test:x:1.0.0.\n> Could not GET 'https://a/x.pom'.", "compileClasspath");
        unresolved.record("Could not resolve com.test:x:1.0.0.\n> Read timed out", "runtimeClasspath");

        assertEquals(1, unresolved.getFailures().size());
        assertEquals(
                HEADER + "  - Could not resolve com.test:x:1.0.0.\n"
                        + "    > Could not GET 'https://a/x.pom'.\n"
                        + "    (compileClasspath)\n"
                        + "    > Read timed out\n"
                        + "    (runtimeClasspath)",
                unresolved.describe(":"));
        assertEquals(
                new TreeSet<>(Arrays.asList("compileClasspath", "runtimeClasspath")),
                unresolved.getFailures().get(0).getConfigurations());
    }

    @Test
    void locationsSearchedForOneConfigurationOnlyStayWithThatConfiguration() {
        // Repositories can be limited to configurations, so the same missing module is searched in different places
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record(
                "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n  - file:/ra/missing.pom",
                "compileClasspath");
        unresolved.record(
                "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n  - file:/rb/missing.pom",
                "runtimeClasspath");

        assertEquals(
                HEADER + "  - Could not find com.test:missing:1.0.0.\n"
                        + "    Searched in the following locations:\n"
                        + "      - file:/ra/missing.pom\n"
                        + "    (compileClasspath)\n"
                        + "    Searched in the following locations:\n"
                        + "      - file:/rb/missing.pom\n"
                        + "    (runtimeClasspath)",
                unresolved.describe(":"));
    }

    @Test
    void aReasonThatContainsAnEarlierOneAbsorbsIt() {
        // After a first failure Gradle disables the repository, and later lookups wrap the earlier error beneath
        // the notice; that is the same reason with more context, not a second one
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record(
                "Could not resolve com.test:x:1.0.0.\n> Could not GET 'https://a/x.pom'.\n  > Connection refused",
                "compileClasspath");
        unresolved.record(
                "Could not resolve com.test:x:1.0.0.\n> Repository maven is disabled due to earlier error below:\n"
                        + "  > Could not GET 'https://a/x.pom'.\n    > Connection refused",
                "runtimeClasspath");

        assertEquals(
                HEADER + "  - Could not resolve com.test:x:1.0.0.\n"
                        + "    > Repository maven is disabled due to earlier error below:\n"
                        + "      > Could not GET 'https://a/x.pom'.\n"
                        + "        > Connection refused\n"
                        + "    (compileClasspath, runtimeClasspath)",
                unresolved.describe(":"));
    }

    @Test
    void distinctFailuresAreListedInTheOrderTheyWereRecorded() {
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record("Could not find nojar-1.0.0.jar (com.test:nojar:1.0.0).", "runtimeClasspath");
        unresolved.record("Could not find com.test:missing:1.0.0.", "runtimeClasspath");

        assertEquals(
                HEADER + "  - Could not find nojar-1.0.0.jar (com.test:nojar:1.0.0).\n"
                        + "    (runtimeClasspath)\n"
                        + "  - Could not find com.test:missing:1.0.0.\n"
                        + "    (runtimeClasspath)",
                unresolved.describe(":"));
    }

    @Test
    void trailingLineBreaksOfADescriptionDoNotProduceEmptyLines() {
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record("Could not find com.test:missing:1.0.0.\n\n", "runtimeClasspath");

        assertEquals(
                HEADER + "  - Could not find com.test:missing:1.0.0.\n" + "    (runtimeClasspath)",
                unresolved.describe(":"));
    }

    @Test
    void theLinesOfAFailureCarryItsSummaryEveryReasonAndTheirConfigurations() {
        UnresolvedDependencies unresolved = new UnresolvedDependencies();
        unresolved.record("Could not resolve com.test:x:1.0.0.\n> Read timed out", "runtimeClasspath");

        assertEquals(
                Arrays.asList("Could not resolve com.test:x:1.0.0.", "> Read timed out", "(runtimeClasspath)"),
                unresolved.getFailures().get(0).lines());
        assertEquals("runtimeClasspath", unresolved.getFailures().get(0).configurationNames());
    }
}
