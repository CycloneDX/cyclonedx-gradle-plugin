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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;

public final class GitUtils {

    private GitUtils() {}

    public static @Nullable String getGitUrlFromEnvironmentVariable() {
        return System.getenv("GIT_URL");
    }

    public static @Nullable String getGitUrlFromGitRepo() {
        try {
            final Process process = Runtime.getRuntime().exec(new String[] {"git", "remote", "get-url", "origin"});

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception ignore) {
            // throw it away if this is not a git repository or if git is not installed
            return null;
        }
    }

    public static String sanitizeGitUrl(final String gitUrl) throws URISyntaxException {
        if (gitUrl.startsWith("git@")) {
            // We still parse to make sure it is valid but do not remove user info
            // (as it is not used for username:password in this case)
            return new URI("ssh://" + gitUrl).toString();
        }

        if (gitUrl.startsWith("ssh://")) {
            // We still parse to make sure it is valid but do not remove user info
            // (as it is not used for username:password in this case)
            return new URI(gitUrl).toString();
        }

        // Remove user info
        final URI uri = new URI(gitUrl);
        return new URI(
                        uri.getScheme(),
                        null,
                        uri.getHost(),
                        uri.getPort(),
                        uri.getRawPath(),
                        uri.getRawQuery(),
                        uri.getRawFragment())
                .toString();
    }
}
