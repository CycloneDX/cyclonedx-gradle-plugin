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
import com.github.packageurl.PackageURL;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.model.ArtifactInfo;
import org.cyclonedx.gradle.model.ComponentComparator;
import org.cyclonedx.gradle.model.DependencyComparator;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Hash;
import org.cyclonedx.util.BomUtils;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.logging.Logger;

public class CycloneDxParser {

    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";

    private final Set<Dependency> dependencies;
    private final Set<Component> components;
    private final Logger logger;
    private final Map<File, List<Hash>> artifactHashes;
    private final Map<String, File> resolvedArtifacts;
    private final MavenHelper mavenHelper;
    private final Version version;

    public CycloneDxParser(final Logger logger) {
        this.logger = logger;
        this.version = CycloneDxUtils.schemaVersion("1.6");
        this.dependencies = new TreeSet<>(new DependencyComparator());
        this.components = new TreeSet<>(new ComponentComparator());
        this.resolvedArtifacts = new HashMap<>();
        this.artifactHashes = new HashMap<>();
        this.mavenHelper = new MavenHelper(logger, version, false);
    }

    public void registerArtifact(final ArtifactInfo artifact) {
        resolvedArtifacts.put(artifact.getComponentId(), artifact.getArtifactFile());
    }

    public void visitGraph(final ResolvedComponentResult rootNode, final String projectName, final String configName) {

        final Set<ResolvedComponentResult> seen = new HashSet<>();
        final Queue<ResolvedComponentResult> queue = new ArrayDeque<>();
        queue.add(rootNode);

        while (!queue.isEmpty()) {
            final ResolvedComponentResult node = queue.poll();
            if (!seen.contains(node)) {
                seen.add(node);
                final Dependency dependency = toDependency(node, projectName, configName);
                for (DependencyResult dep : node.getDependencies()) {
                    if (dep instanceof ResolvedDependencyResult) {
                        final ResolvedComponentResult dependencyComponent =
                                ((ResolvedDependencyResult) dep).getSelected();
                        dependency.addDependency(toDependency(dependencyComponent, projectName, configName));
                        queue.add(dependencyComponent);
                    }
                }
                dependencies.add(dependency);
                final File artifactFile = resolvedArtifacts.get(node.getId().getDisplayName());
                components.add(toComponent(node, artifactFile, projectName, configName));
            }
        }
    }

    public Bom getResultingBom() {
        final Bom bom = new Bom();
        bom.setComponents(new ArrayList<>(components));
        bom.setDependencies(new ArrayList<>(dependencies));
        return bom;
    }

    public Component toComponent(
            final ResolvedComponentResult resolvedComponent,
            final File artifactFile,
            final String projectName,
            final String configName) {

        final Component component = new Component();
        component.setGroup(resolvedComponent.getModuleVersion().getGroup());
        component.setName(resolvedComponent.getModuleVersion().getName());
        component.setVersion(resolvedComponent.getModuleVersion().getVersion());
        component.setType(Component.Type.LIBRARY);
        logger.debug(MESSAGE_CALCULATING_HASHES);
        if (artifactFile != null) {
            component.setHashes(calculateHashes(artifactFile));
        }

        final TreeMap<String, String> qualifiers = new TreeMap<>();
        final String packageUrl = generatePackageUrl(resolvedComponent.getModuleVersion(), qualifiers);
        component.setPurl(packageUrl);

        if (version.getVersion() >= 1.1) {
            component.setModified(mavenHelper.isModified(null));
            component.setBomRef(generateRef(resolvedComponent.getModuleVersion(), qualifiers, projectName, configName));
        }
        return component;
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

    private Dependency toDependency(
            final ResolvedComponentResult component, final String projectName, final String configName) {

        TreeMap<String, String> qualifiers = new TreeMap<>();
        String ref = generateRef(component.getModuleVersion(), qualifiers, projectName, configName);
        return new Dependency(ref);
    }

    private String generateRef(
            final ModuleVersionIdentifier version,
            final TreeMap<String, String> qualifiers,
            final String projectName,
            final String configName) {
        String purl = generatePackageUrl(version, qualifiers);
        return String.format("%s:%s:%s", projectName, configName, purl);
    }

    @Nullable private String generatePackageUrl(final ModuleVersionIdentifier version, final TreeMap<String, String> qualifiers) {
        try {
            return new PackageURL(
                            PackageURL.StandardTypes.MAVEN,
                            version.getGroup(),
                            version.getName(),
                            version.getVersion(),
                            qualifiers,
                            null)
                    .canonicalize();
        } catch (MalformedPackageURLException e) {
            logger.warn("An unexpected issue occurred attempting to create a PackageURL for " + version.getGroup() + ":"
                    + version.getName() + ":" + version);
        }
        return null;
    }
}
