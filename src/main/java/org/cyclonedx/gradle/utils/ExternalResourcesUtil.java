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
import java.util.ArrayList;
import java.util.List;
import org.cyclonedx.model.ExternalReference;

public class ExternalResourcesUtil {

    private ExternalResourcesUtil() {}

    public static List<ExternalReference> createExternalReferences() {
        final List<ExternalReference> references = new ArrayList<>();
        addGitReference(references);
        return references;
    }

    public static void addGitReference(final List<ExternalReference> references) {
        try {
            Process process = Runtime.getRuntime().exec("git remote get-url origin");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                final String remoteUrl = reader.readLine();
                final ExternalReference externalReference = new ExternalReference();
                externalReference.setType(ExternalReference.Type.VCS);
                externalReference.setUrl(mapGitToHttps(remoteUrl));

                references.add(externalReference);
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * Converts a git based git remote url to a https based url. In case the url is null, map will return null
     *
     * @param gitUrl The git url of the current project
     * @return gitUrl Mapped to https based git reference. May be null. Null will result in an empty external resources list
     */
    public static String mapGitToHttps(String gitUrl) {
        if (gitUrl == null) {
            return null;
        }

        if (gitUrl.startsWith("git@")) {
            final String[] parts = gitUrl.split("git@");
            String domain = parts[1];
            return "https://" + domain.replace(":", "/");
        }

        System.out.println("REMOTE URL: " + gitUrl);
        return gitUrl;
    }
}
