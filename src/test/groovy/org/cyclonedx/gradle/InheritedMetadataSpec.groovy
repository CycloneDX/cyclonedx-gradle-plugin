package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Metadata Enrichment reads a component's Effective POM, so what a module inherits from its parent reaches the
 * document, with the inheritance rules Maven applies. See ADR 0017.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class InheritedMetadataSpec extends Specification {

    def "a component inheriting its organization and scm from its parent carries them"() {
        given: "a parent pom declaring the metadata, and a child pom declaring none of it"
        File testDir = project('inherited-metadata', """
                implementation("com.test:inheriting-child:1.0.0")""")

        when:
        def result = runner(testDir).build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the parent's organization is the publisher; its description describes the parent and is not taken"
        Component component = component(parseDirectBom(testDir), 'inheriting-child')
        component.publisher == 'Example Parent Org'
        component.description == null

        and: "each parent url becomes a reference of the child; Maven appends the module to the inherited scm url"
        references(component) == [
            (ExternalReference.Type.WEBSITE)      : 'https://example.org',
            (ExternalReference.Type.BUILD_SYSTEM) : 'https://ci.example.org/project',
            (ExternalReference.Type.DISTRIBUTION) : 'https://downloads.example.org/project',
            (ExternalReference.Type.ISSUE_TRACKER): 'https://issues.example.org/project',
            (ExternalReference.Type.MAILING_LIST) : 'https://lists.example.org/project/dev',
            (ExternalReference.Type.VCS)          : 'https://git.example.org/project/inheriting-child',
        ]

        and: "nothing about its metadata is reported as unresolved"
        unresolvedMetadata(component) == null

        where:
        javaVersion = JavaVersion.current()
    }

    def "a component declaring its own organization and scm keeps them, and inherits only what it does not declare"() {
        given: "a child pom declaring its own organization, scm and description under the same parent"
        File testDir = project('declared-metadata', """
                implementation("com.test:declaring-child:1.0.0")""")

        when:
        def result = runner(testDir).build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "its own declarations win, and its own scm url is not appended to"
        Component component = component(parseDirectBom(testDir), 'declaring-child')
        component.publisher == 'Declaring Org'
        component.description == 'A module describing itself'
        references(component)[ExternalReference.Type.WEBSITE] == 'https://declaring.example.org'
        references(component)[ExternalReference.Type.VCS] == 'https://git.example.org/declaring'

        and: "the urls it does not declare come from the parent"
        references(component)[ExternalReference.Type.ISSUE_TRACKER] == 'https://issues.example.org/project'
        references(component)[ExternalReference.Type.BUILD_SYSTEM] == 'https://ci.example.org/project'

        where:
        javaVersion = JavaVersion.current()
    }

    def "the pom embedded in the jar is read first, so its scm wins over the inherited one"() {
        given: "a child inheriting everything, whose jar embeds a pom declaring a different scm url"
        File testDir = project('embedded-metadata', """
                implementation("com.test:embedded-scm-child:1.0.0")""")

        when:
        def result = runner(testDir).build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the embedded pom's scm url is the vcs reference, not the parent's with the module appended"
        Component component = component(parseDirectBom(testDir), 'embedded-scm-child')
        references(component)[ExternalReference.Type.VCS] == 'https://git.example.org/embedded'

        and: "what the embedded pom does not declare is still inherited"
        component.publisher == 'Example Parent Org'
        references(component)[ExternalReference.Type.ISSUE_TRACKER] == 'https://issues.example.org/project'

        where:
        javaVersion = JavaVersion.current()
    }

    def "a component whose parent cannot be resolved still reports the organization and scm it declares itself"() {
        given: "a child declaring its own organization and scm under a parent the repository does not hold"
        File testDir = project('orphan-metadata', """
                implementation("com.test:orphan-declaring:1.0.0")""", "metadataSources { artifact() }")

        when:
        def result = runner(testDir).build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "its own declarations are reported, as they replace what it would inherit instead of merging with it"
        Component component = component(parseDirectBom(testDir), 'orphan-declaring')
        component.publisher == 'Orphan Org'
        references(component)[ExternalReference.Type.WEBSITE] == 'https://orphan.example.org'
        references(component)[ExternalReference.Type.VCS] == 'https://git.example.org/orphan'

        and: "the document says the parent is what could not be resolved"
        unresolvedMetadata(component) == 'parent-unresolved'

        where:
        javaVersion = JavaVersion.current()
    }

    private static GradleRunner runner(File testDir) {
        return GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
    }

    /**
     * A build resolving from a copy of the inherited-metadata repository. With Gradle's default metadata sources the
     * parent pom resolves along with the child's; resolving from the jars alone lets a component whose parent is
     * missing resolve, so that only its metadata fails.
     */
    private static File project(String rootName, String dependencies, String repositoryOptions = "") {
        String localRepoUri = TestUtils.duplicateRepo("inherited-metadata")

        return TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven {
                    url '$localRepoUri'
                    $repositoryOptions
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {$dependencies
            }""", "rootProject.name = '$rootName'")
    }

    private static Bom parseDirectBom(File testDir) {
        return new ObjectMapper().readValue(new File(testDir, "build/reports/cyclonedx-direct/bom.json"), Bom.class)
    }

    private static Component component(Bom bom, String name) {
        Component component = bom.components.find { it.name == name }
        assert component != null: "component $name is missing from the bom"
        return component
    }

    /**
     * The component's external references by type. A type appears at most once, since enrichment adds each only
     * when the component has none of it yet.
     */
    private static Map<ExternalReference.Type, String> references(Component component) {
        return (component.externalReferences ?: []).collectEntries { [(it.type): it.url] }
    }

    private static String unresolvedMetadata(Component component) {
        return component.properties?.find { it.name == 'cdx:maven:package:metadata-unresolved' }?.value
    }
}
