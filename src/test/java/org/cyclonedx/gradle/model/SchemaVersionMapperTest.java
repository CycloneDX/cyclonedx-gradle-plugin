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

import org.cyclonedx.Version;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link SchemaVersionMapper} reproduces exactly what the old inline {@code SbomBuilder} logic
 * produced for every schema version 1.0 through 1.7:
 *
 * <pre>
 * usesManufacturer      = version.compareTo(Version.VERSION_16) &gt;= 0
 * usesToolInformation   = version.compareTo(Version.VERSION_15) &gt;= 0
 * </pre>
 */
class SchemaVersionMapperTest {

    @Test
    void version10DoesNotUseManufacturerOrToolInformation() {
        assertThresholds(Version.VERSION_10, SchemaVersion.VERSION_10, false, false);
    }

    @Test
    void version11DoesNotUseManufacturerOrToolInformation() {
        assertThresholds(Version.VERSION_11, SchemaVersion.VERSION_11, false, false);
    }

    @Test
    void version12DoesNotUseManufacturerOrToolInformation() {
        assertThresholds(Version.VERSION_12, SchemaVersion.VERSION_12, false, false);
    }

    @Test
    void version13DoesNotUseManufacturerOrToolInformation() {
        assertThresholds(Version.VERSION_13, SchemaVersion.VERSION_13, false, false);
    }

    @Test
    void version14DoesNotUseManufacturerOrToolInformation() {
        assertThresholds(Version.VERSION_14, SchemaVersion.VERSION_14, false, false);
    }

    @Test
    void version15UsesToolInformationButNotManufacturer() {
        assertThresholds(Version.VERSION_15, SchemaVersion.VERSION_15, true, false);
    }

    @Test
    void version16UsesManufacturerAndToolInformation() {
        assertThresholds(Version.VERSION_16, SchemaVersion.VERSION_16, true, true);
    }

    @Test
    void version17UsesManufacturerAndToolInformation() {
        assertThresholds(Version.VERSION_17, SchemaVersion.VERSION_17, true, true);
    }

    @Test
    void everyCoreVersionConstantMapsWithoutThrowing() {
        // Guards against Core adding a new Version constant (e.g. a future 1.8) without this mapper being
        // revisited: every constant Core 13.0.0 defines must round-trip through the mapper without throwing.
        for (final Version coreVersion : Version.values()) {
            final SchemaVersion result = SchemaVersionMapper.from(coreVersion);
            assertEquals(SchemaVersion.valueOf(coreVersion.name()), result);
        }
    }

    private void assertThresholds(
            final Version coreVersion,
            final SchemaVersion expected,
            final boolean usesToolInformation,
            final boolean usesManufacturer) {
        final SchemaVersion result = SchemaVersionMapper.from(coreVersion);

        assertEquals(expected, result);
        assertEquals(usesToolInformation, result.usesToolInformation(), "usesToolInformation");
        assertEquals(usesManufacturer, result.usesManufacturer(), "usesManufacturer");

        if (usesManufacturer) {
            assertTrue(result.usesManufacturer());
        } else {
            assertFalse(result.usesManufacturer());
        }
    }
}
