# CycloneDX Gradle Plugin

[![Build Status](https://github.com/CycloneDX/cyclonedx-gradle-plugin/workflows/Build%20CI/badge.svg)](https://github.com/CycloneDX/cyclonedx-gradle-plugin/actions?workflow=Build+CI)
[![Gradle Plugin](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Forg%2Fcyclonedx%2Fbom%2Forg.cyclonedx.bom.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/org.cyclonedx.bom)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](LICENSE)
[![Website](https://img.shields.io/badge/https://-cyclonedx.org-blue.svg)](https://cyclonedx.org/)
[![Slack Invite](https://img.shields.io/badge/Slack-Join-blue?logo=slack&labelColor=393939)](https://cyclonedx.org/slack/invite)
[![Group Discussion](https://img.shields.io/badge/discussion-groups.io-blue.svg)](https://groups.io/g/CycloneDX)
[![Twitter](https://img.shields.io/twitter/url/http/shields.io.svg?style=social&label=Follow)](https://twitter.com/CycloneDX_Spec)

The CycloneDX Gradle plugin creates an aggregate of all direct and transitive dependencies of a project
and creates a valid CycloneDX SBOM. CycloneDX is a lightweight software bill of materials
(SBOM) specification designed for use in application security contexts and supply chain component analysis.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage](#usage)
    - [Per-Project SBOM Generation](#per-project-sbom-generation)
    - [Multi-Project Aggregation](#multi-project-aggregation)
    - [Usage with Initialization Script](#usage-with-initialization-script)
- [Configuration](#configuration)
    - [Configuration Properties](#configuration-properties)
    - [Output Configuration](#output-configuration)
    - [Advanced Configuration](#advanced-configuration)
- [Tasks](#tasks)
- [Examples](#examples)
- [Gradle Support](#gradle-support)
- [CycloneDX Schema Support](#cyclonedx-schema-support)
- [Copyright & License](#copyright--license)

## Features

- ✅ **Per-Project SBOMs**: Generate individual SBOM documents for each project
- ✅ **Multi-Project Aggregation**: Create consolidated SBOMs for entire project hierarchies
- ✅ **Multiple Output Formats**: JSON and XML format support with CycloneDX specification compliance
- ✅ **Flexible Configuration**: Include/exclude specific dependencies, configurations, and projects
- ✅ **Metadata Enrichment**: Include license information, build system details, and organizational data
- ✅ **Gradle Integration**: Native Gradle task integration with proper incremental build support
- ✅ **Dependency Analysis**: Analyzes all direct and transitive resolved dependencies (not just declared ones)

## Installation

Apply the plugin to your project:

**Groovy DSL:**

```groovy
plugins {
    id 'org.cyclonedx.bom' version '3.2.0'
}
```

**Kotlin DSL:**

```kotlin
plugins {
    id("org.cyclonedx.bom") version "3.2.0"
}
```

> [!IMPORTANT]
> Plugin will register aggregate task `cyclonedxBom` only in the project where it is applied. This task
> aggregates SBOMs from the project and all subprojects in multi-project builds.

> [!IMPORTANT]
> Although the plugin is compatible with Java versions starting from 8, support of all versions prior to 17 is
> deprecated it will be removed in future releases.

## Quick Start

1. Apply the plugin to your root project
2. Run the SBOM generation task:

```bash
# Generate per-project SBOMs
./gradlew cyclonedxDirectBom

# Generate aggregated SBOM (for multi-project builds)
./gradlew cyclonedxBom
```

3. Find your SBOM files:
    - Per-project: `build/reports/cyclonedx-direct/bom.{json,xml}`
    - Aggregated: `build/reports/cyclonedx/bom.{json,xml}`

## Usage

### Per-Project SBOM Generation

The `cyclonedxDirectBom` task generates individual SBOM documents for each project in your build. This is useful when
you want
to analyze dependencies at a granular level.

```bash
# Generate SBOMs for all projects
./gradlew cyclonedxDirectBom

# Generate with verbose logging
./gradlew cyclonedxDirectBom --info

# Generate for specific project only
./gradlew :subproject:cyclonedxDirectBom
```

**Output locations:**

- JSON: `{project}/build/reports/cyclonedx-direct/bom.json`
- XML: `{project}/build/reports/cyclonedx-direct/bom.xml`

### Multi-Project Aggregation

The `cyclonedxBom` task creates a single, consolidated SBOM containing dependencies from all projects in your
build. This provides a complete view of your application's supply chain.

```bash
# Generate aggregated SBOM
./gradlew cyclonedxBom
```

**Output locations:**

- JSON: `build/reports/cyclonedx/bom.json`
- XML: `build/reports/cyclonedx/bom.xml`

### Usage with Initialization Script

It is possible to use the plugin without modifying the project's build script. This is useful for CI/CD pipelines or when you want to generate an SBOM for a project that you do not own or cannot modify.

1. Create a file named `init.gradle.kts` with the following content:

```kotlin
import org.cyclonedx.gradle.CyclonedxPlugin

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.2.0")
    }
}

rootProject {
    apply<CyclonedxPlugin>()
}
```

2. Run the SBOM generation task using the `--init-script` parameter:

```bash
./gradlew cyclonedxBom --init-script init.gradle.kts
```

## Configuration

To configure `cyclonedxDirectBom` task you have to configure it in each project individually. To configure
`cyclonedxBom` task you have to configure it in the plugin placement project (usually root project).

For multi-project builds, you can also set common configuration options for all projects by configuring the
`cyclonedxDirectBom` task in the root project using `allprojects` or `subprojects` blocks.
```kotlin
allprojects {
    tasks.cyclonedxDirectBom {
        // Configuration properties here
    }
}
tasks.cyclonedxBom {
    // Configuration properties here
}
```

### Configuration Properties

#### `cyclonedxDirectBom`

| Property                         | Type                      | Default                   | Description                                                                        |
|----------------------------------|---------------------------|---------------------------|------------------------------------------------------------------------------------|
| `includeConfigs`                 | `List<String>`            | `[]` (all configurations) | Configurations to include in SBOM generation. Supports regex patterns              |
| `skipConfigs`                    | `List<String>`            | `[]`                      | Configurations to exclude from SBOM generation. Supports regex patterns            |
| `projectType`                    | `Component.Type`          | `"library"`               | Type of project (`"application"`, `"library"`, `"framework"`, `"container"`, etc.) |
| `schemaVersion`                  | `SchemaVersion`           | `VERSION_16`              | CycloneDX schema version to use                                                    |
| `includeBomSerialNumber`         | `boolean`                 | `true`                    | Include unique BOM serial number                                                   |
| `includeLicenseText`             | `boolean`                 | `false`                   | Include full license text in components                                            |
| `includeMetadataResolution`      | `boolean`                 | `true`                    | Include complete metadata resolution for components                                |
| `includeBuildEnvironment`        | `boolean`                 | `false`                   | Include build environment dependencies (e.g. from buildscript)                     |
| `includeBuildSystem`             | `boolean`                 | `true`                    | Include build system URL from CI environment                                       |
| `buildSystemEnvironmentVariable` | `String`                  | -                         | Custom environment variable for build system URL                                   |
| `componentVersion`               | `String`                  | Project version           | Override the main component version                                                |
| `componentName`                  | `String`                  | Project name              | Override the main component name                                                   |
| `componentGroup`                 | `String`                  | Project group             | Override the main component group                                                  |
| `organizationalEntity`           | `OrganizationalEntity`    | -                         | Organizational metadata for the project, including name, URLs, and contacts        |
| `externalReferences`             | `List<ExternalReference>` | Git remote URL            | External references for the project, such as documentation or issue trackers       |
| `licenseChoice`                  | `LicenseChoice`           | -                         | License information for the main component                                         |

#### `cyclonedxBom`

| Property                         | Type                      | Default                   | Description                                                                        |
|----------------------------------|---------------------------|---------------------------|------------------------------------------------------------------------------------|
| `projectType`                    | `Component.Type`          | `"library"`               | Type of project (`"application"`, `"library"`, `"framework"`, `"container"`, etc.) |
| `schemaVersion`                  | `SchemaVersion`           | `VERSION_16`              | CycloneDX schema version to use                                                    |
| `includeBomSerialNumber`         | `boolean`                 | `true`                    | Include unique BOM serial number                                                   |
| `includeLicenseText`             | `boolean`                 | `false`                   | Include full license text in components                                            |
| `includeBuildSystem`             | `boolean`                 | `true`                    | Include build system URL from CI environment                                       |
| `buildSystemEnvironmentVariable` | `String`                  | -                         | Custom environment variable for build system URL                                   |
| `componentVersion`               | `String`                  | Project version           | Override the main component version                                                |
| `componentName`                  | `String`                  | Project name              | Override the main component name                                                   |
| `componentGroup`                 | `String`                  | Project group             | Override the main component group                                                  |
| `organizationalEntity`           | `OrganizationalEntity`    | -                         | Organizational metadata for the project, including name, URLs, and contacts        |
| `externalReferences`             | `List<ExternalReference>` | Git remote URL            | External references for the project, such as documentation or issue trackers       |
| `licenseChoice`                  | `LicenseChoice`           | -                         | License information for the main component                                         |

### Output Configuration

Configure output files using explicit properties for each task. The plugin supports both JSON and XML formats
simultaneously or individually:

```kotlin
allprojects {
    tasks.cyclonedxDirectBom {
        // Configure JSON output (default: build/reports/cyclonedx/bom.json)
        jsonOutput.set(file("build/reports/cyclonedx/${project.name}-bom.json"))
        // Configure XML output (default: build/reports/cyclonedx/bom.xml)
        xmlOutput.set(file("build/reports/cyclonedx/${project.name}-bom.xml"))
    }
    tasks.cyclonedxBom {
        // Configure JSON output (default: build/reports/cyclonedx-aggregate/bom.json)
        jsonOutput.set(file("build/reports/cyclonedx-aggregate/${project.name}-bom.json"))
        // Configure XML output (default: build/reports/cyclonedx-aggregate/bom.xml)
        xmlOutput.set(file("build/reports/cyclonedx-aggregate/${project.name}-bom.xml"))
    }
}
```

#### Disabling Output Formats

To generate only one format, you can disable the other by unsetting its convention:

```kotlin
tasks.cyclonedxDirectBom {
    // Generate only JSON format
    xmlOutput.unsetConvention()
    // Or generate only XML format
    jsonOutput.unsetConvention()
}
tasks.cyclonedxBom {
    // Generate only JSON format
    xmlOutput.unsetConvention()
    // Or generate only XML format
    jsonOutput.unsetConvention()
}
```

### Advanced Configuration

#### Full Configuration Example

```kotlin
tasks.cyclonedxDirectBom {
    // Include only runtime dependencies
    includeConfigs = ["runtimeClasspath", "compileClasspath"]

    // Exclude all test-related configurations using regex
    skipConfigs = [".*test.*", ".*Test.*"]

    // Set application metadata
    projectType = "application"
    componentName = "my-microservice"
    componentVersion = "2.0.0-SNAPSHOT"

    // Schema configuration
    schemaVersion = org.cyclonedx.model.schema.SchemaVersion.VERSION_16

    // Metadata options
    includeBomSerialNumber = true
    includeLicenseText = true
    includeMetadataResolution = true
    includeBuildEnvironment = false
    includeBuildSystem = true

    // Custom build system URL template
    buildSystemEnvironmentVariable = '${CI_PIPELINE_URL}/jobs/${CI_JOB_ID}'

    // Custom output locations
    jsonOutput = file("build/reports/sbom/${project.name}-sbom.json")
    xmlOutput = file("build/reports/sbom/${project.name}-sbom.xml")
}
```

#### Excluding Projects from Aggregation

To exclude a specific project from SBOM generation (both direct and aggregate tasks), disable the task:

```kotlin
subprojects {
    tasks.cyclonedxDirectBom.enabled = false // Skip SBOM generation for this project
}
// Or in a specific project's build.gradle.kts:
tasks.cyclonedxDirectBom.enabled = false
```

## Tasks

| Task                 | Description                                        | Scope                    | Type                     | Output Location                   |
|----------------------|----------------------------------------------------|--------------------------|--------------------------|-----------------------------------|
| `cyclonedxDirectBom` | Generates per-project SBOM documents               | Individual projects      | `CyclonedxDirectTask`    | `build/reports/cyclonedx-direct/` |
| `cyclonedxBom`       | Generates aggregated SBOM for multi-project builds | Entire project hierarchy | `CyclonedxAggregateTask` | `build/reports/cyclonedx/`        |

Both tasks support:

- Incremental builds
- Parallel execution
- Configuration cache
- Build cache

## Examples

#### Simple Java Application

```kotlin
plugins {
    id("org.cyclonedx.bom") version "3.2.0"
    id("application")
}

tasks.cyclonedxDirectBom {
    projectType = "application"
    includeConfigs = listOf("runtimeClasspath")
}
```

#### Multi-Project with Filtering

Root `/build.gradle.kts`:

```kotlin
plugins {
    id("org.cyclonedx.bom") version "3.2.0"
}

allprojects {
    tasks.cyclonedxDirectBom {
        // Include only production dependencies
        includeConfigs = listOf("runtimeClasspath", "compileClasspath")
        skipConfigs = listOf("testRuntimeClasspath", "testCompileClasspath")

        // Application metadata
        projectType = "application"
        componentGroup = "com.example"

        // Enable build system tracking
        includeBuildSystem = true
    }
}
tasks.cyclonedxBom {
    // Aggregated SBOM configuration
    projectType = "application"
    componentName = "my-enterprise-app"
    componentVersion = "1.0.0"
    includeBuildSystem = true
}
```

Subproject `/test-utils/build.gradle.kts`:

```kotlin
// Disable SBOM generation for test utility projects
tasks.cyclonedxDirectBom.enabled = false
```

#### Organizational Entity

```kotlin
import org.cyclonedx.model.*
import org.cyclonedx.model.schema.*

plugins {
    id("org.cyclonedx.bom") version "3.2.0"
    id("java")
}

tasks.cyclonedxDirectBom {
    // Project configuration
    projectType = "application"
    schemaVersion = SchemaVersion.VERSION_16

    // Component details
    componentName = "acme-payment-service"
    componentVersion = "3.2.0"

    // Dependency filtering
    includeConfigs = listOf("runtimeClasspath", "compileClasspath")
    skipConfigs = listOf(".*test.*", ".*benchmark.*")

    // Metadata options
    includeBomSerialNumber = true
    includeLicenseText = true
    includeMetadataResolution = true
    includeBuildSystem = true
    buildSystemEnvironmentVariable = "\${BUILD_URL}"

    // Organizational metadata
    organizationalEntity = OrganizationalEntity().apply {
        name = "ACME Corporation"
        urls = listOf("https://www.acme.com", "https://security.acme.com")
        addContact(OrganizationalContact().apply {
            name = "Security Team"
            email = "security@acme.com"
            phone = "+1-555-SECURITY"
        })
    }
}
```

#### External Reference Example

```kotlin
import org.cyclonedx.model.*

plugins {
    id("org.cyclonedx.bom") version "3.2.0"
    id("java")
}

tasks.cyclonedxDirectBom {
    externalReferences = listOf(
        ExternalReference().apply {
            url = "https://cyclonedx.org/"
            type = ExternalReference.Type.WEBSITE
        }
    )
}
```

#### Licenses Example

```kotlin
import org.cyclonedx.model.*

plugins {
    id("org.cyclonedx.bom") version "3.2.0"
    id("java")
}
tasks.cyclonedxDirectBom {
    // Specify licenses for the main component
    licenseChoice = LicenseChoice().apply {
        addLicense(License().apply {
            name = "Apache-2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        })
    }
}
```

#### CI Metadata Example

```kotlin
plugins {
    id("org.cyclonedx.bom") version "3.2.0"
    id("java")
}

tasks.cyclonedxDirectBom {
    projectType = "application"

    // Dynamic versioning for CI/CD
    componentVersion = System.getenv("BUILD_VERSION") ?: project.version.toString()

    // Build system integration
    includeBuildSystem = true
    buildSystemEnvironmentVariable = "\${BUILD_URL}"

    // Conditional configuration based on environment
    if (System.getenv("CI") == "true") {
        // CI environment - include all runtime dependencies
        includeConfigs = listOf("runtimeClasspath", "compileClasspath")
        skipConfigs = listOf("testRuntimeClasspath")
    } else {
        // Local development - lighter analysis
        includeConfigs = listOf("runtimeClasspath")
    }
}

tasks.cyclonedxBom {
    // Timestamped output artifacts (WARNING: will disable Gradle cache)
    jsonOutput = file("build/artifacts/sbom-${Instant.now()}.json")
    xmlOutput.unsetConvention()
}
```

For detailed metadata structure information, refer to
the [CycloneDX specification](https://cyclonedx.org/docs/1.6/json/#metadata).

## Gradle Support

The following table provides information on the version of this Gradle plugin, the Gradle version supported.

| Version | Gradle Version |
|---------|----------------|
| 3.0.x   | Gradle 8.4+    |
| 2.x.x   | Gradle 8.0+    |
| 1.x.x   | Gradle <8.0    |

## CycloneDX Schema Support

The following table provides information on the version of this Gradle plugin, the CycloneDX schema version supported,
as well as the output format options. Use the latest possible version of this plugin that is the compatible with
the CycloneDX version supported by the target system.

| Version | Schema Version | Format(s) |
|---------|----------------|-----------|
| 3.x.x   | CycloneDX v1.6 | XML/JSON  |
| 2.x.x   | CycloneDX v1.6 | XML/JSON  |
| 1.10.x  | CycloneDX v1.6 | XML/JSON  |
| 1.9.x   | CycloneDX v1.6 | XML/JSON  |
| 1.8.x   | CycloneDX v1.5 | XML/JSON  |
| 1.7.x   | CycloneDX v1.4 | XML/JSON  |
| 1.6.x   | CycloneDX v1.4 | XML/JSON  |
| 1.5.x   | CycloneDX v1.3 | XML/JSON  |
| 1.4.x   | CycloneDX v1.3 | XML/JSON  |
| 1.2.x   | CycloneDX v1.2 | XML/JSON  |
| 1.1.x   | CycloneDX v1.1 | XML       |
| 1.0x    | CycloneDX v1.0 | XML       |

## Copyright & License

CycloneDX Gradle Plugin is Copyright (c) OWASP Foundation. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for
the full license.

[License]: https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/master/LICENSE
