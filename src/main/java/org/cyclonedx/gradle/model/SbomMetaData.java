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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.jspecify.annotations.Nullable;

public class SbomMetaData implements Serializable {

    @Nullable private String publisher;

    @Nullable private String description;

    private final List<ExternalReference> externalReferences = new ArrayList<>();

    private SbomMetaData() {}

    @Nullable public String getPublisher() {
        return publisher;
    }

    public void setPublisher(@Nullable final String publisher) {
        this.publisher = publisher;
    }

    @Nullable public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable final String description) {
        this.description = description;
    }

    public void addExternalReference(final String type, final String url) {
        externalReferences.add(new ExternalReference(type, url));
    }

    public List<ExternalReference> getExternalReferences() {
        return externalReferences;
    }

    public static SbomMetaData fromComponent(final Component component) {

        final SbomMetaData metaData = new SbomMetaData();
        metaData.setDescription(component.getDescription());
        metaData.setPublisher(component.getPublisher());
        if (component.getExternalReferences() != null) {
            component.getExternalReferences().forEach(reference -> {
                metaData.addExternalReference(reference.getType().getTypeName(), reference.getUrl());
            });
        }
        return metaData;
    }

    public static class ExternalReference extends org.cyclonedx.model.ExternalReference implements Serializable {

        public ExternalReference(final String type, final String url) {
            super();
            super.setType(org.cyclonedx.model.ExternalReference.Type.fromString(type));
            super.setUrl(url);
        }
    }
}
