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
package org.cyclonedx.gradle.utils;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.cyclonedx.gradle.model.SbomComponent;
import org.cyclonedx.gradle.model.SbomComponentId;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;

public class DependencyUtils {

    private static final String UNSPECIFIED = "unspecified";

    public static Map<SbomComponentId, SbomComponent> mergeGraphs(
            final Map<SbomComponentId, SbomComponent> firstGraph,
            final Map<SbomComponentId, SbomComponent> secondGraph) {

        final Map<SbomComponentId, SbomComponent> mergedGraph = new HashMap<>(firstGraph);
        secondGraph.keySet().stream().forEach(id -> {
            if (firstGraph.containsKey(id)) {
                SbomComponent resultComponent = mergedGraph.get(id);
                SbomComponent targetComponent = secondGraph.get(id);
                resultComponent.getDependencyComponents().addAll(targetComponent.getDependencyComponents());
                resultComponent.getInScopeConfigurations().addAll(targetComponent.getInScopeConfigurations());
            } else {
                mergedGraph.put(id, secondGraph.get(id));
            }
        });

        return mergedGraph;
    }

    public static SbomComponentId toComponentId(final ResolvedComponentResult node, final File file) {

        @Nullable String type = null;
        @Nullable String projectPath = null;
        if (node.getId() instanceof ModuleComponentIdentifier) {
            if (file != null) {
                type = getType(file);
            } else {
                type = "pom";
            }
        } else if (node.getId() instanceof ProjectComponentIdentifier) {
            final ProjectComponentIdentifier id = (ProjectComponentIdentifier) node.getId();
            projectPath = id.getProjectPath();
        }

        if (node.getModuleVersion() != null) {
            return new SbomComponentId(
                    node.getModuleVersion().getGroup(),
                    node.getModuleVersion().getName(),
                    node.getModuleVersion().getVersion(),
                    type,
                    projectPath);
        } else {
            return new SbomComponentId(UNSPECIFIED, node.getId().getDisplayName(), UNSPECIFIED, type, projectPath);
        }
    }

    private static String getType(final File file) {

        final String fileExtension = FilenameUtils.getExtension(file.getName());
        if (StringUtils.isBlank(fileExtension)) {
            return "pom";
        }

        return fileExtension;
    }

    public static String generatePackageUrl(final SbomComponentId componentId) throws MalformedPackageURLException {
        return new PackageURL(
                        PackageURL.StandardTypes.MAVEN,
                        componentId.getGroup().isEmpty() ? UNSPECIFIED : componentId.getGroup(),
                        componentId.getName(),
                        componentId.getVersion().isEmpty() ? UNSPECIFIED : componentId.getVersion(),
                        componentId.getQualifiers(),
                        null)
                .canonicalize();
    }
}
