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

import org.cyclonedx.Version;

public class CycloneDxUtils {

    public static final Version DEFAULT_SCHEMA_VERSION = Version.VERSION_15;

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     *
     * @return the CycloneDX schema to use
     */
    public static Version schemaVersion(String version) {
        switch (version) {
            case "1.0":
                return Version.VERSION_10;
            case "1.1":
                return Version.VERSION_11;
            case "1.2":
                return Version.VERSION_12;
            case "1.3":
                return Version.VERSION_13;
            case "1.4":
                return Version.VERSION_14;
            case "1.5":
                return Version.VERSION_15;
            case "1.6":
                return Version.VERSION_16;
            default:
                return DEFAULT_SCHEMA_VERSION;
        }
    }
}
