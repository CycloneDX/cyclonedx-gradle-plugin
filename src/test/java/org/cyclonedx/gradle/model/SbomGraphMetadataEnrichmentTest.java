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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.License;
import org.cyclonedx.model.Component;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The metadata enrichment line is part of the build cache key, and it is the only thing that separates a document
 * written from stale metadata from one written from current metadata when both fingerprint the same POM files. Two
 * different enrichment results must therefore never encode to the same line, whatever the values contain.
 */
class SbomGraphMetadataEnrichmentTest {

    private static final SbomComponentId ID = new SbomComponentId("com.test", "lib", "1.0.0", "jar", null);

    @Test
    void aNewlineInsideAValueCannotPoseAsTheNextField() {
        // The two documents differ: one has a publisher containing a newline and no description, the other has a
        // plain publisher and a description
        String withNewline = enrichment(metaData("Acme\ndescription=Legacy", null), Collections.emptyList(), null);
        String separate = enrichment(metaData("Acme", "Legacy"), Collections.emptyList(), null);

        assertNotEquals(withNewline, separate);
    }

    @Test
    void aSeparatorInsideALicenseNameCannotPoseAsItsUrl() {
        // The name of one carries what is the url of the other, and the url of the other carries the rest
        License separatorInName = license("MIT|https://x", "y");
        License separatorInUrl = license("MIT", "https://x|y");

        assertNotEquals(
                enrichment(null, Collections.singletonList(separatorInName), null),
                enrichment(null, Collections.singletonList(separatorInUrl), null));
    }

    @Test
    void anAbsentValueDiffersFromAnEmptyOne() {
        assertNotEquals(
                enrichment(metaData("Acme", null), Collections.emptyList(), null),
                enrichment(metaData("Acme", ""), Collections.emptyList(), null));
    }

    @Test
    void twoLicensesDifferFromOneLicenseThatSpellsOutBoth() {
        List<License> two = Arrays.asList(license("A", "https://a"), license("B", "https://b"));
        List<License> one = Collections.singletonList(license("A", "https://a\nlicense=B|https://b"));

        assertNotEquals(enrichment(null, two, null), enrichment(null, one, null));
    }

    @Test
    void whatCouldNotBeReadIsPartOfTheLine() {
        // Two components that carry the same licenses and differ only in what the document says went wrong
        assertNotEquals(
                enrichment(null, Collections.emptyList(), UnresolvedMetadata.PARENT_UNRESOLVED),
                enrichment(null, Collections.emptyList(), UnresolvedMetadata.MODEL_INCOMPLETE));
    }

    @Test
    void aComponentThatCouldNotBeReadDiffersFromOneThatWas() {
        assertNotEquals(
                enrichment(null, Collections.emptyList(), UnresolvedMetadata.PARENT_UNRESOLVED),
                enrichment(metaData("Acme", null), Collections.emptyList(), null));
    }

    @Test
    void theWordNullIsNotAnAbsentValue() {
        assertNotEquals(
                enrichment(metaData("null", null), Collections.emptyList(), null),
                enrichment(metaData(null, null), Collections.emptyList(), null));
    }

    @Test
    void twoExternalReferencesDifferFromOneWhoseUrlSpellsOutTheOther() {
        SbomMetaData two = metaData(null, null);
        two.addExternalReference("website", "https://a");
        two.addExternalReference("vcs", "https://b");
        SbomMetaData one = metaData(null, null);
        one.addExternalReference(
                "website", "https://a\nexternalReference.type=3:vcs\nexternalReference.url=9:https://b");

        assertNotEquals(enrichment(two, Collections.emptyList(), null), enrichment(one, Collections.emptyList(), null));
    }

    @Test
    void theSameMetadataEncodesToTheSameLine() {
        assertEquals(
                enrichment(metaData("Acme", "Legacy"), Collections.singletonList(license("MIT", "https://mit")), null),
                enrichment(metaData("Acme", "Legacy"), Collections.singletonList(license("MIT", "https://mit")), null));
    }

    @Test
    void aSeparatorInsideACoordinateCannotMakeTwoComponentsShareOneKey() {
        // Maven coordinates cannot contain a colon, but the key must not depend on that to stay one per component
        SbomComponentId groupCarriesName = new SbomComponentId("a:b", "c", "1", null, null);
        SbomComponentId nameCarriesGroup = new SbomComponentId("a", "b:c", "1", null, null);
        Map<SbomComponentId, SbomComponent> graph = new HashMap<>();
        graph.put(groupCarriesName, enriched(groupCarriesName, "first"));
        graph.put(nameCarriesGroup, enriched(nameCarriesGroup, "second"));

        assertEquals(
                2,
                new SbomGraph(graph, graph.get(groupCarriesName))
                        .getMetadataEnrichment()
                        .size());
    }

    @Test
    void componentsWithoutEnrichmentHaveNoEntry() {
        SbomComponent bare = new SbomComponent.Builder().withId(ID).build();
        Map<SbomComponentId, SbomComponent> graph = new HashMap<>();
        graph.put(ID, bare);

        assertTrue(new SbomGraph(graph, bare).getMetadataEnrichment().isEmpty());
    }

    private static String enrichment(
            @Nullable final SbomMetaData metaData,
            final List<License> licenses,
            @Nullable final UnresolvedMetadata unresolved) {
        SbomComponent component = new SbomComponent.Builder()
                .withId(ID)
                .withMetaData(metaData)
                .withLicenses(licenses)
                .withUnresolvedMetadata(unresolved)
                .build();
        Map<SbomComponentId, SbomComponent> graph = new HashMap<>();
        graph.put(ID, component);
        Map<String, String> enrichment = new SbomGraph(graph, component).getMetadataEnrichment();
        assertEquals(1, enrichment.size());
        return enrichment.values().iterator().next();
    }

    private static SbomComponent enriched(final SbomComponentId id, final String publisher) {
        return new SbomComponent.Builder()
                .withId(id)
                .withMetaData(metaData(publisher, null))
                .build();
    }

    private static SbomMetaData metaData(@Nullable final String publisher, @Nullable final String description) {
        Component component = new Component();
        component.setPublisher(publisher);
        component.setDescription(description);
        return SbomMetaData.fromComponent(component);
    }

    private static License license(final String name, @Nullable final String url) {
        License license = new License();
        license.setName(name);
        license.setUrl(url);
        return license;
    }
}
