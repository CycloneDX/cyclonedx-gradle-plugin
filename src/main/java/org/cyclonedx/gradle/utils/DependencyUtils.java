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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;

public class DependencyUtils {

    public static String getDependencyName(ResolvedDependency resolvedDependencies) {
        final ModuleVersionIdentifier m = resolvedDependencies.getModule().getId();
        return getDependencyName(m);
    }

    public static String getDependencyName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier m = artifact.getModuleVersion().getId();
        return getDependencyName(m);
    }

    public static boolean canBeResolved(Configuration configuration) {
        // Configuration.isCanBeResolved() has been introduced with Gradle 3.3,
        // thus we need to check for the method's existence first
        try {
            Method method = Configuration.class.getMethod("isCanBeResolved");
            try {
                return (Boolean) method.invoke(configuration);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            // prior to Gradle 3.3 all configurations were resolvable
            return true;
        }
    }

    private static String getDependencyName(ModuleVersionIdentifier moduleVersion) {
        return String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
    }
}
