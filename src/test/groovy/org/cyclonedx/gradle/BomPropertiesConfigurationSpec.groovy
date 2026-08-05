package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Metadata
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

@Requires({ JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion, task name: #taskName")
class BomPropertiesConfigurationSpec extends Specification {

    def "should support no property definitions"() {
        given:
        File testDir = defineBuildScript("no-properties-test", "")

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Metadata meta = parse(testDir, reportLocation).getMetadata()
        assert meta.getProperties() == null
        assert meta.getComponent().getProperties() == null

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include component property when configured"() {
        given:
        File testDir = defineBuildScript(
            "single-comp-property-test",
            """
            componentProperty {
                name = 'test:property-name'
                value = 'helloworld property value'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getComponent().getProperties()
        assert properties.size() == 1
        assert containsProperty(properties, "test:property-name", "helloworld property value")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include all component properties when name is repeated"() {
        given:
        File testDir = defineBuildScript(
            "multi-comp-property-test",
            """
            componentProperty {
                name = 'test:property-name'
                value = 'helloworld property value1'
            }
            componentProperty {
                name = 'test:property-name'
                value = 'helloworld property value2'
            }
            componentProperty {
                name = 'test:different-property-name'
                value = 'different property'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getComponent().getProperties()
        assert properties.size() == 3
        assert containsProperty(properties, "test:property-name", "helloworld property value1")
        assert containsProperty(properties, "test:property-name", "helloworld property value2")
        assert containsProperty(properties, "test:different-property-name", "different property")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should support name-only component properties"() {
        given:
        File testDir = defineBuildScript(
            "comp-properties-with-name-only-test",
            """
            componentProperty {
                name = 'test:property-name1'
            }
            componentProperty {
                // still can be combined with property that has a value
                name = 'test:property-name1'
                value = 'property-value'
            }
            componentProperty {
                name = 'test:property-name2'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getComponent().getProperties()
        assert properties.size() == 3
        assert containsProperty(properties, "test:property-name1", null)
        assert containsProperty(properties, "test:property-name1", "property-value")
        assert containsProperty(properties, "test:property-name2", null)

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include metadata property when configured"() {
        given:
        File testDir = defineBuildScript(
            "single-meta-property-test",
            """
            metadataProperty {
                name = 'meta:property-name'
                value = 'metadata property value'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getProperties()
        assert properties.size() == 1
        assert containsProperty(properties, "meta:property-name", "metadata property value")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should include all metadata properties when name is repeated"() {
        given:
        File testDir = defineBuildScript(
            "multi-meta-property-test",
            """
            metadataProperty {
                name = 'meta:property-name'
                value = 'helloworld property value1'
            }
            metadataProperty {
                name = 'meta:property-name'
                value = 'helloworld property value2'
            }
            metadataProperty {
                name = 'meta:different-property-name'
                value = 'different property'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getProperties()
        assert properties.size() == 3
        assert containsProperty(properties, "meta:property-name", "helloworld property value1")
        assert containsProperty(properties, "meta:property-name", "helloworld property value2")
        assert containsProperty(properties, "meta:different-property-name", "different property")

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    def "should support name-only metadata properties"() {
        given:
        File testDir = defineBuildScript(
            "meta-properties-with-name-only-test",
            """
            metadataProperty {
                name = 'meta:property-name1'
            }
            metadataProperty {
                // still can be combined with property that has a value
                name = 'meta:property-name1'
                value = 'property-value'
            }
            metadataProperty {
                name = 'meta:property-name2'
            }
            """)

        when:
        def result = runTask(testDir, taskName)

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        Bom bom = parse(testDir, reportLocation)
        def properties = bom.getMetadata().getProperties()
        assert properties.size() == 3
        assert containsProperty(properties, "meta:property-name1", null)
        assert containsProperty(properties, "meta:property-name1", "property-value")
        assert containsProperty(properties, "meta:property-name2", null)

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }

    @Unroll("property config: #propertyConfig")
    def "should fail the build when a property name is missing or blank"() {
        given:
        File testDir = defineBuildScript("invalid-property-name-test", propertyConfig)

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.output.contains("Non-blank property name is required")

        where:
        propertyConfig << [
            "componentProperty { value = 'orphan-value' }",
            "componentProperty { name = '   ' }",
            "metadataProperty { value = 'orphan-value' }",
            "metadataProperty { name = '' }"
        ]
    }

    private static boolean containsProperty(List properties, String name, String value) {
        return properties.any { it.getName() == name && it.getValue() == value }
    }

    private static File defineBuildScript(String projectName, String taskConfig) {
        return TestUtils.createFromString("""\
            |plugins {
            |    id 'org.cyclonedx.bom'
            |    id 'java'
            |}
            |repositories { mavenCentral() }
            |group = 'com.example'
            |version = '1.0.0'
            |dependencies {
            |}
            |tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
            |    ${taskConfig.replace("\n", "\n    ")}
            |}
            """.stripMargin(), "rootProject.name = '${projectName}'")
    }

    private static BuildResult runTask(File testDir, String taskName) {
        return GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()
    }

    private static Bom parse(File testDir, String reportLocation) {
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        return new ObjectMapper().readValue(jsonBom, Bom.class)
    }
}
