package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import org.cyclonedx.gradle.utils.CycloneDxUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.cyclonedx.model.Hash
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class DependencyResolutionSpec extends Specification {

    def "only add component with valid purl"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("io.quarkus:quarkus-resteasy-jackson:2.12.0.Final")
                implementation("io.quarkus:quarkus-rest-client-jackson:2.12.0.Final")
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component quarkusJackson = bom.getComponents().find(c -> c.name == 'quarkus-jackson')

        assert quarkusJackson.getBomRef() != null
    }

    def "flatten non-jar dependencies"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Dependency root = bom.getDependencies().find(dependency -> dependency.getRef().contains("example"))

        assert bom.getDependencies().find(dependency -> dependency.getRef().contains("pkg:maven/javax.persistence/javax.persistence-api@2.2?type=jar"))
        assert root.getDependencies().size() == 1
        assert root.getDependencies().get(0).getRef() == "pkg:maven/org.hibernate/hibernate-core@5.6.15.Final?type=jar"
    }

    def "should contain correct hashes"() {
        given:
        String localRepoUri = TestUtils.duplicateRepo("local")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven{
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:componenta:1.0.0")
                testImplementation("com.test:componentb:1.0.1")
            }""", "rootProject.name = 'simple-project'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component componenta = bom.getComponents().find(c -> c.name == 'componenta')
        Hash hasha =
            componenta.hashes.find(c -> c.algorithm == "SHA-256" && c.value == "8b6a28fbdb87b7a521b61bc15d265820fb8dd1273cb44dd44a8efdcd6cd40848")
        assert hasha != null
        Component componentb = bom.getComponents().find(c -> c.name == 'componentb')
        Hash hashb =
            componentb.hashes.find(c -> c.algorithm == "SHA-256" && c.value == "5a5407bd92e71336b546642b8b62b6a9544bca5c4ab2fbb8864d9faa5400ba48")
        assert hashb != null
    }

    def "should generate bom for non-jar artrifacts"() {
        given:
        String localRepoUri = TestUtils.duplicateRepo("local")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven{
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:componentc:1.0.0")
            }""", "rootProject.name = 'simple-project'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component componentc = bom.getComponents().find(c -> c.bomRef == 'pkg:maven/com.test/componentc@1.0.0?type=tgz')
        assert componentc != null
    }

    def "should build bom successfully for native kotlin project"() {
        given:
        File testDir = TestUtils.duplicate("native-kotlin-project")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2
    }

    def "loops between jar dependencies in the dependency graph should be processed"() {
        given:
        File testDir = TestUtils.duplicate("dependency-graph-loop")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        then:
        println(result.output)
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
    }

    def "loops between non-jar dependencies in the dependency graph should be processed"() {
        given:
        String localRepoUri = TestUtils.duplicateRepo("local")

        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven{
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:componentc:1.0.1")
            }""", "rootProject.name = 'simple-project'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component componentc = bom.getComponents().find(c -> c.bomRef == 'pkg:maven/com.test/componentc@1.0.1?type=tgz')
        assert componentc != null
        Component componentd = bom.getComponents().find(c -> c.bomRef == 'pkg:maven/com.test/componentd@1.0.0?type=tgz')
        assert componentd != null
    }

    def "multi-module with plugin at root should output boms in build/reports with default version including sub-projects as components"() {
        given:
        File testDir = TestUtils.duplicate("multi-module")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S", "--configuration-cache")
            .withPluginClasspath()
            .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a")
        assert appAComponent.hasComponentDefined()
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")
        assert appBComponent.hasComponentDefined()
        assert appBComponent.dependsOn("pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a")
    }

    def "multi-module with plugin in subproject should output boms in build/reports with for sub-project app-a"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-subproject")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(":app-a:cyclonedxBom", "--info", "-S", "--configuration-cache")
            .withPluginClasspath()
            .build()
        then:
        result.task(":app-a:cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "app-a/build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        assert jsonBom.metadata.component.type == "library"
        assert jsonBom.metadata.component."bom-ref" == "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a"
        assert jsonBom.metadata.component.group == "com.example"
        assert jsonBom.metadata.component.name == "app-a"
        assert jsonBom.metadata.component.version == "1.0.0"
        assert jsonBom.metadata.component.purl == "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a"

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a")
        assert !appAComponent.hasComponentDefined()
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")
        assert !appBComponent.hasComponentDefined()
        assert appBComponent.dependencies == null
    }

    def "multi-module with plugin in subproject should output boms in build/reports with for sub-project app-b"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-subproject")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(":app-a:assemble", ":app-b:cyclonedxBom", "--info", "-S")
            .withPluginClasspath()
            .build()
        then:
        result.task(":app-b:cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "app-b/build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2

        def jsonBom = loadJsonBom(new File(reportDir, "bom.json"))
        assert jsonBom.specVersion == CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString

        assert jsonBom.metadata.component.type == "library"
        assert jsonBom.metadata.component."bom-ref" == "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b"
        assert jsonBom.metadata.component.group == "com.example"
        assert jsonBom.metadata.component.name == "app-b"
        assert jsonBom.metadata.component.version == "1.0.0"
        assert jsonBom.metadata.component.purl == "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b"

        def appAComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a")
        assert appAComponent.hasComponentDefined()
        assert appAComponent.component.hashes != null
        assert !appAComponent.component.hashes.empty
        assert !appAComponent.dependsOn("pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")

        def appBComponent = JsonBomComponent.of(jsonBom, "pkg:maven/com.example/app-b@1.0.0?project_path=%3Aapp-b")
        assert !appBComponent.hasComponentDefined()
        assert appBComponent.dependsOn("pkg:maven/com.example/app-a@1.0.0?project_path=%3Aapp-a")
    }

    private static def loadJsonBom(File file) {
        return new JsonSlurper().parse(file)
    }

    private static class JsonBomComponent {

        def component
        def dependencies

        boolean hasComponentDefined() {
            return component != null
                && ["library", "application"].contains(component.type)
                && !component.group.empty
                && !component.name.empty
                && !component.version.empty
                && !component.purl.empty
        }

        boolean dependsOn(String ref) {
            return dependencies != null && dependencies.dependsOn.contains(ref)
        }

        static JsonBomComponent of(jsonBom, String ref) {
            return new JsonBomComponent(
                component: jsonBom.components.find { it."bom-ref".equals(ref) },
                dependencies: jsonBom.dependencies.find { it.ref.equals(ref) }
            )
        }
    }
}
