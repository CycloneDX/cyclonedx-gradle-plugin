package org.cyclonedx.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.artifacts.ResolvedArtifact;


public class MavenUtils {

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param file the file object of the POM to read
     * @return a MavenProject
     * @throws IOException oops
     */
    public static MavenProject readPom(File file) throws IOException {
        try (final FileInputStream in = new FileInputStream(file)) {
            return readPom(in);
        }
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param in the inputstream to read from
     * @return a MavenProject
     */
    public static MavenProject readPom(InputStream in) {
        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final InputStreamReader reader = new InputStreamReader(in)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            //getLogger().error("An error occurred attempting to read POM", e);
        }
        return null;
    }

}
