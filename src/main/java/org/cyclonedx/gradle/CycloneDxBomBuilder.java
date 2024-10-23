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
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.ComponentComparator;
import org.cyclonedx.gradle.model.DependencyComparator;
import org.cyclonedx.gradle.model.GraphNode;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.gradle.utils.DependencyUtils;
import org.cyclonedx.model.*;
import org.cyclonedx.util.BomUtils;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.logging.Logger;

public class CycloneDxBomBuilder {

    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    private static final TreeMap<String, String> DEFAULT_TYPE = new TreeMap<>();

    static {
        DEFAULT_TYPE.put("type", "jar");
    }

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
            final Map<GraphNode, Set<GraphNode>> resultGraph,
            final GraphNode parentNode,
            final Map<ComponentIdentifier, File> resolvedArtifacts) {

        final Set<Dependency> dependencies = new TreeSet<>(new DependencyComparator());
        final Set<Component> components = new TreeSet<>(new ComponentComparator());

        resultGraph.keySet().forEach(node -> {
            addDependency(dependencies, resultGraph.get(node), node, resolvedArtifacts);
            addComponent(components, node, parentNode, resolvedArtifacts);
        });

        final Bom bom = new Bom();
        bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
        bom.setMetadata(buildMetadata(parentNode));
        bom.setComponents(new ArrayList<>(components));
        bom.setDependencies(new ArrayList<>(dependencies));
        return bom;
    }

    private Metadata buildMetadata(final GraphNode parentNode) {
        final Metadata metadata = new Metadata();
        try {
            metadata.setComponent(toComponent(parentNode, null));
        } catch (MalformedPackageURLException e) {
            logger.warn("Error constructing packageUrl for parent component. Skipping...", e);
        }
        return metadata;
    }

    private void addDependency(
            final Set<Dependency> dependencies,
            final Set<GraphNode> dependencyNodes,
            final GraphNode node,
            final Map<ComponentIdentifier, File> resolvedArtifacts) {

        final Dependency dependency;
        try {
            dependency = toDependency(node.getResult(), resolvedArtifacts);
        } catch (MalformedPackageURLException e) {
            logger.warn("Error constructing packageUrl for node. Skipping...", e);
            return;
        }
        dependencyNodes.forEach(dependencyNode -> {
            try {
                dependency.addDependency(toDependency(dependencyNode.getResult(), resolvedArtifacts));
            } catch (MalformedPackageURLException e) {
                logger.warn("Error constructing packageUrl for node dependency. Skipping...", e);
            }
        });
        dependencies.add(dependency);
    }

    private Dependency toDependency(
            final ResolvedComponentResult component, final Map<ComponentIdentifier, File> resolvedArtifacts)
            throws MalformedPackageURLException {

        final File artifactFile = resolvedArtifacts.get(component.getId());
        final String ref = DependencyUtils.generatePackageUrl(component.getModuleVersion(), getType(artifactFile));
        return new Dependency(ref);
    }

    private void addComponent(
            final Set<Component> components,
            final GraphNode node,
            final GraphNode parentNode,
            final Map<ComponentIdentifier, File> resolvedArtifacts) {
        if (!node.equals(parentNode)) {
            final File artifactFile = resolvedArtifacts.get(node.getResult().getId());
            try {
                components.add(toComponent(node, artifactFile));
            } catch (MalformedPackageURLException e) {
                logger.warn("Error constructing packageUrl for node component. Skipping...", e);
            }
        }
    }

    private Component toComponent(final GraphNode node, final File artifactFile) throws MalformedPackageURLException {

        final ModuleVersionIdentifier moduleVersion = node.getResult().getModuleVersion();
        final String packageUrl = DependencyUtils.generatePackageUrl(moduleVersion, getType(artifactFile));

        final Component component = new Component();
        component.setGroup(moduleVersion.getGroup());
        component.setName(moduleVersion.getName());
        component.setVersion(moduleVersion.getVersion());
        component.setType(Component.Type.LIBRARY);
        component.setPurl(packageUrl);
        component.setProperties(buildProperties(node));
        if (version.getVersion() >= 1.1) {
            component.setModified(mavenHelper.isModified(null));
            component.setBomRef(packageUrl);
        }

        logger.debug(MESSAGE_CALCULATING_HASHES);
        if (artifactFile != null) {
            component.setHashes(calculateHashes(artifactFile));
        }

        return component;
    }

    private List<Property> buildProperties(GraphNode node) {
        return node.getInScopeConfigurations().stream()
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
            return DEFAULT_TYPE;
        }
        final TreeMap<String, String> type = new TreeMap<>();
        type.put("type", FilenameUtils.getExtension(file.getName()));
        return type;
    }
}
