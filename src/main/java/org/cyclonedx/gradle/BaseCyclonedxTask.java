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

import org.cyclonedx.Version;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

@CacheableTask
public abstract class BaseCyclonedxTask extends DefaultTask {

    /**
     * @see CyclonedxBomExtension#getComponentGroup()
     *
     */
    @Input
    public abstract Property<String> getComponentGroup();

    /**
     * @see CyclonedxBomExtension#getComponentName()
     *
     */
    @Input
    public abstract Property<String> getComponentName();

    /**
     * @see CyclonedxBomExtension#getComponentVersion()
     *
     */
    @Input
    public abstract Property<String> getComponentVersion();

    /**
     * @see CyclonedxBomExtension#getSchemaVersion()
     *
     */
    @Input
    abstract Property<Version> getSchemaVersion();

    /**
     * <p>The output file for the XML report.</p>
     * <p>If not set, the default is "${project.buildDir}/reports/cyclonedx/bom.xml".</p>
     * <p>To disable the XML output, you need to unset convention: {@code xmlOutput.unsetConvention()}</p>
     *
     * @return the XML output file property
     * @see #getJsonOutput()
     * @see org.gradle.api.provider.Property#unsetConvention()
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getXmlOutput();

    /**
     * <p>The output file for the JSON report.</p>
     * <p>If not set, the default is "${project.buildDir}/reports/cyclonedx/bom.json".</p>
     * <p>To disable the JSON output, you need to unset convention: {@code jsonOutput.unsetConvention()}</p>
     *
     * @return the JSON output file property
     * @see #getXmlOutput()
     * @see org.gradle.api.provider.Property#unsetConvention()
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getJsonOutput();

    /**
     * @see CyclonedxBomExtension#getIncludeBomSerialNumber()
     *
     */
    @Input
    abstract Property<Boolean> getIncludeBomSerialNumber();

    /**
     * @see CyclonedxBomExtension#getProjectType()
     *
     */
    @Input
    public abstract Property<Component.Type> getProjectType();

    /**
     * @see CyclonedxBomExtension#getIncludeBuildSystem()
     *
     */
    @Input
    abstract Property<Boolean> getIncludeBuildSystem();

    /**
     * @see CyclonedxBomExtension#getBuildSystemEnvironmentVariable()
     *
     */
    @Input
    @Optional
    abstract Property<String> getBuildSystemEnvironmentVariable();

    /**
     * @see CyclonedxBomExtension#getOrganizationalEntity()
     *
     */
    @Internal
    public abstract Property<OrganizationalEntity> getOrganizationalEntity();

    /**
     * @see CyclonedxBomExtension#getLicenseChoice()
     *
     */
    @Internal
    public abstract Property<LicenseChoice> getLicenseChoice();

    /**
     * @see CyclonedxBomExtension#getExternalReferences()
     *
     */
    @Internal
    public abstract ListProperty<ExternalReference> getExternalReferences();

    /**
     * @see CyclonedxBomExtension#getIncludeLicenseText()
     *
     */
    @Input
    abstract Property<Boolean> getIncludeLicenseText();
}
