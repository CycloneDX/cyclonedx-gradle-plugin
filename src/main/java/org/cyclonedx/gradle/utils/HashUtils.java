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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.cyclonedx.Version;
import org.cyclonedx.model.Hash;

/**
 * Selects the artifact hash algorithms a Direct SBOM carries.
 *
 * <p>The set is declared here rather than inherited from {@code BomUtils.calculateHashes(File, Version)} so that a
 * cyclonedx-core-java upgrade cannot silently move the SBOM Output Contract. See ADR 0007.
 *
 * <p>The SHA3 family is filtered by availability because Java 8 ships no SHA3 provider. Core 12.1.0 replaced the
 * defensive acquisition that used to skip those algorithms with one that throws, which aborts SBOM generation outright
 * on a Java 8 build JVM. Only SHA3 is filtered: the remaining algorithms are requested unguarded, so a build JVM that
 * cannot provide them still fails loudly instead of emitting an SBOM whose missing hashes are indistinguishable from
 * ones the schema never asked for.
 */
public class HashUtils {

    /**
     * Algorithms carried at every schema version.
     */
    private static final List<Hash.Algorithm> BASE_ALGORITHMS = Collections.unmodifiableList(Arrays.asList(
            Hash.Algorithm.MD5,
            Hash.Algorithm.SHA1,
            Hash.Algorithm.SHA_256,
            Hash.Algorithm.SHA_512,
            Hash.Algorithm.SHA3_256,
            Hash.Algorithm.SHA3_512));

    /**
     * Algorithms carried in addition from schema version 1.2 onwards.
     */
    private static final List<Hash.Algorithm> EXTENDED_ALGORITHMS =
            Collections.unmodifiableList(Arrays.asList(Hash.Algorithm.SHA_384, Hash.Algorithm.SHA3_384));

    /**
     * Selects the algorithms available to this build JVM for the given schema version.
     *
     * @param schemaVersion the CycloneDX schema version the SBOM is generated against
     *
     * @return the algorithms to request hashes for, in the order they are computed
     */
    public static List<Hash.Algorithm> selectAlgorithms(final Version schemaVersion) {
        return selectAlgorithms(schemaVersion, HashUtils::isAvailable);
    }

    /**
     * Selects the algorithms for the given schema version, testing SHA3 availability with the supplied predicate.
     *
     * @param schemaVersion the CycloneDX schema version the SBOM is generated against
     * @param available tests whether this build JVM can provide an algorithm
     *
     * @return the algorithms to request hashes for, in the order they are computed
     */
    static List<Hash.Algorithm> selectAlgorithms(
            final Version schemaVersion, final Predicate<Hash.Algorithm> available) {

        final List<Hash.Algorithm> candidates = new ArrayList<>(BASE_ALGORITHMS);
        if (schemaVersion.getVersion() >= 1.2) {
            candidates.addAll(EXTENDED_ALGORITHMS);
        }

        final List<Hash.Algorithm> selected = new ArrayList<>(candidates.size());
        for (final Hash.Algorithm candidate : candidates) {
            if (!isSha3(candidate) || available.test(candidate)) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private static boolean isSha3(final Hash.Algorithm algorithm) {
        return algorithm == Hash.Algorithm.SHA3_256
                || algorithm == Hash.Algorithm.SHA3_384
                || algorithm == Hash.Algorithm.SHA3_512;
    }

    private static boolean isAvailable(final Hash.Algorithm algorithm) {
        try {
            MessageDigest.getInstance(algorithm.getSpec());
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
