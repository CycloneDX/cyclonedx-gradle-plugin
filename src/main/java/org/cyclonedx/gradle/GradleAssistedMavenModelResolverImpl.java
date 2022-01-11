package org.cyclonedx.gradle;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.resolution.ModelResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class GradleAssistedMavenModelResolverImpl implements ModelResolver {
    private final Project project;

    public GradleAssistedMavenModelResolverImpl(Project project) {
        super();
        this.project = project;
    }

    @Override
    public ModelSource2 resolveModel(String groupId, String artifactId, String version) {
        String depNotation = String.format("%s:%s:%s@pom", groupId, artifactId, version);
        org.gradle.api.artifacts.Dependency dependency = project.getDependencies().create(depNotation);
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
