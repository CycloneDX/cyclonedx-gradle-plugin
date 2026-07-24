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
package org.cyclonedx.gradle.model;

import org.cyclonedx.Version;

/**
 * One-way mapper from Core's {@link Version} to the plugin-internal {@link SchemaVersion}. Only the two
 * version-conditional decision points in {@code SbomBuilder.buildMetadata()} need this translation; every other use
 * of {@link Version} in the plugin (hash calculation, BOM serialization/validation, the schema-version CLI/DSL
 * mapping in {@code CyclonedxUtils.schemaVersion(String)}) keeps using the Core type directly and is untouched by
 * this class.
 */
public final class SchemaVersionMapper {

    private SchemaVersionMapper() {}

    /**
     * Maps a Core {@link Version} to the corresponding {@link SchemaVersion}.
     *
     * <p>Comparisons are ordinal, mirroring the {@code Version.compareTo(...)} checks this type replaces, so a
     * genuinely unknown future {@link Version} constant (newer than {@link Version#VERSION_17}) is never rejected —
     * it falls back to the newest known schema's thresholds ({@link SchemaVersion#VERSION_17}), staying exactly as
     * permissive as the raw {@code compareTo} logic it replaces.
     *
     * @param coreVersion the Core schema version to translate
     * @return the corresponding internal {@link SchemaVersion}
     */
    public static SchemaVersion from(final Version coreVersion) {
        if (coreVersion.compareTo(Version.VERSION_17) >= 0) {
            return SchemaVersion.VERSION_17;
        } else if (coreVersion.compareTo(Version.VERSION_16) >= 0) {
            return SchemaVersion.VERSION_16;
        } else if (coreVersion.compareTo(Version.VERSION_15) >= 0) {
            return SchemaVersion.VERSION_15;
        } else if (coreVersion.compareTo(Version.VERSION_14) >= 0) {
            return SchemaVersion.VERSION_14;
        } else if (coreVersion.compareTo(Version.VERSION_13) >= 0) {
            return SchemaVersion.VERSION_13;
        } else if (coreVersion.compareTo(Version.VERSION_12) >= 0) {
            return SchemaVersion.VERSION_12;
        } else if (coreVersion.compareTo(Version.VERSION_11) >= 0) {
            return SchemaVersion.VERSION_11;
        } else {
            return SchemaVersion.VERSION_10;
        }
    }
}
