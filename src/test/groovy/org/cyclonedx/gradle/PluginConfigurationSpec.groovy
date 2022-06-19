package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification


class PluginConfigurationSpec extends Specification {

    def "simple-project should output boms in build/reports with version 1.4"() {
        given:
          File testDir = TestUtils.duplicate("simple-project")

        when:
            def result = GradleRunner.create()
                    .withProjectDir(testDir)
                    .withArguments("cyclonedxBom")
                    .withPluginClasspath()
                    .build()
        then:
            result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
            File reportDir = new File(testDir, "build/reports")

            assert reportDir.exists()
            reportDir.listFiles().length == 2
            File jsonBom = new File(reportDir, "bom.json")
            assert jsonBom.text.contains("\"specVersion\" : \"1.4\"")
    }

    def "custom-destination project should output boms in output-dir"() {
        given:
        File testDir = TestUtils.duplicate("custom-destination")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "output-dir")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
    }

    def "custom-output project should write boms under my-bom"() {
        given:
        File testDir = TestUtils.duplicate("custom-outputname")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        assert new File(testDir, "build/reports/my-bom.json").exists()
        assert new File(testDir, "build/reports/my-bom.xml").exists()
    }

    def "pom-xml-encoding project should not output errors to console"() {
        given:
        File testDir = TestUtils.duplicate("pom-xml-encoding")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")
        assert reportDir.exists()
        reportDir.listFiles().length == 2

        assert !result.output.contains("An error occurred attempting to read POM")
    }

    def "should use configured schemaVersion"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.3\"")
    }

    def "should use configured outputFormat to limit generated file"() {
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
            cyclonedxBom {
                outputFormat = 'json'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 1
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.exists()
    }

    def "includes component bom-ref when schema version greater than 1.0"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'
    }
}
