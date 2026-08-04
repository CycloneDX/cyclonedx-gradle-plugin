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

/**
 * Plugin-internal, Core-free stand-in for the CycloneDX schema selectors the plugin currently knows about (1.0
 * through 1.7). It exists solely to capture the structural differences the plugin branches on when building a
 * {@code Bom}, so those call sites do not need to reason about {@code org.cyclonedx.Version} directly.
 *
 * <p>This type deliberately does not import anything from {@code org.cyclonedx.*}. See {@link SchemaVersionMapper}
 * for the one-way translation from Core's {@code org.cyclonedx.Version}.
 */
public enum SchemaVersion {
    VERSION_10(false, false, false),
    VERSION_11(false, false, false),
    VERSION_12(false, false, true),
    VERSION_13(false, false, true),
    VERSION_14(false, false, true),
    VERSION_15(true, false, true),
    VERSION_16(true, true, true),
    VERSION_17(true, true, true);

    private final boolean usesToolInformation;
    private final boolean usesManufacturer;
    private final boolean includesExtendedHashes;

    SchemaVersion(
            final boolean usesToolInformation, final boolean usesManufacturer, final boolean includesExtendedHashes) {
        this.usesToolInformation = usesToolInformation;
        this.usesManufacturer = usesManufacturer;
        this.includesExtendedHashes = includesExtendedHashes;
    }

    /**
     * @return {@code true} for schema 1.5 and above, where CycloneDX metadata tooling is expressed as
     *     {@code ToolInformation} (a list of {@code Component}s) rather than the legacy {@code Tool}.
     */
    public boolean usesToolInformation() {
        return usesToolInformation;
    }

    /**
     * @return {@code true} for schema 1.6 and above, where the metadata author is expressed via
     *     {@code metadata.manufacturer} rather than the legacy {@code metadata.manufacture}.
     */
    public boolean usesManufacturer() {
        return usesManufacturer;
    }

    /**
     * @return {@code true} for schema 1.2 and above, where an artifact carries SHA-384 and SHA3-384 hashes in addition
     *     to the set every schema version carries. Core rejects SHA3-384 below 1.2 via its {@code VersionFilter}.
     */
    public boolean includesExtendedHashes() {
        return includesExtendedHashes;
    }
}
