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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Unresolved Dependencies of a project's scanned configurations: every dependency or artifact Gradle reported it
 * could not resolve, each described by Gradle's own text with the reason from its causes beneath. The first line of
 * that text names what did not resolve and is the identity of a failure. Gradle reports the same failure once per
 * configuration, and not always with the same lines beneath: a later lookup is answered from its cache without the
 * locations it searched, a repository is disabled after its first failure and later lookups name that instead, and a
 * repository limited to some configurations is searched for those alone. The lines beneath are therefore kept per
 * reason: a body whose lines are all contained in another's is the same reason with less context and merges into it,
 * anything else is its own reason with its own configurations, so nothing Gradle said is lost and nothing is
 * attributed to a configuration that did not see it.
 */
public final class UnresolvedDependencies implements Serializable {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final Map<String, Failure> failures = new LinkedHashMap<>();

    public void record(final String description, final String configuration) {
        final String[] lines = lines(description);
        failures.computeIfAbsent(lines[0], Failure::new)
                .record(Arrays.copyOfRange(lines, 1, lines.length), configuration);
    }

    public boolean isEmpty() {
        return failures.isEmpty();
    }

    /**
     * The distinct failures in the order they were first recorded.
     */
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(new ArrayList<>(failures.values()));
    }

    /**
     * The failure text for the build log: one bullet per failure with its first line, and beneath it every reason
     * with the configurations it occurred in.
     */
    public String describe(final String projectPath) {
        final StringBuilder text = new StringBuilder();
        text.append("The SBOM of project '")
                .append(projectPath)
                .append("' cannot describe the build, these dependencies did not resolve:");
        for (final Failure failure : failures.values()) {
            final List<String> lines = failure.lines();
            text.append("\n  - ").append(lines.get(0));
            for (int i = 1; i < lines.size(); i++) {
                text.append("\n    ").append(lines.get(i));
            }
        }
        return text.toString();
    }

    /**
     * The lines of a text, without the empty ones a trailing line break would leave behind.
     */
    public static String[] lines(final String text) {
        final String[] lines = LINE_BREAK.split(text, -1);
        int end = lines.length;
        while (end > 1 && lines[end - 1].isEmpty()) {
            end--;
        }
        return end == lines.length ? lines : Arrays.copyOf(lines, end);
    }

    /**
     * One failure: what did not resolve, and every reason Gradle gave with the configurations it occurred in.
     */
    public static final class Failure implements Serializable {

        private final String summary;
        private final List<Reason> reasons = new ArrayList<>();

        private Failure(final String summary) {
            this.summary = summary;
        }

        private void record(final String[] body, final String configuration) {
            final Reason reason = new Reason(body);
            for (final Reason existing : reasons) {
                if (existing.covers(reason)) {
                    existing.configurations.add(configuration);
                    return;
                }
                if (reason.covers(existing)) {
                    existing.body = reason.body;
                    existing.trimmedLines = reason.trimmedLines;
                    existing.configurations.add(configuration);
                    return;
                }
            }
            reason.configurations.add(configuration);
            reasons.add(reason);
        }

        /**
         * The first line of Gradle's text, which names what did not resolve.
         */
        public String getSummary() {
            return summary;
        }

        /**
         * Every configuration the failure occurred in, over all its reasons.
         */
        public Set<String> getConfigurations() {
            final Set<String> configurations = new TreeSet<>();
            for (final Reason reason : reasons) {
                configurations.addAll(reason.configurations);
            }
            return Collections.unmodifiableSet(configurations);
        }

        public String configurationNames() {
            return String.join(", ", getConfigurations());
        }

        /**
         * The summary, then for each reason its lines followed by the configurations it occurred in.
         */
        public List<String> lines() {
            final List<String> lines = new ArrayList<>();
            lines.add(summary);
            for (final Reason reason : reasons) {
                lines.addAll(Arrays.asList(reason.body));
                lines.add("(" + String.join(", ", reason.configurations) + ")");
            }
            return lines;
        }
    }

    private static final class Reason implements Serializable {

        private String[] body;
        private Set<String> trimmedLines;
        private final Set<String> configurations = new TreeSet<>();

        private Reason(final String[] body) {
            this.body = body;
            this.trimmedLines = trimmed(body);
        }

        /**
         * Whether every line of the other reason appears in this one, which makes the other the same reason with
         * less context.
         */
        private boolean covers(final Reason other) {
            return trimmedLines.containsAll(other.trimmedLines);
        }

        private static Set<String> trimmed(final String[] body) {
            final Set<String> lines = new HashSet<>();
            for (final String line : body) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
            return lines;
        }
    }
}
