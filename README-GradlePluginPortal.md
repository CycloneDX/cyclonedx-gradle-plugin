# Gradle Plugin Portal publishing

Gradle provides a registry of published plugins that can easily be imported
into projects without having to modify the pluginManagement resoultionStrategy.

Additionally, gradle provides a plugin to facilitate the building of plugins
that does extra validation checks, and another plugin that simplifies the
publishing of plugins to the portal.

# Register At Gradle Plugin Portal

In order to publish plugins in the portal, you need to register for an account
and retrieve your API key.  See their 
[website](https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/#create_an_account_on_the_gradle_plugin_portal)
for more details.  A summary of steps is listed below.

## Instructions Summary

* Go to https://plugins.gradle.org/user/register
* Register for an account
* Log in
* Go to user page
* Go to API Keys
* Generate publishing API keys
* Copy contents of textbox to `~/.gradle/gradle.properties`

# Build Gradle Plugin

Though the maven pom.xml is currently the maintained build file for this
plugin, a `build.gradle` file is also provided that can build the plugin
and publish it to the portal. Note that the `version` value in the 
`build.gradle` file must be _manually maintained_ in sync with the one
in the maven pom.xml 
 
Additionally, per standard gradle convention, a gradle wrapper pinned at 
a specific version is provided to reduce build environment dependencies.

Performing a build of the plugin is achieved by running

```
./gradlew clean build
```

The plugin will be output to `builds/libs`, and the plugin descriptor output 
to `builds/pluginDescriptors`.

# Publish locally for testing

To use the gradle build to publish the plugin locally for testing, use

```
./gradlew publishToMavenLocal
```

In order to use this locally published plugin in another build, you will need to add a
`settings.gradle` file to that build with a pluginManagement resolutionPolicy that looks
in the mavenLocal() repository.

```
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.toString() == 'com.cyclonedx.bom') {
                useModule('com.cyclonedx:cyclonedx-gradle-plugin:1.1.2-SNAPSHOT')
            }
        }
    }
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
```

# Publish to Gradle Plugin Portal

To publish the plugin to the Gradle Plugin Portal, you must first have set
up your plugin portal credentials, as describe above.  Ensure that the
`version` field in the `build.gradle` is set to the desired value.  Then 
simply run:

```
./gradlew publishPlugins
```

If this is the first time the plugin has been published, the name may need
to be approved by the portal maintainers.  This typically takes about 12 hours
during the work week.  You will see on your portal user console page that
your plugin is marked 'Pending approval'.  After it is approved, the API
credentials used to publish will 'own' the plugin name, and plugins will be
available in the portal immediately after publishing.  Note that every plugin
must be published with a NEW version.  There is no automatic overwriting of
`-SNAPSHOT` versions.  Also you have up to 1 week after publishing to delete
an uploaded version if you need to remove it.  After that time you need to 
request support to delete an uploaded version.

# Using Portal Published Plugin

To use a portal published version of the plugin, the `settings.gradle` file
modifying the resolutionStrategy is not needed and should be removed.  Instead,
users can just use the plugin DSL to import the plugin.  Note, however, that 
portal plugins _must_ specify the desired version in the DSL.

```
plugins {
    id 'com.cyclonedx.bom' version '1.1.2'
}
```