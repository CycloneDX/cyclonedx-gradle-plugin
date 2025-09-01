package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.gradle.utils.CyclonedxUtils
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Tool
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion, task name: #taskName")
class PluginConfigurationSpec extends Specification {

    def "simple-project should output boms in build/reports with default schema version"() {
        given:
        File testDir = TestUtils.duplicate("simple-project")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()
        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"${CyclonedxUtils.DEFAULT_SCHEMA_VERSION.versionString}\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()

    }

    def "custom-destination project should output boms in output-dir"() {
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
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }
            tasks.cyclonedxDirectBom {
                jsonOutput = file('output-dir-bom/bom.json')
                xmlOutput = file('output-dir-bom/bom.xml')
            }
            tasks.cyclonedxBom {
                jsonOutput = file('output-dir-aggregate-bom/bom.json')
                xmlOutput = file('output-dir-aggregate-bom/bom.xml')
            }""", "rootProject.name = 'hello-world'")


        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()
        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File bomReportDir = new File(testDir, "output-dir-bom")
        assert bomReportDir.exists()
        bomReportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File aggregateBomReportDir = new File(testDir, "output-dir-aggregate-bom")
        assert aggregateBomReportDir.exists()
        aggregateBomReportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2

        where:
        taskName = 'cyclonedxBom'
        javaVersion = JavaVersion.current()
    }

    def "pom-xml-encoding project should not output errors to console"() {
        given:
        File testDir = TestUtils.duplicate("pom-xml-encoding")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports/cyclonedx-direct")
        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2

        assert !result.output.contains("An error occurred attempting to read POM")

        where:
        taskName = 'cyclonedxBom'
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                schemaVersion = org.cyclonedx.Version.VERSION_13
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.3\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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

            // No componentName override -> Use rootProject.name

            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                testImplementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
                implementation group: 'org.jetbrains.kotlin', name: 'kotlin-native-prebuilt', version: '2.0.20'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"hello-world\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should use configured componentGroup"() {
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
            tasks.cyclonedxDirectBom {
                componentGroup = 'customized-component-group'
            }
            tasks.cyclonedxBom {
                componentGroup = 'customized-component-group'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"group\" : \"customized-component-group\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                componentName = 'customized-component-name'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"name\" : \"customized-component-name\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                componentVersion = '999-SNAPSHOT'
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"version\" : \"999-SNAPSHOT\"")
        assert jsonBom.text.contains("\"purl\" : \"pkg:maven/com.example/hello-world@999-SNAPSHOT?project_path=%3A\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                projectType = 'framework'
            }
            dependencies {
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"type\" : \"framework\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.cyclonedxDirectBom {
                jsonOutput = file('build/reports/cyclonedx-direct/bom.json')
                xmlOutput.unsetConvention()
            }
            tasks.cyclonedxBom {
                jsonOutput = file('build/reports/cyclonedx-aggregate/bom.json')
                xmlOutput.unsetConvention()
            }
            dependencies {
                implementation group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version:'2.8.11'
                implementation group: 'org.springframework.boot', name: 'spring-boot-starter-web', version:'1.5.18.RELEASE'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 1
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.exists()
        File xmlBom = new File(reportDir, "bom.xml")
        assert !xmlBom.exists()

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx-aggregate"
        javaVersion = JavaVersion.current()
    }

    def "kotlin-dsl-project should allow configuring all properties"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert !jsonBom.text.contains("serialNumber")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "kotlin-dsl-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-project-manufacture-licenses")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
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

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "groovy-project-manufacture-licenses should allow definition of manufacture-data and licenses-data"() {
        given:
        File testDir = TestUtils.duplicate("groovy-project-manufacture-licenses")

        when:
        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
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

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.cyclonedxDirectBom {
                skipConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore == null

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.cyclonedxDirectBom {
                includeConfigs = ['.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')

        assert log4jCore.getBomRef() == 'pkg:maven/org.apache.logging.log4j/log4j-core@2.15.0?type=jar'

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, reportLocation)

        assert reportDir.exists()
        reportDir.listFiles({ File file -> file.isFile() } as FileFilter).length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert jsonBom.text.contains("\"specVersion\" : \"1.6\"")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        assert jsonBom.text.contains("pkg:maven/unspecified/hello-world@unspecified?project_path=%3A")
        assert result.output.contains("Project group or version are not set for project [hello-world]")

        where:
        taskName = 'cyclonedxDirectBom'
        javaVersion = JavaVersion.current()
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
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        assert jsonBom.text.contains("\"id\" : \"Apache-2.0\"")

        where:
        taskName = 'cyclonedxDirectBom'
        javaVersion = JavaVersion.current()
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
            tasks.cyclonedxDirectBom {
                includeMetadataResolution = false
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        assert !jsonBom.text.contains("\"id\" : \"Apache-2.0\"")

        where:
        taskName = 'cyclonedxDirectBom'
        javaVersion = JavaVersion.current()
    }

    def "should not use deprecated tool section if schema is 1.5 or higher"() {
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                schemaVersion = org.cyclonedx.Version.VERSION_16
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getToolChoice().getComponents().size() == 1
        Component cycloneDxTool = bom.getMetadata().getToolChoice().getComponents().get(0)
        assert cycloneDxTool.getName() == "cyclonedx-gradle-plugin"
        assert cycloneDxTool.getAuthor() == "CycloneDX"

        where:
        taskName = 'cyclonedxDirectBom'
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                schemaVersion = org.cyclonedx.Version.VERSION_14
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getTools().size() == 1
        Tool tool = bom.getMetadata().getTools().get(0);
        assert tool.getName() == "cyclonedx-gradle-plugin"
        assert tool.getVendor() == "CycloneDX"

        where:
        taskName = 'cyclonedxDirectBom'
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                includeBuildSystem = true
            }
            dependencies {
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["BUILD_URL": "https://jenkins.example.com/job/123"])
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences().size() == 1
        ExternalReference buildSystemRef = bom.getMetadata().getComponent().getExternalReferences().get(0);
        assert ExternalReference.Type.BUILD_SYSTEM == buildSystemRef.getType()
        assert buildSystemRef.getUrl() == "https://jenkins.example.com/job/123"

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                includeBuildSystem = true
                buildSystemEnvironmentVariable = '${SERVER}/build/${BUILD_ID}'
            }
            dependencies {
            }''', "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["SERVER": "https://ci.example.com", "BUILD_ID": "123"])
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences().size() == 1
        ExternalReference buildSystemRef = bom.getMetadata().getComponent().getExternalReferences().get(0);
        assert buildSystemRef.getType() == ExternalReference.Type.BUILD_SYSTEM
        assert buildSystemRef.getUrl() == "https://ci.example.com/build/123"

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
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
            tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                includeBuildSystem = true
                buildSystemEnvironmentVariable = '${SERVER}/build/${BUILD_ID}'
            }
            dependencies {
            }''', "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withEnvironment(["SERVER": "https://ci.example.com"])
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        assert bom.getMetadata().getComponent().getExternalReferences() == null

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should not include dependency constraints in bom"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'java'
                id 'org.cyclonedx.bom'
            }

            group = 'com.example'
            version = '0.0.1-SNAPSHOT'

            repositories {
                mavenCentral()
            }

            dependencies {
                constraints {
                    implementation 'org.jspecify:jspecify:1.0.0'
                }
                implementation 'com.google.guava:guava:33.4.8-jre'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)

        // Verify that constraints are not included in dependencies
        def rootDependency = bom.getDependencies().find { dep ->
            dep.getRef().contains("hello-world@0.0.1-SNAPSHOT")
        }
        assert rootDependency != null
        assert rootDependency.getDependencies().size() == 1
        assert rootDependency.getDependencies().get(0).getRef().contains("guava@33.4.8-jre")

        // Verify jspecify is still an entry in bom overall
        def jspecifyDependency = bom.getDependencies().find { dep ->
            dep.getRef().contains("jspecify@1.0.0")
        }
        assert jspecifyDependency != null

        // Verify guava dependency exists and has its transitive dependencies
        def guavaDependency = bom.getDependencies().find { dep ->
            dep.getRef().contains("guava@33.4.8-jre")
        }
        assert guavaDependency != null
        assert guavaDependency.getDependencies().size() > 1
        assert guavaDependency.getDependencies().any { dep ->
            dep.getRef().contains("jspecify@1.0.0")
        }

        where:
        taskName             | reportLocation
        "cyclonedxBom"       | "build/reports/cyclonedx"
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        javaVersion = JavaVersion.current()
    }

    def "should use use base config and apply custom subproject config"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-with-custom-configs")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-a:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-b:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        def objectMapper = new ObjectMapper()
        def rootProjectBom = objectMapper.readValue(new File(testDir, "build/reports/cyclonedx-direct/bom.json"), Bom.class)
        assert rootProjectBom.getMetadata().getComponent().getName() == 'multi-module-component'

        def appAProjectBom = objectMapper.readValue(new File(testDir, "app-a/build/reports/cyclonedx-direct/bom.json"), Bom.class)
        assert appAProjectBom.getMetadata().getComponent().getName() == 'app-a-component'

        def appBProjectBom = objectMapper.readValue(new File(testDir, "app-b/build/reports/cyclonedx-direct/bom.json"), Bom.class)
        assert appBProjectBom.getMetadata().getComponent().getName() == 'another name'

        where:
        taskName = "cyclonedxBom"
        javaVersion = JavaVersion.current()
    }

    def "should skip sub-project when task is disabled"() {
        given:
        File testDir = TestUtils.duplicate("multi-module-with-skipped-project")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-a:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.task(":app-b:cyclonedxDirectBom").outcome == TaskOutcome.SKIPPED
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        def objectMapper = new ObjectMapper()
        def rootProjectBomFile = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        assert rootProjectBomFile.exists()
        def rootProjectBom = objectMapper.readValue(rootProjectBomFile, Bom.class)
        assert rootProjectBom.getComponents().findAll { it.name == 'app-b' }.empty
        def appAProjectBomFile = new File(testDir, "app-a/build/reports/cyclonedx-direct/bom.json")
        assert appAProjectBomFile.exists()
        assert !new File(testDir, "app-b/build/reports/cyclonedx-direct/bom.json").exists()

        where:
        taskName = "cyclonedxBom"
        javaVersion = JavaVersion.current()
    }
}
