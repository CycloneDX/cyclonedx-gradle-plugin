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

import static org.cyclonedx.gradle.CyclonedxPlugin.LOG_PREFIX;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.LicenseItem;
import org.cyclonedx.model.license.Acknowledgement;
import org.cyclonedx.model.license.Expression;
import org.cyclonedx.util.LicenseResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

/**
 * Ported from CycloneDX Maven plugin.
 */
class MavenHelper {

    private static final Logger LOGGER = Logging.getLogger(MavenHelper.class);
    private final Boolean includeLicenseText;
    private final Consumer<File> onPomRead;

    public MavenHelper(final Boolean includeLicenseText) {
        this(includeLicenseText, pomFile -> {});
    }

    /**
     * @param onPomRead receives every POM file this helper reads from disk beyond the artifact itself, so that the
     *                  caller can declare it as evidence the SBOM was produced from
     */
    MavenHelper(final Boolean includeLicenseText, final Consumer<File> onPomRead) {
        this.includeLicenseText = includeLicenseText;
        this.onPomRead = onPomRead;
    }

    /**
     * Resolves meta for an artifact. This method essentially does what an
     * 'effective pom' would do, but for an artifact instead of a project. This
     * method will attempt to resolve metadata at the lowest level of the
     * inheritance tree and work its way up.
     *
     * @param artifact
     *            the artifact to resolve metadata for
     * @param project
     *            the associated project for the artifact
     * @param component
     *            the component to populate data for
     */
    void getClosestMetadata(
            final File artifact,
            final MavenProject project,
            final Component component,
            final ModuleVersionIdentifier mid) {
        extractMetadata(project, component);
        if (project.getParent() != null) {
            getClosestMetadata(artifact, project.getParent(), component, mid);
        } else if (project.getModel().getParent() != null) {
            final MavenProject parentProject = retrieveParentProject(artifact, project, mid);
            if (parentProject != null) {
                getClosestMetadata(artifact, parentProject, component, mid);
            }
        }
    }

    /**
     * Extracts data from a project and adds the data to the component.
     *
     * @param project
     *            the project to extract data from
     * @param component
     *            the component to add data to
     */
    public void extractMetadata(final MavenProject project, final Component component) {
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
        if (project.getOrganization() != null && project.getOrganization().getUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.WEBSITE)) {
                addExternalReference(
                        ExternalReference.Type.WEBSITE,
                        project.getOrganization().getUrl(),
                        component);
            }
        }
        if (project.getCiManagement() != null && project.getCiManagement().getUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.BUILD_SYSTEM)) {
                addExternalReference(
                        ExternalReference.Type.BUILD_SYSTEM,
                        project.getCiManagement().getUrl(),
                        component);
            }
        }
        if (project.getDistributionManagement() != null
                && project.getDistributionManagement().getDownloadUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                addExternalReference(
                        ExternalReference.Type.DISTRIBUTION,
                        project.getDistributionManagement().getDownloadUrl(),
                        component);
            }
        }
        if (project.getDistributionManagement() != null
                && project.getDistributionManagement().getRepository() != null
                && project.getDistributionManagement().getRepository().getUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                addExternalReference(
                        ExternalReference.Type.DISTRIBUTION,
                        project.getDistributionManagement().getRepository().getUrl(),
                        component);
            }
        }
        if (project.getIssueManagement() != null && project.getIssueManagement().getUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.ISSUE_TRACKER)) {
                addExternalReference(
                        ExternalReference.Type.ISSUE_TRACKER,
                        project.getIssueManagement().getUrl(),
                        component);
            }
        }
        if (project.getMailingLists() != null && !project.getMailingLists().isEmpty()) {
            for (MailingList list : project.getMailingLists()) {
                if (list.getArchive() != null) {
                    if (lacksExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                        addExternalReference(ExternalReference.Type.MAILING_LIST, list.getArchive(), component);
                    }
                } else if (list.getSubscribe() != null) {
                    if (lacksExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                        addExternalReference(ExternalReference.Type.MAILING_LIST, list.getSubscribe(), component);
                    }
                }
            }
        }
        if (project.getScm() != null && project.getScm().getUrl() != null) {
            if (lacksExternalReference(component, ExternalReference.Type.VCS)) {
                addExternalReference(
                        ExternalReference.Type.VCS, project.getScm().getUrl(), component);
            }
        }
    }

    private void addExternalReference(
            final ExternalReference.Type referenceType, final String url, final Component component) {
        final String uri = asUri(url);
        if (uri != null) {
            final ExternalReference ref = new ExternalReference();
            ref.setType(referenceType);
            ref.setUrl(uri);
            component.addExternalReference(ref);
        }
    }

    /**
     * The URL as a URI string, or null when it is not one; a URL the POM got wrong is thrown away.
     */
    @Nullable private static String asUri(final String url) {
        try {
            return new URI(url.trim()).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private boolean lacksExternalReference(final Component component, final ExternalReference.Type type) {
        if (component.getExternalReferences() != null
                && !component.getExternalReferences().isEmpty()) {
            for (final ExternalReference ref : component.getExternalReferences()) {
                if (type == ref.getType()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Resolves the licenses a POM declares into the licenses of a component. Maven reads several licenses as
     * alternatives the user may choose among, and a CycloneDX list below 1.7 holds either license objects or one
     * expression, so once one of them resolves to an expression they are all joined into a single expression with
     * {@code OR}. A name that resolves to no SPDX id cannot join an expression, so in that case the licenses stay
     * objects and the expression becomes one with the expression as its name and the URL the POM gave it. Whichever
     * shape results, it is what the POM states, so every license is acknowledged as declared.
     */
    @Nullable LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses) {
        final List<DeclaredLicense> declared = new ArrayList<>();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            final DeclaredLicense license = resolveMavenLicense(artifactLicense);
            if (license != null) {
                declared.add(license);
            }
        }
        if (declared.isEmpty()) {
            return null;
        }
        final LicenseChoice licenseChoice = new LicenseChoice();
        if (declared.stream().anyMatch(DeclaredLicense::isExpression)
                && declared.stream().allMatch(DeclaredLicense::hasSpdxTerm)) {
            final Set<String> alternatives = new LinkedHashSet<>();
            for (DeclaredLicense license : declared) {
                alternatives.add(license.spdxTerm());
            }
            licenseChoice.addExpression(new Expression(String.join(" OR ", alternatives)));
        } else {
            for (DeclaredLicense license : declared) {
                licenseChoice.addLicense(license.asLicense());
            }
        }
        for (LicenseItem item : licenseChoice.getItems()) {
            if (item.getExpression() != null) {
                item.getExpression().setAcknowledgement(Acknowledgement.DECLARED);
            } else {
                item.getLicense().setAcknowledgement(Acknowledgement.DECLARED);
            }
        }
        return licenseChoice;
    }

    @Nullable private DeclaredLicense resolveMavenLicense(final org.apache.maven.model.License artifactLicense) {
        final @Nullable String name = artifactLicense.getName();
        final @Nullable String url = artifactLicense.getUrl();
        LicenseItem resolved = resolveSpdx(name);
        if (resolved == null) {
            resolved = resolveSpdx(url);
        }
        if (resolved != null) {
            return new DeclaredLicense(resolved, url);
        }
        // A license object needs a name, so an entry without one has nothing to be written with
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return new DeclaredLicense(LicenseItem.ofLicense(namedLicense(name, url)), url);
    }

    @Nullable private LicenseItem resolveSpdx(final @Nullable String nameOrUrl) {
        if (nameOrUrl == null || nameOrUrl.trim().isEmpty()) {
            return null;
        }
        final LicenseChoice resolved = LicenseResolver.resolve(nameOrUrl, includeLicenseText);
        if (resolved == null
                || resolved.getItems() == null
                || resolved.getItems().isEmpty()) {
            return null;
        }
        return resolved.getItems().get(0);
    }

    private static org.cyclonedx.model.License namedLicense(final String name, final @Nullable String url) {
        final org.cyclonedx.model.License license = new org.cyclonedx.model.License();
        license.setName(name.trim());
        if (url != null && !url.trim().isEmpty()) {
            license.setUrl(asUri(url));
        }
        return license;
    }

    /**
     * One license entry of a POM as resolved, with the URL the POM gave it, for the case where an expression has to be
     * written as a named license.
     */
    private static final class DeclaredLicense {
        final LicenseItem item;
        final @Nullable String pomUrl;

        DeclaredLicense(final LicenseItem item, final @Nullable String pomUrl) {
            this.item = item;
            this.pomUrl = pomUrl;
        }

        boolean isExpression() {
            return item.getExpression() != null;
        }

        boolean hasSpdxTerm() {
            return isExpression() || item.getLicense().getId() != null;
        }

        String spdxTerm() {
            return isExpression()
                    ? item.getExpression().getValue()
                    : item.getLicense().getId();
        }

        org.cyclonedx.model.License asLicense() {
            return isExpression() ? namedLicense(item.getExpression().getValue(), pomUrl) : item.getLicense();
        }
    }

    /**
     * Retrieves the parent pom for an artifact (if any). The parent pom may contain
     * license, description, and other metadata whereas the artifact itself may not.
     *
     * @param artifact
     *            the artifact to retrieve the parent pom for
     * @param project
     *            the maven project the artifact is part of
     */
    @Nullable private MavenProject retrieveParentProject(
            final @Nullable File artifact, MavenProject project, final ModuleVersionIdentifier mid) {
        if (artifact == null || artifact.getParentFile() == null) {
            return null;
        }
        final Parent parent = project.getModel().getParent();
        if (parent == null) {
            return null;
        }
        final File parentFile = parentPomFile(artifact.getParentFile(), parent, mid);
        if (parentFile.exists() && parentFile.isFile()) {
            try {
                final File canonicalParentFile = parentFile.getCanonicalFile();
                onPomRead.accept(canonicalParentFile);
                return readPom(canonicalParentFile);
            } catch (Exception e) {
                LOGGER.error("{} An error occurred retrieving an artifacts parent pom", LOG_PREFIX, e);
            }
        }
        return null;
    }

    /**
     * Locates the parent pom in the repository layout the artifact sits in.
     */
    private static File parentPomFile(final File artifactDir, final Parent parent, final ModuleVersionIdentifier mid) {
        // Navigate out of version, artifactId, and first (possibly only) level of
        // groupId
        final StringBuilder getout = new StringBuilder("../../../");
        final int periods =
                mid.getGroup().length() - mid.getGroup().replace(".", "").length();
        for (int i = 0; i < periods; i++) {
            getout.append("../");
        }
        return new File(
                artifactDir,
                getout + parent.getGroupId().replace(".", "/") + "/" + parent.getArtifactId() + "/"
                        + parent.getVersion() + "/" + parent.getArtifactId() + "-" + parent.getVersion() + ".pom");
    }

    /**
     * Extracts a pom from an artifacts jar file and creates a MavenProject from it.
     *
     * @param artifact
     *            the artifact to extract the pom from
     * @return a Maven project
     */
    @Nullable MavenProject extractPom(final @Nullable File artifact, final ModuleVersionIdentifier mid) {
        if (artifact != null && artifact.exists()) {
            try {
                LOGGER.debug("{} extractPom {}", LOG_PREFIX, artifact.getName());
                if (artifact.getName().endsWith(".pom")) {
                    return readPom(artifact);
                }
                if (!artifact.getName().endsWith(".jar")) {
                    LOGGER.debug("{} Skipping pom extraction of non jar artifact: {}", LOG_PREFIX, artifact.getName());
                    return null;
                }
                try (final JarFile jarFile = new JarFile(artifact)) {
                    final JarEntry entry =
                            jarFile.getJarEntry("META-INF/maven/" + mid.getGroup() + "/" + mid.getName() + "/pom.xml");
                    if (entry != null) {
                        try (final InputStream input = jarFile.getInputStream(entry)) {
                            return readPom(input);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("{} An error occurred attempting to extract POM from artifact", LOG_PREFIX, e);
            }
        }
        return null;
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     *
     * @param file
     *            the file object of the POM to read
     * @return a MavenProject
     *             oops
     */
    @Nullable static MavenProject readPom(final @Nullable File file) {

        if (file == null) {
            return null;
        }

        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final Reader reader = ReaderFactory.newXmlReader(file)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("An error occurred attempting to read POM", e);
        }
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     *
     * @param in
     *            the inputstream to read from
     * @return a MavenProject
     */
    @Nullable static MavenProject readPom(final InputStream in) {
        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final Reader reader = ReaderFactory.newXmlReader(in)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            // logger.error("An error occurred attempting to read POM", e);
        }
        return null;
    }

    /**
     * Resolves an effective pom, including properties inherited from parent
     * hierarchy.
     *
     * @param pomFile
     *            the dependency pomFile
     * @param gradleProject
     *            the current gradle project which gets used as the base resolver
     * @param onPomRead
     *            receives every parent and imported POM file resolved while building the model
     * @return model for effective pom
     */
    static @Nullable Model resolveEffectivePom(
            final @Nullable File pomFile, final Project gradleProject, final Consumer<File> onPomRead) {
        // force the parent POMs and BOMs to be resolved
        final ModelResolver modelResolver = new GradleAssistedMavenModelResolverImpl(gradleProject, onPomRead);
        final ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.setModelResolver(modelResolver);
        req.setPomFile(pomFile);
        req.getSystemProperties().putAll(System.getProperties());
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        // execute the model building request
        final DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
        final DefaultModelBuilder builder = factory.newInstance();
        try {
            return builder.build(req).getEffectiveModel();
        } catch (ModelBuildingException e) {
            throw new IllegalStateException("An error occurred attempting to resolve effective POM", e);
        }
    }

    boolean isModified(final @Nullable ResolvedArtifact artifact) {
        // todo: compare hashes + GAV with what the artifact says against Maven Central
        // to determine if component has been modified.
        return false;
    }
}
