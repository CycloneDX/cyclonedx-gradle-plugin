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

import java.util.ArrayList;
import org.cyclonedx.Version;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class CyclonedxBomExtension {

    /**
     * The group of the component that will be used in the BOM.
     * This is typically the group of the project.
     * If not set, the project group will be used.
     *
     * @see org.gradle.api.Project#getGroup()
     * @return the group of the component
     */
    public abstract Property<String> getComponentGroup();

    /**
     * The name of the component that will be used in the BOM.
     * This is typically the name of the project.
     * If not set, the project name will be used.
     *
     * @see org.gradle.api.Project#getName()
     * @return the name of the component
     */
    public abstract Property<String> getComponentName();

    /**
     * The version of the component that will be used in the BOM.
     * This is typically the version of the project.
     * If not set, the project version will be used.
     *
     * @see org.gradle.api.Project#getVersion()
     * @return the version of the component
     */
    public abstract Property<String> getComponentVersion();

    /**
     * The list of project names to include in the BOM.
     * If not set, all projects will be included.
     *
     * @return the list of project names to include
     */
    public abstract ListProperty<String> getIncludeProjects();

    /**
     * The list of project names to skip in the BOM.
     * If not set, no projects will be skipped.
     *
     * @return the list of project names to skip
     */
    public abstract ListProperty<String> getSkipProjects();

    /**
     * The list of configuration names to include in the BOM.
     * If not set, all configurations will be included.
     * Regex patterns can be used to match multiple configurations.
     *
     * @return the list of configuration names to include
     */
    public abstract ListProperty<String> getIncludeConfigs();

    /**
     * The list of configuration names to skip in the BOM.
     * If not set, no configurations will be skipped.
     * Regex patterns can be used to match multiple configurations.
     *
     * @return the list of configuration names to skip
     */
    public abstract ListProperty<String> getSkipConfigs();

    /**
     * The schema version of the BOM.
     * It can be one of the supported versions, e.g., {@link Version#VERSION_16}.
     * If not set, it defaults to {@link Version#VERSION_16}.
     *
     * @see org.cyclonedx.Version
     * @return the schema version of the BOM
     */
    public abstract Property<Version> getSchemaVersion();

    /**
     * Whether to include the license text in the BOM.
     * If not set, it defaults to false.
     *
     * @return true if license text should be included, false otherwise
     */
    public abstract Property<Boolean> getIncludeLicenseText();

    /**
     * Whether to include metadata resolution in the BOM. For example, license information.
     * If not set, it defaults to true.
     *
     * @return true if metadata resolution should be included, false otherwise
     */
    public abstract Property<Boolean> getIncludeMetadataResolution();

    /**
     * Whether to include the BOM serial number in the BOM.
     * If not set, it defaults to true.
     *
     * @return true if BOM serial number should be included, false otherwise
     */
    public abstract Property<Boolean> getIncludeBomSerialNumber();

    /**
     * The type of the project that will be used in the BOM.
     * It can be one of the types defined in {@link Component.Type}.
     * If not set, it defaults to {@link Component.Type#LIBRARY}.
     *
     * @return the type of the project
     */
    public abstract Property<Component.Type> getProjectType();

    /**
     * Whether to include the build system information in the BOM.
     * If not set, it defaults to true.
     *
     * @return true if build system information should be included, false otherwise
     */
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
    public abstract Property<String> getBuildSystemEnvironmentVariable();

    /**
     * The organizational entity that will be used in the BOM.
     * This is typically the organization that owns the project.
     * If not set, it defaults to an empty organizational entity.
     *
     * @return the organizational entity
     */
    public abstract Property<OrganizationalEntity> getOrganizationalEntity();

    /**
     * The license choice that will be used in the BOM.
     * This is typically the license choice of the project.
     * If not set, it defaults to an empty license choice.
     *
     * @return the license choice
     */
    public abstract Property<LicenseChoice> getLicenseChoice();

    /**
     * The external reference that will be used in the BOM.
     * This can be used to link to external resources related to the project.
     * If not set, it defaults to resolution of git remote URL, if available.
     *
     * @return the external reference
     */
    public abstract ListProperty<ExternalReference> getExternalReferences();

    public CyclonedxBomExtension(final ObjectFactory objects) {
        getIncludeProjects().convention(new ArrayList<>());
        getSkipProjects().convention(new ArrayList<>());
        getIncludeConfigs().convention(new ArrayList<>());
        getSkipConfigs().convention(new ArrayList<>());
        getSchemaVersion().convention(Version.VERSION_16);
        getIncludeLicenseText().convention(false);
        getIncludeMetadataResolution().convention(true);
        getIncludeBomSerialNumber().convention(true);
        getProjectType().convention(Component.Type.LIBRARY);
        getIncludeBuildSystem().convention(true);
        getOrganizationalEntity().convention(objects.property(OrganizationalEntity.class));
        getLicenseChoice().convention(objects.property(LicenseChoice.class));
        getExternalReferences().convention(objects.listProperty(ExternalReference.class));
    }
}
