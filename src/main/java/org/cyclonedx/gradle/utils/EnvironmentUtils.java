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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for working with environment variables in common CI environments.
 */
public class EnvironmentUtils {

    /**
     * Pattern to match environment variables in a string.
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    /**
     * Jenkins build URL environment variable.
     */
    private static final String JENKINS_BUILD_URL = "BUILD_URL";
    /**
     * Travis build URL environment variable.
     */
    private static final String TRAVIS_BUILD_WEB_URL = "TRAVIS_BUILD_WEB_URL";
    /**
     * CircleCI build URL environment variable.
     */
    private static final String CIRCLE_BUILD_URL = "CIRCLE_BUILD_URL";
    /**
     * Drone build link environment variable.
     */
    private static final String DRONE_BUILD_LINK = "DRONE_BUILD_LINK";

    /**
     * Get the URI of the current build from the environment variables set on common build systems like GitHub Actions, GitLab CI, etc.
     * @return the URI of the current build or null if it cannot be determined
     */
    @Nullable public static String getBuildURI() {
        return Optional.ofNullable(fromGithubActions()).orElseGet(() -> Optional.ofNullable(fromGitlabCI())
                .orElseGet(() -> Optional.ofNullable(fromEnvironment(JENKINS_BUILD_URL))
                        .orElseGet(() -> Optional.ofNullable(fromEnvironment(CIRCLE_BUILD_URL))
                                .orElseGet(() -> Optional.ofNullable(fromEnvironment(TRAVIS_BUILD_WEB_URL))
                                        .orElseGet(() -> Optional.ofNullable(fromEnvironment(DRONE_BUILD_LINK))
                                                .orElse(null))))));
    }

    /**
     * Get the URI of the current build from the specified environment variable.
     * Additionally, a pattern can be provided to extract the URI from the environment variable.
     * An example pattern could be "${SERVER}/jobs/${JOB_ID}".
     *
     * @param str the name of the environment variable or pattern to use
     * @return the URI of the current build or null if it cannot be determined
     */
    @Nullable public static String getBuildURI(final String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        if (str.contains("${")) {
            return getBuildUriFromPattern(str);
        }
        return System.getenv(str);
    }

    @Nullable private static String fromGithubActions() {
        final String githubServerUrl = System.getenv("GITHUB_SERVER_URL");
        final String githubRepository = System.getenv("GITHUB_REPOSITORY");
        final String githubRunId = System.getenv("GITHUB_RUN_ID");

        if (!StringUtils.isBlank(githubServerUrl)
                && !StringUtils.isBlank(githubRepository)
                && !StringUtils.isBlank(githubRunId)) {
            return String.format("%s/%s/actions/runs/%s", githubServerUrl, githubRepository, githubRunId);
        }

        return null;
    }

    @Nullable private static String fromGitlabCI() {
        final String ciProjectUrl = System.getenv("CI_PROJECT_URL");
        final String ciJobId = System.getenv("CI_JOB_ID");
        if (!StringUtils.isBlank(ciProjectUrl) && !StringUtils.isBlank(ciJobId)) {
            return String.format("%s/-/jobs/%s", ciProjectUrl, ciJobId);
        }
        return null;
    }

    @Nullable private static String fromEnvironment(final String name) {
        final String url = System.getenv(name);
        if (!StringUtils.isBlank(url)) {
            return url;
        }
        return null;
    }

    /**
     * Get the URI of the current build from the specified pattern and environment variables and pattern. An example
     * pattern could be "${SERVER}/jobs/${JOB_ID}". The environment variables are specified as a list of names.
     * @param pattern the pattern to use to build the URI
     * @return the URI of the current build or null if it cannot be determined
     */
    @Nullable private static String getBuildUriFromPattern(final @Nullable String pattern) {
        if (pattern == null) {
            return null;
        }

        final StringBuilder result = new StringBuilder(pattern);
        final Matcher matcher = VARIABLE_PATTERN.matcher(pattern);

        while (matcher.find()) {
            final String varName = matcher.group(1);
            if (StringUtils.isBlank(varName)) {
                return null;
            }

            final String value = System.getenv(varName);
            if (StringUtils.isBlank(value)) {
                return null;
            }

            final int start = result.indexOf("${" + varName + "}");
            final int end = start + varName.length() + 3;
            result.replace(start, end, value);
        }

        return result.toString();
    }
}
