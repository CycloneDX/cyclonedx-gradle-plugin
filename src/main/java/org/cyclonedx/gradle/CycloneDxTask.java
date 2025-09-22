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
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.SbomGraph;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.gradle.utils.GitUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * This task mainly acts a container for the user configurations (includeConfigs, projectType, schemaVersion, ...)
 * and orchestrating the calls between the core objects (SbomGraphProvider and SbomBuilder)
 */
public abstract class CycloneDxTask extends DefaultTask {

    private static final String MESSAGE_WRITING_BOM_OUTPUT = "CycloneDX: Writing BOM output";
    private static final Component.Type DEFAULT_PROJECT_TYPE = Component.Type.LIBRARY;

    final Property<String> outputName;
    final Property<String> outputFormat;
    final Property<File> destination;
    public final Property<Version> schemaVersion;
    private final Property<String> schemaVersionAsString;
    public final Property<String> componentName;
    public final Property<String> componentVersion;
    public final Property<Boolean> includeBomSerialNumber;
    public final ListProperty<String> skipConfigs;
    public final ListProperty<String> includeConfigs;
    public final Property<Boolean> includeMetadataResolution;
    public final Property<Boolean> includeLicenseText;
    public final Property<Component.Type> projectType;
    private final Property<String> projectTypeAsString;
    private final ListProperty<String> skipProjects;
    public final Property<Boolean> includeBuildSystem;
    public final Property<String> buildSystemEnvironmentVariable;
    public final Property<OrganizationalEntity> organizationalEntity;
    public final Property<LicenseChoice> licenseChoice;
    public final ListProperty<ExternalReference> externalReferences;

    private final Provider<SbomGraph> componentsProvider;

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

    public CycloneDxTask() {

        componentsProvider = getProject().getProviders().provider(new SbomGraphProvider(getProject(), this));

        outputName = getProject().getObjects().property(String.class);
        outputName.convention("bom");

        schemaVersion = getProject().getObjects().property(Version.class);
        schemaVersion.convention(CycloneDxUtils.DEFAULT_SCHEMA_VERSION);
        schemaVersionAsString = getProject().getObjects().property(String.class);
        schemaVersionAsString.convention(
                getProject().getProviders().provider(() -> schemaVersion.get().getVersionString()));

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

        projectType = getProject().getObjects().property(Component.Type.class);
        projectType.convention(DEFAULT_PROJECT_TYPE);
        projectTypeAsString = getProject().getObjects().property(String.class);
        projectTypeAsString.convention(
                getProject().getProviders().provider(() -> projectType.get().getTypeName()));

        skipProjects = getProject().getObjects().listProperty(String.class);

        organizationalEntity = getProject().getObjects().property(OrganizationalEntity.class);
        licenseChoice = getProject().getObjects().property(LicenseChoice.class);
        externalReferences = getProject().getObjects().listProperty(ExternalReference.class);

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

    /**
     * @deprecated Use {@link #getJsonOutput()} and {@link #getXmlOutput()} instead. It will be removed in version 3.0.0.
     */
    @Input
    @Deprecated
    public Property<String> getOutputName() {
        return outputName;
    }

    /**
     * @deprecated Use {@link #getJsonOutput()} and {@link #getXmlOutput()} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setOutputName(final String output) {
        this.outputName.set(output);
    }

    @Input
    public Property<String> getSchemaVersion() {
        return schemaVersionAsString;
    }

    /**
     * @deprecated Use {@link #schemaVersion} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setSchemaVersion(final String schemaVersion) {
        this.schemaVersion.set(Version.fromVersionString(schemaVersion));
    }

    @Input
    public Property<String> getComponentName() {
        return componentName;
    }

    /**
     * @deprecated Use {@link #componentName} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setComponentName(final String componentName) {
        this.componentName.set(componentName);
    }

    @Input
    public Property<String> getComponentVersion() {
        return componentVersion;
    }

    /**
     * @deprecated Use {@link #componentVersion} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setComponentVersion(final String componentVersion) {
        this.componentVersion.set(componentVersion);
    }

    /**
     * @deprecated Use {@link #getJsonOutput()} and {@link #getXmlOutput()} instead. It will be removed in version 3.0.0.
     */
    @Input
    @Deprecated
    public Property<String> getOutputFormat() {
        return outputFormat;
    }

    /**
     * @deprecated Use {@link #outputFormat} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setOutputFormat(final String format) {
        this.outputFormat.set(format);
    }

    @Input
    public Property<Boolean> getIncludeBomSerialNumber() {
        return includeBomSerialNumber;
    }

    /**
     * @deprecated Use {@link #includeBomSerialNumber} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setIncludeBomSerialNumber(final boolean includeBomSerialNumber) {
        this.includeBomSerialNumber.set(includeBomSerialNumber);
    }

    @Input
    public ListProperty<String> getSkipConfigs() {
        return skipConfigs;
    }

    /**
     * @deprecated Use {@link #skipConfigs} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setSkipConfigs(final Collection<String> skipConfigs) {
        this.skipConfigs.addAll(skipConfigs);
    }

    @Input
    public ListProperty<String> getIncludeConfigs() {
        return includeConfigs;
    }

    /**
     * @deprecated Use {@link #includeConfigs} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setIncludeConfigs(final Collection<String> includeConfigs) {
        this.includeConfigs.addAll(includeConfigs);
    }

    @Input
    public Property<Boolean> getIncludeMetadataResolution() {
        return includeMetadataResolution;
    }

    /**
     * @deprecated Use {@link #includeMetadataResolution} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setIncludeMetadataResolution(final boolean includeMetadataResolution) {
        this.includeMetadataResolution.set(includeMetadataResolution);
    }

    @Input
    public Property<Boolean> getIncludeLicenseText() {
        return includeLicenseText;
    }

    /**
     * @deprecated Use {@link #includeLicenseText} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setIncludeLicenseText(final boolean includeLicenseText) {
        this.includeLicenseText.set(includeLicenseText);
    }

    @Input
    public Property<String> getProjectType() {
        return projectTypeAsString;
    }

    /**
     * @deprecated Use {@link #projectType} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setProjectType(final String projectType) {
        this.projectType.set(Component.Type.valueOf(projectType.toUpperCase(Locale.ROOT)));
    }

    @Input
    public ListProperty<String> getSkipProjects() {
        return skipProjects;
    }

    /**
     * @deprecated It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setSkipProjects(final Collection<String> skipProjects) {
        this.skipProjects.addAll(skipProjects);
    }

    @Input
    public Property<Boolean> getIncludeBuildSystem() {
        return includeBuildSystem;
    }

    /**
     * @deprecated Use {@link #includeBuildSystem} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
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

    /**
     * @deprecated Use {@link #buildSystemEnvironmentVariable} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setBuildSystemEnvironmentVariable(final String buildSystemEnvironmentVariable) {
        this.buildSystemEnvironmentVariable.set(buildSystemEnvironmentVariable);
    }

    @Internal
    @Nullable OrganizationalEntity getOrganizationalEntity() {
        return organizationalEntity.getOrNull();
    }

    @Internal
    @Nullable LicenseChoice getLicenseChoice() {
        return licenseChoice.getOrNull();
    }

    @Internal
    List<ExternalReference> getExternalReferences() {
        return externalReferences.get();
    }

    /**
     * @deprecated Use {@link #externalReferences} instead. It will be removed in version 3.0.0.
     */
    @Internal
    @Deprecated
    @Nullable ExternalReference getGitVCS() {
        return externalReferences.get().get(0);
    }

    /**
     * @deprecated Use {@link #getJsonOutput()} and {@link #getXmlOutput()} instead. It will be removed in version 3.0.0.
     */
    @Internal
    @Deprecated
    public Property<File> getDestination() {
        return destination;
    }

    /**
     * @deprecated Use {@link #getJsonOutput()} and {@link #getXmlOutput()} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
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
        if (getJsonOutput().isPresent()) {
            CycloneDxUtils.writeJSONBom(
                    schemaVersion.get(), bom, getJsonOutput().getAsFile().get());
        }
        if (getXmlOutput().isPresent()) {
            CycloneDxUtils.writeXmlBom(
                    schemaVersion.get(), bom, getXmlOutput().getAsFile().get());
        }
    }
    /**
     * @deprecated Use {@link #organizationalEntity} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setOrganizationalEntity(final Consumer<OrganizationalEntity> customizer) {
        final OrganizationalEntity origin = new OrganizationalEntity();
        customizer.accept(origin);
        this.organizationalEntity.set(origin);

        final Map<String, String> organizationalEntity = new HashMap<>();

        organizationalEntity.put("name", origin.getName());
        if (origin.getUrls() != null) {
            for (int i = 0; i < origin.getUrls().size(); i++) {
                organizationalEntity.put("url" + i, origin.getUrls().get(i));
            }
        }
        if (origin.getContacts() != null) {
            for (int i = 0; i < origin.getContacts().size(); i++) {
                organizationalEntity.put(
                        "contact_name" + i, origin.getContacts().get(i).getName());
                organizationalEntity.put(
                        "contact_email" + i, origin.getContacts().get(i).getEmail());
                organizationalEntity.put(
                        "contact_phone" + i, origin.getContacts().get(i).getPhone());
            }
        }
        // Definition of gradle Input via Hashmap because Hashmap is serializable (OrganizationalEntity isn't
        // serializable)
        getInputs().property("OrganizationalEntity", organizationalEntity);
    }
    /**
     * @deprecated Use {@link #licenseChoice} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setLicenseChoice(final Consumer<LicenseChoice> customizer) {
        final LicenseChoice origin = new LicenseChoice();
        customizer.accept(origin);
        this.licenseChoice.set(origin);

        final Map<String, String> licenseChoice = new HashMap<>();

        if (origin.getLicenses() != null) {
            for (int i = 0; i < origin.getLicenses().size(); i++) {
                if (origin.getLicenses().get(i).getName() != null) {
                    licenseChoice.put(
                            "licenseChoice" + i + "name",
                            origin.getLicenses().get(i).getName());
                }
                if (origin.getLicenses().get(i).getId() != null) {
                    licenseChoice.put(
                            "licenseChoice" + i + "id",
                            origin.getLicenses().get(i).getId());
                }
                licenseChoice.put(
                        "licenseChoice" + i + "text",
                        origin.getLicenses().get(i).getAttachmentText().getText());
                licenseChoice.put(
                        "licenseChoice" + i + "url", origin.getLicenses().get(i).getUrl());
            }
        }

        if (origin.getExpression() != null) {
            licenseChoice.put("licenseChoice_Expression", origin.getExpression().getValue());
        }
        // Definition of gradle Input via Hashmap because Hashmap is serializable (LicenseChoice isn't serializable)
        getInputs().property("LicenseChoice", licenseChoice);
    }
    /**
     * @deprecated Use {@link #externalReferences} instead. It will be removed in version 3.0.0.
     */
    @Deprecated
    public void setVCSGit(final Consumer<ExternalReference> customizer) {
        final ExternalReference origin = new ExternalReference();
        customizer.accept(origin);

        try {
            origin.setUrl(GitUtils.sanitizeGitUrl(origin.getUrl()));
        } catch (URISyntaxException e) {
            getLogger().warn("CycloneDX: Invalid Git URL provided, ignoring it");
            return;
        }

        origin.setType(ExternalReference.Type.VCS);
        this.externalReferences.add(origin);

        final Map<String, String> externalReference = new HashMap<>();

        externalReference.put("type", origin.getType().toString());
        externalReference.put("url", origin.getUrl());
        externalReference.put("comment", origin.getComment());

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
            getLogger().info("componentName             : " + componentName.get());
            getLogger().info("componentVersion          : " + componentVersion.get());
            getLogger().info("projectType               : " + projectType.get());
            getLogger().info("jsonOutput                : {}", getJsonOutput().getOrNull());
            getLogger().info("xmlOutput                 : {}", getXmlOutput().getOrNull());
            getLogger().info("------------------------------------------------------------------------");
        }
    }
}
