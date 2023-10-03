plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")  version "1.2.1"
    id("org.cyclonedx.bom") version "1.7.4"
}

val organization = "CycloneDX"
group = "org.cyclonedx"
version = "1.7.4"

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.cyclonedx:cyclonedx-core-java:8.0.0") {
        exclude(group = "org.apache.logging.log4j", module ="log4j-slf4j-impl")
    }

    implementation("commons-codec:commons-codec:1.16.0")
    implementation("commons-io:commons-io:2.13.0")
    implementation("org.apache.maven:maven-core:3.9.4")

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

