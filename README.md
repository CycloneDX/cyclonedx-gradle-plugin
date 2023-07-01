[![Build Status](https://github.com/CycloneDX/cyclonedx-gradle-plugin/workflows/Build%20CI/badge.svg)](https://github.com/CycloneDX/cyclonedx-gradle-plugin/actions?workflow=Build+CI)
[![Gradle Plugin](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Forg%2Fcyclonedx%2Fbom%2Forg.cyclonedx.bom.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/org.cyclonedx.bom)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][License]
[![Website](https://img.shields.io/badge/https://-cyclonedx.org-blue.svg)](https://cyclonedx.org/)
[![Slack Invite](https://img.shields.io/badge/Slack-Join-blue?logo=slack&labelColor=393939)](https://cyclonedx.org/slack/invite)
[![Group Discussion](https://img.shields.io/badge/discussion-groups.io-blue.svg)](https://groups.io/g/CycloneDX)
[![Twitter](https://img.shields.io/twitter/url/http/shields.io.svg?style=social&label=Follow)](https://twitter.com/CycloneDX_Spec)

# CycloneDX Gradle Plugin

The CycloneDX Gradle plugin creates an aggregate of all direct and transitive dependencies of a project
and creates a valid CycloneDX SBOM. CycloneDX is a lightweight software bill of materials
(SBOM) specification designed for use in application security contexts and supply chain component analysis.

## Usage
__Execution:__
```bash
gradle cyclonedxBom
```

__Output CycloneDX Generation Info:__
```bash
gradle cyclonedxBom -info
```

__build.gradle__ (excerpt)

To generate BOM for a single project add the plugin to the `build.gradle`.


```groovy
plugins {
    id 'org.cyclonedx.bom' version '1.7.4'
}
```

Once a BOM is generated, by default it will reside at `./build/reports/bom.xml` and `./build/reports/bom.json`

__Configuration:__

You can add the following configuration to `build.gradle` to control various options in generating a BOM:


```groovy
cyclonedxBom {
    // includeConfigs is the list of configuration names to include when generating the BOM (leave empty to include every configuration)
    includeConfigs = ["runtimeClasspath"]
    // skipConfigs is a list of configuration names to exclude when generating the BOM
    skipConfigs = ["compileClasspath", "testCompileClasspath"]
    // skipProjects is a list of project names to exclude when generating the BOM
    skipProjects = [rootProject.name, "yourTestSubProject"]
    // Specified the type of project being built. Defaults to 'library'
    projectType = "application"
    // Specified the version of the CycloneDX specification to use. Defaults to '1.4'
    schemaVersion = "1.4"
    // Boms destination directory. Defaults to 'build/reports'
    destination = file("build/reports")
    // The file name for the generated BOMs (before the file format suffix). Defaults to 'bom'
    outputName = "bom"
    // The file format generated, can be xml, json or all for generating both. Defaults to 'all'
    outputFormat = "json"
    // Exclude BOM Serial Number. Defaults to 'true'
    includeBomSerialNumber = false
    // Exclude License Text. Defaults to 'true'
    includeLicenseText = false
    // Override component version. Defaults to the project version
    componentVersion = "2.0.0"
}
```

If you are using the Kotlin DSL, the plugin can be configured as following:

```kotlin
tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setSkipConfigs(listOf("compileClasspath", "testCompileClasspath"))
    setSkipProjects(listOf(rootProject.name, "yourTestSubProject"))
    setProjectType("application")
    setSchemaVersion("1.4")
    setDestination(project.file("build/reports"))
    setOutputName("bom")
    setOutputFormat("json")
    setIncludeBomSerialNumber(false)
    setIncludeLicenseText(true)
    setComponentVersion("2.0.0")
}
```

Run Gradle with info logging (-i option) to see which configurations add to the BOM.

__Generate BOM for multiple projects:__

You can also build the BOM for multiple projects using the `--init-script` option:


```bash
gradle --init-script <path-to-init.gradle> cyclonedxBom -info
```

where the `init.gradle` can look like this:

```groovy
initscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.cyclonedx:cyclonedx-gradle-plugin:1.7.4"
  }
}

allprojects{
  apply plugin:org.cyclonedx.gradle.CycloneDxPlugin
  cyclonedxBom {
    includeConfigs = ["runtimeClasspath"]
    skipConfigs = ["compileClasspath", "testCompileClasspath"]
    skipProjects = [rootProject.name, "yourTestSubProject"]
    projectType = "application"
    schemaVersion = "1.4"
    destination = file("build/reports")
    outputName = "bom"
    outputFormat = "json"
    includeBomSerialNumber = false
    includeLicenseText = true
    componentVersion = "2.0.0"
  }
}
```

## CycloneDX Schema Support

The following table provides information on the version of this Gradle plugin, the CycloneDX schema version supported,
as well as the output format options. Use the latest possible version of this plugin that is the compatible with
the CycloneDX version supported by the target system.

| Version | Schema Version | Format(s) |
|---------| ----------------- | --------- |
| 1.7.x   | CycloneDX v1.4 | XML/JSON |
| 1.6.x   | CycloneDX v1.4 | XML/JSON |
| 1.5.x   | CycloneDX v1.3 | XML/JSON |
| 1.4.x   | CycloneDX v1.3 | XML/JSON |
| 1.2.x   | CycloneDX v1.2 | XML/JSON |
| 1.1.x   | CycloneDX v1.1 | XML |
| 1.0x    | CycloneDX v1.0 | XML |

## Copyright & License

CycloneDX Gradle Plugin is Copyright (c) OWASP Foundation. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for the full license.

[License]: https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/master/LICENSE
