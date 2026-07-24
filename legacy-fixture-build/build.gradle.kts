// A deliberately separate Gradle build (its own settings.gradle.kts, invoked via `--project-dir` from the main
// build's `legacyFixtureJar` task; see build.gradle.kts) rather than a source set of the main project.
//
// Why it can't be a source set: the main project publishes itself as `org.cyclonedx:cyclonedx-gradle-plugin`, the
// exact GA of the artifact this fixture must compile against (the previously published 3.3.0 release). Gradle 9.x
// unconditionally substitutes a project for any module dependency whose group:name matches a project in the *same
// build*, regardless of requested version and regardless of any `resolutionStrategy.dependencySubstitution` rule -
// this is "deprecated" starting Gradle 10 but is current, non-overridable behavior on the Gradle 9.x line this repo
// targets. So `compileOnly("org.cyclonedx:cyclonedx-gradle-plugin:3.3.0")` declared inside the main build would
// always resolve back to the main build's own (candidate, Core 13.0.0) classes, silently defeating the whole point.
// A separate build has no such self-reference and resolves the real published 3.3.0 artifact.
plugins {
    id("java")
    id("com.diffplug.spotless") version "8.8.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // compileOnly: this fixture must never bundle the 3.3.0 plugin or Core 11.0.1 classes it compiles against -
    // only its own compiled class(es) get jarred (see the `jar` task below) and placed on a test's runtime
    // classpath, where those types must resolve against the *candidate* plugin instead.
    compileOnly("org.cyclonedx:cyclonedx-gradle-plugin:3.3.0")
    compileOnly(gradleApi())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 8
}

tasks.jar {
    archiveFileName.set("legacy-consumer-fixture.jar")
}

// Mirrors the root build's spotless { java { ... } } block: this nested build has its own build.gradle.kts, so the
// root project's spotless configuration does not reach in here. Kept in sync by hand if the root config changes.
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
