package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.cyclonedx.model.Hash
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class DependencyResolutionSpec extends Specification {

    def "only add component with valid purl"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("io.quarkus:quarkus-resteasy-jackson:2.12.0.Final")
                implementation("io.quarkus:quarkus-rest-client-jackson:2.12.0.Final")
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component quarkusJackson = bom.getComponents().find(c -> c.name == 'quarkus-jackson')

        assert quarkusJackson.getBomRef() != null
    }

    def "flatten non-jar dependencies"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Dependency root = bom.getDependencies().find(dependency -> dependency.getRef().contains("example"))

        assert bom.getDependencies().find(dependency -> dependency.getRef().contains("pkg:maven/javax.persistence/javax.persistence-api@2.2?type=jar"))
        assert root.getDependencies().size() == 1
        assert root.getDependencies().get(0).getRef() == "pkg:maven/org.hibernate/hibernate-core@5.6.15.Final?type=jar"
    }

    def "should contain correct hashes"() {
        given:
        String localRepoUri = TestUtils.duplicateRepo("local")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven{
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:componenta:1.0.0")
                testImplementation("com.test:componentb:1.0.1")
            }""", "rootProject.name = 'simple-project'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component componenta = bom.getComponents().find(c -> c.name == 'componenta')
        Hash hasha =
            componenta.hashes.find(c -> c.algorithm == "SHA-256" && c.value == "8b6a28fbdb87b7a521b61bc15d265820fb8dd1273cb44dd44a8efdcd6cd40848")
        assert hasha != null
        Component componentb = bom.getComponents().find(c -> c.name == 'componentb')
        Hash hashb =
            componentb.hashes.find(c -> c.algorithm == "SHA-256" && c.value == "5a5407bd92e71336b546642b8b62b6a9544bca5c4ab2fbb8864d9faa5400ba48")
        assert hashb != null
    }

    def "should generate bom for non-jar artrifacts"() {
        given:
        String localRepoUri = TestUtils.duplicateRepo("local")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven{
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:componentc:1.0.0")
            }""", "rootProject.name = 'simple-project'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component componentc = bom.getComponents().find(c -> c.bomRef == 'pkg:maven/com.test/componentc@1.0.0?type=tgz')
        assert componentc != null
    }
}
