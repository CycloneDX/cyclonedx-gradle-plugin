package org.cyclonedx.gradle

import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Regression coverage for issue #884: upgrading {@code cyclonedx-core-java} must not break consumers who configure
 * this plugin from precompiled bytecode (a {@code buildSrc} convention plugin or an included {@code build-logic}
 * build), compiled once against an older plugin release and then loaded, unchanged, against a newer one.
 *
 * <p>Ordinary specs can't catch this: they always drive the plugin through a build script Gradle recompiles from
 * source, which recompiles against whatever Core version is currently on the classpath and so can never observe a
 * removed/incompatibly-changed method.
 *
 * <p>This spec instead loads {@code LegacyConsumerFixture} - compiled ahead of time (see the {@code legacy-fixture-
 * build} nested Gradle build and its {@code legacyFixtureJar} wiring in the root {@code build.gradle.kts}) against
 * the published {@code cyclonedx-gradle-plugin:3.3.0}, which
 * transitively depends on {@code cyclonedx-core-java:11.0.1} - onto the buildscript classpath of a project that
 * applies the *candidate* plugin (this build's own code, resolved as a real published artifact from the local test
 * repository, on whatever Core version is currently under test). Neither the 3.3.0 jar nor Core 11.0.1 is anywhere on
 * that runtime classpath, so every type {@code LegacyConsumerFixture} references resolves against the candidate's
 * classes. If a method or field it calls was removed or changed incompatibly, the build fails with a real
 * {@code NoSuchMethodError}/{@code NoSuchFieldError} instead of quietly succeeding.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
class BinaryCompatibilitySpec extends Specification {

    def "precompiled consumer bytecode from plugin 3.3.0 runs against the candidate plugin's Core version"() {
        given: "the candidate plugin resolved as a real published artifact, plus a fixture jar compiled against 3.3.0"
        def localRepoUrl = new File(System.getProperty("localRepoPath")).toURI().toString()
        def pluginVersion = System.getProperty("pluginVersion")
        def legacyFixtureJarPath = System.getProperty("legacyFixtureJarPath").replace("\\", "/")

        File testDir = TestUtils.createFromString("""
            buildscript {
                dependencies {
                    classpath files('${legacyFixtureJarPath}')
                }
            }
            plugins {
                id 'org.cyclonedx.bom' version '${pluginVersion}'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            // Stands in for a buildSrc/build-logic convention plugin precompiled against plugin 3.3.0: it directly
            // casts the live task to CyclonedxDirectTask and calls the same five Core-backed properties a real
            // consumer would, using Core 11.0.1 types.
            tasks.named('cyclonedxDirectBom') { t ->
                org.cyclonedx.gradle.fixture.LegacyConsumerFixture.configure(t)
            }
            """, """
            pluginManagement {
                repositories {
                    maven { url '${localRepoUrl}' }
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'binary-compatibility'
        """)

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the BOM reflects values the 3.3.0-compiled fixture set - proving the old calls actually took effect"
        def bom = new JsonSlurper().parse(new File(testDir, "build/reports/cyclonedx-direct/bom.json"))
        bom.metadata.manufacturer.name == "Legacy Consumer Org"
        bom.metadata.licenses*.license*.name == ["Legacy Consumer License"]
        bom.metadata.component.externalReferences*.url.contains("https://legacy-consumer.example.com")
    }
}
