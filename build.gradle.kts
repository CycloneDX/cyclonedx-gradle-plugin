import java.util.Properties
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")  version "2.0.0"
    id("org.cyclonedx.bom") version "3.0.1"
    id("groovy")
    id("com.diffplug.spotless") version "8.0.0"
    id("net.ltgt.errorprone") version "4.3.0"
}

val organization = "CycloneDX"
group = "org.cyclonedx"
version = "3.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.cyclonedx:cyclonedx-core-java:11.0.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }
    api("org.jspecify:jspecify:1.0.0")

    implementation("commons-codec:commons-codec:1.19.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.maven:maven-core:3.9.11")

    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:2.4-M6-groovy-4.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")

    errorprone("com.uber.nullaway:nullaway:0.12.10")
    errorprone("com.google.errorprone:error_prone_core:2.41.0")
}

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

tasks.named<ProcessResources>("processResources") {
    val resourcesDirectory = project.layout.buildDirectory.dir("resources/main")
    val pluginPropertiesFile = file("${resourcesDirectory.get()}/org/cyclonedx/gradle/plugin.properties")
    outputs.file(pluginPropertiesFile)
    val pluginProperties = Properties()
    pluginProperties["name"] = project.name
    pluginProperties["vendor"] = organization
    pluginProperties["version"] = project.version
    doLast {
        pluginProperties.store(pluginPropertiesFile.writer(), "Automatically populated by Gradle build.")
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
        palantirJavaFormat("2.50.0")
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
