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
package org.cyclonedx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CyclonedxUtils;
import org.cyclonedx.model.Bom;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * The serial number of a Reproducible SBOM names the document's content: the name-based UUID of the form the schema
 * version can write, without a serial number in it.
 */
class SbomBuilderSerialNumberTest {

    private static final String TIMESTAMP = "2026-01-02T03:04:05Z";

    @Test
    void namesTheJsonFormWhenTheSchemaHasOne() {
        final CyclonedxDirectTask task = task(Version.VERSION_16, TIMESTAMP);
        final SbomBuilder<CyclonedxDirectTask> builder = new SbomBuilder<>(task);
        final Bom bom = builder.buildBom(graph());
        final String expected = nameOf(CyclonedxUtils.toJsonString(Version.VERSION_16, bom));

        builder.assignSerialNumber(bom);

        assertEquals(expected, bom.getSerialNumber());
    }

    @Test
    void namesTheXmlFormWhenTheSchemaHasNoJson() {
        final CyclonedxDirectTask task = task(Version.VERSION_11, TIMESTAMP);
        final SbomBuilder<CyclonedxDirectTask> builder = new SbomBuilder<>(task);
        final Bom bom = builder.buildBom(graph());
        final String expected = nameOf(CyclonedxUtils.toXmlString(Version.VERSION_11, bom));

        builder.assignSerialNumber(bom);

        assertEquals(expected, bom.getSerialNumber());
    }

    @Test
    void staysRandomWithoutATimestamp() {
        final CyclonedxDirectTask task = task(Version.VERSION_16, null);
        final SbomBuilder<CyclonedxDirectTask> builder = new SbomBuilder<>(task);
        final Bom first = builder.buildBom(graph());
        final Bom second = builder.buildBom(graph());

        builder.assignSerialNumber(first);
        builder.assignSerialNumber(second);

        assertTrue(first.getSerialNumber().startsWith("urn:uuid:"), first.getSerialNumber());
        assertNotEquals(first.getSerialNumber(), second.getSerialNumber());
    }

    @Test
    void assignsNothingWhenTheSerialNumberIsSwitchedOff() {
        final CyclonedxDirectTask task = task(Version.VERSION_16, TIMESTAMP);
        task.getIncludeBomSerialNumber().set(false);
        final SbomBuilder<CyclonedxDirectTask> builder = new SbomBuilder<>(task);
        final Bom bom = builder.buildBom(graph());

        builder.assignSerialNumber(bom);

        assertNull(bom.getSerialNumber());
    }

    private static String nameOf(final String content) {
        return "urn:uuid:" + UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8));
    }

    private static CyclonedxDirectTask task(final Version schemaVersion, final String timestamp) {
        final Project project = ProjectBuilder.builder().build();
        project.setGroup("com.example");
        project.setVersion("1.0.0");
        final CyclonedxDirectTask task =
                project.getTasks().register("bom", CyclonedxDirectTask.class).get();
        task.getSchemaVersion().set(schemaVersion);
        task.getIncludeBuildSystem().set(false);
        if (timestamp != null) {
            task.getTimestamp().set(timestamp);
        }
        return task;
    }

    private static SbomGraph graph() {
        final SbomComponent root = new SbomComponent.Builder()
                .withId(new SbomComponentId("com.example", "app", "1.0.0", null, ":"))
                .build();
        return new SbomGraph(Collections.emptyMap(), root);
    }
}
