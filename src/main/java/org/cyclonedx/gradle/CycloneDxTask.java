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

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * This task mainly acts a container for the user configurations (includeConfigs, projectType, schemaVersion, ...)
 * and orchestrating the calls between the core objects (SbomGraphProvider and SbomBuilder)
 */
public abstract class CycloneDxTask extends DefaultTask {

    private static final String MESSAGE_WRITING_BOM_OUTPUT = "CycloneDX: Writing BOM output";
    private static final String DEFAULT_PROJECT_TYPE = "library";

    private final Property<String> outputName;
    private final Property<String> schemaVersion;
    private final Property<String> componentName;
    private final Property<String> componentVersion;
    private final Property<String> outputFormat;
    private final Property<Boolean> includeBomSerialNumber;
    private final ListProperty<String> skipConfigs;
    private final ListProperty<String> includeConfigs;
    private final Property<Boolean> includeMetadataResolution;
    private final Property<Boolean> includeLicenseText;
    private final Property<String> projectType;
    private final ListProperty<String> skipProjects;
    private final Property<File> destination;
    private final Provider<SbomGraph> componentsProvider;
    private final Property<Boolean> includeBuildSystem;
    private final Property<String> buildSystemEnvironmentVariable;

    @Nullable private OrganizationalEntity organizationalEntity;

    @Nullable private LicenseChoice licenseChoice;

    @Nullable private ExternalReference gitVCS;

    public CycloneDxTask() {

        componentsProvider = getProject().getProviders().provider(new SbomGraphProvider(getProject(), this));

        outputName = getProject().getObjects().property(String.class);
        outputName.convention("bom");

        schemaVersion = getProject().getObjects().property(String.class);
        schemaVersion.convention(CycloneDxUtils.DEFAULT_SCHEMA_VERSION.getVersionString());

        componentName = getProject().getObjects().property(String.class);
        componentName.convention(getProject().getName());

        componentVersion = getProject().getObjects().property(String.class);
        componentVersion.convention(getProject()
                .getProviders()
                .provider(() -> getProject().getVersion().toString()));

        outputFormat = getProject().getObjects().property(String.class);
        outputFormat.convention("all");

        includeBomSerialNumber = getProject().getObjects().property(Boolean.class);
        includeBomSerialNumber.convention(true);

        skipConfigs = getProject().getObjects().listProperty(String.class);
        includeConfigs = getProject().getObjects().listProperty(String.class);

        includeMetadataResolution = getProject().getObjects().property(Boolean.class);
        includeMetadataResolution.convention(true);

        includeLicenseText = getProject().getObjects().property(Boolean.class);
        includeLicenseText.convention(true);

        projectType = getProject().getObjects().property(String.class);
        projectType.convention(DEFAULT_PROJECT_TYPE);

        skipProjects = getProject().getObjects().listProperty(String.class);

        organizationalEntity = new OrganizationalEntity();
        licenseChoice = new LicenseChoice();
        gitVCS = new ExternalReference();

        destination = getProject().getObjects().property(File.class);
        destination.convention(getProject()
                .getLayout()
                .getBuildDirectory()
                .dir("reports")
                .get()
                .getAsFile());

        includeBuildSystem = getProject().getObjects().property(Boolean.class);
        includeBuildSystem.convention(false);
        buildSystemEnvironmentVariable = getProject().getObjects().property(String.class);
    }

    @Input
    public Property<String> getOutputName() {
        return outputName;
    }

    public void setOutputName(final String output) {
        this.outputName.set(output);
    }

    @Input
    public Property<String> getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(final String schemaVersion) {
        this.schemaVersion.set(schemaVersion);
    }

    @Input
    public Property<String> getComponentName() {
        return componentName;
    }

    public void setComponentName(final String componentName) {
        this.componentName.set(componentName);
    }

    @Input
    public Property<String> getComponentVersion() {
        return componentVersion;
    }

    public void setComponentVersion(final String componentVersion) {
        this.componentVersion.set(componentVersion);
    }

    @Input
    public Property<String> getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(final String format) {
        this.outputFormat.set(format);
    }

    @Input
    public Property<Boolean> getIncludeBomSerialNumber() {
        return includeBomSerialNumber;
    }

    public void setIncludeBomSerialNumber(final boolean includeBomSerialNumber) {
        this.includeBomSerialNumber.set(includeBomSerialNumber);
    }

    @Input
    public ListProperty<String> getSkipConfigs() {
        return skipConfigs;
    }

    public void setSkipConfigs(final Collection<String> skipConfigs) {
        this.skipConfigs.addAll(skipConfigs);
    }

    @Input
    public ListProperty<String> getIncludeConfigs() {
        return includeConfigs;
    }

    public void setIncludeConfigs(final Collection<String> includeConfigs) {
        this.includeConfigs.addAll(includeConfigs);
    }

    @Input
    public Property<Boolean> getIncludeMetadataResolution() {
        return includeMetadataResolution;
    }

    public void setIncludeMetadataResolution(final boolean includeMetadataResolution) {
        this.includeMetadataResolution.set(includeMetadataResolution);
    }

    @Input
    public Property<Boolean> getIncludeLicenseText() {
        return includeLicenseText;
    }

    public void setIncludeLicenseText(final boolean includeLicenseText) {
        this.includeLicenseText.set(includeLicenseText);
    }

    @Input
    public Property<String> getProjectType() {
        return projectType;
    }

    public void setProjectType(final String projectType) {
        this.projectType.set(projectType);
    }

    @Input
    public ListProperty<String> getSkipProjects() {
        return skipProjects;
    }

    public void setSkipProjects(final Collection<String> skipProjects) {
        this.skipProjects.addAll(skipProjects);
    }

    @Input
    public Property<Boolean> getIncludeBuildSystem() {
        return includeBuildSystem;
    }

    public void setIncludeBuildSystem(final boolean includeBuildSystem) {
        this.includeBuildSystem.set(includeBuildSystem);
    }

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
    public Property<String> getBuildSystemEnvironmentVariable() {
        return buildSystemEnvironmentVariable;
    }

    public void setBuildSystemEnvironmentVariable(final String buildSystemEnvironmentVariable) {
        this.buildSystemEnvironmentVariable.set(buildSystemEnvironmentVariable);
    }

    @Internal
    @Nullable OrganizationalEntity getOrganizationalEntity() {
        return organizationalEntity;
    }

    @Internal
    @Nullable LicenseChoice getLicenseChoice() {
        return licenseChoice;
    }

    @Internal
    @Nullable ExternalReference getGitVCS() {
        return gitVCS;
    }

    @OutputDirectory
    public Property<File> getDestination() {
        return destination;
    }

    public void setDestination(final File destination) {
        this.destination.set(destination);
    }

    /**
     * Executes the main logic of the plugin by loading the dependency graph (SbomGraphProvider.get())
     * and providing the result to SbomBuilder
     */
    @TaskAction
    public void createBom() {

        logParameters();

        final SbomBuilder builder = new SbomBuilder(getLogger(), this);
        final SbomGraph components = componentsProvider.get();
        final Bom bom = builder.buildBom(components.getGraph(), components.getRootComponent());

        getLogger().info(MESSAGE_WRITING_BOM_OUTPUT);
        CycloneDxUtils.writeBom(
                bom,
                getDestination().get(),
                getOutputName().get(),
                CycloneDxUtils.schemaVersion(getSchemaVersion().get()),
                getOutputFormat().get());
    }

    public void setOrganizationalEntity(final Consumer<OrganizationalEntity> customizer) {
        final OrganizationalEntity origin = new OrganizationalEntity();
        customizer.accept(origin);
        this.organizationalEntity = origin;

        final Map<String, String> organizationalEntity = new HashMap<>();

        organizationalEntity.put("name", this.organizationalEntity.getName());
        if (this.organizationalEntity.getUrls() != null) {
            for (int i = 0; i < this.organizationalEntity.getUrls().size(); i++) {
                organizationalEntity.put(
                        "url" + i, this.organizationalEntity.getUrls().get(i));
            }
        }
        if (this.organizationalEntity.getContacts() != null) {
            for (int i = 0; i < this.organizationalEntity.getContacts().size(); i++) {
                organizationalEntity.put(
                        "contact_name" + i,
                        this.organizationalEntity.getContacts().get(i).getName());
                organizationalEntity.put(
                        "contact_email" + i,
                        this.organizationalEntity.getContacts().get(i).getEmail());
                organizationalEntity.put(
                        "contact_phone" + i,
                        this.organizationalEntity.getContacts().get(i).getPhone());
            }
        }
        // Definition of gradle Input via Hashmap because Hashmap is serializable (OrganizationalEntity isn't
        // serializable)
        getInputs().property("OrganizationalEntity", organizationalEntity);
    }

    public void setLicenseChoice(final Consumer<LicenseChoice> customizer) {
        final LicenseChoice origin = new LicenseChoice();
        customizer.accept(origin);
        this.licenseChoice = origin;

        final Map<String, String> licenseChoice = new HashMap<>();

        if (this.licenseChoice.getLicenses() != null) {
            for (int i = 0; i < this.licenseChoice.getLicenses().size(); i++) {
                if (this.licenseChoice.getLicenses().get(i).getName() != null) {
                    licenseChoice.put(
                            "licenseChoice" + i + "name",
                            this.licenseChoice.getLicenses().get(i).getName());
                }
                if (this.licenseChoice.getLicenses().get(i).getId() != null) {
                    licenseChoice.put(
                            "licenseChoice" + i + "id",
                            this.licenseChoice.getLicenses().get(i).getId());
                }
                licenseChoice.put(
                        "licenseChoice" + i + "text",
                        this.licenseChoice
                                .getLicenses()
                                .get(i)
                                .getAttachmentText()
                                .getText());
                licenseChoice.put(
                        "licenseChoice" + i + "url",
                        this.licenseChoice.getLicenses().get(i).getUrl());
            }
        }

        if (this.licenseChoice.getExpression() != null) {
            licenseChoice.put(
                    "licenseChoice_Expression",
                    this.licenseChoice.getExpression().getValue());
        }
        // Definition of gradle Input via Hashmap because Hashmap is serializable (LicenseChoice isn't serializable)
        getInputs().property("LicenseChoice", licenseChoice);
    }

    public void setVCSGit(final Consumer<ExternalReference> customizer) {
        final ExternalReference origin = new ExternalReference();
        customizer.accept(origin);
        this.gitVCS = origin;
        this.gitVCS.setType(ExternalReference.Type.VCS);

        final Map<String, String> externalReference = new HashMap<>();

        externalReference.put("type", this.gitVCS.getType().toString());
        externalReference.put("url", this.gitVCS.getUrl());
        externalReference.put("comment", this.gitVCS.getComment());

        // Definition of gradle Input via Hashmap because Hashmap is serializable
        // (OrganizationalEntity isn't serializable)
        getInputs().property("GitVCS", externalReference);
    }

    private void logParameters() {
        if (getLogger().isInfoEnabled()) {
            getLogger().info("CycloneDX: Parameters");
            getLogger().info("------------------------------------------------------------------------");
            getLogger().info("schemaVersion             : " + schemaVersion.get());
            getLogger().info("includeLicenseText        : " + includeLicenseText.get());
            getLogger().info("includeBomSerialNumber    : " + includeBomSerialNumber.get());
            getLogger().info("includeConfigs            : " + includeConfigs.get());
            getLogger().info("skipConfigs               : " + skipConfigs.get());
            getLogger().info("skipProjects              : " + skipProjects.get());
            getLogger().info("includeMetadataResolution : " + includeMetadataResolution.get());
            getLogger().info("destination               : " + destination.get());
            getLogger().info("outputName                : " + outputName.get());
            getLogger().info("componentName             : " + componentName.get());
            getLogger().info("componentVersion          : " + componentVersion.get());
            getLogger().info("outputFormat              : " + outputFormat.get());
            getLogger().info("projectType               : " + projectType.get());
            getLogger().info("------------------------------------------------------------------------");
        }
    }
}
