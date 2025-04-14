import java.util.*

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")  version "1.3.1"
    id("org.cyclonedx.bom") version "2.2.0"
    id("groovy")
    id("com.diffplug.spotless") version "6.13.0"
}

val organization = "CycloneDX"
group = "org.cyclonedx"
version = "2.2.0"

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.cyclonedx:cyclonedx-core-java:10.2.1") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }

    implementation("commons-codec:commons-codec:1.18.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("org.apache.maven:maven-core:3.9.9")

    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:2.3-groovy-3.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    if (JavaVersion.current().isJava11Compatible()) {
        jvmArgs = listOf(
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    doLast {
        val resourcesDirectory = project.layout.buildDirectory.dir("resources/main")
        val pluginPropertiesFile = file("${resourcesDirectory.get()}/org/cyclonedx/gradle/plugin.properties")

        val pluginProperties = Properties()
        pluginProperties["name"] = project.name
        pluginProperties["vendor"] = organization
        pluginProperties["version"] = project.version

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
            implementationClass = "org.cyclonedx.gradle.CycloneDxPlugin"
            tags.set(listOf("cyclonedx", "dependency", "dependencies", "owasp", "inventory", "bom", "sbom"))
        }
    }
}

spotless {
    java {
        var palantirVersion = "1.1.0"
        if (JavaVersion.current() == JavaVersion.VERSION_21) {
            palantirVersion = "2.50.0"
        }
        palantirJavaFormat(palantirVersion)
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
