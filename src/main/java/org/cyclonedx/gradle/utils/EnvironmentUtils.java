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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvironmentUtils {

    /**
     * Checks if a string is null or blank.
     * @param value the string to check
     * @return true if the string is null or blank, false otherwise
     */
    private static boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Get the URI of the current build from the environment variables set on common build systems like GitHub Actions, GitLab CI, etc.
     * @return the URI of the current build or null if it cannot be determined
     */
    public static String getBuildURI() {
        // GitHub Actions
        String githubServerUrl = System.getenv("GITHUB_SERVER_URL");
        String githubRepository = System.getenv("GITHUB_REPOSITORY");
        String githubRunId = System.getenv("GITHUB_RUN_ID");

        if (!isNullOrBlank(githubServerUrl) && !isNullOrBlank(githubRepository) && !isNullOrBlank(githubRunId)) {
            return String.format("%s/%s/actions/runs/%s", githubServerUrl, githubRepository, githubRunId);
        }

        // GitLab CI
        String ciProjectUrl = System.getenv("CI_PROJECT_URL");
        String ciJobId = System.getenv("CI_JOB_ID");
        if (!isNullOrBlank(ciProjectUrl) && !isNullOrBlank(ciJobId)) {
            return String.format("%s/-/jobs/%s", ciProjectUrl, ciJobId);
        }

        // Jenkins
        String buildUrl = System.getenv("BUILD_URL");
        if (!isNullOrBlank(buildUrl)) {
            return buildUrl;
        }
        //        String jobUrl = System.getenv("JOB_URL");
        //        if (!isNullOrBlank(jobUrl)) {
        //            return jobUrl;
        //        }

        // Travis CI
        String travisBuildUrl = System.getenv("TRAVIS_BUILD_WEB_URL");
        if (!isNullOrBlank(travisBuildUrl)) {
            return travisBuildUrl;
        }
        //        String travisJobUrl = System.getenv("TRAVIS_JOB_WEB_URL");
        //        if (!isNullOrBlank(travisJobUrl)) {
        //            return travisJobUrl;
        //        }

        // CircleCI
        String circleBuildUrl = System.getenv("CIRCLE_BUILD_URL");
        if (!isNullOrBlank(circleBuildUrl)) {
            return circleBuildUrl;
        }

        // Drone by Harness
        String droneBuildUrl = System.getenv("DRONE_BUILD_LINK");
        if (!isNullOrBlank(droneBuildUrl)) {
            return droneBuildUrl;
        }
        return null;
    }

    /**
     * Get the URI of the current build from the specified pattern and environment variables and pattern. An example
     * pattern could be "${SERVER}/jobs/${JOB_ID}". The environment variables are specified as a list of names.
     * @param pattern the pattern to use to build the URI
     * @return the URI of the current build or null if it cannot be determined
     */
    private static String getBuildUriFromPattern(String pattern) {
        String result = pattern;
        Pattern regex = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = regex.matcher(pattern);

        while (matcher.find()) {
            String varName = matcher.group(1);
            if (isNullOrBlank(varName)) {
                return null;
            }
            String value = System.getenv(varName);
            if (isNullOrBlank(value)) {
                return null;
            }
            result = result.replace("${" + varName + "}", value);
        }
        return result;
    }

    /**
     * Get the URI of the current build from the specified environment variable.
     * Additionally, a pattern can be provided to extract the URI from the environment variable.
     * An example pattern could be "${SERVER}/jobs/${JOB_ID}".
     *
     * @param str the name of the environment variable or pattern to use
     * @return the URI of the current build or null if it cannot be determined
     */
    public static String getBuildURI(String str) {
        if (str.contains("${")) {
            return getBuildUriFromPattern(str);
        }
        return System.getenv(str);
    }
}
