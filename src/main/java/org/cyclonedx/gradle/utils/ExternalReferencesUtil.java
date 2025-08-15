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

import java.net.URISyntaxException;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.gradle.api.logging.Logger;

public class ExternalReferencesUtil {

    private ExternalReferencesUtil() {}

    public static void complementByEnvironment(@Nonnull final Component component, @Nonnull final Logger logger) {
        // ignore all other VCSs for the time being
        addGitReference(component, logger);
    }

    private static void addGitReference(@Nonnull final Component component, @Nonnull final Logger logger) {
        // abort early if a VCS external reference has already been provided
        if (component.getExternalReferences() != null
                && component.getExternalReferences().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(er -> ExternalReference.Type.VCS == er.getType())) {
            return;
        }

        String gitUrl = GitUtils.getGitUrlFromEnvironmentVariable();

        if (gitUrl == null || gitUrl.isEmpty()) {
            gitUrl = GitUtils.getGitUrlFromGitRepo();
        }

        if (gitUrl == null) {
            return;
        }

        try {
            gitUrl = GitUtils.sanitizeGitUrl(gitUrl);
        } catch (URISyntaxException e) {
            logger.warn("Invalid Git URL identified from environment, ignoring it");
            return;
        }

        final ExternalReference externalReference = new ExternalReference();
        externalReference.setType(ExternalReference.Type.VCS);
        externalReference.setUrl(gitUrl);

        // addExternalReferences only accepts 'https://' and 'ssh://' remote urls.
        // invalid urls will be ignored and the reference will not be added
        component.addExternalReference(externalReference);
    }
}
