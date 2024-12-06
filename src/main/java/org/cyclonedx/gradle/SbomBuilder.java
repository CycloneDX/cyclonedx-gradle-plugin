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

import com.github.packageurl.MalformedPackageURLException;
import com.networknt.schema.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.ComponentComparator;
import org.cyclonedx.gradle.model.DependencyComparator;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.cyclonedx.gradle.utils.EnvironmentUtils;
import org.cyclonedx.gradle.utils.ExternalReferencesUtil;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.Tool;
import org.cyclonedx.model.metadata.ToolInformation;
import org.cyclonedx.util.BomUtils;
import org.gradle.api.logging.Logger;

/**
 * Generates the CycloneDX Bom from the aggregated dependency graph taking into account the provided
 * user configuration (componentName, includeBomSerialNumber,...)
 */
class SbomBuilder {

    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    private static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";

    private static final TreeMap<String, String> EMPTY_TYPE = new TreeMap<>();

    private final Logger logger;
    private final Map<File, List<Hash>> artifactHashes;
    private final MavenHelper mavenHelper;
    private final Version version;
    private final CycloneDxTask task;

    SbomBuilder(final Logger logger, final CycloneDxTask task) {
        this.logger = logger;
        this.version = CycloneDxUtils.schemaVersion(task.getSchemaVersion().get());
        this.artifactHashes = new HashMap<>();
        this.mavenHelper = new MavenHelper(logger, task.getIncludeLicenseText().get());
        this.task = task;
    }

    /**
     * Builds the CycloneDX Bom from the aggregated dependency graph
     *
     * @param resultGraph the aggregated dependency graph across all the configurations
     * @param rootComponent the root component of the graph which is the parent project
     *
     * @return the CycloneDX Bom
     */
    Bom buildBom(final Map<SbomComponentId, SbomComponent> resultGraph, final SbomComponent rootComponent) {

        task.getLogger().info(MESSAGE_CREATING_BOM);

        final Set<Dependency> dependencies = new TreeSet<>(new DependencyComparator());
        final Set<Component> components = new TreeSet<>(new ComponentComparator());

        resultGraph.keySet().forEach(componentId -> {
            addDependency(dependencies, resultGraph.get(componentId));
            addComponent(components, resultGraph.get(componentId), rootComponent);
        });

        final Bom bom = new Bom();
        if (task.getIncludeBomSerialNumber().get()) bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
        bom.setMetadata(buildMetadata(rootComponent));
        bom.setComponents(new ArrayList<>(components));
        bom.setDependencies(new ArrayList<>(dependencies));
        return bom;
    }

    private Metadata buildMetadata(final SbomComponent parentComponent) {
        final Metadata metadata = new Metadata();
        try {
            final Component component = toComponent(parentComponent, null, resolveProjectType());
            component.setProperties(null);
            component.setName(task.getComponentName().get());
            component.setVersion(task.getComponentVersion().get());
            addBuildSystemMetaData(component);
            component.addExternalReference(task.getGitVCS());
            ExternalReferencesUtil.complementByEnvironment(component);
            metadata.setComponent(component);
        } catch (MalformedPackageURLException e) {
            logger.warn(
                    "Error constructing packageUrl for parent component {}. Skipping...",
                    parentComponent.getId().getName(),
                    e);
        }
        metadata.setLicenseChoice(task.getLicenseChoice());
        metadata.setManufacture(task.getOrganizationalEntity());

        final Properties pluginProperties = readPluginProperties();
        if (!pluginProperties.isEmpty()) {
            // if schema version is 1.5 or higher use tools instead of tool
            if (version.compareTo(Version.VERSION_15) >= 0) {
                final Component component = new Component();
                component.setType(Component.Type.APPLICATION);
                component.setAuthor(pluginProperties.getProperty("vendor"));
                component.setName(pluginProperties.getProperty("name"));
                component.setVersion(pluginProperties.getProperty("version"));
                final ToolInformation tool = new ToolInformation();
                tool.setComponents(Collections.singletonList(component));
                metadata.setToolChoice(tool);
            } else {
                final Tool tool = new Tool();
                tool.setVendor(pluginProperties.getProperty("vendor"));
                tool.setName(pluginProperties.getProperty("name"));
                tool.setVersion(pluginProperties.getProperty("version"));
                metadata.addTool(tool);
            }
        }

        return metadata;
    }

    private void addBuildSystemMetaData(final Component component) {
        if (task.getIncludeBuildSystem().get()) {
            String url;
            if (task.getBuildSystemEnvironmentVariable().isPresent()
                    && task.getBuildSystemEnvironmentVariable().get() != null) {
                url = EnvironmentUtils.getBuildURI(
                        task.getBuildSystemEnvironmentVariable().get());
            } else {
                url = EnvironmentUtils.getBuildURI();
            }
            if (url != null) {
                ExternalReference buildRef = new ExternalReference();
                buildRef.setType(ExternalReference.Type.BUILD_SYSTEM);
                buildRef.setUrl(url);
                component.addExternalReference(buildRef);
            }
        }
    }

    private void addDependency(final Set<Dependency> dependencies, final SbomComponent component) {

        final Dependency dependency;
        try {
            dependency = toDependency(component.getId());
        } catch (MalformedPackageURLException e) {
            logger.warn(
                    "Error constructing packageUrl for component {}. Skipping...",
                    component.getId().getName(),
                    e);
            return;
        }
        component.getDependencyComponents().forEach(dependencyComponent -> {
            try {
                dependency.addDependency(toDependency(dependencyComponent));
            } catch (MalformedPackageURLException e) {
                logger.warn(
                        "Error constructing packageUrl for component dependency {}. Skipping...",
                        dependencyComponent.getName(),
                        e);
            }
        });
        dependencies.add(dependency);
    }

    private Dependency toDependency(final SbomComponentId componentId) throws MalformedPackageURLException {

        final String ref = DependencyUtils.generatePackageUrl(componentId, getQualifiers(componentId.getType()));
        return new Dependency(ref);
    }

    private void addComponent(
            final Set<Component> components, final SbomComponent component, final SbomComponent parentComponent) {
        if (!component.equals(parentComponent)) {
            @Nullable final File artifactFile = component.getArtifactFile().orElse(null);
            try {
                components.add(toComponent(component, artifactFile, Component.Type.LIBRARY));
            } catch (MalformedPackageURLException e) {
                logger.warn(
                        "Error constructing packageUrl for component {}. Skipping...",
                        component.getId().getName(),
                        e);
            }
        }
    }

    private Component toComponent(final SbomComponent component, final File artifactFile, final Component.Type type)
            throws MalformedPackageURLException {

        final String packageUrl = DependencyUtils.generatePackageUrl(
                component.getId(), getQualifiers(component.getId().getType()));

        final Component resultComponent = new Component();
        resultComponent.setGroup(component.getId().getGroup());
        resultComponent.setName(component.getId().getName());
        resultComponent.setVersion(component.getId().getVersion());
        resultComponent.setType(type);
        resultComponent.setPurl(packageUrl);
        resultComponent.setProperties(buildProperties(component));
        resultComponent.setModified(mavenHelper.isModified(null));
        resultComponent.setBomRef(packageUrl);

        component.getSbomMetaData().ifPresent(metaData -> {
            resultComponent.setDescription(metaData.getDescription());
            resultComponent.setPublisher(metaData.getPublisher());
            metaData.getExternalReferences().forEach(reference -> {
                final ExternalReference ref = new ExternalReference();
                ref.setType(ExternalReference.Type.valueOf(reference.getType()));
                ref.setUrl(reference.getUrl());
                resultComponent.addExternalReference(ref);
            });
        });

        if (!component.getLicenses().isEmpty()) {
            LicenseChoice licenseChoice = mavenHelper.resolveMavenLicenses(component.getLicenses());
            resultComponent.setLicenses(licenseChoice);
        }

        logger.debug(MESSAGE_CALCULATING_HASHES);
        if (artifactFile != null) {
            resultComponent.setHashes(calculateHashes(artifactFile));
        }

        return resultComponent;
    }

    private List<Property> buildProperties(final SbomComponent component) {
        final Property isTestProperty = buildIsTestProperty(component);
        final List<Property> resultProperties = new ArrayList<>();
        resultProperties.add(isTestProperty);

        return resultProperties;
    }

    private Property buildIsTestProperty(final SbomComponent component) {

        boolean isTestComponent = component.getInScopeConfigurations().stream()
                .allMatch(v -> v.getConfigName().startsWith("test"));

        Property property = new Property();
        property.setName("cdx:maven:package:test");
        property.setValue(Boolean.toString(isTestComponent));
        return property;
    }

    private List<Hash> calculateHashes(final File artifactFile) {
        return artifactHashes.computeIfAbsent(artifactFile, f -> {
            try {
                return BomUtils.calculateHashes(f, version);
            } catch (IOException e) {
                logger.error("Error encountered calculating hashes", e);
            }
            return Collections.emptyList();
        });
    }

    private Component.Type resolveProjectType() {
        for (Component.Type type : Component.Type.values()) {
            if (type.getTypeName().equalsIgnoreCase(task.getProjectType().get())) {
                return type;
            }
        }
        logger.warn("Invalid project type. Defaulting to 'library'");
        logger.warn("Valid types are:");
        for (Component.Type type : Component.Type.values()) {
            logger.warn("  " + type.getTypeName());
        }
        return Component.Type.LIBRARY;
    }

    private TreeMap<String, String> getQualifiers(final String type) {
        if (StringUtils.isBlank(type)) {
            return EMPTY_TYPE;
        }

        final TreeMap<String, String> qualifiers = new TreeMap<>();
        qualifiers.put("type", type);
        return qualifiers;
    }

    private Properties readPluginProperties() {

        final Properties props = new Properties();
        try (final InputStream inputStream = this.getClass().getResourceAsStream("plugin.properties")) {
            if (inputStream == null) {
                logger.info("plugin.properties is not found on the classpath");
            } else {
                props.load(inputStream);
            }
        } catch (Exception e) {
            logger.warn("Error whilst loading plugin.properties", e);
        }
        return props;
    }
}
