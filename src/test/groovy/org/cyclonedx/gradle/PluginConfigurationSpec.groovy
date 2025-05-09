package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.utils.CycloneDxUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Tool
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification


class PluginConfigurationSpec extends Specification {

    def "simple-project should output boms in build/reports with default schema version"() {
        given:
          File testDir = TestUtils.duplicate("simple-project")

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
            File jsonBom = new File(reportDir, "bom.json")
            assert jsonBom.text.contains("\"specVersion\" : \"${CycloneDxUtils.DEFAULT_SCHEMA_VERSION.versionString}\"")
    }

    def "custom-destination project should output boms in output-dir"() {
        given:
        File testDir = TestUtils.duplicate("custom-destination")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom", "--configuration-cache")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "output-dir")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2
    }

    def "custom-output project should write boms under my-bom"() {
        given:
        File testDir = TestUtils.duplicate("custom-outputname")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom", "--configuration-cache")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        assert new File(testDir, "build/reports/my-bom.json").exists()
        assert new File(testDir, "build/reports/my-bom.xml").exists()
    }

    def "pom-xml-encoding project should not output errors to console"() {
        given:
        File testDir = TestUtils.duplicate("pom-xml-encoding")

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

        assert !result.output.contains("An error occurred attempting to read POM")
    }

    def "should use configured schemaVersion"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.3\"")
    }

    def "should use project name as componentName"() {
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
            cyclonedxBom {
                // No componentName override -> Use rootProject.name
            }

            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                testImplementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
                implementation group: 'org.jetbrains.kotlin', name: 'kotlin-native-prebuilt', version: '2.0.20'
            }""", "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"hello-world\"")
    }

    def "should use configured componentName"() {
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
            cyclonedxBom {
                componentName = 'customized-component-name'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"customized-component-name\"")
    }

    def "should use configured componentVersion"() {
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
            cyclonedxBom {
                componentVersion = '999-SNAPSHOT'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"version\" : \"999-SNAPSHOT\"")
        assert jsonBom.text.contains("\"purl\" : \"pkg:maven/com.example/hello-world@999-SNAPSHOT?project_path=%3A\"")
    }

    def "should use configured projectType"() {
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
            cyclonedxBom {
                projectType = 'framework'
            }
            dependencies {
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"type\" : \"framework\"")
    }

    def "should use configured outputFormat to limit generated file"() {
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
            cyclonedxBom {
                outputFormat = 'json'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

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
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 1
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.exists()
    }

    def "includes component bom-ref when schema version greater than 1.0"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'
    }

    def "kotlin-dsl-project should allow configuring all properties"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert !jsonBom.text.contains("serialNumber")
    }

    def "kotlin-dsl-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project-manufacture-licenses")

        when:
        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        //check Manufacture Data
        assert jsonBom.text.contains("\"name\" : \"Test\"")
        assert jsonBom.text.contains("\"url\"")
        assert jsonBom.text.contains("\"name\" : \"Max_Mustermann\"")
        assert jsonBom.text.contains("\"email\" : \"max.mustermann@test.org\"")
        assert jsonBom.text.contains("\"phone\" : \"0000 99999999\"")

        //check Licenses Data
        assert jsonBom.text.contains("\"licenses\"")
        assert jsonBom.text.contains("\"content\" : \"This is a Licenses-Test\"")
        assert jsonBom.text.contains("\"url\" : \"https://www.test-Url.org/\"")

    }

    def "groovy-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("groovy-project-manufacture-licenses")

        when:
        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--info", "-S", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles({File file -> file.isFile()} as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        //check Manufacture Data
        assert jsonBom.text.contains("\"name\" : \"Test\"")
        assert jsonBom.text.contains("\"url\"")
        assert jsonBom.text.contains("\"name\" : \"Max_Mustermann\"")
        assert jsonBom.text.contains("\"email\" : \"max.mustermann@test.org\"")
        assert jsonBom.text.contains("\"phone\" : \"0000 99999999\"")

        //check Licenses Data
        assert jsonBom.text.contains("\"licenses\"")
        assert jsonBom.text.contains("\"content\" : \"This is a Licenses-Test\"")
        assert jsonBom.text.contains("\"url\" : \"https://www.test-Url.org/\"")

    }

    def "should skip configurations with regex"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
                skipConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--stacktrace", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore == null
    }

    def "should include configurations with regex"() {
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
            cyclonedxBom {
                schemaVersion = '1.3'
                includeConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'
    }

    def "should use 1.6 is default schema version"() {
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
            """, "rootProject.name = 'hello-world'")

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
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.6\"")
    }

    def "should print error if project group, name, or version unset"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = ''
            version = ''
            """, "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--stacktrace")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        assert jsonBom.text.contains("pkg:maven/unspecified/hello-world@unspecified?project_path=%3A")
        assert result.output.contains("Project group or version are not set for project [hello-world]")
    }

    def "should include metadata by default"() {
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
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("cyclonedxBom", "--stacktrace", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        assert jsonBom.text.contains("\"id\" : \"Apache-2.0\"")
    }

    def "should not include metadata when includeMetadataResolution is false"() {
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
            cyclonedxBom {
                includeMetadataResolution = false
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        assert !jsonBom.text.contains("\"id\" : \"Apache-2.0\"")
    }

    def "should not use depecrated tool section if schema is 1.5 or higher"() {
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
            cyclonedxBom {
                schemaVersion = "1.6"
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        assert bom.getMetadata().getToolChoice().getComponents().size() == 1
        Component cycloneDxTool = bom.getMetadata().getToolChoice().getComponents().get(0)
        assert cycloneDxTool.getName() == "cyclonedx-gradle-plugin"
        assert cycloneDxTool.getAuthor() == "CycloneDX"
    }

    def "should use legacy tools section if schema is below 1.5"() {
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
            cyclonedxBom {
                schemaVersion = "1.4"
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
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
        assert bom.getMetadata().getTools().size() == 1
        Tool tool = bom.getMetadata().getTools().get(0);
        assert tool.getName() == "cyclonedx-gradle-plugin"
        assert tool.getVendor() == "CycloneDX"
    }

    def "should include external reference - build-system"() {
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
            cyclonedxBom {
                includeBuildSystem = true
            }
            dependencies {
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["BUILD_URL" : "https://jenkins.example.com/job/123"])
            .withArguments("cyclonedxBom", "--configuration-cache")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences().size() == 1
        ExternalReference buildSystemRef = bom.getMetadata().getComponent().getExternalReferences().get(0);
        assert ExternalReference.Type.BUILD_SYSTEM == buildSystemRef.getType()
        assert buildSystemRef.getUrl() == "https://jenkins.example.com/job/123"
    }

    def "should include external reference - build-system using configured environment variables"() {
        given:
        File testDir = TestUtils.createFromString('''
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
                includeBuildSystem = true
                buildSystemEnvironmentVariable = '${SERVER}/build/${BUILD_ID}'
            }
            dependencies {
            }''', "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["SERVER" : "https://ci.example.com", "BUILD_ID" : "123"])
            .withArguments("cyclonedxBom", "--configuration-cache", "--stacktrace")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences().size() == 1
        ExternalReference buildSystemRef = bom.getMetadata().getComponent().getExternalReferences().get(0);
        assert buildSystemRef.getType() == ExternalReference.Type.BUILD_SYSTEM
        assert buildSystemRef.getUrl() == "https://ci.example.com/build/123"
    }

    def "should not include external reference if specified environment variables do not exist"() {
        given:
        File testDir = TestUtils.createFromString('''
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
                includeBuildSystem = true
                buildSystemEnvironmentVariable = '${SERVER}/build/${BUILD_ID}'
            }
            dependencies {
            }''', "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["SERVER" : "https://ci.example.com"])
            .withArguments("cyclonedxBom", "--configuration-cache", "--stacktrace")
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences().size() == 0
    }
}
