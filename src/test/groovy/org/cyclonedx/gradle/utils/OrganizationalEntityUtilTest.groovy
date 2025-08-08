package org.cyclonedx.gradle.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.TestUtils
import org.cyclonedx.model.Bom
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class OrganizationalEntityUtilTest extends Specification {

    def "manufacturer should be empty if no organizational entity is provided"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            cyclonedxBom {
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments( TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getManufacturer() == null
    }

    def "manufacturer should be empty if empty organizational entity is provided"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            def oe = new org.cyclonedx.model.OrganizationalEntity()
            cyclonedxBom {
                organizationalEntity = oe
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getManufacturer() == null
    }

    def "manufacturer should not be empty if organizational entity is provided"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            def oe = new org.cyclonedx.model.OrganizationalEntity()
            oe.name = "name"
            cyclonedxBom {
                organizationalEntity = oe
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getManufacturer().getName() == "name"
    }
}
