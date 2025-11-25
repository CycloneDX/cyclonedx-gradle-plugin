package org.cyclonedx.gradle.utils

import org.cyclonedx.Version
import org.cyclonedx.gradle.TestUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.parsers.XmlParser
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion, spec version: #specVersion.name()")
class OrganizationalEntityUtilTest extends Specification {

    static final Version[] versionsToSkip = [ Version.VERSION_10, Version.VERSION_11, Version.VERSION_12 ]

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

            tasks.cyclonedxBom {
                schemaVersion = org.cyclonedx.Version.${specVersion.name()}
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
        File reportDir = new File(testDir, "build/reports/cyclonedx")
        Bom jsonBom = new JsonParser().parse(new File(reportDir, "bom.json"))
        Bom xmlBom = new XmlParser().parse(new File(reportDir, "bom.xml"))

        assert jsonBom.getMetadata().getManufacture() == null
        assert xmlBom.getMetadata().getManufacture() == null
        assert jsonBom.getMetadata().getManufacturer() == null
        assert xmlBom.getMetadata().getManufacturer() == null

        where:
        javaVersion = JavaVersion.current()
        specVersion << Version.values() - versionsToSkip
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
            tasks.cyclonedxBom {
                schemaVersion = org.cyclonedx.Version.${specVersion.name()}
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
        File reportDir = new File(testDir, "build/reports/cyclonedx")
        Bom jsonBom = new JsonParser().parse(new File(reportDir, "bom.json"))
        Bom xmlBom = new XmlParser().parse(new File(reportDir, "bom.xml"))

        assert jsonBom.getMetadata().getManufacture() == null
        assert xmlBom.getMetadata().getManufacture() == null
        assert jsonBom.getMetadata().getManufacturer() == null
        assert xmlBom.getMetadata().getManufacturer() == null

        where:
        javaVersion = JavaVersion.current()
        specVersion << Version.values() - versionsToSkip
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
            tasks.cyclonedxBom {
                schemaVersion = org.cyclonedx.Version.${specVersion.name()}
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
        File reportDir = new File(testDir, "build/reports/cyclonedx")
        Bom jsonBom = new JsonParser().parse(new File(reportDir, "bom.json"))
        Bom xmlBom = new XmlParser().parse(new File(reportDir, "bom.xml"))

        if (specVersion.compareTo(Version.VERSION_16) >= 0) {
            assert jsonBom.getMetadata().getManufacture() == null
            assert xmlBom.getMetadata().getManufacture() == null
            assert jsonBom.getMetadata().getManufacturer().getName() == "name"
            assert xmlBom.getMetadata().getManufacturer().getName() == "name"
        } else {
            assert jsonBom.getMetadata().getManufacture().getName() == "name"
            assert xmlBom.getMetadata().getManufacture().getName() == "name"
            assert jsonBom.getMetadata().getManufacturer() == null
            assert xmlBom.getMetadata().getManufacturer() == null
        }

        where:
        javaVersion = JavaVersion.current()
        specVersion << Version.values() - versionsToSkip
    }
}
