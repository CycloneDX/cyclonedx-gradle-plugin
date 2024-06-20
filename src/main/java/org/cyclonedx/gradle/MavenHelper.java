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
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.util.LicenseResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.logging.Logger;

/**
 * Ported from CycloneDX Maven plugin.
 */
class MavenHelper {

    private Logger logger;
    private CycloneDxSchema.Version schemaVersion;
    private Boolean includeLicenseText;

    public MavenHelper(Logger logger, CycloneDxSchema.Version schemaVersion, Boolean includeLicenseText) {
        this.logger = logger;
        this.schemaVersion = schemaVersion;
        this.includeLicenseText = includeLicenseText;
    }

    /**
     * Resolves meta for an artifact. This method essentially does what an 'effective pom' would do,
     * but for an artifact instead of a project. This method will attempt to resolve metadata at
     * the lowest level of the inheritance tree and work its way up.
     * @param artifact the artifact to resolve metadata for
     * @param project the associated project for the artifact
     * @param component the component to populate data for
     */
    void getClosestMetadata(ResolvedArtifact artifact, MavenProject project, Component component) {
        extractMetadata(project, component);
        if (project.getParent() != null) {
            getClosestMetadata(artifact, project.getParent(), component);
        } else if (project.getModel().getParent() != null) {
            final MavenProject parentProject = retrieveParentProject(artifact, project);
            if (parentProject != null) {
                getClosestMetadata(artifact, parentProject, component);
            }
        }
    }

    /**
     * Extracts data from a project and adds the data to the component.
     * @param project the project to extract data from
     * @param component the component to add data to
     */
    public void extractMetadata(MavenProject project, Component component) {
        if (component.getPublisher() == null) {
            // If we don't already have publisher information, retrieve it.
            if (project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
        }
        if (component.getDescription() == null) {
            // If we don't already have description information, retrieve it.
            component.setDescription(project.getDescription());
        }
        if (component.getLicenseChoice() == null || component.getLicenseChoice().getLicenses() == null || component.getLicenseChoice().getLicenses().isEmpty()) {
            // If we don't already have license information, retrieve it.
            if (project.getLicenses() != null) {
                component.setLicenseChoice(resolveMavenLicenses(project.getLicenses()));
            }
        }
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
            if (project.getOrganization() != null && project.getOrganization().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.WEBSITE)) {
                    addExternalReference(ExternalReference.Type.WEBSITE, project.getOrganization().getUrl(), component);
                }
            }
            if (project.getCiManagement() != null && project.getCiManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.BUILD_SYSTEM)) {
                    addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.getCiManagement().getUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getDownloadUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getDownloadUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getRepository() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getRepository().getUrl(), component);
                }
            }
            if (project.getIssueManagement() != null && project.getIssueManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.ISSUE_TRACKER)) {
                    addExternalReference(ExternalReference.Type.ISSUE_TRACKER, project.getIssueManagement().getUrl(), component);
                }
            }
            if (project.getMailingLists() != null && project.getMailingLists().size() > 0) {
                for (MailingList list : project.getMailingLists()) {
                    if (list.getArchive() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getArchive(), component);
                        }
                    } else if (list.getSubscribe() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getSubscribe(), component);
                        }
                    }
                }
            }
            if (project.getScm() != null && project.getScm().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.VCS)) {
                    addExternalReference(ExternalReference.Type.VCS, project.getScm().getUrl(), component);
                }
            }
        }
    }

    private void addExternalReference(final ExternalReference.Type referenceType, final String url, final Component component) {
        try {
            final URI uri = new URI(url.trim());
            final ExternalReference ref = new ExternalReference();
            ref.setType(referenceType);
            ref.setUrl(uri.toString());
            component.addExternalReference(ref);
        } catch (URISyntaxException e) {
            // throw it away
        }
    }

    private boolean doesComponentHaveExternalReference(final Component component, final ExternalReference.Type type) {
        if (component.getExternalReferences() != null && !component.getExternalReferences().isEmpty()) {
            for (final ExternalReference ref : component.getExternalReferences()) {
                if (type == ref.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses) {
        final LicenseChoice licenseChoice = new LicenseChoice();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            boolean resolved = false;
            if (artifactLicense.getName() != null) {
                final LicenseChoice resolvedByName = LicenseResolver.resolve(artifactLicense.getName(), this.includeLicenseText);
                if (resolvedByName != null) {
                    if (resolvedByName.getLicenses() != null && !resolvedByName.getLicenses().isEmpty()) {
                        resolved = true;
                        licenseChoice.addLicense(resolvedByName.getLicenses().get(0));
                    } else if (resolvedByName.getExpression() != null && CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
                        resolved = true;
                        licenseChoice.setExpression(resolvedByName.getExpression());
                    }
                }
            }
            if (artifactLicense.getUrl() != null && !resolved) {
                final LicenseChoice resolvedByUrl = LicenseResolver.resolve(artifactLicense.getUrl(), includeLicenseText);
                if (resolvedByUrl != null) {
                    if (resolvedByUrl.getLicenses() != null && !resolvedByUrl.getLicenses().isEmpty()) {
                        resolved = true;
                        licenseChoice.addLicense(resolvedByUrl.getLicenses().get(0));
                    } else if (resolvedByUrl.getExpression() != null && CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
                        resolved = true;
                        licenseChoice.setExpression(resolvedByUrl.getExpression());
                    }
                }
            }
            if (artifactLicense.getName() != null && !resolved) {
                final org.cyclonedx.model.License license = new org.cyclonedx.model.License();;
                license.setName(artifactLicense.getName().trim());
                if (StringUtils.isNotBlank(artifactLicense.getUrl())) {
                    try {
                        final URI uri = new URI(artifactLicense.getUrl().trim());
                        license.setUrl(uri.toString());
                    } catch (URISyntaxException  e) {
                        // throw it away
                    }
                }
                licenseChoice.addLicense(license);
            }
        }
        return licenseChoice;
    }

    /**
     * Retrieves the parent pom for an artifact (if any). The parent pom may contain license,
     * description, and other metadata whereas the artifact itself may not.
     * @param artifact the artifact to retrieve the parent pom for
     * @param project the maven project the artifact is part of
     */
    private MavenProject retrieveParentProject(ResolvedArtifact artifact, MavenProject project) {
        if (artifact.getFile() == null || artifact.getFile().getParentFile() == null || !isDescribedArtifact(artifact)) {
            return null;
        }
        final Model model = project.getModel();
        if (model.getParent() != null) {
            final Parent parent = model.getParent();
            // Navigate out of version, artifactId, and first (possibly only) level of groupId
            final StringBuilder getout = new StringBuilder("../../../");
            final ModuleVersionIdentifier mid = artifact.getModuleVersion().getId();
            final int periods = mid.getGroup().length() - mid.getGroup().replace(".", "").length();
            for (int i= 0; i< periods; i++) {
                getout.append("../");
            }
            final File parentFile = new File(artifact.getFile().getParentFile(), getout + parent.getGroupId().replace(".", "/") + "/" + parent.getArtifactId() + "/" + parent.getVersion() + "/" + parent.getArtifactId() + "-" + parent.getVersion() + ".pom");
            if (parentFile.exists() && parentFile.isFile()) {
                try {
                    return readPom(parentFile.getCanonicalFile());
                } catch (Exception e) {
                    logger.error("An error occurred retrieving an artifacts parent pom", e);
                }
            }
        }
        return null;
    }

    /**
     * Extracts a pom from an artifacts jar file and creates a MavenProject from it.
     * @param artifact the artifact to extract the pom from
     * @return a Maven project
     */
    MavenProject extractPom(ResolvedArtifact artifact) {
        if (!isDescribedArtifact(artifact)) {
            return null;
        }
        if (artifact.getFile() != null && artifact.getFile().exists()) {
            try {
                final JarFile jarFile = new JarFile(artifact.getFile());
                final ModuleVersionIdentifier mid = artifact.getModuleVersion().getId();
                final JarEntry entry = jarFile.getJarEntry("META-INF/maven/"+ mid.getGroup() + "/" + mid.getName() + "/pom.xml");
                if (entry != null) {
                    try (final InputStream input = jarFile.getInputStream(entry)) {
                        return readPom(input);
                    }
                }
            } catch (IOException e) {
                logger.error("An error occurred attempting to extract POM from artifact", e);
            }
        }
        return null;
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param file the file object of the POM to read
     * @return a MavenProject
     * @throws IOException oops
     */
    MavenProject readPom(File file) {
        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final Reader reader = ReaderFactory.newXmlReader(file)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            logger.error("An error occurred attempting to read POM", e);
        }
        return null;
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param in the inputstream to read from
     * @return a MavenProject
     */
    MavenProject readPom(InputStream in) {
        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final Reader reader = ReaderFactory.newXmlReader(in)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            logger.error("An error occurred attempting to read POM", e);
        }
        return null;
    }

    /**
     * Resolves an effective pom, including properties inherited from parent hierarchy.
     * @param pomFile the dependency pomFile
     * @param gradleProject the current gradle project which gets used as the base resolver
     * @return model for effective pom
     */
    Model resolveEffectivePom(File pomFile, Project gradleProject) {
        // force the parent POMs and BOMs to be resolved
        ModelResolver modelResolver = new GradleAssistedMavenModelResolverImpl(gradleProject);
        ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.setModelResolver(modelResolver);
        req.setPomFile(pomFile);
        req.getSystemProperties().putAll(System.getProperties());
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        // execute the model building request
        DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
        DefaultModelBuilder builder = factory.newInstance();
        Model effectiveModel = null;
        try {
            effectiveModel = builder.build(req).getEffectiveModel();
        } catch (ModelBuildingException e) {
            logger.error("An error occurred attempting to resolve effective POM", e);
        }
        return effectiveModel;
    }

    /**
     * Returns true for any artifact type which will positively have a POM that
     * describes the artifact.
     * @param artifact the artifact
     * @return true if artifact will have a POM, false if not
     */
    boolean isDescribedArtifact(Artifact artifact) {
        return artifact.getType().equalsIgnoreCase("jar");
    }

    /**
     * Returns true for any artifact type which will positively have a POM that
     * describes the artifact.
     * @param artifact the artifact
     * @return true if artifact will have a POM, false if not
     */
    boolean isDescribedArtifact(ResolvedArtifact artifact) {
        return artifact.getType().equalsIgnoreCase("jar");
    }

    boolean isModified(ResolvedArtifact artifact) {
        //todo: compare hashes + GAV with what the artifact says against Maven Central to determine if component has been modified.
        return false;
    }
}
