package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

@Requires({ JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion, task name: #taskName")
class BomPropertiesConfigurationSpec extends Specification {

    def "should include component property when configured"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
            }
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                componentProperty {
                    name = 'test:property-name'
                    value = 'helloworld property value'
                }
            }
            """, "rootProject.name = 'single-comp-property-test'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        with(bom.getMetadata().getComponent().getProperties()) {
            assert size() == 1
            with(get(0)) {
                assert getName() == "test:property-name"
                assert getValue() == "helloworld property value"
            }
        }

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include all component properties when name is repeated"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
            }
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                componentProperty {
                    name = 'test:property-name'
                    value = 'helloworld property value1'
                }
                componentProperty {
                    name = 'test:property-name'
                    value = 'helloworld property value2'
                }
                componentProperty {
                    name = 'test:different-property-name'
                    value = 'different property'
                }
            }
            """, "rootProject.name = 'multi-comp-property-test'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        with(bom.getMetadata().getComponent().getProperties()) {
            assert size() == 3
            with(get(0)) {
                assert name == "test:property-name"
                assert value == "helloworld property value1"
            }
            with(get(1)) {
                assert name == "test:property-name"
                assert value == "helloworld property value2"
            }
            with(get(2)) {
                assert name == "test:different-property-name"
                assert value == "different property"
            }
        }

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include metadata property when configured"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
            }
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                metadataProperty {
                    name = 'meta:property-name'
                    value = 'metadata property value'
                }
            }
            """, "rootProject.name = 'single-meta-property-test'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        with(bom.getMetadata().getProperties()) {
            assert size() == 1
            with(get(0)) {
                assert name == "meta:property-name"
                assert value == "metadata property value"
            }
        }

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include all metadata properties when name is repeated"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
            }
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                metadataProperty {
                    name = 'meta:property-name'
                    value = 'helloworld property value1'
                }
                metadataProperty {
                    name = 'meta:property-name'
                    value = 'helloworld property value2'
                }
                metadataProperty {
                    name = 'meta:different-property-name'
                    value = 'different property'
                }
            }
            """, "rootProject.name = 'multi-meta-property-test'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        with(bom.getMetadata().getProperties()) {
            assert size() == 3
            with(get(0)) {
                assert name == "meta:property-name"
                assert value == "helloworld property value1"
            }
            with(get(1)) {
                assert name == "meta:property-name"
                assert value == "helloworld property value2"
            }
            with(get(2)) {
                assert name == "meta:different-property-name"
                assert value == "different property"
            }
        }

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }
}
