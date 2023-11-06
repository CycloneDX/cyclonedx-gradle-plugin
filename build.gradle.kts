import java.io.FileInputStream
import java.util.Properties

plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")  version "1.2.1"
    id("org.cyclonedx.bom") version "1.8.0"
    id("groovy")
}

val organization = "CycloneDX"
group = "org.cyclonedx"
version = "1.8.1"

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.cyclonedx:cyclonedx-core-java:8.0.3") {
        exclude(group = "org.apache.logging.log4j", module ="log4j-slf4j-impl")
    }

    implementation("commons-codec:commons-codec:1.16.0")
    implementation("commons-io:commons-io:2.15.0")
    implementation("org.apache.maven:maven-core:3.9.5")

    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:2.2-M1-groovy-3.0") {
        exclude(module = "groovy-all")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    dependsOn.add(processPluginPropertiesFile)
}

val processPluginPropertiesFile = tasks.register<DefaultTask>("processPluginPropertiesFile") {
    group = "build"
    description = "Update the project information in the plugin.properties file."
    doLast {
        val pluginPropertiesFile = file("src/main/resources/plugin.properties")

        val pluginProperties = Properties()
        pluginProperties.load(FileInputStream(pluginPropertiesFile))

        // Check for diff to avoid making continuous changes to the file
        // because of the new timestamp even if there have been no real changes.
        var diffExists = false
        if (pluginProperties["vendor"] != organization) {
            pluginProperties["vendor"] = organization
            diffExists = true
        }
        if (pluginProperties["name"] != project.name) {
            pluginProperties["name"] = project.name
            diffExists = true
        }
        if (pluginProperties["version"] != project.version) {
            pluginProperties["version"] = project.version
            diffExists = true
        }

        if (diffExists) {
            pluginProperties.store(pluginPropertiesFile.writer(), "Automatically populated by Gradle build. Do NOT modify!")
        }
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

