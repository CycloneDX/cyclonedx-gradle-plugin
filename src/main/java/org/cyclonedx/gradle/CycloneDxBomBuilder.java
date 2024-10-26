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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.ComponentComparator;
import org.cyclonedx.gradle.model.DependencyComparator;
import org.cyclonedx.gradle.model.SerializableComponent;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.util.BomUtils;
import org.gradle.api.logging.Logger;

public class CycloneDxBomBuilder {

    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    private static final TreeMap<String, String> EMPTY_TYPE = new TreeMap<>();

    private final Logger logger;
    private final Map<File, List<Hash>> artifactHashes;
    private final MavenHelper mavenHelper;
    private final Version version;

    public CycloneDxBomBuilder(final Logger logger) {
        this.logger = logger;
        this.version = CycloneDxUtils.DEFAULT_SCHEMA_VERSION;
        this.artifactHashes = new HashMap<>();
        this.mavenHelper = new MavenHelper(logger, version, false);
    }

    public Bom buildBom(
            final Map<SerializableComponent, Set<SerializableComponent>> resultGraph,
            final SerializableComponent parentComponent) {

        final Set<Dependency> dependencies = new TreeSet<>(new DependencyComparator());
        final Set<Component> components = new TreeSet<>(new ComponentComparator());

        resultGraph.keySet().forEach(component -> {
            addDependency(dependencies, resultGraph.get(component), component);
            addComponent(components, component, parentComponent);
        });

        final Bom bom = new Bom();
        bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
        bom.setMetadata(buildMetadata(parentComponent));
        bom.setComponents(new ArrayList<>(components));
        bom.setDependencies(new ArrayList<>(dependencies));
        return bom;
    }

    private Metadata buildMetadata(final SerializableComponent parentComponent) {
        final Metadata metadata = new Metadata();
        try {
            metadata.setComponent(toComponent(parentComponent, null));
        } catch (MalformedPackageURLException e) {
            logger.warn("Error constructing packageUrl for parent component. Skipping...", e);
        }
        return metadata;
    }

    private void addDependency(
            final Set<Dependency> dependencies,
            final Set<SerializableComponent> dependencyComponents,
            final SerializableComponent component) {

        final Dependency dependency;
        try {
            dependency = toDependency(component);
        } catch (MalformedPackageURLException e) {
            logger.warn("Error constructing packageUrl for component. Skipping...", e);
            return;
        }
        dependencyComponents.forEach(dependencyComponent -> {
            try {
                dependency.addDependency(toDependency(dependencyComponent));
            } catch (MalformedPackageURLException e) {
                logger.warn("Error constructing packageUrl for component dependency. Skipping...", e);
            }
        });
        dependencies.add(dependency);
    }

    private Dependency toDependency(final SerializableComponent component) throws MalformedPackageURLException {

        final String ref = DependencyUtils.generatePackageUrl(
                component, getType(component.getArtifactFile().orElse(null)));
        return new Dependency(ref);
    }

    private void addComponent(
            final Set<Component> components,
            final SerializableComponent component,
            final SerializableComponent parentComponent) {
        if (!component.equals(parentComponent)) {
            final File artifactFile = component.getArtifactFile().orElse(null);
            try {
                components.add(toComponent(component, artifactFile));
            } catch (MalformedPackageURLException e) {
                logger.warn("Error constructing packageUrl for component. Skipping...", e);
            }
        }
    }

    private Component toComponent(final SerializableComponent component, final File artifactFile)
            throws MalformedPackageURLException {

        final String packageUrl = DependencyUtils.generatePackageUrl(component, getType(artifactFile));

        final Component resultComponent = new Component();
        resultComponent.setGroup(component.getGroup());
        resultComponent.setName(component.getName());
        resultComponent.setVersion(component.getVersion());
        resultComponent.setType(Component.Type.LIBRARY);
        resultComponent.setPurl(packageUrl);
        resultComponent.setProperties(buildProperties(component));
        if (version.getVersion() >= 1.1) {
            resultComponent.setModified(mavenHelper.isModified(null));
            resultComponent.setBomRef(packageUrl);
        }

        logger.debug(MESSAGE_CALCULATING_HASHES);
        if (artifactFile != null) {
            resultComponent.setHashes(calculateHashes(artifactFile));
        }

        return resultComponent;
    }

    private List<Property> buildProperties(SerializableComponent component) {
        return component.getInScopeConfigurations().stream()
                .map(v -> {
                    Property property = new Property();
                    property.setName("inScopeConfiguration");
                    property.setValue(String.format("%s:%s", v.getProjectName(), v.getConfigName()));
                    return property;
                })
                .collect(Collectors.toList());
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

    private TreeMap<String, String> getType(final File file) {
        if (file == null) {
            return EMPTY_TYPE;
        }

        String fileExtension = FilenameUtils.getExtension(file.getName());
        if (StringUtils.isBlank(fileExtension)) {
            return EMPTY_TYPE;
        }

        final TreeMap<String, String> type = new TreeMap<>();
        type.put("type", fileExtension);
        return type;
    }
}
