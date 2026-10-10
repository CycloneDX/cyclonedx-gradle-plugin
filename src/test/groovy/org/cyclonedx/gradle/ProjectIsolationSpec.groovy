package org.cyclonedx.gradle

import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assumptions
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Acceptance test for Project Isolation support (issue #847) with the explicit, per-project aggregator setup.
 */
@Unroll("java version: #javaVersion, gradle version: #gradleVersion")
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
class ProjectIsolationSpec extends Specification {

    /** Oldest Gradle version the plugin is verified against with Project Isolation enabled. */
    static final String MIN_ISOLATED_PROJECTS_GRADLE = "8.11"

    private static final String ROOT_REF = "pkg:maven/com.example/isolated@1.0.0?project_path=%3A"
    private static final String APP_A_REF = "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a"
    private static final String APP_B_REF = "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b"

    def "should aggregate explicitly declared members with Project Isolation enabled"() {
        given:
        Assumptions.assumeTrue(
            GradleVersion.version(gradleVersion) >= GradleVersion.version(MIN_ISOLATED_PROJECTS_GRADLE),
            "Project Isolation is verified from Gradle " + MIN_ISOLATED_PROJECTS_GRADLE)
        Assumptions.assumeFalse(
            JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)
                && GradleVersion.version(gradleVersion).majorVersion < 9,
            "Gradle prior 9 does not support Java 25 or higher")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
            }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                cyclonedxAggregation project(':app-a')
                cyclonedxAggregation project(':app-b')
            }
            """, "rootProject.name = 'isolated'\ninclude 'app-a', 'app-b'")
        writeMember(testDir, "app-a", "java-library", "implementation 'commons-io:commons-io:2.18.0'")
        writeMember(testDir, "app-b", "java", "implementation project(':app-a')")

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom", "-Dorg.gradle.unsafe.isolated-projects=true"))
            .withPluginClasspath()
            .build()

        then: "aggregation succeeds with Project Isolation active"
        result.output.toLowerCase(Locale.ROOT).contains("isolated projects is an incubating feature")
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-a:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-b:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "every declared member is present"
        def bom = new JsonSlurper().parse(new File(testDir, "build/reports/cyclonedx/bom.json"))
        bom.metadata.component."bom-ref" == ROOT_REF
        def componentRefs = bom.components*."bom-ref"
        componentRefs.contains(APP_A_REF)
        componentRefs.contains(APP_B_REF)
        bom.components*.name.contains("commons-io")

        and: "the root component depends on each member"
        def rootDependencies = bom.dependencies.find { it.ref == ROOT_REF }.dependsOn
        rootDependencies.contains(APP_A_REF)
        rootDependencies.contains(APP_B_REF)

        and: "member relationships are preserved"
        bom.dependencies.find { it.ref == APP_B_REF }.dependsOn.contains(APP_A_REF)

        where:
        gradleVersion << [GradleVersion.current().version, '9.0.0', '8.14', MIN_ISOLATED_PROJECTS_GRADLE]
        javaVersion = JavaVersion.current()
    }

    private static void writeMember(File testDir, String name, String javaPlugin, String dependency) {
        File memberDir = new File(testDir, name)
        memberDir.mkdirs()
        new File(memberDir, "build.gradle") << """
            plugins {
                id 'org.cyclonedx.bom'
                id '${javaPlugin}'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                ${dependency}
            }
            """
    }
}
