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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimestampUtilsTest {

    private static final long INSTANT_MILLIS = 1767323045000L; // 2026-01-02T03:04:05Z

    @Test
    void parsesAnIso8601InstantInUtc() {
        assertEquals(
                INSTANT_MILLIS, TimestampUtils.parse("2026-01-02T03:04:05Z").getTime());
    }

    @Test
    void parsesAnIso8601InstantWithAnOffset() {
        assertEquals(
                INSTANT_MILLIS,
                TimestampUtils.parse("2026-01-02T04:04:05+01:00").getTime());
    }

    @Test
    void parsesSecondsSinceTheEpoch() {
        assertEquals(INSTANT_MILLIS, TimestampUtils.parse("1767323045").getTime());
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertEquals(INSTANT_MILLIS, TimestampUtils.parse(" 1767323045\n").getTime());
        assertEquals(
                INSTANT_MILLIS, TimestampUtils.parse("2026-01-02T03:04:05Z\n").getTime());
    }

    @Test
    void rejectsSecondsOutsideTheRepresentableRange() {
        for (final String text : new String[] {"9223372036854775807", "99999999999999999", "-99999999999999999"}) {
            final IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> TimestampUtils.parse(text), text);
            assertTrue(e.getMessage().contains("seconds since the epoch"), e.getMessage());
            assertTrue(e.getMessage().contains("'" + text + "'"), e.getMessage());
        }
    }

    @Test
    void rejectsAnythingElseNamingBothAcceptedForms() {
        for (final String text : new String[] {"yesterday", "", "2026-01-02", "2026-01-02T03:04:05", "1.5"}) {
            final IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> TimestampUtils.parse(text), text);
            assertTrue(e.getMessage().contains("ISO 8601"), e.getMessage());
            assertTrue(e.getMessage().contains("seconds since the epoch"), e.getMessage());
            assertTrue(e.getMessage().contains("'" + text + "'"), e.getMessage());
        }
    }
}
