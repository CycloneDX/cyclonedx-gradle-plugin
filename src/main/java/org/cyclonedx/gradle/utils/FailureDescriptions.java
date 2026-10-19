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

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.cyclonedx.gradle.model.UnresolvedDependencies;
import org.gradle.internal.exceptions.MultiCauseException;

/**
 * Turns a failure Gradle reported for a dependency into text that carries the reason. Gradle's resolve exceptions
 * name what did not resolve in their message and keep why in their causes: a refused connection, a 401, a timeout,
 * a variant that did not match. The build's own failure output renders that tree; a message copied out of it does
 * not, so the causes are rendered here the way Gradle does, each on lines of its own prefixed with {@code > } and
 * nested two spaces deeper per level.
 */
public final class FailureDescriptions {

    private static final int MAX_DEPTH = 16;

    private FailureDescriptions() {}

    public static String describe(final Throwable failure) {
        final String message = messageOf(failure);
        final StringBuilder text = new StringBuilder(message);
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(failure);
        appendCauses(text, failure, UnresolvedDependencies.lines(message)[0], 0, visited);
        return text.toString();
    }

    private static void appendCauses(
            final StringBuilder text,
            final Throwable failure,
            final String parentSummary,
            final int depth,
            final Set<Throwable> visited) {
        if (depth >= MAX_DEPTH) {
            return;
        }
        for (final Throwable cause : causesOf(failure)) {
            if (!visited.add(cause)) {
                continue;
            }
            final String[] lines = UnresolvedDependencies.lines(messageOf(cause));
            if (lines[0].equals(parentSummary)) {
                // Gradle hands over a copy of the failure that carries the paths which required the dependency,
                // with the bare failure as its cause: the same line again, and the reason beneath it
                appendCauses(text, cause, parentSummary, depth, visited);
                continue;
            }
            final String indent = spaces(depth * 2);
            text.append('\n').append(indent).append("> ").append(lines[0]);
            for (int i = 1; i < lines.length; i++) {
                text.append('\n').append(indent).append("  ").append(lines[i]);
            }
            appendCauses(text, cause, lines[0], depth + 1, visited);
        }
    }

    /**
     * Gradle reports one cause per repository it asked, which {@link Throwable#getCause()} would reduce to the
     * first. The interface that lists them is Gradle internal, so a release that moves it falls back to the first
     * cause instead of failing every lenient build that has an unresolved dependency.
     */
    private static List<? extends Throwable> causesOf(final Throwable failure) {
        try {
            if (failure instanceof MultiCauseException) {
                return ((MultiCauseException) failure).getCauses();
            }
        } catch (final LinkageError e) {
            // fall through to the single cause
        }
        final Throwable cause = failure.getCause();
        return cause == null ? Collections.emptyList() : Collections.singletonList(cause);
    }

    private static String messageOf(final Throwable failure) {
        final String message = failure.getMessage();
        return message == null || message.trim().isEmpty() ? failure.getClass().getName() : message;
    }

    private static String spaces(final int count) {
        final char[] spaces = new char[count];
        Arrays.fill(spaces, ' ');
        return new String(spaces);
    }
}
