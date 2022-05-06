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

__Exclude BOM Serial Number:__
```bash
gradle cyclonedxBom -Pcyclonedx.includeBomSerialNumber=false
```

__build.gradle__ (excerpt)
```groovy
plugins {
    id 'org.cyclonedx.bom' version '1.5.0'
}
```

Once a BOM is generated, it will reside at `./build/reports/bom.xml` and `./build/reports/bom.json`


__Configuration:__
You can control the configurations included in the BOM:
```groovy
cyclonedxBom {
    // includeConfigs is the list of configuration names to include when generating the BOM (leave empty to include every configuration)
    includeConfigs += ["runtimeClasspath"]
    // skipConfigs is a list of configuration names to exclude when generating the BOM
    skipConfigs += ["compileClasspath", "testCompileClasspath"]
    // Specified the type of project being built. Defaults to 'library' 
    projectType = "application"
    // Specified the version of the CycloneDX specification to use. Defaults to 1.2.
    schemaVersion = "1.2"
    // Boms destination directory (defaults to build/reports)
    destination = file("build/reports")
}
```

Run gradle with info logging (-i option) to see which configurations add to the BOM. 

## CycloneDX Schema Support

The following table provides information on the version of this node module, the CycloneDX schema version supported, 
as well as the output format options. Use the latest possible version of this node module that is the compatible with 
the CycloneDX version supported by the target system.

| Version | Schema Version | Format(s) |
| ------- | ----------------- | --------- |
| 1.4.x | CycloneDX v1.3 | XML/JSON |
| 1.2.x | CycloneDX v1.2 | XML/JSON |
| 1.1.x | CycloneDX v1.1 | XML |
| 1.0x | CycloneDX v1.0 | XML |

## Copyright & License

CycloneDX Gradle Plugin is Copyright (c) OWASP Foundation. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for the full license.

[License]: https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/master/LICENSE
