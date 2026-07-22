package org.cyclonedx.gradle

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.parsers.JsonParser
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class TestConfigurationLabelingSpec extends Specification {

    def "default testConfigs marks standard test dependencies as test"() {
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
                implementation 'com.fasterxml.jackson.core:jackson-core:2.14.2'
                testImplementation 'junit:junit:4.13.2'
            }
            """, "rootProject.name = 'test-label-default'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirectBom(testDir)
        testProperty(bom, "junit", "junit") == "true"
        testProperty(bom, "com.fasterxml.jackson.core", "jackson-core") == "false"

        where:
        javaVersion = JavaVersion.current()
    }

    def "default testConfigs does not treat e2eTest configurations as test"() {
        given:
        File testDir = projectWithE2eTestSourceSet("")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirectBom(testDir)
        testProperty(bom, "junit", "junit") == "false"

        where:
        javaVersion = JavaVersion.current()
    }

    def "custom testConfigs marks e2eTest-only dependencies as test"() {
        given:
        File testDir = projectWithE2eTestSourceSet("""
            tasks.cyclonedxDirectBom {
                testConfigs = ['(?i).*test.*']
            }
            """)

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirectBom(testDir)
        testProperty(bom, "junit", "junit") == "true"
        testProperty(bom, "com.fasterxml.jackson.core", "jackson-core") == "false"

        where:
        javaVersion = JavaVersion.current()
    }

    def "dependency in both production and test configs is not marked test"() {
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
                implementation 'com.fasterxml.jackson.core:jackson-core:2.14.2'
                testImplementation 'com.fasterxml.jackson.core:jackson-core:2.14.2'
            }
            """, "rootProject.name = 'test-label-mixed'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirectBom(testDir)
        testProperty(bom, "com.fasterxml.jackson.core", "jackson-core") == "false"

        where:
        javaVersion = JavaVersion.current()
    }

    def "empty testConfigs marks every component as non-test"() {
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
                implementation 'com.fasterxml.jackson.core:jackson-core:2.14.2'
                testImplementation 'junit:junit:4.13.2'
            }
            tasks.cyclonedxDirectBom {
                testConfigs = []
            }
            """, "rootProject.name = 'test-label-empty'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirectBom(testDir)
        testProperty(bom, "junit", "junit") == "false"
        testProperty(bom, "com.fasterxml.jackson.core", "jackson-core") == "false"

        where:
        javaVersion = JavaVersion.current()
    }

    private static File projectWithE2eTestSourceSet(String taskConfig) {
        return TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            sourceSets {
                e2eTest
            }
            dependencies {
                implementation 'com.fasterxml.jackson.core:jackson-core:2.14.2'
                e2eTestImplementation 'junit:junit:4.13.2'
            }
            ${taskConfig}
            """, "rootProject.name = 'test-label-e2e'")
    }

    private static Bom parseDirectBom(File testDir) {
        File reportDir = new File(testDir, "build/reports/cyclonedx-direct")
        return new JsonParser().parse(new File(reportDir, "bom.json"))
    }

    private static String testProperty(Bom bom, String group, String name) {
        Component component = bom.components.find { it.group == group && it.name == name }
        assert component != null: "component ${group}:${name} not found in BOM"
        def property = component.properties?.find { it.name == "cdx:maven:package:test" }
        assert property != null: "cdx:maven:package:test missing on ${group}:${name}"
        return property.value
    }
}
