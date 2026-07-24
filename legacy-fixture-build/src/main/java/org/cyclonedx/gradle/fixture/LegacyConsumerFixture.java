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
package org.cyclonedx.gradle.fixture;

import java.util.Collections;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.CyclonedxDirectTask;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.Task;

/**
 * A stand-in for a real plugin consumer's precompiled build logic (e.g. a {@code buildSrc} convention plugin or an
 * included {@code build-logic} build).
 *
 * <p>This class lives in {@code legacy-fixture-build}, a Gradle build kept deliberately separate from the rest of
 * this repository, and is compiled against the previously published {@code org.cyclonedx:cyclonedx-gradle-plugin
 * :3.3.0} artifact, which transitively pulls {@code org.cyclonedx:cyclonedx-core-java:11.0.1}. (It can't be a source
 * set of the main build: that build publishes itself under this exact same GA, and Gradle always substitutes a
 * project for a module dependency with matching group:name within the same build - see the {@code legacyFixtureJar}
 * task in the root {@code build.gradle.kts} for the full explanation.) Its resulting {@code .class} file is packaged
 * by that nested build's {@code jar} task into a small jar containing only this fixture, and is never rebuilt
 * afterwards.
 *
 * <p>At test time that jar is placed on a build's {@code buildscript} classpath alongside the <em>candidate</em>
 * version of this plugin (built from this repository's current source, on the newer Core release under test) but
 * without the 3.3.0 jar or Core 11.0.1 anywhere on the runtime classpath. When {@link #configure(Task)} runs, the JVM
 * resolves every type it references &mdash; {@code org.cyclonedx.gradle.CyclonedxDirectTask}, {@code
 * org.cyclonedx.Version}, {@code org.cyclonedx.model.*} &mdash; against whatever classes are actually present at
 * runtime, i.e. the candidate's. If the candidate's Core dependency removed or incompatibly changed any method this
 * class calls, that resolution fails with a real {@code NoSuchMethodError}/{@code NoSuchFieldError}, exactly as it
 * would for a real precompiled consumer. See {@code BinaryCompatibilitySpec}.
 */
public final class LegacyConsumerFixture {

    public static final String ORGANIZATION_NAME = "Legacy Consumer Org";

    public static final String LICENSE_NAME = "Legacy Consumer License";

    public static final String EXTERNAL_REFERENCE_URL = "https://legacy-consumer.example.com";

    private LegacyConsumerFixture() {}

    /**
     * Exercises all five Core-backed {@code BaseCyclonedxTask} properties using Core 11.0.1 types, the same way a
     * precompiled consumer configuring {@code cyclonedxDirectBom} directly would.
     *
     * @param rawTask the live {@code cyclonedxDirectBom} task instance, passed as the Gradle {@link Task} supertype
     *     so the caller does not need this fixture's compile-time-only {@code CyclonedxDirectTask} type on its own
     *     classpath
     */
    public static void configure(final Task rawTask) {
        final CyclonedxDirectTask task = (CyclonedxDirectTask) rawTask;

        task.getSchemaVersion().set(Version.VERSION_16);
        task.getProjectType().set(Component.Type.LIBRARY);

        final OrganizationalEntity organizationalEntity = new OrganizationalEntity();
        organizationalEntity.setName(ORGANIZATION_NAME);
        task.getOrganizationalEntity().set(organizationalEntity);

        final License license = new License();
        license.setName(LICENSE_NAME);
        final LicenseChoice licenseChoice = new LicenseChoice();
        licenseChoice.addLicense(license);
        task.getLicenseChoice().set(licenseChoice);

        final ExternalReference externalReference = new ExternalReference();
        externalReference.setType(ExternalReference.Type.WEBSITE);
        externalReference.setUrl(EXTERNAL_REFERENCE_URL);
        task.getExternalReferences().set(Collections.singletonList(externalReference));
    }
}
