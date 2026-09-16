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
     * The group of the component that will be used in the BOM.
     * This is typically the group of the project.
     * If not set, the project group will be used.
     *
     * @see org.gradle.api.Project#getGroup()
     * @return the group of the component
     */
    @Input
    public abstract Property<String> getComponentGroup();

    /**
     * The name of the component that will be used in the BOM.
     * This is typically the name of the project.
     * If not set, the project name will be used.
     *
     * @see org.gradle.api.Project#getName()
     * @return the name of the component
     */
    @Input
    public abstract Property<String> getComponentName();

    /**
     * The version of the component that will be used in the BOM.
     * This is typically the version of the project.
     * If not set, the project version will be used.
     *
     * @see org.gradle.api.Project#getVersion()
     * @return the version of the component
     */
    @Input
    public abstract Property<String> getComponentVersion();

    /**
     * The schema version of the BOM.
     * It can be one of the supported versions, e.g., {@link Version#VERSION_16}.
     * If not set, it defaults to {@link Version#VERSION_16}.
     *
     * @see org.cyclonedx.Version
     * @return the schema version of the BOM
     */
    @Input
    public abstract Property<Version> getSchemaVersion();

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
     * Whether to include the BOM serial number in the BOM.
     * If not set, it defaults to true.
     *
     * @return true if BOM serial number should be included, false otherwise
     */
    @Input
    public abstract Property<Boolean> getIncludeBomSerialNumber();

    /**
     * The type of the project that will be used in the BOM.
     * It can be one of the types defined in {@link Component.Type}.
     * If not set, it defaults to {@link Component.Type#LIBRARY}.
     *
     * @return the type of the project
     */
    @Input
    public abstract Property<Component.Type> getProjectType();

    /**
     * Whether to include the build system information in the BOM.
     * If not set, it defaults to true.
     *
     * @return true if build system information should be included, false otherwise
     */
    @Input
    public abstract Property<Boolean> getIncludeBuildSystem();

    /**
     * The environment variable to use to determine the build system URL. If the
     * build system URL needs to be constructed from multiple environment variables a
     * pattern can be set using `buildSystemEnvironmentVariable = '${SERVER}/jobs/${JOB_ID}'`.
     * Note, that when configuring in kotlin or groovy single quotes must be used to prevent the
     * build itself from interpolating that variables.
     *
     * @return the environment variable to use to determine the build system
     */
    @Input
    @Optional
    public abstract Property<String> getBuildSystemEnvironmentVariable();

    /**
     * The organizational entity that will be used in the BOM.
     * This is typically the organization that owns the project.
     * If not set, it defaults to an empty organizational entity.
     *
     * @return the organizational entity
     */
    @Internal
    public abstract Property<OrganizationalEntity> getOrganizationalEntity();

    /**
     * The license choice that will be used in the BOM.
     * This is typically the license choice of the project.
     * If not set, it defaults to an empty license choice.
     *
     * @return the license choice
     */
    @Internal
    public abstract Property<LicenseChoice> getLicenseChoice();

    /**
     * The external reference that will be used in the BOM.
     * This can be used to link to external resources related to the project.
     * If not set, it defaults to resolution of git remote URL, if available.
     *
     * @return the external reference
     */
    @Internal
    public abstract ListProperty<ExternalReference> getExternalReferences();

    /**
     * Whether to include the license text in the BOM.
     * If not set, it defaults to false.
     *
     * @return true if license text should be included, false otherwise
     */
    @Input
    public abstract Property<Boolean> getIncludeLicenseText();

    /**
     * The list of artifacts to exclude from the BOM.
     * The format is `group:name:version` where `group`, `name`, and `version` can be regular expressions.
     *
     * @return the list of artifacts to exclude
     */
    @Input
    public abstract ListProperty<String> getExcludeArtifacts();

    public BaseCyclonedxTask() {
        super();
        getComponentGroup().convention(getProject().getProviders().provider(() -> getProject()
                .getGroup()
                .toString()));
        getComponentName().convention(getProject().getProviders().provider(() -> getProject()
                .getName()));
        getComponentVersion().convention(getProject().getProviders().provider(() -> getProject()
                .getVersion()
                .toString()));
        getSchemaVersion().convention(Version.VERSION_16);
        getIncludeLicenseText().convention(false);
        getIncludeBomSerialNumber().convention(true);
        getProjectType().convention(Component.Type.LIBRARY);
        getIncludeBuildSystem().convention(true);
        getOrganizationalEntity().convention(getProject().getObjects().property(OrganizationalEntity.class));
        getLicenseChoice().convention(getProject().getObjects().property(LicenseChoice.class));
        getExternalReferences().convention(getProject().getObjects().listProperty(ExternalReference.class));
        getExcludeArtifacts()
                .convention(getProject().getObjects().listProperty(String.class).empty());
    }
}
