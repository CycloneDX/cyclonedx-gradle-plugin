package org.cyclonedx.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification


class PluginConfigurationSpec extends Specification {

    def "simple-project should output boms in build/reports"() {
        given:
          File testDir = TestUtils.duplicate("simple-project")

        when:
            def result = GradleRunner.create()
                    .withProjectDir(testDir)
                    .withArguments("cyclonedxBom")
                    .withPluginClasspath()
                    .build()
        then:
            result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
            File reportDir = new File(testDir, "build/reports")

            assert reportDir.exists()
            reportDir.listFiles().length == 2
    }

    def "custom-destination project should output boms in output-dir"() {
        given:
        File testDir = TestUtils.duplicate("custom-destination")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "output-dir")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
    }

    def "custom-output project should write boms under my-bom"() {
        given:
        File testDir = TestUtils.duplicate("custom-outputname")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()
        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS

        assert new File(testDir, "build/reports/my-bom.json").exists()
        assert new File(testDir, "build/reports/my-bom.xml").exists()
    }

    def "kotlin-dsl-project should allow configuring all properties"() {
        given:
        File testDir = TestUtils.duplicate("kotlin-dsl-project")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments("cyclonedxBom")
                .withPluginClasspath()
                .build()

        then:
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        File reportDir = new File(testDir, "build/reports")

        assert reportDir.exists()
        reportDir.listFiles().length == 2
        File jsonBom = new File(reportDir, "bom.json")
        assert !jsonBom.text.contains("serialNumber")
    }

}
