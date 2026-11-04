package org.cyclonedx.gradle

import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@Unroll("java version: #javaVersion")
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
class AggregationMembershipSpec extends Specification {

    private static final String ROOT_REF = "pkg:maven/com.example/agg@1.0.0?project_path=%3A"
    private static final String APP_A_REF = "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a"

    def "aggregate task is skipped when no members are declared"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            group = 'com.example'
            version = '1.0.0'
            """, "rootProject.name = 'agg'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SKIPPED
        result.task(":cyclonedxDirectBom") == null
        result.output.contains("it declares at least one cyclonedxAggregation member")
        !new File(testDir, "build/reports/cyclonedx/bom.json").exists()

        where:
        javaVersion = JavaVersion.current()
    }

    def "aggregating project contributes its own Direct SBOM only when it declares itself"() {
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
                implementation 'org.apache.commons:commons-lang3:3.12.0'
                ${selfDeclaration}
                cyclonedxAggregation project(':app-a')
            }
            """, "rootProject.name = 'agg'\ninclude 'app-a'")
        writeMember(testDir, "app-a", "implementation 'commons-io:commons-io:2.18.0'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-a:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        (result.task(":cyclonedxDirectBom") != null) == selfIncluded

        def bom = new JsonSlurper().parse(new File(testDir, "build/reports/cyclonedx/bom.json"))
        def componentNames = bom.components*.name
        componentNames.contains("app-a")
        componentNames.contains("commons-io")
        componentNames.contains("commons-lang3") == selfIncluded
        bom.dependencies.find { it.ref == ROOT_REF }.dependsOn.contains(APP_A_REF)

        where:
        selfDeclaration                     | selfIncluded
        ""                                  | false
        "cyclonedxAggregation project(':')" | true
        javaVersion = JavaVersion.current()
    }

    def "aggregate task fails when a declared member's Direct SBOM task is disabled, even with an earlier SBOM on disk"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
            }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                cyclonedxAggregation project(':app-a')
            }
            """, "rootProject.name = 'agg'\ninclude 'app-a'")
        File memberBuildFile = writeMember(testDir, "app-a", "")
        File staleSbom = new File(testDir, "app-a/build/reports/cyclonedx-direct/bom.json")

        when: "the member's Direct SBOM is generated"
        def firstRun = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        firstRun.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        staleSbom.exists()

        when: "its producer is disabled while the earlier SBOM remains on disk"
        memberBuildFile << "\ntasks.cyclonedxDirectBom { enabled = false }\n"
        def secondRun = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom"))
            .withPluginClasspath()
            .buildAndFail()

        then:
        staleSbom.exists()
        secondRun.task(":cyclonedxBom").outcome == TaskOutcome.FAILED
        secondRun.output.contains("Declared aggregation members produced no Direct BOM artifacts: [:app-a]")
        secondRun.output.contains("remove it from cyclonedxAggregation")

        where:
        javaVersion = JavaVersion.current()
    }

    def "aggregate task fails on a disabled declared member in a clean workspace"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
            }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                cyclonedxAggregation project(':app-a')
            }
            """, "rootProject.name = 'agg'\ninclude 'app-a'")
        writeMember(testDir, "app-a", "", "tasks.cyclonedxDirectBom { enabled = false }")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom"))
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.FAILED
        result.output.contains("Declared aggregation members produced no Direct BOM artifacts: [:app-a]")

        where:
        javaVersion = JavaVersion.current()
    }

    def "documented whole-tree initialization script aggregates every project (#scriptName)"() {
        given:
        def localRepoUrl = new File(System.getProperty("localRepoPath")).toURI().toString()
        def pluginVersion = System.getProperty("pluginVersion")
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }
            """, "rootProject.name = 'agg'\ninclude 'app-a'")
        File memberDir = new File(testDir, "app-a")
        memberDir.mkdirs()
        new File(memberDir, "build.gradle") << """
            plugins {
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                implementation 'commons-io:commons-io:2.18.0'
            }
            """
        new File(testDir, scriptName) << initScript
            .replace("LOCAL_REPO", localRepoUrl)
            .replace("PLUGIN_VERSION", pluginVersion)

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":cyclonedxBom", "--init-script", scriptName))
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        def bom = new JsonSlurper().parse(new File(testDir, "build/reports/cyclonedx/bom.json"))
        def componentNames = bom.components*.name
        componentNames.containsAll(["app-a", "commons-io", "commons-lang3"])
        bom.dependencies.find { it.ref == ROOT_REF }.dependsOn.contains(APP_A_REF)

        where:
        scriptName        | initScript
        "init.gradle"     | """
            import org.cyclonedx.gradle.CyclonedxPlugin

            initscript {
                repositories {
                    maven { url 'LOCAL_REPO' }
                    mavenCentral()
                }
                dependencies {
                    classpath 'org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:PLUGIN_VERSION'
                }
            }

            rootProject { aggregator ->
                aggregator.allprojects { member ->
                    member.apply plugin: CyclonedxPlugin
                    aggregator.dependencies.add('cyclonedxAggregation', aggregator.dependencies.project(path: member.path))
                }
            }
            """
        "init.gradle.kts" | """
            import org.cyclonedx.gradle.CyclonedxPlugin

            initscript {
                repositories {
                    maven { url = uri("LOCAL_REPO") }
                    mavenCentral()
                }
                dependencies {
                    classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:PLUGIN_VERSION")
                }
            }

            rootProject {
                val aggregator = this
                allprojects {
                    apply<CyclonedxPlugin>()
                    aggregator.dependencies.add("cyclonedxAggregation", aggregator.dependencies.project(mapOf("path" to path)))
                }
            }
            """
        javaVersion = JavaVersion.current()
    }

    private static File writeMember(File testDir, String name, String dependencies, String extra = "") {
        File memberDir = new File(testDir, name)
        memberDir.mkdirs()
        File buildFile = new File(memberDir, "build.gradle")
        buildFile << """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories { mavenCentral() }
            group = 'com.example'
            version = '1.0.0'
            dependencies {
                ${dependencies}
            }
            ${extra}
            """
        return buildFile
    }
}
