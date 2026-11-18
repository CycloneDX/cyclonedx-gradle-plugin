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

    def "task should re-run when dependency version changes with single resolution"() {
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
                implementation 'commons-io:commons-io:2.18.0'
            }
        """.stripIndent(), "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "change dependency version"
        new File(testDir, "build.gradle").text = """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                implementation 'commons-io:commons-io:2.17.0'
            }
        """.stripIndent()
        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should re-execute (not UP_TO_DATE)"
        result2.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        javaVersion = JavaVersion.current()
    }

    def "task should re-run when its #property changes"() {
        given:
        File testDir = TestUtils.createFromString(
            buildWithMetadata(taskName, property, "first"), "rootProject.name = 'cache-test'")

        when: "first run"
        def result1 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should succeed"
        result1.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        when: "the value changes and nothing else does"
        new File(testDir, "build.gradle").text = buildWithMetadata(taskName, property, "second")

        def result2 = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "task should re-run (not UP-TO-DATE)"
        result2.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | property
        "cyclonedxDirectBom" | "organizationalEntity"
        "cyclonedxDirectBom" | "licenseChoice"
        "cyclonedxDirectBom" | "externalReferences"
        "cyclonedxBom"       | "organizationalEntity"
        "cyclonedxBom"       | "licenseChoice"
        "cyclonedxBom"       | "externalReferences"
        javaVersion = JavaVersion.current()
    }

    def "task runs with its externalReferences unset"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            group = 'com.example'
            version = '1.0.0'
            tasks.named('${taskName}') {
                externalReferences.set((Iterable) null)
            }
            """, "rootProject.name = 'cache-test'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then: "no value is a valid state for the references, so none is needed for their input either"
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName             | _
        "cyclonedxDirectBom" | _
        "cyclonedxBom"       | _
        javaVersion = JavaVersion.current()
    }

    /**
     * A build that sets one of the task's Core-typed metadata properties on one task only, so that the other task's
     * inputs stay the same between two runs.
     */
    private static String buildWithMetadata(String taskName, String property, String value) {
        def declaration
        switch (property) {
            case "organizationalEntity":
                declaration = """
                    def oe = new OrganizationalEntity()
                    oe.name = '${value}'
                    tasks.named('${taskName}') { organizationalEntity = oe }"""
                break
            case "licenseChoice":
                declaration = """
                    def license = new License()
                    license.name = '${value}'
                    def lc = new LicenseChoice()
                    lc.addLicense(license)
                    tasks.named('${taskName}') { licenseChoice = lc }"""
                break
            case "externalReferences":
                declaration = """
                    def reference = new ExternalReference()
                    reference.type = ExternalReference.Type.WEBSITE
                    reference.url = 'https://${value}.example.com'
                    tasks.named('${taskName}') { externalReferences = [reference] }"""
                break
            default:
                throw new IllegalArgumentException(property)
        }
        return """
            import org.cyclonedx.model.*

            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            ${declaration}
            """
    }
}
