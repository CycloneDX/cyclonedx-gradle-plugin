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
 * through 1.7). It exists solely to capture the two structural differences that {@code SbomBuilder} branches on when
 * building {@code Metadata}, so that class does not need to reason about {@code org.cyclonedx.Version} directly for
 * those decisions.
 *
 * <p>This type deliberately does not import anything from {@code org.cyclonedx.*}. See {@link SchemaVersionMapper}
 * for the one-way translation from Core's {@code org.cyclonedx.Version}.
 */
public enum SchemaVersion {
    VERSION_10(false, false),
    VERSION_11(false, false),
    VERSION_12(false, false),
    VERSION_13(false, false),
    VERSION_14(false, false),
    VERSION_15(true, false),
    VERSION_16(true, true),
    VERSION_17(true, true);

    private final boolean usesToolInformation;
    private final boolean usesManufacturer;

    SchemaVersion(final boolean usesToolInformation, final boolean usesManufacturer) {
        this.usesToolInformation = usesToolInformation;
        this.usesManufacturer = usesManufacturer;
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
}
