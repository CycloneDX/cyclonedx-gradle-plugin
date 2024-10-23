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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import java.util.TreeMap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

public class DependencyUtils {

    public static String generatePackageUrl(
            final ModuleVersionIdentifier version, final TreeMap<String, String> qualifiers)
            throws MalformedPackageURLException {
        return new PackageURL(
                        PackageURL.StandardTypes.MAVEN,
                        version.getGroup(),
                        version.getName(),
                        version.getVersion(),
                        qualifiers,
                        null)
                .canonicalize();
    }
}
