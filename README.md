[![Build Status](https://travis-ci.org/CycloneDX/cyclonedx-gradle-plugin.svg?branch=master)](https://travis-ci.org/CycloneDX/cyclonedx-gradle-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.cyclonedx/cyclonedx-gradle-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.cyclonedx/cyclonedx-gradle-plugin)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][License]
[![Website](https://img.shields.io/badge/https://-cyclonedx.org-blue.svg)](https://cyclonedx.org/)
[![Group Discussion](https://img.shields.io/badge/discussion-groups.io-blue.svg)](https://groups.io/g/CycloneDX)
[![Twitter](https://img.shields.io/twitter/url/http/shields.io.svg?style=social&label=Follow)](https://twitter.com/CycloneDX_Spec)

# CycloneDX Gradle Plugin
The CycloneDX Gradle plugin creates an aggregate of all dependencies and transitive dependencies of a project 
and creates a valid CycloneDX bill-of-material document from the results. CycloneDX is a lightweight BoM 
specification that is easily created, human readable, and simple to parse. The resulting bom.xml can be used
with tools such as [OWASP Dependency-Track](https://dependencytrack.org/) for the continuous analysis of components.

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
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.cyclonedx:cyclonedx-gradle-plugin:1.1.0"
    }
}

apply plugin: 'java'
apply plugin: 'maven'
apply plugin: 'org.cyclonedx.bom'

repositories {
    mavenCentral()
}
```

Once a BOM is generated, it will reside at `./build/reports/bom.xml`


__Configuration:__
You can control the configurations included in the BOM:
```groovy
cyclonedxBom {
    // skipConfigs is a list of configuration names to exclude when generating the BOM
    skipConfigs += ["testImplementation", "testRuntimeOnly"]
}
```

Run gradle with info logging (-i option) to see which configurations add to the BOM. 



## Copyright & License
CycloneDX Gradle Plugin is Copyright (c) Steve Springett. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for the full license.

[License]: https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/master/LICENSE
