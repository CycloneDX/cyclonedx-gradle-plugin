package org.cyclonedx.gradle

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Tests for implicit task dependency issues.
 *
 * The CycloneDX task resolves all resolvable configurations and declares the
 * result as @InputFiles for cache invalidation. When any configuration contains
 * a file dependency backed by a task output (e.g. {@code dependencies { implementation files(someTask) }}),
 * Gradle detects that the CycloneDX task uses another task's output without
 * declaring an explicit dependency, causing a build failure:
 *
 * "Task ':cyclonedxDirectBom' uses this output of task ':someTask'
 *  without declaring an explicit or implicit dependency."
 *
 * This affects any plugin or build logic that adds task-produced files as
 * dependencies to a resolvable configuration, including but not limited to
 * the java-gradle-plugin (pluginUnderTestMetadata) and custom code generation tasks.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
class ImplicitDependencySpec extends Specification {

    def "should not fail with implicit dependency on custom task output added as file dependency"() {
        given: "a project with a custom task whose output is added as a file dependency"
        def testDir = File.createTempDir("from-literal")

        new File(testDir, "settings.gradle") << "rootProject.name = 'custom-task-project'"
        new File(testDir, "build.gradle") << """
            plugins {
                id 'java'
                id 'org.cyclonedx.bom'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            def generateCode = tasks.register('generateCode', Copy) {
                from 'templates'
                into layout.buildDirectory.dir('generated')
            }

            dependencies {
                implementation files(generateCode)
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }
        """

        // Create the templates directory so the Copy task has a source
        new File(testDir, "templates").mkdirs()
        new File(testDir, "templates/placeholder.txt") << "placeholder"

        when: "running both cyclonedxDirectBom and build tasks together"
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom", "build"))
            .withPluginClasspath()
            .build()

        then: "the build should succeed without implicit dependency errors"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
    }

    def "should not fail with implicit dependency on pluginUnderTestMetadata"() {
        given: "a project that applies both java-gradle-plugin and org.cyclonedx.bom"
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'java-gradle-plugin'
                id 'org.cyclonedx.bom'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = 'com.example.my-plugin'
                        implementationClass = 'com.example.MyPlugin'
                    }
                }
            }

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }
        """, "rootProject.name = 'plugin-project'")

        when: "running both cyclonedxDirectBom and build tasks together"
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom", "build"))
            .withPluginClasspath()
            .build()

        then: "the build should succeed without implicit dependency errors"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
    }

    def "should not fail with implicit dependency in multi-project with java-gradle-plugin subproject"() {
        given: "a multi-project build where a subproject applies java-gradle-plugin"
        def rootDir = File.createTempDir("from-literal")

        // Root settings.gradle
        new File(rootDir, "settings.gradle") << """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'multi-module-plugin'
            include 'plugin-sub'
        """

        // Root build.gradle
        new File(rootDir, "build.gradle") << """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
        """

        // Subproject that applies java-gradle-plugin
        def subDir = new File(rootDir, "plugin-sub")
        subDir.mkdirs()

        new File(subDir, "build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
                id 'org.cyclonedx.bom'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = 'com.example.sub-plugin'
                        implementationClass = 'com.example.SubPlugin'
                    }
                }
            }

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }
        """

        when: "running the build and cyclonedxBom aggregate tasks together"
        def result = GradleRunner.create()
            .withProjectDir(rootDir)
            .withArguments(TestUtils.arguments("build", "cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then: "the build should succeed without implicit dependency errors"
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
    }
}
