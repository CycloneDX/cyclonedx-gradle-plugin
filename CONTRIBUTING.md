# Contributing guide

We try to make it easy, and all contributions, even the smaller ones, are more than welcome.
This includes bug reports, fixes, documentation, examples...

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing and what you would expect to see.

## Building the project locally

Just do the following:

```
git@github.com:CycloneDX/cyclonedx-gradle-plugin.git
cd cyclonedx-gradle-plugin
./gradlew publishToMavenLocal
```

This will build and publish the new plugin version locally

## Using local plugin builds in your project

In the settings.gradle.kts file, add:
```
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```
In order for your local plugin version to be found, it is important that you add mavenLocal() to your plugin repositories.
