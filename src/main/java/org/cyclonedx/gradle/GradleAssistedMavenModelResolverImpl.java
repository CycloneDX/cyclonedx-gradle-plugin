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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.resolution.ModelResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class GradleAssistedMavenModelResolverImpl implements ModelResolver {
    private final Project project;

    public GradleAssistedMavenModelResolverImpl(Project project) {
        super();
        this.project = project;
    }

    @Override
    public ModelSource2 resolveModel(String groupId, String artifactId, String version) {
        String depNotation = String.format("%s:%s:%s@pom", groupId, artifactId, version);
        org.gradle.api.artifacts.Dependency dependency =
                project.getDependencies().create(depNotation);
        Configuration config = project.getConfigurations().detachedConfiguration(dependency);

        File pomXml = config.getSingleFile();
        return new ModelSource2() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(pomXml);
            }

            @Override
            public String getLocation() {
                return pomXml.getAbsolutePath();
            }

            @Override
            public ModelSource2 getRelatedSource(String relPath) {
                return null;
            }

            @Override
            public URI getLocationURI() {
                return null;
            }
        };
    }

    @Override
    public ModelSource2 resolveModel(Parent parent) {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public ModelSource2 resolveModel(Dependency dependency) {
        return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    @Override
    public void addRepository(Repository repository) {
        // ignore
    }

    @Override
    public void addRepository(Repository repository, boolean replace) {
        // ignore
    }

    @Override
    public ModelResolver newCopy() {
        return this;
    }
}
