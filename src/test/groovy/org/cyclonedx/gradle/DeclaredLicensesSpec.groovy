package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.Version
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.license.Acknowledgement
import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.parsers.XmlParser
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
class DeclaredLicensesSpec extends Specification {

    @Unroll("schema version: #schemaVersion")
    def "a pom listing a license that resolves to an expression next to others is written as one expression"() {
        given: "the expression after another license, before one, and next to a name no SPDX id matches"
        String localRepoUri = TestUtils.duplicateRepo("declared-licenses")
        File testDir = TestUtils.createFromString("""
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
                implementation("com.test:expression-last:1.0.0")
                implementation("com.test:expression-first:1.0.0")
                implementation("com.test:expression-and-name:1.0.0")
            }
            tasks.named('cyclonedxDirectBom') {
                schemaVersion = org.cyclonedx.Version.${schemaVersion.name()}
            }""", "rootProject.name = 'declared-licenses'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "both documents pass their schema, which below 1.7 allows license objects or one expression, never both"
        File reportDir = new File(testDir, "build/reports/cyclonedx-direct")
        new JsonParser().validate(new File(reportDir, "bom.json"), schemaVersion).isEmpty()
        new XmlParser().validate(new File(reportDir, "bom.xml"), schemaVersion).isEmpty()

        and: "the licenses around the expression join it, in the order the pom declared them"
        Bom bom = new ObjectMapper().readValue(new File(reportDir, "bom.json"), Bom.class)
        expression(component(bom, 'expression-last')) == 'Apache-2.0 OR (CDDL-1.0 OR GPL-2.0-with-classpath-exception)'
        expression(component(bom, 'expression-first')) == '(CDDL-1.0 OR GPL-2.0-with-classpath-exception) OR Apache-2.0'

        and: "a name no SPDX id matches cannot join an expression, so the expression becomes a named license beside it"
        licenseNames(component(bom, 'expression-and-name')) ==
            ['(CDDL-1.0 OR GPL-2.0-with-classpath-exception)', 'Some Bespoke License']

        and: "all of it is what the pom states, so it is declared wherever the schema can say so"
        component(bom, 'expression-last').licenses.items*.expression*.acknowledgement == [acknowledgement]
        component(bom, 'expression-and-name').licenses.items*.license*.acknowledgement ==
            [acknowledgement, acknowledgement]

        where:
        schemaVersion      | acknowledgement
        Version.VERSION_15 | null
        Version.VERSION_16 | Acknowledgement.DECLARED
        Version.VERSION_17 | Acknowledgement.DECLARED
    }

    private static Component component(Bom bom, String name) {
        Component component = bom.components.find { it.name == name }
        assert component != null: "component $name is missing from the bom"
        return component
    }

    private static String expression(Component component) {
        assert component.licenses.items.size() == 1: "expected one expression, got ${component.licenses.items}"
        return component.licenses.items[0].expression.value
    }

    private static List<String> licenseNames(Component component) {
        return component.licenses.items.collect { it.license.name }
    }
}
