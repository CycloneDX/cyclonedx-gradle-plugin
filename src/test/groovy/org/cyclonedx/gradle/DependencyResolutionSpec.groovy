package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
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
}
