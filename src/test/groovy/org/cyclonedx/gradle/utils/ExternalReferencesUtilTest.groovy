package org.cyclonedx.gradle.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.TestUtils
import org.cyclonedx.model.Bom
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class ExternalReferencesUtilTest extends Specification {

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
            TestUtils.VCS.GIT_HTTPS
        )
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert !bom.getMetadata().getComponent().getExternalReferences().isEmpty()
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() != null
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"
    }

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
            TestUtils.VCS.GIT_SSH
        )
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "ssh://git@github.com:barblin/cyclonedx-gradle-plugin.git"
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
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withEnvironment(["GIT_URL": "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"])
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"
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
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().isEmpty()
    }

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

            cyclonedxBom {
                setVCSGit { vcs ->
                    vcs.url = "https://github.com/CycloneDX/byUserInput.git"
                }
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "https://github.com/CycloneDX/byUserInput.git"
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getType().name() == "VCS"
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getComment() == null
    }

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

            cyclonedxBom {
                setVCSGit { vcs ->
                    vcs.url = "ssh://git@github.com:barblin/byUserInput.git"
                    vcs.comment = "optional comment"
                }
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "ssh://git@github.com:barblin/byUserInput.git"
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getType().name() == "VCS"
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getComment() == "optional comment"
    }

    def "should ignore invalid ssh url"() {
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

            cyclonedxBom {
                setVCSGit { vcs ->
                    vcs.url = "git@github.com:barblin/byUserInput.git"
                }
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'"
        )

        and: "given the current test directory context (otherwise it will pick up the repo url from cycloneDx repo)"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().isEmpty()
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

            cyclonedxBom {
                setVCSGit { vcs ->
                    vcs.url = "https://github.com/CycloneDX/byUserInput.git"
                }
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.6.15.Final")
            }""", "rootProject.name = 'hello-world'",
            TestUtils.VCS.GIT_SSH
        )

        and: "given the current test directory context"
        System.setProperty("user.dir", testDir.toPath().toString())

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["GIT_URL": "https://github.com/CycloneDX/cyclonedx-gradle-plugin.git"])
            .withArguments("cyclonedxBom")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getUrl() ==
            "https://github.com/CycloneDX/byUserInput.git"
        assert bom.getMetadata().getComponent().getExternalReferences().get(0).getType().name() ==
            "VCS"
    }
}
