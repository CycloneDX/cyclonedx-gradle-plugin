package org.cyclonedx.gradle.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.TestUtils
import org.cyclonedx.model.Bom
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class ExternalResourcesUtilTest extends Specification {

    def "should map git@ url to https based external reference"() {
        expect:
        ExternalResourcesUtil.mapGitToHttps("git@github.com:barblin/cyclonedx-gradle-plugin.git") == "https://github.com/barblin/cyclonedx-gradle-plugin.git"
    }

    def "should add git reference to metadata"() {
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

        assert !bom.getMetadata().getComponent().getExternalReferences().isEmpty()
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() != null
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl().contains("https://")
    }
}
