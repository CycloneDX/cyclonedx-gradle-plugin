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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * Reads the timestamp a task was given for its document: an ISO 8601 date-time with an offset, such as
 * {@code 2026-01-02T03:04:05Z}, or the seconds since the epoch, such as {@code 1767323045}, which is what
 * {@code SOURCE_DATE_EPOCH} carries.
 */
public final class TimestampUtils {

    private TimestampUtils() {}

    /**
     * Parses a timestamp in either accepted form.
     *
     * @param text the configured value
     * @return the instant it names
     * @throws IllegalArgumentException when the value is in neither form; the message names both
     */
    public static Date parse(final String text) {
        final String trimmed = text.trim();
        try {
            return Date.from(OffsetDateTime.parse(trimmed).toInstant());
        } catch (final DateTimeException | IllegalArgumentException ignored) {
            // not a date-time, so it has to be the seconds since the epoch
        }
        try {
            return Date.from(Instant.ofEpochSecond(Long.parseLong(trimmed)));
        } catch (final DateTimeException | IllegalArgumentException ignored) {
            // neither form, or outside the range a date can hold
        }
        throw new IllegalArgumentException("A timestamp must be an ISO 8601 date-time such as 2026-01-02T03:04:05Z"
                + " or the seconds since the epoch such as 1767323045, but is '" + trimmed + "'");
    }
}
