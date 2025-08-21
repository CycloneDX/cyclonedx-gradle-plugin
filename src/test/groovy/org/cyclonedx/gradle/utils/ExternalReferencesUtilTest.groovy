package org.cyclonedx.gradle.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.TestUtils
import org.cyclonedx.model.Bom
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions
import spock.lang.Specification
import spock.lang.Unroll

@Unroll("java version: #javaVersion")
class ExternalReferencesUtilTest extends Specification {

    @Unroll("java version: #javaVersion, user info: #userInfo")
    def "should add git https remote url to metadata from .git directory"() {
        given: "A mocked project directory with .git config and https url"
        File testDir = TestUtils.createFromString(
            """
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
            }""", "rootProject.name = 'hello-world'",
            "https://${userInfo}github.com/CycloneDX/cyclonedx-gradle-plugin.git"
        )
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert !bom.getMetadata().getComponent().getExternalReferences().isEmpty()
        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er -> er.url == "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git" }

        where:
        userInfo             | _
        ""                   | _
        "username@"          | _
        "username:password@" | _
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, prefix: #prefix")
    def "should add git ssh remote url to metadata from .git directory"() {
        given: "A mocked project directory with .git config and ssh url"
        File testDir = TestUtils.createFromString(
            """
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
            }""", "rootProject.name = 'hello-world'",
            "${prefix}git@github.com:barblin/cyclonedx-gradle-plugin.git"
        )
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er -> er.url == "ssh://git@github.com:barblin/cyclonedx-gradle-plugin.git" }

        where:
        prefix   | _
        ""       | _
        "ssh://" | _
        javaVersion = TestUtils.javaVersion
    }

    def "should add git reference to metadata from environment (Jenkins e.g.)"() {
        given: "A mocked project directory with .git config and https url"
        File testDir = TestUtils.createFromString(
            """
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
            }""", "rootProject.name = 'hello-world'",
            TestUtils.VCS.GIT_HTTPS
        )

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withEnvironment(["GIT_URL": "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"])
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er -> er.url == "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git" }

        where:
        javaVersion = TestUtils.javaVersion
    }

    def "should not set git remote url if the working directory is not a valid repo nor has a valid environment variable"() {
        given: "A mocked project directory"
        File testDir = TestUtils.createFromString(
            """
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
            }""", "rootProject.name = 'hello-world'"
        )
        and: "given the current test directory context"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .noneMatch { er -> er.url == "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git" }

        where:
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, user info: #userInfo")
    def "should set https remote git url by custom configuration"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            import org.cyclonedx.model.ExternalReference
            def er = new ExternalReference()
            er.type = ExternalReference.Type.VCS
            er.url = "https://${userInfo}github.com/CycloneDX/byUserInput.git"
            cyclonedxBom {
              externalReferences = [er]
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er ->
                {
                    er.url == "https://github.com/CycloneDX/byUserInput.git"
                    er.type.name() == "VCS"
                    er.comment == null
                }
            }

        where:
        userInfo             | _
        ""                   | _
        "username@"          | _
        "username:password@" | _
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, prefix: #prefix")
    def "should set ssh remote git url by custom configuration"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            import org.cyclonedx.model.ExternalReference
            def er = new ExternalReference()
            er.type = ExternalReference.Type.VCS
            er.url = "${prefix}git@github.com:barblin/byUserInput.git"
            er.comment = "optional comment"
            cyclonedxBom {
              externalReferences = [er]
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er ->
                {
                    er.url == "ssh://git@github.com:barblin/byUserInput.git"
                        && er.type.name() == "VCS"
                        && er.comment == "optional comment"
                }
            }

        where:
        prefix   | _
        "ssh://" | _
        javaVersion = TestUtils.javaVersion
    }

    def "should ignore invalid url"() {
        given: "A mocked project directory with no git repo configuration"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            import org.cyclonedx.model.ExternalReference
            def er = new ExternalReference()
            er.type = ExternalReference.Type.VCS
            er.url = "invalid-url@github.com:barblin/byUserInput.git"
            cyclonedxBom {
              externalReferences = [er]
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .noneMatch { er -> er.url == "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git" }

        where:
        javaVersion = TestUtils.javaVersion
    }

    def "should prioritize custom configuration over environment variable and repo config"() {
        given: "A mocked project directory with .git config and ssh url"
        File testDir = TestUtils.createFromString(
            """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            import org.cyclonedx.model.ExternalReference
            def er = new ExternalReference()
            er.type = ExternalReference.Type.VCS
            er.url = "https://github.com/CycloneDX/byUserInput.git"
            cyclonedxBom {
              externalReferences = [er]
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'",
            TestUtils.VCS.GIT_SSH
        )

        and: "given the current test directory context"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        Assumptions.assumeTrue(javaVersion >= 17)
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["GIT_URL": "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"])
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().stream()
            .anyMatch { er ->
                {
                    er.url == "https://github.com/CycloneDX/byUserInput.git"
                        && er.type.name() == "VCS"
                }
            }

        where:
        javaVersion = TestUtils.javaVersion
    }
}
