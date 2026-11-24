package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.xml.XmlSlurper
import org.cyclonedx.Version
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.License
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A License Comment, the addendum a POM attaches to one of its licenses, reaches the document as the
 * {@code cdx:maven:license:comments} property of the license read from that entry.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class LicenseCommentsSpec extends Specification {

    private static final String PROPERTY = 'cdx:maven:license:comments'
    private static final String COMMENT =
        'The portion of Marker.java that has been copied from org.slf4j.helpers.BasicMarker is released under the MIT License.'

    def "a license's comments are kept as a property of that license, whitespace collapsed"() {
        given: "a component declaring Apache-2.0 with a blank comment and MIT with a two-line comment"
        File testDir = project('license-comments')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "in JSON, the MIT license carries the comment on one line and the Apache license carries nothing"
        Component component = component(parseJsonBom(testDir), 'commentedlicense')
        licenseIds(component) == ['Apache-2.0', 'MIT']
        comments(license(component, 'Apache-2.0')) == null
        comments(license(component, 'MIT')) == COMMENT

        and: "in XML, the same"
        def xml = new XmlSlurper(false, false).parse(new File(testDir, "build/reports/cyclonedx-direct/bom.xml"))
        def xmlComponent = xml.components.component.find { it.name.text() == 'commentedlicense' }
        def xmlMit = xmlComponent.licenses.license.find { it.id.text() == 'MIT' }
        xmlMit.properties.property.find { it.@name == PROPERTY }.text() == COMMENT
        xmlComponent.licenses.license.find { it.id.text() == 'Apache-2.0' }.properties.size() == 0

        where:
        javaVersion = JavaVersion.current()
    }

    def "the property is written from schema #schemaVersion on, which is where licenses gained properties"() {
        given:
        File testDir = project("license-comments-${schemaVersion.versionString}",
            "schemaVersion = org.cyclonedx.Version.${schemaVersion.name()}")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Component component = component(parseJsonBom(testDir), 'commentedlicense')
        (comments(license(component, 'MIT')) != null) == written

        where:
        schemaVersion      | written
        Version.VERSION_14 | false
        Version.VERSION_15 | true
        Version.VERSION_17 | true
        javaVersion = JavaVersion.current()
    }

    private static File project(String rootName, String taskConfiguration = "") {
        String localRepoUri = TestUtils.duplicateRepo("local")

        return TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven {
                    url '$localRepoUri'
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:commentedlicense:1.0.0")
            }

            tasks.named('cyclonedxDirectBom') {
                $taskConfiguration
            }""", "rootProject.name = '$rootName'")
    }

    private static Bom parseJsonBom(File testDir) {
        return new ObjectMapper().readValue(new File(testDir, "build/reports/cyclonedx-direct/bom.json"), Bom.class)
    }

    private static Component component(Bom bom, String name) {
        Component component = bom.components.find { it.name == name }
        assert component != null: "component $name is missing from the bom"
        return component
    }

    private static List<String> licenseIds(Component component) {
        return component.licenses.items.findAll { it.license != null }.collect { it.license.id ?: it.license.name }
    }

    private static License license(Component component, String id) {
        License license = component.licenses.items.collect { it.license }.find { it?.id == id }
        assert license != null: "license $id is missing from ${component.name}"
        return license
    }

    private static String comments(License license) {
        return license.properties?.find { it.name == PROPERTY }?.value
    }
}
