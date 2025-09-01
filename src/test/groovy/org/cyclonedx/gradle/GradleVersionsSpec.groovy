package org.cyclonedx.gradle

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assumptions
import spock.lang.Specification
import spock.lang.Unroll

@Unroll("java version: #javaVersion, gradle version: #gradleVersion task name: #taskName")
class GradleVersionsSpec extends Specification {

    def "should support gradle versions"() {
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
            }""", "rootProject.name = 'hello-world'")


        when:
        Assumptions.assumeFalse(
            !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)
                && GradleVersion.version(gradleVersion).majorVersion >= 9,
            "Gradle 9 requires Java 17 or higher")
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()
        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS

        where:
        taskName = 'cyclonedxBom'
        javaVersion = JavaVersion.current()
        gradleVersion << ['9.0.0', '8.14', '8.13', '8.12', '8.11', '8.10', '8.9', '8.8', '8.7', '8.6', '8.5', '8.4']
    }
}
