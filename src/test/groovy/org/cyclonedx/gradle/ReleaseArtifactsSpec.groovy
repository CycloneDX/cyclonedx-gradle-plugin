package org.cyclonedx.gradle

import groovy.json.JsonSlurper
import java.nio.file.Files
import org.gradle.api.JavaVersion
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ JavaVersion.current() != JavaVersion.VERSION_21 })
class ReleaseArtifactsSpec extends Specification {

    def "release artifacts contain the verified Direct SBOM"() {
        given:
        def version = System.getProperty("pluginVersion")
        def localRepository = new File(System.getProperty("localRepoPath"))
        def implementationDirectory = new File(
            localRepository,
            "org/cyclonedx/cyclonedx-gradle-plugin/${version}"
        )
        def markerDirectory = new File(
            localRepository,
            "org/cyclonedx/bom/org.cyclonedx.bom.gradle.plugin/${version}"
        )
        def releaseSbom = new File(
            "build/reports/cyclonedx-direct/cyclonedx-gradle-plugin-${version}-cyclonedx.json"
        )
        def releaseJars = [
            new File("build/libs/cyclonedx-gradle-plugin-${version}.jar"),
            new File("build/libs/cyclonedx-gradle-plugin-${version}-sources.jar"),
            new File("build/libs/cyclonedx-gradle-plugin-${version}-javadoc.jar")
        ]

        expect: "the Plugin Portal boundary contains the three supported JAR artifacts"
        releaseJars.every { it.isFile() }
        def publishedJarNames = implementationDirectory.listFiles().findAll { it.name.endsWith(".jar") }*.name as Set
        publishedJarNames == (releaseJars*.name as Set)
        releaseJars.every { jar ->
            def publishedJar = new File(implementationDirectory, jar.name)
            publishedJar.isFile() && Files.mismatch(jar.toPath(), publishedJar.toPath()) == -1
        }

        and: "the local Maven publication does not pretend the Portal can publish JSON classifiers"
        implementationDirectory.listFiles().findAll { it.name.endsWith("-cyclonedx.json") }.isEmpty()
        markerDirectory.listFiles().findAll { it.name.endsWith("-cyclonedx.json") }.isEmpty()

        and: "the GitHub Release SBOM has a stable versioned artifact name"
        releaseSbom.isFile()

        when:
        def bom = new JsonSlurper().parse(releaseSbom)

        then: "the SBOM describes this plugin release"
        bom.bomFormat == "CycloneDX"
        bom.specVersion == "1.6"
        bom.metadata.component.group == "org.cyclonedx"
        bom.metadata.component.name == "cyclonedx-gradle-plugin"
        bom.metadata.component.version == version
        bom.metadata.licenses*.license*.id == ["Apache-2.0"]

        and: "the generator is identified"
        bom.metadata.tools.components.size() == 1
        bom.metadata.tools.components[0].name == "cyclonedx-gradle-plugin"
        bom.metadata.tools.components[0].version == "3.3.0"

        and: "the published dependency graph contains the plugin's direct runtime dependencies"
        def componentNames = bom.components*.name as Set
        componentNames.containsAll(["cyclonedx-core-java", "jspecify", "maven-core"])
        def rootDependency = bom.dependencies.find { it.ref == bom.metadata.component."bom-ref" }
        rootDependency
        rootDependency.dependsOn.size() >= 3
    }
}
