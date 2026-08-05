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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import org.cyclonedx.Version;
import org.cyclonedx.model.Hash;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HashUtils}. The SHA3 predicate is injected rather than probed so the degraded selection a Java 8
 * build JVM produces is provable on any JVM, instead of only surfacing under {@code testJava8}.
 */
class HashUtilsTest {

    private static boolean allAvailable(final Hash.Algorithm algorithm) {
        return true;
    }

    private static boolean noSha3(final Hash.Algorithm algorithm) {
        return false;
    }

    @Test
    void selectsEightAlgorithmsFrom12Onwards() {
        assertEquals(
                Arrays.asList(
                        Hash.Algorithm.MD5,
                        Hash.Algorithm.SHA1,
                        Hash.Algorithm.SHA_256,
                        Hash.Algorithm.SHA_512,
                        Hash.Algorithm.SHA3_256,
                        Hash.Algorithm.SHA3_512,
                        Hash.Algorithm.SHA_384,
                        Hash.Algorithm.SHA3_384),
                HashUtils.selectAlgorithms(Version.VERSION_16, HashUtilsTest::allAvailable));
    }

    @Test
    void omitsExtendedAlgorithmsBelow12() {
        assertEquals(
                Arrays.asList(
                        Hash.Algorithm.MD5,
                        Hash.Algorithm.SHA1,
                        Hash.Algorithm.SHA_256,
                        Hash.Algorithm.SHA_512,
                        Hash.Algorithm.SHA3_256,
                        Hash.Algorithm.SHA3_512),
                HashUtils.selectAlgorithms(Version.VERSION_11, HashUtilsTest::allAvailable));
    }

    /**
     * The Java 8 case: no SHA3 provider, so those algorithms are dropped rather than aborting SBOM generation.
     */
    @Test
    void dropsSha3WhenUnavailableFrom12Onwards() {
        assertEquals(
                Arrays.asList(
                        Hash.Algorithm.MD5,
                        Hash.Algorithm.SHA1,
                        Hash.Algorithm.SHA_256,
                        Hash.Algorithm.SHA_512,
                        Hash.Algorithm.SHA_384),
                HashUtils.selectAlgorithms(Version.VERSION_16, HashUtilsTest::noSha3));
    }

    @Test
    void dropsSha3WhenUnavailableBelow12() {
        assertEquals(
                Arrays.asList(Hash.Algorithm.MD5, Hash.Algorithm.SHA1, Hash.Algorithm.SHA_256, Hash.Algorithm.SHA_512),
                HashUtils.selectAlgorithms(Version.VERSION_11, HashUtilsTest::noSha3));
    }

    /**
     * Only SHA3 is filtered. A build JVM missing MD5 or SHA-1, such as one in FIPS mode, still fails loudly in Core
     * rather than silently emitting an SBOM without them.
     */
    @Test
    void neverFiltersNonSha3Algorithms() {
        final List<Hash.Algorithm> selected = HashUtils.selectAlgorithms(Version.VERSION_16, HashUtilsTest::noSha3);
        assertTrue(selected.contains(Hash.Algorithm.MD5));
        assertTrue(selected.contains(Hash.Algorithm.SHA1));
        assertTrue(selected.contains(Hash.Algorithm.SHA_256));
        assertTrue(selected.contains(Hash.Algorithm.SHA_384));
        assertTrue(selected.contains(Hash.Algorithm.SHA_512));
    }

    /**
     * Core rejects an algorithm requested below the schema version its {@code VersionFilter} allows, so no selection
     * may contain SHA3-384 before 1.2.
     */
    @Test
    void neverSelectsVersionFilteredAlgorithmsTooEarly() {
        for (final Version version : Version.values()) {
            final List<Hash.Algorithm> selected = HashUtils.selectAlgorithms(version, HashUtilsTest::allAvailable);
            if (version.getVersion() < 1.2) {
                assertFalse(selected.contains(Hash.Algorithm.SHA3_384), version.getVersionString());
                assertFalse(selected.contains(Hash.Algorithm.SHA_384), version.getVersionString());
            } else {
                assertTrue(selected.contains(Hash.Algorithm.SHA3_384), version.getVersionString());
                assertTrue(selected.contains(Hash.Algorithm.SHA_384), version.getVersionString());
            }
        }
    }

    /**
     * The production probe must agree with the JVM actually running this test, at every schema version.
     */
    @Test
    void probesRealAvailability() {
        final boolean sha3Supported = sha3Supported();
        for (final Version version : Version.values()) {
            assertEquals(
                    sha3Supported,
                    HashUtils.selectAlgorithms(version).contains(Hash.Algorithm.SHA3_256),
                    version.getVersionString());
        }
    }

    private static boolean sha3Supported() {
        try {
            MessageDigest.getInstance("SHA3-256");
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
