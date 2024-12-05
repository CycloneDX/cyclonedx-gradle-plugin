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

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EnvironmentUtils}. Many of the tests clear the GITHUB_REPOSITORY environment variable to null
 * because tests normally run on GitHub Actions and if the variable was not cleared for the test - the test would fail.
 */
class EnvironmentUtilsTest {

    @Test
    void getBuildURI_GithubActions() throws Exception {
        String uri = withEnvironmentVariable("GITHUB_SERVER_URL", "https://github.com")
                .and("GITHUB_REPOSITORY", "CycloneDX/cyclonedx-gradle-plugin")
                .and("GITHUB_RUN_ID", "12345")
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI();
                });
        String expected = "https://github.com/CycloneDX/cyclonedx-gradle-plugin/actions/runs/12345";
        assertEquals(expected, uri);
    }

    @Test
    void getBuildURI_GitlabCI() throws Exception {
        String uri = withEnvironmentVariable("CI_PROJECT_URL", "https://gitlab.com/project")
                .and("CI_JOB_ID", "67890")
                .and("GITHUB_REPOSITORY", null)
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI();
                });
        String expected = "https://gitlab.com/project/-/jobs/67890";
        assertEquals(expected, uri);
    }

    @Test
    void getBuildURI_Jenkins() throws Exception {
        String uri = withEnvironmentVariable("BUILD_URL", "https://jenkins.example.com/job/123")
                .and("GITHUB_REPOSITORY", null)
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI();
                });
        assertEquals("https://jenkins.example.com/job/123", uri);
    }

    @Test
    void getBuildURI_Drone() throws Exception {
        String uri = withEnvironmentVariable("DRONE_BUILD_LINK", "https://drone.company.com/octocat/hello-world/42")
                .and("GITHUB_REPOSITORY", null)
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI();
                });
        assertEquals("https://drone.company.com/octocat/hello-world/42", uri);
    }

    @Test
    void getBuildUriFromPattern() throws Exception {
        String uri = withEnvironmentVariable("SERVER", "https://example.com")
                .and("JOB_ID", "123456")
                .and("GITHUB_REPOSITORY", null)
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI("${SERVER}/jobs/${JOB_ID}");
                });

        String expected = "https://example.com/jobs/123456";
        assertEquals(expected, uri);
    }

    @Test
    void getBuildURI_SingleEnvVar() throws Exception {
        String uri = withEnvironmentVariable("BUILD_PATH", "https://ci.example.com/build/789")
                .execute(() -> {
                    return EnvironmentUtils.getBuildURI("BUILD_PATH");
                });
        assertEquals("https://ci.example.com/build/789", uri);
    }

    @Test
    void getBuildURI_NoEnvVars() throws Exception {
        String uri = withEnvironmentVariable("GITHUB_REPOSITORY", null).execute(() -> {
            return EnvironmentUtils.getBuildURI();
        });
        assertNull(uri);
    }
}
