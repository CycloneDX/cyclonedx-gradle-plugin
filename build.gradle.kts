import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")  version "2.1.1"
    id("org.cyclonedx.bom") version "3.3.0"
    id("groovy")
    id("com.diffplug.spotless") version "8.8.0"
    id("net.ltgt.errorprone") version "5.1.0"
}

val organization = "CycloneDX"
group = "org.cyclonedx"
version = "3.3.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.cyclonedx:cyclonedx-core-java:13.0.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }
    api("org.jspecify:jspecify:1.0.0")
    implementation("org.apache.maven:maven-core:3.9.16")

    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:2.4-M6-groovy-4.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")

    errorprone("com.uber.nullaway:nullaway:0.13.8")
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
}

// Builds LegacyConsumerFixture (see legacy-fixture-build/) - a stand-in for a buildSrc/build-logic consumer
// precompiled against the published cyclonedx-gradle-plugin:3.3.0 (Core 11.0.1) - so BinaryCompatibilitySpec can run
// that precompiled bytecode against this build's own candidate plugin (Core 13.0.0) and catch binary-incompatible
// Core upgrades that source-recompiled tests never see. It has to live in its own nested Gradle build, invoked here
// via `--project-dir` rather than as a source set of this project: this project publishes itself under the exact
// same GA (`org.cyclonedx:cyclonedx-gradle-plugin`) that the fixture needs to compile against at version 3.3.0, and
// Gradle 9.x unconditionally substitutes a project for any module dependency whose group:name matches a project in
// the same build - regardless of requested version, and not overridable via `resolutionStrategy` - which would
// silently make the fixture compile against this project's own (candidate) classes instead of the real old release.
val legacyFixtureBuildDir = "legacy-fixture-build"
val legacyFixtureJarFile = layout.projectDirectory.file("$legacyFixtureBuildDir/build/libs/legacy-consumer-fixture.jar")

val legacyFixtureJar = tasks.register<Exec>("legacyFixtureJar") {
    description = "Builds the LegacyConsumerFixture jar in an isolated nested Gradle build (see legacy-fixture-build/)"
    workingDir = rootDir
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val gradlewName = if (isWindows) "gradlew.bat" else "./gradlew"
    commandLine(gradlewName, "--project-dir", legacyFixtureBuildDir, "jar")
    inputs.dir("$legacyFixtureBuildDir/src")
    inputs.file("$legacyFixtureBuildDir/build.gradle.kts")
    inputs.file("$legacyFixtureBuildDir/settings.gradle.kts")
    outputs.file(legacyFixtureJarFile)
}

val localTestRepository = layout.buildDirectory.dir("local-repo")

listOf(8, 11, 17, 21, 25).forEach { version ->
    tasks.register<Test>("testJava$version") {
        description = "Runs tests with Java $version"
        group = "verification"
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(version))
            }
        )
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        if (version >= 11) {
            jvmArgs = listOf(
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED"
            )
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
        dependsOn("publishAllPublicationsToLocalTestRepository", legacyFixtureJar)
        // The fixture jar is only referenced by absolute path via a system property below, which Gradle's
        // up-to-date check treats as an opaque string - it would not notice the jar's *content* changing (e.g. a
        // LegacyConsumerFixture.java edit that doesn't also touch BinaryCompatibilitySpec.groovy) and could report a
        // stale PASSED result. Declaring it as an explicit input makes its content part of this task's up-to-date
        // check.
        inputs.files(legacyFixtureJar).withPropertyName("legacyFixtureJar").withPathSensitivity(PathSensitivity.NONE)
        systemProperty("localRepoPath", project.relativePath(localTestRepository.get()))
        systemProperty("pluginVersion", project.version.toString())
        systemProperty("legacyFixtureJarPath", legacyFixtureJarFile.asFile.absolutePath)
    }
}

tasks.named("test") {
    dependsOn("testJava8", "testJava11", "testJava17", "testJava21", "testJava25")
    enabled = false // Prevents the default test task from running tests itself
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn("processResources")
    options.encoding = "UTF-8"
    options.release = 8
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        check("DefaultCharset", CheckSeverity.ERROR)
        check("MissingOverride", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "org.cyclonedx.gradle")
        disable("MissingSummary")
    }
    // Include to disable NullAway on test code
    if (name.lowercase().contains("test")) {
        options.errorprone {
            disable("NullAway")
        }
    }
}

tasks.withType<GroovyCompile>().configureEach {
    dependsOn("processResources")
    options.encoding = "UTF-8"
    options.release = 8
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    )
}

val generatePluginProperties = tasks.register<WriteProperties>("generatePluginProperties") {
    destinationFile.set(layout.buildDirectory.file("generated-resources/plugin-properties/plugin.properties"))
    comment = "Automatically populated by Gradle build."
    property("name", project.name)
    property("vendor", organization)
    property("version", project.version)
}

tasks.named<ProcessResources>("processResources") {
    from(generatePluginProperties) {
        into("org/cyclonedx/gradle")
    }
}

publishing {
    repositories {
        maven {
            name = "localTest"
            url = uri(localTestRepository)
        }
    }
}

gradlePlugin {
    website.set("https://cyclonedx.org")
    vcsUrl.set("https://github.com/CycloneDX/cyclonedx-gradle-plugin.git")
    plugins {
        create("cycloneDxPlugin") {
            id = "org.cyclonedx.bom"
            displayName = "CycloneDX BOM Generator"
            description = "The CycloneDX Gradle plugin creates an aggregate of all direct and transitive dependencies of a project and creates a valid CycloneDX Software Bill of Materials (SBOM)."
            implementationClass = "org.cyclonedx.gradle.CyclonedxPlugin"
            tags.set(listOf("cyclonedx", "dependency", "dependencies", "owasp", "inventory", "bom", "sbom"))
        }
    }
}

spotless {
    java {
        palantirJavaFormat("2.86.0")
        formatAnnotations()
        licenseHeader("/*\n" +
            " * This file is part of CycloneDX Gradle Plugin.\n" +
            " *\n" +
            " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            " * you may not use this file except in compliance with the License.\n" +
            " * You may obtain a copy of the License at\n" +
            " *\n" +
            " *     http://www.apache.org/licenses/LICENSE-2.0\n" +
            " *\n" +
            " * Unless required by applicable law or agreed to in writing, software\n" +
            " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            " * See the License for the specific language governing permissions and\n" +
            " * limitations under the License.\n" +
            " *\n" +
            " * SPDX-License-Identifier: Apache-2.0\n" +
            " * Copyright (c) OWASP Foundation. All Rights Reserved.\n" +
            " */")
    }
}
