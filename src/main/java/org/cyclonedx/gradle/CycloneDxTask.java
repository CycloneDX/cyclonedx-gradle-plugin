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
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.cyclonedx.gradle;

import org.apache.commons.io.FileUtils;
import org.cyclonedx.BomGenerator;
import org.cyclonedx.BomParser;
import org.cyclonedx.model.Component;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.TaskAction;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CycloneDxTask extends DefaultTask {

    /**
     * Various messages sent to console.
     */
    private static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    private static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    private static final String MESSAGE_WRITING_BOM = "CycloneDX: Writing BOM";
    private static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM";
    private static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard as defined by the XSD";

    private File buildDir;

    public void setBuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    /**
     * This code does not currently work. TESTING ONLY
     */
    @TaskAction
    public void createBom() {
        Set<Component> components = new LinkedHashSet<>();
        getLogger().info("PROJECT NAME" + getProject().getName());
        for (Project p : getProject().getAllprojects()) {
            getLogger().info("ALL-PROJECT NAME" + p.getName());
            for (Configuration configuration : p.getConfigurations()) {
                if (!shouldSkipConfiguration(configuration)) {
                    ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
                    if (resolvedConfiguration != null) {
                        for (ResolvedArtifact artifact : resolvedConfiguration.getResolvedArtifacts()) {
                            getLogger().info("RESOLVED ARTIFACt: " + artifact.getName());
                            getLogger().info("TYPE: " + artifact.getType());
                            getLogger().info("CLASSIFIER: " + artifact.getClassifier());
                            getLogger().info("GROUP: " + artifact.getModuleVersion().getId().getGroup());
                        }
                    }
                }
                DependencySet ds = configuration.getAllDependencies();
                for (Dependency dependency : ds) {
                    getLogger().info(dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion());
                }
            }
        }
    }

    private boolean shouldSkipConfiguration(Configuration configuration) {
        final List<String> skipConfigs = Arrays.asList("apiElements", "implementation", "runtimeElements", "runtimeOnly", "testImplementation", "testRuntimeOnly");
        return skipConfigs.contains(configuration.getName());
    }

    /**
     * Ported from Maven plugin.
     */
    protected void execute(Set<Component> components) throws GradleException{
        try {
            getLogger().info(MESSAGE_CREATING_BOM);
            final BomGenerator bomGenerator = new BomGenerator(components);
            bomGenerator.generate();
            final String bomString = bomGenerator.toXmlString();
            final File bomFile = new File(buildDir, "target/bom.xml");
            getLogger().info(MESSAGE_WRITING_BOM);
            FileUtils.write(bomFile, bomString, Charset.forName("UTF-8"), false);
            getLogger().info(MESSAGE_VALIDATING_BOM);
            final BomParser bomParser = new BomParser();
            if (!bomParser.isValid(bomFile)) {
                throw new GradleException(MESSAGE_VALIDATION_FAILURE);
            }

        } catch (ParserConfigurationException | TransformerException | IOException e) {
            throw new GradleException("An error occurred executing " + this.getClass().getName(), e);
        }
    }
}
