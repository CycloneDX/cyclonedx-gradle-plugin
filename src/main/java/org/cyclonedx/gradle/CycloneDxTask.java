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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.generators.xml.BomXmlGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Tool;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;
import org.cyclonedx.util.BomUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class CycloneDxTask extends DefaultTask {

    /**
     * Various messages sent to console.
     */
    private static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    private static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    private static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    private static final String MESSAGE_WRITING_BOM_XML = "CycloneDX: Writing BOM XML";
    private static final String MESSAGE_WRITING_BOM_JSON = "CycloneDX: Writing BOM JSON";
    private static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM";
    private static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard";
    private static final String MESSAGE_SKIPPING = "Skipping CycloneDX";

    private File buildDir;
    private MavenHelper mavenHelper;
    private CycloneDxSchema.Version schemaVersion = CycloneDxSchema.Version.VERSION_12;
    private boolean includeBomSerialNumber;
    private boolean skip;
    private String projectType;
    private final List<String> skipConfigs = new ArrayList<>();

    @Input
    public List<String> getSkipConfigs() {
    	return skipConfigs;
    }

    public void setSkipConfigs(Collection<String> skipConfigs) {
    	this.skipConfigs.clear();
    	this.skipConfigs.addAll(skipConfigs);
    }

    public void setBuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    private void initialize() {
        schemaVersion = schemaVersion();
        mavenHelper = new MavenHelper(getLogger(), schemaVersion);
        if (schemaVersion == CycloneDxSchema.Version.VERSION_10) {
            includeBomSerialNumber = false;
        } else {
            includeBomSerialNumber = getBooleanParameter("cyclonedx.includeBomSerialNumber", true);
        }
        skip = getBooleanParameter("cyclonedx.skip", false);
        projectType = getStringParameter("projectType", "library");
    }

    @TaskAction
    @SuppressWarnings("unused")
    public void createBom() {
        initialize();
        if (skip) {
            getLogger().info(MESSAGE_SKIPPING);
            return;
        }
        logParameters();
        getLogger().info(MESSAGE_RESOLVING_DEPS);
        final Set<String> builtDependencies = getProject()
                .getRootProject()
                .getSubprojects()
                .stream()
                .map(p -> p.getGroup() + ":" + p.getName() + ":" + p.getVersion())
                .collect(Collectors.toSet());

        final Metadata metadata = createMetadata();
        final Set<Component> components = new LinkedHashSet<>();
        for (final Project p : getProject().getAllprojects()) {
            for (final Configuration configuration : p.getConfigurations()) {
                if (!shouldSkipConfiguration(configuration) && canBeResolved(configuration)) {
                    final ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
                    if (resolvedConfiguration != null) {
                    	List<String> depsFromConfig = new ArrayList<>();
                        for (final ResolvedArtifact artifact : resolvedConfiguration.getResolvedArtifacts()) {
                            // Don't include other resources built from this Gradle project.
                            final String dependencyName = getDependencyName(artifact);
                            if(builtDependencies.stream().anyMatch(c -> c.equals(dependencyName))) {
                                continue;
                            }

                            depsFromConfig.add(dependencyName);

                            // Convert into a Component and augment with pom metadata if available.
                            final Component component = convertArtifact(artifact);
                            augmentComponentMetadata(component, dependencyName);
                            components.add(component);
                        }
                        Collections.sort(depsFromConfig);
                        getLogger().info("BOM inclusion for configuration {} : {}", configuration.getName(), depsFromConfig);
                    }
                }
            }
        }
        writeBom(metadata, components);
    }

    private boolean canBeResolved(Configuration configuration) {
        // Configuration.isCanBeResolved() has been introduced with Gradle 3.3,
        // thus we need to check for the method's existence first
        try {
            Method method = Configuration.class.getMethod("isCanBeResolved");
            try {
                return (Boolean) method.invoke(configuration);
            } catch (IllegalAccessException | InvocationTargetException e) {
                getLogger().warn("Failed to check resolvability of configuration {} -- assuming resolvability. Exception was: {}",
                        configuration.getName(), e);
                return true;
            }
        } catch (NoSuchMethodException e) {
            // prior to Gradle 3.3 all configurations were resolvable
            return true;
        }
    }

    private String getDependencyName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier m = artifact.getModuleVersion().getId();
        return m.getGroup() + ":" + m.getName() + ":" + m.getVersion();
    }

    private void augmentComponentMetadata(Component component, String dependencyName) {
        final Dependency pomDep = getProject()
            .getDependencies()
            .create(dependencyName + "@pom");
        final Configuration pomCfg = getProject()
            .getConfigurations()
            .detachedConfiguration(pomDep);
        MavenProject project = null;

        try {
            final File pomFile = pomCfg.resolve().stream().findFirst().orElse(null);
            project = mavenHelper.readPom(pomFile);
        } catch(ResolveException | IOException err) {
            getLogger().error("Unable to resolve POM for " + component.getPurl() + ": " + err);
        }

        if(project != null) {
            if(project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
            component.setDescription(project.getDescription());
            component.setLicenseChoice(mavenHelper.resolveMavenLicenses(project.getLicenses()));
        }
    }

    /**
     * Converts a MavenProject into a Metadata object.
     * @return a CycloneDX Metadata object
     */
    protected Metadata createMetadata() {
        final Project project = getProject().getRootProject();
        final Properties properties = readPluginProperties();
        final Metadata metadata = new Metadata();
        final Tool tool = new Tool();
        tool.setVendor(properties.getProperty("vendor"));
        tool.setName(properties.getProperty("name"));
        tool.setVersion(properties.getProperty("version"));
        // TODO: Attempt to add hash values from the current mojo
        metadata.addTool(tool);
        final Component component = new Component();
        component.setGroup((StringUtils.trimToNull(project.getGroup().toString()) != null) ? project.getGroup().toString() : null);
        component.setName(project.getName());
        component.setVersion(project.getVersion().toString());
        component.setType(resolveProjectType());
        component.setPurl(generatePackageUrl(project.getGroup().toString(), project.getName(), project.getVersion().toString(), null, null));
        component.setBomRef(component.getPurl());
        metadata.setComponent(component);
        return metadata;
    }

    private Properties readPluginProperties() {
        final Properties props = new Properties();
        try {
            props.load(this.getClass().getClassLoader().getResourceAsStream("plugin.properties"));
        } catch (NullPointerException | IOException e) {
            getLogger().warn("Unable to load plugin.properties", e);
        }
        return props;
    }

    private Component.Type resolveProjectType() {
        for (Component.Type type: Component.Type.values()) {
            if (type.getTypeName().equalsIgnoreCase(this.projectType)) {
                return type;
            }
        }
        getLogger().warn("Invalid project type. Defaulting to 'library'");
        getLogger().warn("Valid types are:");
        for (Component.Type type: Component.Type.values()) {
            getLogger().warn("  " + type.getTypeName());
        }
        return Component.Type.LIBRARY;
    }

    private Component convertArtifact(ResolvedArtifact artifact) {
        final Component component = new Component();
        getLogger().debug("convertArtifact: working on " + artifact.getModuleVersion().getId().getName());
        component.setGroup(artifact.getModuleVersion().getId().getGroup());
        component.setName(artifact.getModuleVersion().getId().getName());
        component.setVersion(artifact.getModuleVersion().getId().getVersion());
        component.setType(Component.Type.LIBRARY);
        try {
            getLogger().debug(MESSAGE_CALCULATING_HASHES);
            component.setHashes(BomUtils.calculateHashes(artifact.getFile(), schemaVersion));
        } catch(IOException e) {
            getLogger().error("Error encountered calculating hashes", e);
        }
        if (schemaVersion().getVersion() >= 1.1) {
            component.setModified(mavenHelper.isModified(artifact));
        }
        component.setPurl(generatePackageUrl(artifact));
        //if (CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
        //    component.setBomRef(component.getPurl());
        //}
        if (mavenHelper.isDescribedArtifact(artifact)) {
            final MavenProject project = mavenHelper.extractPom(artifact);
            if (project != null) {
                mavenHelper.getClosestMetadata(artifact, project, component);
            }
        }

        return component;
    }

    private boolean shouldSkipConfiguration(Configuration configuration) {
        return skipConfigs.contains(configuration.getName());
    }

    private String generatePackageUrl(final ResolvedArtifact artifact) {
        TreeMap<String, String> qualifiers = null;
        if (artifact.getType() != null || artifact.getClassifier() != null) {
            qualifiers = new TreeMap<>();
            if (artifact.getType() != null) {
                qualifiers.put("type", artifact.getType());
            }
            if (artifact.getClassifier() != null) {
                qualifiers.put("classifier", artifact.getClassifier());
            }
        }
        return generatePackageUrl(artifact.getModuleVersion().getId().getGroup(),
                artifact.getModuleVersion().getId().getName(),
                artifact.getModuleVersion().getId().getVersion(),
                qualifiers, null);
    }

    private String generatePackageUrl(String groupId, String artifactId, String version, TreeMap<String, String> qualifiers, String subpath) {
        try {
            return new PackageURL(PackageURL.StandardTypes.MAVEN, groupId, artifactId, version, qualifiers, subpath).canonicalize();
        } catch(MalformedPackageURLException e) {
            getLogger().warn("An unexpected issue occurred attempting to create a PackageURL for "
                    + groupId + ":" + artifactId + ":" + version, e);
        }
        return null;
    }

    /**
     * Ported from Maven plugin.
     * @param components The CycloneDX components extracted from gradle dependencies
     */
    protected void writeBom(Metadata metadata, Set<Component> components) throws GradleException{
        try {
            getLogger().info(MESSAGE_CREATING_BOM);
            final Bom bom = new Bom();
            if (CycloneDxSchema.Version.VERSION_10 != schemaVersion && includeBomSerialNumber) {
                bom.setSerialNumber("urn:uuid:" + UUID.randomUUID().toString());
            }
            bom.setMetadata(metadata);
            bom.setComponents(new ArrayList<>(components));
            writeXMLBom(schemaVersion, bom);
            if (schemaVersion().getVersion() >= 1.2) {
                writeJSONBom(schemaVersion, bom);
            }
        } catch (ParserConfigurationException | TransformerException | IOException e) {
            throw new GradleException("An error occurred executing " + this.getClass().getName(), e);
        }
    }

    private void writeXMLBom(final CycloneDxSchema.Version schemaVersion, final Bom bom)
            throws ParserConfigurationException, TransformerException, IOException {
        final BomXmlGenerator bomGenerator = BomGeneratorFactory.createXml(schemaVersion, bom);
        bomGenerator.generate();
        final String bomString = bomGenerator.toXmlString();
        final File bomFile = new File(buildDir, "reports/bom.xml");
        getLogger().info(MESSAGE_WRITING_BOM_XML);
        FileUtils.write(bomFile, bomString, StandardCharsets.UTF_8, false);
        getLogger().info(MESSAGE_VALIDATING_BOM);
        final Parser bomParser = new XmlParser();
        try {
            if (!bomParser.isValid(bomFile)) {
                throw new GradleException(MESSAGE_VALIDATION_FAILURE);
            }
        } catch (Exception e) { // Changed to Exception.
            // Gradle will erroneously report "exception IOException is never thrown in body of corresponding try statement"
            throw new GradleException(MESSAGE_VALIDATION_FAILURE, e);
        }
    }

    private void writeJSONBom(final CycloneDxSchema.Version schemaVersion, final Bom bom) throws IOException {
        final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion, bom);
        bomGenerator.generate();
        final String bomString = bomGenerator.toJsonString();
        final File bomFile = new File(buildDir, "reports/bom.json");
        getLogger().info(MESSAGE_WRITING_BOM_JSON);
        FileUtils.write(bomFile, bomString, StandardCharsets.UTF_8, false);
        getLogger().info(MESSAGE_VALIDATING_BOM);
        final Parser bomParser = new JsonParser();
        try {
            if (!bomParser.isValid(bomFile)) {
                throw new GradleException(MESSAGE_VALIDATION_FAILURE);
            }
        } catch (Exception e) { // Changed to Exception.
            // Gradle will erroneously report "exception IOException is never thrown in body of corresponding try statement"
            throw new GradleException(MESSAGE_VALIDATION_FAILURE, e);
        }
    }

    private boolean getBooleanParameter(String parameter, boolean defaultValue) {
        final Project project = super.getProject();
        if (project.hasProperty(parameter)) {
            final Object o = project.getProperties().get(parameter);
            if (o instanceof String) {
                return Boolean.parseBoolean((String)o);
            }
        }
        return defaultValue;
    }

    private String getStringParameter(String parameter, String defaultValue) {
        final Project project = super.getProject();
        if (project.hasProperty(parameter)) {
            final Object o = project.getProperties().get(parameter);
            if (o instanceof String) {
                return (String)o;
            }
        }
        return defaultValue;
    }

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     * @return the CycloneDX schema to use
     */
    private CycloneDxSchema.Version schemaVersion() {
        final Project project = super.getProject();
        final String version;
        if (project.hasProperty("cyclonedx.schemaVersion")) {
            version = (String)project.getProperties().get("cyclonedx.schemaVersion");
        } else {
            version = getStringParameter("schemaVersion", CycloneDxSchema.Version.VERSION_12.getVersionString());
        }
        if ("1.0".equals(version)) {
            return CycloneDxSchema.Version.VERSION_10;
        } else if ("1.1".equals(version)) {
            return CycloneDxSchema.Version.VERSION_11;
        }
        return CycloneDxSchema.Version.VERSION_12;
    }

    protected void logParameters() {
        if (getLogger().isInfoEnabled()) {
            getLogger().info("CycloneDX: Parameters");
            getLogger().info("------------------------------------------------------------------------");
            getLogger().info("schemaVersion          : " + schemaVersion.name());
            getLogger().info("includeBomSerialNumber : " + includeBomSerialNumber);
            getLogger().info("------------------------------------------------------------------------");
        }
    }
}
