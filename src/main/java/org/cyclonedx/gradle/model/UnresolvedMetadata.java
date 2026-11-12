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
 * Why a component's Maven metadata could not be read, recorded on the component so that a consumer
 * can tell a component that declares nothing apart from one whose metadata was unavailable.
 */
public enum UnresolvedMetadata {

    /** The repository returned no usable POM artifact for the component. */
    POM_UNRESOLVED("pom-unresolved"),

    /** A POM was returned but could not be read as one. */
    POM_UNPARSEABLE("pom-unparseable"),

    /**
     * The POM was read, and the parent POM it declares was not obtained. Reported only where the failure
     * identifies that parent; what the component declares itself is still reported, what it would have
     * inherited is missing.
     */
    PARENT_UNRESOLVED("parent-unresolved"),

    /**
     * The effective model could not be completed, and which of its inputs failed is not established. An
     * import that could not be read arrives here, as does a parent the failure does not identify. What the
     * component declares itself is still reported.
     */
    MODEL_INCOMPLETE("model-incomplete");

    private final String value;

    UnresolvedMetadata(final String value) {
        this.value = value;
    }

    /** The value written to the component property; part of the SBOM output contract. */
    public String getValue() {
        return value;
    }
}
