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
gradle cyclonedxBom --info
```

__build.gradle__ (excerpt)

To generate a BOM for a single project add the plugin to the `build.gradle`.


```groovy
plugins {
    id 'org.cyclonedx.bom' version '2.3.1'
}
```

Once a BOM is generated, by default it will reside at `./build/reports/bom.xml` and `./build/reports/bom.json`

__Configuration:__

You can add the following configuration to `build.gradle` to control various options in generating a BOM:


```groovy
cyclonedxBom {
    // includeConfigs is the list of configuration names to include when generating the BOM (leave empty to include every configuration), regex is supported
    includeConfigs = ["runtimeClasspath"]
    // skipConfigs is a list of configuration names to exclude when generating the BOM, regex is supported
    skipConfigs = ["compileClasspath", "testCompileClasspath"]
    // skipProjects is a list of project names to exclude when generating the BOM
    skipProjects = [rootProject.name, "yourTestSubProject"]
    // Specified the type of project being built. Defaults to 'library'
    projectType = "application"
    // Specified the version of the CycloneDX specification to use. Defaults to '1.6'
    schemaVersion = "1.6"
    // Boms destination directory. Defaults to 'build/reports'
    destination = file("build/reports")
    // The file name for the generated BOMs (before the file format suffix). Defaults to 'bom'
    outputName = "bom"
    // The file format generated, can be xml, json or all for generating both. Defaults to 'all'
    outputFormat = "json"
    // Include BOM Serial Number. Defaults to 'true'
    includeBomSerialNumber = false
    // Include License Text. Defaults to 'true'
    includeLicenseText = false
    // Include resolution of full metadata for components including licenses. Defaults to 'true'
    includeMetadataResolution = true
    // Attempt to include the build-system URL by reading environment variables from common CI system such as GitHub Actions, GitLab CI, Drone, Jenkins, Travis CI, and Circle CI. Defaults to 'false'
    includeBuildSystem = true
    // if includeBuildSystem is true, the given environment variables will be used to construct the build-system URL that will be included in the BOM. The dollar sign and curly braces (e.g. `${NAME}`) are required to specify an environment variable. Optional, defaults to `null`.
    buildSystemEnvironmentVariable = '${CUSTOM_CI_URL}/jobs/${CUSTOM_JOB_ID}'
    // Override component version. Defaults to the project version
    componentVersion = "2.0.0"
    // Override component name. Defaults to the project name
    componentName = "my-component"
}
```

If you are using the Kotlin DSL, the plugin can be configured as following:

```kotlin
tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setSkipConfigs(listOf("compileClasspath", "testCompileClasspath"))
    setSkipProjects(listOf(rootProject.name, "yourTestSubProject"))
    setProjectType("application")
    setSchemaVersion("1.6")
    setDestination(project.file("build/reports"))
    setOutputName("bom")
    setOutputFormat("json")
    setIncludeBomSerialNumber(false)
    setIncludeLicenseText(true)
    setIncludeMetadataResolution(true)
    setComponentVersion("2.0.0")
    setComponentName("my-component")
}
```

Run Gradle with info logging (-i option) to see which configurations add to the BOM.

__Generate BOM for multiple projects:__

You can also build the BOM for multiple projects using the `--init-script` option:


```bash
gradle --init-script <path-to-init.gradle> cyclonedxBom --info
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
    classpath "org.cyclonedx:cyclonedx-gradle-plugin:2.3.1"
  }
}

allprojects{
  apply plugin:org.cyclonedx.gradle.CycloneDxPlugin
  cyclonedxBom {
    includeConfigs = ["runtimeClasspath"]
    skipConfigs = ["compileClasspath", "testCompileClasspath"]
    skipProjects = [rootProject.name, "yourTestSubProject"]
    projectType = "application"
    schemaVersion = "1.6"
    destination = file("build/reports")
    outputName = "bom"
    outputFormat = "json"
    includeBomSerialNumber = false
    includeLicenseText = true
    componentVersion = "2.0.0"
    componentName = "my-component"
  }
}
```

## How to manually modify Metadata

The Plugin makes it possible to manually add Manufacture-Data and Licenses-Data to the Metadata of the BOM. <br>
The structure of the Metadata is shown on https://cyclonedx.org/docs/1.6/json/#metadata. <br>
The editing of the Manufacture and Licenses-Data is optional. If the Manufacture/Licenses-Date isn't edited,
then the respective structure won't appear in the BOM.

To enable the modification of the metadata the cyclonedx-core-java plugin must be implemented in the build.gradle.

---

## Adding Manufacture-Data

In order to be able to define the Manufacture-Data you must __import org.cyclonedx.model.*;__ into the build.gradle.
<br>
You can add the Manufacture-Data by passing an Object of the Type __OrganizationalEntity__ to the Plugin.

__Example (groovy):__
```groovy
cyclonedxBom {
    // declaration of the Object from OrganizationalContact
    OrganizationalContact organizationalContact = new OrganizationalContact()

    // setting the Name[String], Email[String] and Phone[String] of the Object
    organizationalContact.setName("Max_Mustermann")
    organizationalContact.setEmail("max.mustermann@test.org")
    organizationalContact.setPhone("0000 99999999")

    // passing Data to the plugin
    organizationalEntity { oe ->
        oe.name = 'Test'
        oe.url = ['www.test1.com', 'www.test2.com']
        oe.addContact(organizationalContact)
    }
}
```

__Example (Kotlin):__
```kotlin
cyclonedxBom {
    // declaration of the Object from OrganizationalContact
    var organizationalContact1 = OrganizationalContact()

    // setting the Name[String], Email[String] and Phone[String] of the Object
    organizationalContact1.setName("Max_Mustermann")
    organizationalContact1.setEmail("max.mustermann@test.org")
    organizationalContact1.setPhone("0000 99999999")

    // passing data to the plugin
    setOrganizationalEntity { oe ->
        oe.name = "Test";
        oe.urls = listOf("www.test1.com", "www.test2.com")
        oe.addContact(organizationalContact1)
    }
}
```
It should be noted that some data like OrganizationalContact, Url, Name,... can be left out. <br>
OrganizationalEntity can also include multiple OrganizationalContact.

For details refer to https://cyclonedx.org/docs/1.6/json/#metadata.


## Adding Licenses-Data

In order to define the Manufacture-Data you must __import org.cyclonedx.model.*;__ to the build.gradle.

You can add the license data by passing an object of the type __LicenseChoice__ to the plugin.
The object from LicenseChoice includes __either license or expression__. It can't include both.

### License

__Example (groovy):__
```groovy
cyclonedxBom {
    // declaration of the object from AttachmentText -> Needed for the setting of LicenseText
    AttachmentText attachmentText = new AttachmentText()
    attachmentText.setText("This is a Licenses-Text")

    // declaration of the Object from License
    License license = new License()

    // setting the Name[String], LicenseText[AttachmentText] and Url[String]
    license.setName("XXXX XXXX Software")

    // license.setId("Mup")     // either id or name -> both not possible
    license.setLicenseText(attachmentText);
    license.setUrl("https://www.test-Url.org/")

    // passing Data to Plugin
    licenseChoice { lc ->
        lc.addLicense(license)
    }
}
```

__Example (Kotlin):__
```kotlin
cyclonedxBom {
    // declaration of the object from AttachmentText -> Needed for the setting of LicenseText
    val attachmentText = AttachmentText()
    attachmentText.setText("This is a Licenses-Text")

    // declaration of the object from License
    val license = License()

    // setting the Name[String], LicenseText[AttachmentText] and Url[String]
    license.setName("XXXX XXXX Software")

    // license.setId("Mup")
    // either id or name -> both not possible
    license.setLicenseText(attachmentText)
    license.setUrl("https://www.test-Url.org/")

    // passing Data to Plugin
    setLicenseChoice { lc ->
        lc.addLicense(license)
    }
}
```

It should be noted that License requires __either id or name__, but both can't be included at the same time.

Text and url are optional for inclusion and multiple licenses can be added to LicenseChoice.

---

## Adding Git VCS url

Add the Git VCS url by passing an object of the type __ExternalReference__ to the plugin.

### Git VCS

__Example (groovy) and (Kotlin):__

```groovy
cyclonedxBom {
    //passing Data to the plugin
    setVCSGit { vcs ->
        // set either the ssh or https URL[String] of the remote repository
        // make sure that the ssh URL starts with the protocol (e.g. 'ssh://'), otherwise it will not be accepted
        // e.g. vcs.url = "ssh://git@github.com:CycloneDX/cyclonedx-gradle-plugin.git"
        vcs.url = "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"

        // (optional) you can add a comment to describe your repository
        // vcs.comment = "comment"
    }
}
```

It should be noted that __url is mandatory__ and type will be ignored. You may add a comment to describe the external reference.

---
For details of the BOM structure look at https://cyclonedx.org/docs/1.6/json/#metadata.

## CycloneDX Schema Support

The following table provides information on the version of this Gradle plugin, the CycloneDX schema version supported,
as well as the output format options. Use the latest possible version of this plugin that is the compatible with
the CycloneDX version supported by the target system.

| Version | Schema Version | Format(s) |
|---------|----------------|-----------|
| 2.0.x   | CycloneDX v1.6 | XML/JSON  |
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

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] file for the full license.

[License]: https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/master/LICENSE
