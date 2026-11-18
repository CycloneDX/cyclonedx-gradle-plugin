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

import java.util.ArrayList;
import java.util.List;
import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.GradleException;

/**
 * Writes the Core-typed values a task is configured with in the JSON form the document writes them in, so that they
 * can be declared as task inputs. The Core types are not serializable, so Gradle cannot fingerprint them itself, and a
 * task whose entity, licenses or references changed stayed up to date with the old document (ADR 0010 states the
 * principle for the metadata read from POMs). Rendering them through the same generator as the document covers every
 * field the document can carry; the newest schema is used so that no field is filtered out of the input.
 */
public final class CanonicalForm {

    private static final Version SCHEMA = Version.VERSION_17;

    private CanonicalForm() {}

    public static String ofEntity(final OrganizationalEntity entity) {
        final Bom bom = emptyBom();
        bom.getMetadata().setManufacturer(entity);
        return toJson(bom);
    }

    public static String ofLicenses(final LicenseChoice licenses) {
        final Bom bom = emptyBom();
        bom.getMetadata().setLicenses(licenses);
        return toJson(bom);
    }

    public static String ofReferences(final List<ExternalReference> references) {
        final Bom bom = emptyBom();
        bom.setExternalReferences(new ArrayList<>(references));
        return toJson(bom);
    }

    private static Bom emptyBom() {
        final Metadata metadata = new Metadata();
        // the constructor stamps the current time, which would make every build see a changed input
        metadata.setTimestamp(null);
        final Bom bom = new Bom();
        bom.setMetadata(metadata);
        return bom;
    }

    private static String toJson(final Bom bom) {
        try {
            return BomGeneratorFactory.createJson(SCHEMA, bom).toJsonString(false);
        } catch (GeneratorException e) {
            throw new GradleException("Error writing task metadata as JSON", e);
        }
    }
}
