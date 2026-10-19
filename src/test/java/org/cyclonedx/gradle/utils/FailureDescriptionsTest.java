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

import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.junit.jupiter.api.Test;

class FailureDescriptionsTest {

    @Test
    void theMessageIsKeptVerbatim() {
        assertEquals(
                "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n  - https://repo/x.pom",
                FailureDescriptions.describe(
                        new RuntimeException(
                                "Could not find com.test:missing:1.0.0.\nSearched in the following locations:\n  - https://repo/x.pom")));
    }

    @Test
    void aFailureWithoutAMessageIsNamedByItsType() {
        assertEquals("java.lang.IllegalStateException", FailureDescriptions.describe(new IllegalStateException()));
        assertEquals("java.lang.IllegalStateException", FailureDescriptions.describe(new IllegalStateException("  ")));
    }

    @Test
    void theCauseChainIsListedBeneathTheMessageWithItsNesting() {
        // Gradle's resolve exceptions carry the reason, a refused connection or a 401, in their causes only
        Throwable failure = new RuntimeException(
                "Could not resolve com.test:x:1.0.0.\nRequired by:\n    root project :",
                new RuntimeException(
                        "Could not GET 'http://repo/x.pom'.",
                        new RuntimeException("Connection refused\nby the proxy")));

        assertEquals(
                "Could not resolve com.test:x:1.0.0.\n"
                        + "Required by:\n"
                        + "    root project :\n"
                        + "> Could not GET 'http://repo/x.pom'.\n"
                        + "  > Connection refused\n"
                        + "    by the proxy",
                FailureDescriptions.describe(failure));
    }

    @Test
    void everyCauseOfAFailureWithSeveralCausesIsListed() {
        // One failure per repository asked, which getCause() alone would reduce to the first
        Throwable failure = new DefaultMultiCauseException(
                "Could not resolve com.test:x:1.0.0.",
                new RuntimeException("Could not GET 'https://a/x.pom'."),
                new RuntimeException("Could not GET 'https://b/x.pom'."));

        assertEquals(
                "Could not resolve com.test:x:1.0.0.\n"
                        + "> Could not GET 'https://a/x.pom'.\n"
                        + "> Could not GET 'https://b/x.pom'.",
                FailureDescriptions.describe(failure));
    }

    @Test
    void aCauseThatRepeatsTheFirstLineOfItsParentIsNotListedTwice() {
        // Gradle hands over a copy of the resolve failure whose message carries the paths that required the
        // dependency, with the bare failure as its cause and the real reason beneath that
        Throwable failure = new RuntimeException(
                "Could not resolve com.test:x:1.0.0.\nRequired by:\n    root project :",
                new RuntimeException("Could not resolve com.test:x:1.0.0.", new RuntimeException("Read timed out")));

        assertEquals(
                "Could not resolve com.test:x:1.0.0.\nRequired by:\n    root project :\n> Read timed out",
                FailureDescriptions.describe(failure));
    }

    @Test
    void aCycleOfCausesEnds() {
        RuntimeException a = new RuntimeException("m");
        RuntimeException b = new RuntimeException("m", a);
        a.initCause(b);

        assertEquals("m", FailureDescriptions.describe(a));
    }
}
