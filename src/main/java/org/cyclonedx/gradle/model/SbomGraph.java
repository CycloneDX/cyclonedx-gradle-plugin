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
package org.cyclonedx.gradle.model;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.model.License;
import org.cyclonedx.model.ExternalReference;
import org.jspecify.annotations.Nullable;

/**
 * Represents the aggregated dependency graph across all the configurations of the projects in scope. It is fully
 * serializable to support the build cache.
 */
public class SbomGraph implements Serializable {

    private final Map<SbomComponentId, SbomComponent> graph;
    private final SbomComponent rootComponent;
    private final Set<File> pomFiles;

    public SbomGraph(final Map<SbomComponentId, SbomComponent> graph, final SbomComponent rootComponent) {
        this(graph, rootComponent, Collections.emptySet());
    }

    public SbomGraph(
            final Map<SbomComponentId, SbomComponent> graph,
            final SbomComponent rootComponent,
            final Set<File> pomFiles) {
        this.graph = graph;
        this.rootComponent = rootComponent;
        this.pomFiles = Collections.unmodifiableSet(pomFiles);
    }

    public Map<SbomComponentId, SbomComponent> getGraph() {
        return graph;
    }

    public SbomComponent getRootComponent() {
        return rootComponent;
    }

    /**
     * Every POM read while enriching the components of this graph: the component POMs, and the parent and imported
     * POMs the effective models were built from. Together with the artifacts they are the evidence the SBOM was
     * produced from, so the task declares them as inputs.
     */
    public Set<File> getPomFiles() {
        return pomFiles;
    }

    /**
     * What metadata enrichment contributed to each component, as one canonical line per component keyed by its
     * coordinates: licenses, publisher, description, external references and the unresolved-metadata value. This is
     * what the document is written from, so the task declares it as an input beside the POM files it was read from:
     * a document is then only ever served to a build whose metadata is the same, even where the files and the
     * metadata were read at different moments. Components that were not looked up, project components among them,
     * have no entry, so the map is empty when metadata resolution is disabled. Every value is written with its length
     * in front and an absent value is marked as such, so two different enrichment results never encode alike,
     * whatever the values contain: this line is what tells a stale document from a current one when both fingerprint
     * the same files, so nothing else may be relied on to separate them.
     */
    public Map<String, String> getMetadataEnrichment() {
        final Map<String, String> enrichment = new TreeMap<>();
        for (final SbomComponent component : graph.values()) {
            final String description = describeEnrichment(component);
            if (!description.isEmpty()) {
                enrichment.put(coordinates(component.getId()), description);
            }
        }
        return enrichment;
    }

    /**
     * The key of a component, its group, name, version and type each written with its length in front, so that a
     * separator inside one part cannot make two components share one key.
     */
    private static String coordinates(final SbomComponentId id) {
        final StringBuilder coordinates = new StringBuilder();
        part(coordinates, id.getGroup());
        part(coordinates, id.getName());
        part(coordinates, id.getVersion());
        part(coordinates, id.getType());
        return coordinates.toString();
    }

    private static void part(final StringBuilder coordinates, @Nullable final String value) {
        if (coordinates.length() > 0) {
            coordinates.append(':');
        }
        if (value == null) {
            coordinates.append("null");
        } else {
            coordinates.append(value.length()).append(':').append(value);
        }
    }

    private static String describeEnrichment(final SbomComponent component) {
        final StringBuilder description = new StringBuilder();
        for (final License license : component.getLicenses()) {
            field(description, "license.name", license.getName());
            field(description, "license.url", license.getUrl());
        }
        component.getSbomMetaData().ifPresent(metaData -> {
            field(description, "publisher", metaData.getPublisher());
            field(description, "description", metaData.getDescription());
            for (final ExternalReference reference : metaData.getExternalReferences()) {
                field(description, "externalReference.type", reference.getType().getTypeName());
                field(description, "externalReference.url", reference.getUrl());
            }
        });
        component
                .getUnresolvedMetadata()
                .ifPresent(unresolved -> field(description, "unresolved", unresolved.getValue()));
        return description.toString();
    }

    /**
     * Appends one field as {@code name=<length>:<value>} on a line of its own, or {@code name=null} where the value
     * is absent. The length states how many characters belong to the value, so a newline or any other character
     * inside it cannot be mistaken for the start of the next field, and an empty value is not an absent one.
     */
    private static void field(final StringBuilder description, final String name, @Nullable final String value) {
        description.append(name).append('=');
        if (value == null) {
            description.append("null");
        } else {
            description.append(value.length()).append(':').append(value);
        }
        description.append('\n');
    }
}
