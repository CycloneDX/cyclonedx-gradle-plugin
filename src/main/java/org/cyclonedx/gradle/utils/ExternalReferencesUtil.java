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
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;

public class ExternalReferencesUtil {

    private ExternalReferencesUtil() {}

    public static void complementByEnvironment(@Nonnull final Component component) {
        // ignore all other VCSs for the time being
        addGitReference(component);
    }

    private static void addGitReference(@Nonnull final Component component) {
        // abort early if a VCS external reference has already been provided
        if (component.getExternalReferences() != null
                && component.getExternalReferences().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(er -> ExternalReference.Type.VCS == er.getType())) {
            return;
        }

        String gitUrl = fromEnvironment();

        if (gitUrl == null || gitUrl.isEmpty()) {
            gitUrl = fromGitRepo();
        }

        if (gitUrl != null) {
            if (gitUrl.startsWith("git@")) {
                gitUrl = "ssh://" + gitUrl;
            }

            final ExternalReference externalReference = new ExternalReference();
            externalReference.setType(ExternalReference.Type.VCS);
            externalReference.setUrl(gitUrl);

            // addExternalReferences only accepts 'https://' and 'ssh://' remote urls.
            // invalid urls will be ignored and the reference will not be added
            component.addExternalReference(externalReference);
        }
    }

    private static @Nullable String fromEnvironment() {
        return System.getenv("GIT_URL");
    }

    private static @Nullable String fromGitRepo() {
        try {
            final Process process = Runtime.getRuntime().exec(new String[] {"git", "remote", "get-url", "origin"});

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception ignore) {
            // throw it away if this is not a git repository or if git is not installed
        }

        return null;
    }
}
