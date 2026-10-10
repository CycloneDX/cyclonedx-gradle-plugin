# CycloneDX Gradle Plugin

[![Build Status](https://github.com/CycloneDX/cyclonedx-gradle-plugin/workflows/Build%20CI/badge.svg)](https://github.com/CycloneDX/cyclonedx-gradle-plugin/actions?workflow=Build+CI)
[![Gradle Plugin](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Forg%2Fcyclonedx%2Fbom%2Forg.cyclonedx.bom.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/org.cyclonedx.bom)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](LICENSE)

The CycloneDX Gradle plugin generates CycloneDX Software Bill of Materials (SBOM) documents from Gradle's resolved
dependency graphs. It records the components and relationships Gradle selected after conflict resolution,
substitution, constraints, and transitive dependency resolution.

Apply the plugin to each project that should be described. Every such project gets:

- a **Direct SBOM** of that project, from `cyclonedxDirectBom`; and
- a `cyclonedxBom` task that produces an **Aggregate SBOM** of the projects it explicitly declares as members.

The plugin writes both JSON and XML by default and supports configuration cache, parallel execution, Gradle
up-to-date checks, the build cache, and Gradle's Project Isolation.

> [!IMPORTANT]
> Plugin 4.0 replaced root-driven aggregation with explicit membership. Applying the plugin only to the root project no
> longer generates SBOMs for subprojects or includes them in the Aggregate SBOM. See the
> [migration guide](docs/migrating-to-4.0.md).

> [!NOTE]
> This README documents the code on the current branch. For an installed release, use the README from that release's
> Git tag.

## Contents

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Choose an SBOM](#choose-an-sbom)
- [Aggregate a multi-project build](#aggregate-a-multi-project-build)
  - [Declare Contributing Projects](#declare-contributing-projects)
  - [Exclude a project from aggregation](#exclude-a-project-from-aggregation)
  - [Project Isolation](#project-isolation)
- [Configure the tasks](#configure-the-tasks)
  - [Configure Direct SBOMs in a multi-project build](#configure-direct-sboms-in-a-multi-project-build)
  - [Select configurations](#select-configurations)
  - [Configure output files](#configure-output-files)
  - [Add a CI build reference](#add-a-ci-build-reference)
- [Configuration reference](#configuration-reference)
- [Advanced recipes](#advanced-recipes)
  - [Set component and organizational metadata](#set-component-and-organizational-metadata)
  - [Apply the plugin from an initialization script](#apply-the-plugin-from-an-initialization-script)
- [Using SBOMs with SLSA provenance](#using-sboms-with-slsa-provenance)
- [Compatibility history](#compatibility-history)
- [Migrating from 3.x](docs/migrating-to-4.0.md)
- [Community and contributing](#community-and-contributing)
- [License](#license)

## Requirements

| Requirement | Plugin 4.x |
|-------------|------------|
| Gradle | 8.4 or newer |
| Build JVM | Java 8 or newer; support for versions before Java 17 is deprecated |
| CycloneDX schema | 1.6 by default; 1.7 can be selected with `schemaVersion` |
| Output formats | JSON and XML |

The JVM that can run a particular build also depends on the
[Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html). The plugin is tested
on Java 8, 11, 17, 21, and 25.

## Quick start

Apply the plugin to the project. The version shown here matches the version declared by this branch.

**Kotlin DSL (`build.gradle.kts`):**

```kotlin
plugins {
    id("org.cyclonedx.bom") version "4.0.0"
}
```

**Groovy DSL (`build.gradle`):**

```groovy
plugins {
    id 'org.cyclonedx.bom' version '4.0.0'
}
```

Generate the project's Direct SBOM:

```shell
./gradlew cyclonedxDirectBom
```

With the default configuration, the outputs are:

```text
build/reports/cyclonedx-direct/bom.json
build/reports/cyclonedx-direct/bom.xml
```

To produce an Aggregate SBOM, declare which projects it contains. A single-project build declares itself:

**Kotlin DSL:**

```kotlin
dependencies {
    cyclonedxAggregation(project(":"))
}
```

**Groovy DSL:**

```groovy
dependencies {
    cyclonedxAggregation project(':')
}
```

```shell
./gradlew cyclonedxBom
```

The Aggregate SBOM is written to `build/reports/cyclonedx/bom.{json,xml}`. Multi-project builds are covered in
[Aggregate a multi-project build](#aggregate-a-multi-project-build).

## Choose an SBOM

| Task | Use it when | Default output |
|------|-------------|----------------|
| `cyclonedxDirectBom` | You need the SBOM of one project, for example to publish or attest with that project's artifact. | `<project>/build/reports/cyclonedx-direct/bom.{json,xml}` |
| `cyclonedxBom` | You need one SBOM combining several projects, for example for a distribution assembled from them. | `<project>/build/reports/cyclonedx/bom.{json,xml}` |

Applying the plugin to a project registers both tasks on that project, and on that project only. `cyclonedxBom`
aggregates exactly the projects declared on the same project's `cyclonedxAggregation` configuration. When none are
declared it has nothing to aggregate and is skipped, so a project can apply the plugin purely to contribute its Direct
SBOM.

Generate every Direct SBOM of the build:

```shell
./gradlew cyclonedxDirectBom
```

Generate one subproject's Direct SBOM:

```shell
./gradlew :subproject:cyclonedxDirectBom
```

## Aggregate a multi-project build

### Declare Contributing Projects

Apply the plugin to every project whose Direct SBOM belongs to the Aggregate SBOM, and to the Aggregating Project that
produces it. Then declare the members on the Aggregating Project's `cyclonedxAggregation` configuration, in the same way
as Gradle's `jacocoAggregation` and `testReportAggregation`.

In `app-a/build.gradle.kts` and `app-b/build.gradle.kts`:

```kotlin
plugins {
    id("org.cyclonedx.bom") version "4.0.0"
}
```

In the Aggregating Project's `build.gradle.kts`:

```kotlin
plugins {
    id("org.cyclonedx.bom") version "4.0.0"
}

dependencies {
    cyclonedxAggregation(project(":app-a"))
    cyclonedxAggregation(project(":app-b"))
}
```

Or, in Groovy DSL:

```groovy
dependencies {
    cyclonedxAggregation project(':app-a')
    cyclonedxAggregation project(':app-b')
}
```

Run `./gradlew :cyclonedxBom`. Gradle builds each member's Direct SBOM first. The Aggregate SBOM contains every
declared member with its components and relationships, plus a dependency from the Aggregate SBOM's main component to
each member's main component. The leading `:` runs only the root's aggregation; an unqualified `./gradlew cyclonedxBom`
also runs, and skips, the task in every project that declares no members.

Membership is explicit only:

- Nothing is discovered. A project that is not declared is not in the Aggregate SBOM, even if it applies the plugin.
- The Aggregating Project's own Direct SBOM, with the dependencies declared in its build script, is included only when
  it declares itself with `cyclonedxAggregation(project(":"))`, or with its own path when it is not the root.
- `cyclonedxAggregation` accepts only project dependencies. A module dependency fails the build.

Rather than leaving a declared member out of the document, `cyclonedxBom` fails when that member:

- does not apply the plugin, so it has no `cyclonedxDirectBom` variant;
- has a disabled `cyclonedxDirectBom` task, or unsets both outputs, so it publishes no Direct SBOM;
- has no Direct SBOM on disk because the producing task was skipped at execution time, for example by `onlyIf`; or
- has a Direct SBOM that cannot be parsed.

Any project can be an Aggregating Project, and several can coexist, for example one Aggregate SBOM per distribution.

### Exclude a project from aggregation

Remove its `cyclonedxAggregation` declaration. Disabling a declared member's `cyclonedxDirectBom` task does not exclude
it: the member then publishes no Direct SBOM and `cyclonedxBom` fails, even when a file from an earlier build is still
on disk.

To also stop generating a project's Direct SBOM, disable the task in that project once nothing declares it:

**Kotlin DSL:**

```kotlin
tasks.cyclonedxDirectBom {
    enabled = false
}
```

**Groovy DSL:**

```groovy
tasks.cyclonedxDirectBom {
    enabled = false
}
```

### Project Isolation

The per-project setup above is compatible with
[Project Isolation](https://docs.gradle.org/current/userguide/isolated_projects.html): each project configures only
itself, and the Aggregate SBOM consumes members through variant-aware dependency resolution. It is tested with
`-Dorg.gradle.unsafe.isolated-projects=true` on Gradle 8.11 and newer. Project Isolation is an incubating Gradle feature.

Anything that configures other projects from one build script, such as `allprojects`, `subprojects`, or the whole-tree
[initialization script](#apply-the-plugin-from-an-initialization-script), requires Project Isolation to be disabled.

## Configure the tasks

Configuration belongs directly to `cyclonedxDirectBom` and `cyclonedxBom`; the plugin does not add an extension.
Configure a Direct SBOM in the project it describes, and configure an Aggregate SBOM in its Aggregating Project. Values
are not copied between the tasks. For example, set `schemaVersion` on both when Direct and Aggregate SBOMs should use
the same non-default schema.

### Configure Direct SBOMs in a multi-project build

Configure each Direct SBOM in the project it describes. For example, in `subproject/build.gradle.kts`:

**Kotlin DSL:**

```kotlin
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    projectType = Component.Type.APPLICATION
    includeLicenseText = true
}
```

Or in `subproject/build.gradle`:

```groovy
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

tasks.named('cyclonedxDirectBom', CyclonedxDirectTask) {
    projectType = Component.Type.APPLICATION
    includeLicenseText = true
}
```

When many projects share settings, put the plugin and this configuration in a
[convention plugin](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html) and apply
it to those projects. Unlike `allprojects` and `subprojects`, a convention plugin keeps each project's configuration
local to it and remains compatible with Project Isolation.

### Select configurations

By default, a Direct SBOM scans every resolvable project configuration. `includeConfigs` and `skipConfigs` contain
regular expressions matched against the whole configuration name, as with Java's `String.matches`. An empty
`includeConfigs` includes every resolvable configuration; a matching `skipConfigs` entry always excludes a
configuration.

`testConfigs` does not select dependencies. It controls the `cdx:maven:package:test` label on components that are
already in the Direct SBOM. A component is marked as test only when every configuration that contributed it matches a
`testConfigs` pattern.

**Kotlin DSL:**

```kotlin
tasks.cyclonedxDirectBom {
    includeConfigs = listOf("runtimeClasspath", "compileClasspath")
    skipConfigs = listOf("(?i).*test.*")
    testConfigs = listOf("(?i).*test.*")
}
```

**Groovy DSL:**

```groovy
tasks.cyclonedxDirectBom {
    includeConfigs = ['runtimeClasspath', 'compileClasspath']
    skipConfigs = ['(?i).*test.*']
    testConfigs = ['(?i).*test.*']
}
```

Set `testConfigs` to an empty list when no configuration should be classified as a Test Configuration.

### Configure output files

Both tasks write JSON and XML by default. Assign a different file to move or rename an output. Clear an output's
convention to disable that format. The explicit `RegularFile` cast keeps the example compatible with Gradle 8.4.
Configure each project's tasks in that project, or in a convention plugin it applies.

**Kotlin DSL:**

```kotlin
import org.gradle.api.file.RegularFile

tasks.cyclonedxDirectBom {
    jsonOutput = layout.buildDirectory.file("reports/sbom/${project.name}-bom.json")
    xmlOutput.convention(null as RegularFile?)
}

tasks.cyclonedxBom {
    jsonOutput = layout.buildDirectory.file("reports/sbom/bom.json")
    xmlOutput.convention(null as RegularFile?)
}
```

**Groovy DSL:**

```groovy
import org.gradle.api.file.RegularFile

tasks.cyclonedxDirectBom {
    jsonOutput = layout.buildDirectory.file("reports/sbom/${project.name}-bom.json")
    xmlOutput.convention((RegularFile) null)
}

tasks.cyclonedxBom {
    jsonOutput = layout.buildDirectory.file('reports/sbom/bom.json')
    xmlOutput.convention((RegularFile) null)
}
```

A member that disables one format still contributes the other. A member that disables both publishes no Direct SBOM,
so declaring it fails aggregation.

### Add a CI build reference

When `includeBuildSystem` is `true`, the plugin automatically detects build URLs from GitHub Actions, GitLab CI,
Jenkins, CircleCI, Travis CI, and Drone. Set `buildSystemEnvironmentVariable` to use another environment variable or
to construct a URL from several variables.

Every variable in a template must exist and have a non-blank value; otherwise no build-system reference is added.

**Kotlin DSL:**

```kotlin
tasks.cyclonedxDirectBom {
    buildSystemEnvironmentVariable = "\${CI_SERVER_URL}/jobs/\${CI_JOB_ID}"
}
```

**Groovy DSL:**

```groovy
tasks.cyclonedxDirectBom {
    buildSystemEnvironmentVariable = '${CI_SERVER_URL}/jobs/${CI_JOB_ID}'
}
```

To read one variable directly, set the property to its name, for example `"BUILD_URL"` in Kotlin or `'BUILD_URL'` in
Groovy.

## Configuration reference

### Properties shared by both tasks

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `componentGroup` | `String` | Project group | Group of the document's main component. |
| `componentName` | `String` | Project name | Name of the document's main component. |
| `componentVersion` | `String` | Project version | Version of the document's main component. |
| `componentDescription` | `String` | Not set | Description of the document's main component. |
| `projectType` | `Component.Type` | `LIBRARY` | CycloneDX type of the main component. Kotlin requires an enum value such as `Component.Type.APPLICATION`; Groovy also accepts a type name such as `'application'`. |
| `schemaVersion` | `Version` | `VERSION_16` | CycloneDX schema used for serialization. Set `Version.VERSION_17` to opt in to CycloneDX 1.7. |
| `includeBomSerialNumber` | `Boolean` | `true` | Add a generated `urn:uuid:` serial number. |
| `includeLicenseText` | `Boolean` | `false` | Include complete license text when it can be resolved. |
| `includeBuildSystem` | `Boolean` | `true` | Add a `BUILD_SYSTEM` external reference when the build URL can be detected. |
| `buildSystemEnvironmentVariable` | `String` | Not set | Environment-variable name or `${NAME}` template used instead of automatic CI detection. |
| `organizationalEntity` | `OrganizationalEntity` | Not set | Manufacturer or organizational metadata for the SBOM. |
| `licenseChoice` | `LicenseChoice` | Not set | License information placed in the SBOM metadata. |
| `externalReferences` | `List<ExternalReference>` | Not set | External references added to the main component. A VCS reference is detected from CI or the Git remote when one was not supplied. |
| `jsonOutput` | `RegularFileProperty` | Task-specific `bom.json` | JSON output. Clear its convention to disable JSON. |
| `xmlOutput` | `RegularFileProperty` | Task-specific `bom.xml` | XML output. Clear its convention to disable XML. |

### Properties specific to `cyclonedxDirectBom`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `includeConfigs` | `List<String>` | `[]` | Full-match regular expressions for configurations to scan. Empty means every resolvable configuration. |
| `skipConfigs` | `List<String>` | `[]` | Full-match regular expressions for configurations to exclude. Exclusion takes precedence over inclusion. |
| `testConfigs` | `List<String>` | `["^test.*"]` | Full-match regular expressions that classify Test Configurations for `cdx:maven:package:test`. Empty means no configuration is a Test Configuration. |
| `includeMetadataResolution` | `Boolean` | `true` | Resolve additional dependency metadata such as descriptions, publishers, external references, and licenses. |
| `includeBuildEnvironment` | `Boolean` | `false` | Also scan resolvable buildscript configurations. The include and skip patterns apply to them too. |

`cyclonedxBom` has no additional user-configurable properties.

## Advanced recipes

### Set component and organizational metadata

These properties use types from `cyclonedx-core-java`, which is exposed by the plugin.

**Kotlin DSL:**

```kotlin
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity

tasks.cyclonedxDirectBom {
    projectType = Component.Type.APPLICATION
    schemaVersion = Version.VERSION_17
    componentName = "payment-service"
    componentVersion = "2.0.0"

    organizationalEntity = OrganizationalEntity().apply {
        name = "ACME Corporation"
        urls = listOf("https://www.example.com")
        addContact(OrganizationalContact().apply {
            name = "Security Team"
            email = "security@example.com"
        })
    }

    externalReferences = listOf(
        ExternalReference().apply {
            type = ExternalReference.Type.WEBSITE
            url = "https://www.example.com/payment-service"
        }
    )

    licenseChoice = LicenseChoice().apply {
        addLicense(License().apply {
            name = "Apache-2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        })
    }
}
```

**Groovy DSL:**

```groovy
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity

def organization = new OrganizationalEntity()
organization.name = 'ACME Corporation'
organization.urls = ['https://www.example.com']

def contact = new OrganizationalContact()
contact.name = 'Security Team'
contact.email = 'security@example.com'
organization.addContact(contact)

def website = new ExternalReference()
website.type = ExternalReference.Type.WEBSITE
website.url = 'https://www.example.com/payment-service'

def license = new License()
license.name = 'Apache-2.0'
license.url = 'https://www.apache.org/licenses/LICENSE-2.0.txt'

def licenses = new LicenseChoice()
licenses.addLicense(license)

tasks.cyclonedxDirectBom {
    projectType = Component.Type.APPLICATION
    schemaVersion = Version.VERSION_17
    componentName = 'payment-service'
    componentVersion = '2.0.0'
    organizationalEntity = organization
    externalReferences = [website]
    licenseChoice = licenses
}
```

An explicit VCS external reference suppresses automatic Git-remote detection. Other explicit reference types are
combined with the automatically detected VCS reference when one is available.

The example configures one Direct SBOM. Configure the same metadata properties on `cyclonedxBom` when they should
describe the Aggregate SBOM's main component or metadata.

### Apply the plugin from an initialization script

An initialization script can generate SBOMs without changing a build's files. This is useful in CI or for a build you
do not own. The following script restores the zero-wiring, whole-tree aggregation of plugin 3.x: it applies the plugin
to every project and declares every project, including the root, as a member of the root's Aggregate SBOM.

> [!WARNING]
> This script configures every project from the root, which is cross-project configuration. It works only with
> [Project Isolation](#project-isolation) disabled. With Project Isolation enabled, apply the plugin and declare
> members in each build script instead.

**Kotlin DSL (`init.gradle.kts`):**

```kotlin
import org.cyclonedx.gradle.CyclonedxPlugin

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:4.0.0")
    }
}

rootProject {
    val aggregator = this
    allprojects {
        apply<CyclonedxPlugin>()
        aggregator.dependencies.add("cyclonedxAggregation", aggregator.dependencies.project(mapOf("path" to path)))
    }
}
```

Run:

```shell
./gradlew :cyclonedxBom --init-script init.gradle.kts
```

**Groovy DSL (`init.gradle`):**

```groovy
import org.cyclonedx.gradle.CyclonedxPlugin

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath 'org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:4.0.0'
    }
}

rootProject { aggregator ->
    aggregator.allprojects { member ->
        member.apply plugin: CyclonedxPlugin
        aggregator.dependencies.add('cyclonedxAggregation', aggregator.dependencies.project(path: member.path))
    }
}
```

Run:

```shell
./gradlew :cyclonedxBom --init-script init.gradle
```

`allprojects` visits the root first, so the root applies the plugin before any member is declared on it. To leave a
project out, skip it in the `allprojects` block instead of disabling its task.

## Using SBOMs with SLSA provenance

SLSA Build levels apply to an artifact's build provenance and build platform, not to its SBOM. This plugin generates
the CycloneDX SBOM; a hosted build platform can separately generate signed SLSA provenance and bind that same artifact
to the SBOM in an SBOM attestation. Applying this plugin alone does not establish a SLSA Build level, and there is no
such thing as a “SLSA-compliant SBOM.”

An artifact must be paired with an SBOM whose boundary describes it. A JAR from one Gradle project normally uses that
project's `cyclonedxDirectBom`. Use `cyclonedxBom` only when the attested distribution represents the same set of
Contributing Projects as the Aggregate SBOM.

### Publishing the Direct SBOM

A Direct SBOM can be published with the artifact it describes. For a normal Maven repository, the `cyclonedx`
classifier follows the convention used by other CycloneDX JVM tooling:

```kotlin
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("org.cyclonedx.bom") version "4.0.0"
    id("maven-publish")
    id("java")
}

val cyclonedxDirectBom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    xmlOutput.unsetConvention()
    includeConfigs = listOf("compileClasspath", "runtimeClasspath")
    jsonOutput.set(
        layout.buildDirectory.file("reports/cyclonedx-direct/${project.name}-${project.version}-cyclonedx.json")
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(cyclonedxDirectBom.flatMap { it.jsonOutput }) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(cyclonedxDirectBom)
            }
        }
    }
}
```

This produces `<artifact>-<version>-cyclonedx.json`. Publication makes the SBOM available to consumers; it does not
sign the document or create SLSA provenance. The Gradle Plugin Portal accepts JAR artifacts but not JSON classifiers,
so projects that publish only there can distribute the same versioned file as a GitHub Release asset instead. This
project uses that release-asset approach for its own SBOM.

### GitHub Actions example

The following release workflow builds one JAR, creates SLSA Build provenance and a CycloneDX SBOM attestation for that
exact artifact, and publishes the versioned SBOM as a GitHub Release asset. Replace the example paths with the single
release artifact described by your Direct SBOM.

```yaml
name: Build and attest

on:
  release:
    types: [published]

permissions:
  contents: write
  id-token: write
  attestations: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v6

      - name: Build artifact and Direct SBOM
        run: ./gradlew jar cyclonedxDirectBom

      - name: Attest SLSA Build provenance
        uses: actions/attest@v4
        with:
          subject-path: build/libs/my-app-1.0.0.jar

      - name: Attest CycloneDX SBOM
        uses: actions/attest@v4
        with:
          subject-path: build/libs/my-app-1.0.0.jar
          sbom-path: build/reports/cyclonedx-direct/my-app-1.0.0-cyclonedx.json

      - name: Publish SBOM with the release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release upload "${{ github.event.release.tag_name }}" \
            build/reports/cyclonedx-direct/my-app-1.0.0-cyclonedx.json \
            --clobber
```

GitHub documents its artifact provenance attestations as providing SLSA v1 Build Level 2 for supported workflows.
Other hosted build platforms can implement the same two-attestation pattern. Choose action version pinning and release
asset retry behavior that match your project's security policy. This project's own pinned, self-verifying
implementation is in [the release workflow](.github/workflows/ci-publish.yaml).

### Verifying the attestations

Consumers can verify both claims against a downloaded artifact. Provenance is the default predicate; CycloneDX uses
the `https://cyclonedx.org/bom` predicate:

```bash
gh attestation verify my-app-1.0.0.jar --repo owner/repository
gh attestation verify my-app-1.0.0.jar \
  --repo owner/repository \
  --predicate-type https://cyclonedx.org/bom
```

See GitHub's documentation for [artifact attestations](https://docs.github.com/actions/concepts/security/artifact-attestations)
and [`gh attestation verify`](https://cli.github.com/manual/gh_attestation_verify).

## Compatibility history

### Gradle support

| Plugin version | Gradle version |
|----------------|----------------|
| 4.x | 8.4 or newer; Project Isolation tested on 8.11 or newer |
| 3.x | 8.4 or newer |
| 2.x | 8.0 or newer |
| 1.x | Earlier than 8.0 |

### CycloneDX schema support

The table records the newest schema supported by each plugin line. Use the newest plugin version compatible with the
Gradle version and downstream CycloneDX consumer in your environment.

| Plugin version | Newest CycloneDX schema | Formats |
|----------------|---------------------------|---------|
| 4.x | 1.7 opt-in; 1.6 default | XML and JSON |
| 3.x | 1.7 opt-in; 1.6 default | XML and JSON |
| 2.x | 1.6 | XML and JSON |
| 1.10.x | 1.6 | XML and JSON |
| 1.9.x | 1.6 | XML and JSON |
| 1.8.x | 1.5 | XML and JSON |
| 1.7.x | 1.4 | XML and JSON |
| 1.6.x | 1.4 | XML and JSON |
| 1.5.x | 1.3 | XML and JSON |
| 1.4.x | 1.3 | XML and JSON |
| 1.2.x | 1.2 | XML and JSON |
| 1.1.x | 1.1 | XML |
| 1.0.x | 1.0 | XML |

## Community and contributing

- Read the [CycloneDX specification](https://cyclonedx.org/docs/).
- Ask questions in the [CycloneDX discussion group](https://groups.io/g/CycloneDX) or
  [Slack](https://cyclonedx.org/slack/invite).
- Report bugs and request features in [GitHub Issues](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues).
- See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change.

## License

Copyright (c) OWASP Foundation. All Rights Reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
