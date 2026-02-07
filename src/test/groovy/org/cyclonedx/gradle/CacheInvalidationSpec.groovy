package org.cyclonedx.gradle

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for cache invalidation when dependencies change.
 * The CycloneDX task should properly invalidate its cache when:
 * - A new dependency is added
 * - A dependency version changes
 * - A dynamic version resolves to a different version
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion, task name: #taskName")
class CacheInvalidationSpec extends Specification {

    def "task should be UP-TO-DATE when nothing changes"() {
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }""", "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should succeed"
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "second run without changes"
        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should be UP-TO-DATE or FROM_CACHE"
        result2.task(":" + taskName).outcome in [TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE]

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        javaVersion = JavaVersion.current()
    }

    def "task should re-run when a new dependency is added"() {
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }""", "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should succeed"
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "add a new dependency"
        def buildFile = new File(testDir, "build.gradle")
        buildFile.text = """
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
                implementation 'com.google.guava:guava:32.1.3-jre'
            }"""

        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should re-run (not UP-TO-DATE)"
        result2.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        javaVersion = JavaVersion.current()
    }

    def "task should re-run when dependency version changes"() {
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }""", "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should succeed"
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "change dependency version"
        def buildFile = new File(testDir, "build.gradle")
        buildFile.text = """
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
                implementation 'org.apache.commons:commons-lang3:3.13.0'
            }"""

        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should re-run (not UP-TO-DATE)"
        result2.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        javaVersion = JavaVersion.current()
    }

    def "task should re-run when dependency is removed"() {
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
                implementation 'com.google.guava:guava:32.1.3-jre'
            }""", "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should succeed"
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "remove a dependency"
        def buildFile = new File(testDir, "build.gradle")
        buildFile.text = """
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }"""

        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should re-run (not UP-TO-DATE)"
        result2.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        javaVersion = JavaVersion.current()
    }
}
