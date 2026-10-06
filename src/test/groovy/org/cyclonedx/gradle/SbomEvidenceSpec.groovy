package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The build cache key of a Direct SBOM covers its SBOM Evidence, the artifacts and every POM read for Metadata
 * Enrichment, and the metadata that enrichment wrote into the document. A document produced while a POM was missing
 * or unreadable must not be served once that POM is fine, a document whose metadata is older than the POMs it
 * fingerprints must not be served to a build whose metadata is current, and a document produced from unchanged
 * evidence must still be served. The cases cover a machine that keeps its configuration cache, a machine that
 * configures afresh and shares the build cache, and a POM rewritten between configuration and execution.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class SbomEvidenceSpec extends Specification {

    def "a document produced while a pom was missing is not served once the pom is there"() {
        given: "a build cache, and a component whose pom the repository does not have"
        Fixture fixture = fixture('evidence-missing-pom', 'implementation("com.test:jaronly:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'jaronly')) == 'pom-unresolved'

        when: "the repository gains the pom, whose parent carries the license"
        fixture.addPom('jaronly', 'child-ok')
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "the task runs again instead of loading the document produced without the pom"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Component jaronly = component(fixture.directBom(), 'jaronly')
        unresolvedMetadata(jaronly) == null
        licenseIds(jaronly) == ['Apache-2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced while a parent pom was missing is not served once the parent is there"() {
        given: "a build cache, and a component whose own pom is fine but whose parent the repository does not have"
        Fixture fixture = fixture('evidence-missing-parent', 'implementation("com.test:orphanpom:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'orphanpom')) == 'parent-unresolved'

        when: "the repository gains the parent, which is where the license lives"
        fixture.addPom('nowhere-parent', 'parent-ok')
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "the task runs again, since the component's own pom did not change but its parent did"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Component orphanpom = component(fixture.directBom(), 'orphanpom')
        unresolvedMetadata(orphanpom) == null
        licenseIds(orphanpom) == ['Apache-2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced while a parent pom was unreadable is not served once the parent is readable"() {
        given: "a build cache, and a component whose parent pom is an error page stored in place of the pom"
        Fixture fixture = fixture('evidence-unreadable-parent', 'implementation("com.test:badparentchild:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'badparentchild')) == 'model-incomplete'

        when: "the error page is replaced by the parent pom, which is where the license lives"
        fixture.addPom('bad-parent', 'parent-ok')
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "the task runs again, since a file it read changed even though it could not parse it"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Component badparentchild = component(fixture.directBom(), 'badparentchild')
        unresolvedMetadata(badparentchild) == null
        licenseIds(badparentchild) == ['Apache-2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced while an imported bom was missing is not served once the bom is there"() {
        given: "a build cache, and a component that imports a bom the repository does not have"
        Fixture fixture = fixture('evidence-missing-import', 'implementation("com.test:importsmissingbom:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'importsmissingbom')) == 'model-incomplete'

        when: "the repository gains the imported bom"
        fixture.addPom('nowhere-bom', 'good-bom')
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "the task runs again, since an imported pom is read for the effective model like a parent is"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the component keeps its own license, and the model is now complete"
        Component importsmissingbom = component(fixture.directBom(), 'importsmissingbom')
        unresolvedMetadata(importsmissingbom) == null
        licenseIds(importsmissingbom) == ['BSD-3-Clause']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a machine that keeps its configuration cache keeps its document, and does not hand it to a machine that configured afresh"() {
        given: "a build cache, and a component whose parent the repository does not have"
        Fixture fixture = fixture('evidence-stale-configuration', 'implementation("com.test:orphanpom:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'orphanpom')) == 'parent-unresolved'

        when: "the parent appears, and the same machine runs again with its configuration cache intact"
        fixture.addPom('nowhere-parent', 'parent-ok')
        fixture.deleteOutputs()
        BuildResult second = run(fixture)

        then: "Gradle does not record the absence of the parent as a configuration input today, so the stored graph is replayed; a Gradle that did record it would configure again and read the parent, which is accepted too"
        second.task(":cyclonedxDirectBom").outcome in [TaskOutcome.FROM_CACHE, TaskOutcome.SUCCESS]

        and: "either way the document agrees with itself: it still says what it could not read, or it carries the license and says nothing"
        Component sameMachine = component(fixture.directBom(), 'orphanpom')
        (unresolvedMetadata(sameMachine) == 'parent-unresolved' && licenseIds(sameMachine).isEmpty()) ||
            (unresolvedMetadata(sameMachine) == null && licenseIds(sameMachine) == ['Apache-2.0'])

        when: "a machine that configures afresh shares the build cache"
        fixture.startAsAnotherMachine()
        BuildResult third = run(fixture)

        then: "it computes a key of its own and produces the document the parent allows"
        third.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Component fresh = component(fixture.directBom(), 'orphanpom')
        unresolvedMetadata(fresh) == null
        licenseIds(fresh) == ['Apache-2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a parent pom whose license changes in place is read again, with or without a configuration cache"() {
        given: "a build cache, and a component whose license comes from its parent"
        Fixture fixture = fixture('evidence-relicensed-parent', 'implementation("com.test:child-ok:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        licenseIds(component(fixture.directBom(), 'child-ok')) == ['Apache-2.0']

        when: "the parent is re-published under another license, and the same machine keeps its configuration cache"
        fixture.relicenseToMit('parent-ok')
        fixture.deleteOutputs()
        BuildResult second = run(fixture)

        then: "a pom that was read is a configuration input, so the build configures again and reads the new license"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        licenseIds(component(fixture.directBom(), 'child-ok')) == ['MIT']

        when: "a machine that configures afresh shares the build cache"
        fixture.startAsAnotherMachine()
        BuildResult third = run(fixture)

        then: "it is served the document that carries the new license"
        third.task(":cyclonedxDirectBom").outcome == TaskOutcome.FROM_CACHE
        licenseIds(component(fixture.directBom(), 'child-ok')) == ['MIT']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document written from metadata older than the poms it fingerprints is not served to a machine whose metadata is current"() {
        given: "a parent pom that is an error page while the build configures, and is replaced before the task runs"
        Fixture fixture = fixture('evidence-healed-during-build', 'implementation("com.test:badparentchild:1.0.0")', """
            tasks.register('healParent') {
                def target = new File('@REPOSITORY@', 'com/test/bad-parent/1.0.0/bad-parent-1.0.0.pom')
                def healthy = new File('@REPOSITORY@', 'com/test/parent-ok/1.0.0/parent-ok-1.0.0.pom')
                outputs.file(target)
                outputs.upToDateWhen { false }
                doLast { target.text = healthy.text.replace('parent-ok', 'bad-parent') }
            }
            tasks.named('cyclonedxDirectBom') { dependsOn('healParent') }""")

        when: "the document is produced from the error page, and stored under a key that fingerprints the healthy pom"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(fixture.directBom(), 'badparentchild')) == 'model-incomplete'

        when: "a machine that configures afresh, with the healthy pom in place, shares the build cache"
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "its metadata is current, so its key differs from the one the stale document was stored under"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Component badparentchild = component(fixture.directBom(), 'badparentchild')
        unresolvedMetadata(badparentchild) == null
        licenseIds(badparentchild) == ['Apache-2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced from unchanged evidence is served from the cache"() {
        given: "a build cache, and a component whose pom and parent both resolve"
        Fixture fixture = fixture('evidence-unchanged', 'implementation("com.test:child-ok:1.0.0")')

        when: "the document is produced and stored"
        BuildResult first = run(fixture)

        then:
        first.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        when: "nothing about the evidence changes"
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture)

        then: "the stored document is served, so declaring the poms did not make the task uncacheable"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.FROM_CACHE

        where:
        javaVersion = JavaVersion.current()
    }

    private static BuildResult run(Fixture fixture) {
        return GradleRunner.create()
            .withProjectDir(fixture.projectDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom", "--build-cache"))
            .withPluginClasspath()
            .build()
    }

    /**
     * A build resolving from the jars alone, so a component resolves even when its pom is absent, with a build
     * cache of its own so that what one run stores is what the next run may load.
     */
    private static Fixture fixture(String rootName, String dependencies, String extraBuildScript = '') {
        String localRepoUri = TestUtils.duplicateRepo("broken-metadata")
        File repository = new File(new URI(localRepoUri))
        String repositoryPath = repository.absolutePath.replace('\\', '/')
        File projectDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven {
                    url '$localRepoUri'
                    metadataSources { artifact() }
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                $dependencies
            }
            ${extraBuildScript.replace('@REPOSITORY@', repositoryPath)}""", """
            rootProject.name = '$rootName'
            buildCache {
                local {
                    directory = new File(rootDir, '.build-cache')
                }
            }""")
        return new Fixture(projectDir, repository)
    }

    private static Component component(Bom bom, String name) {
        Component component = bom.components.find { it.name == name }
        assert component != null: "component $name is missing from the bom"
        return component
    }

    private static List<String> licenseIds(Component component) {
        if (component.licenses == null || component.licenses.items == null) {
            return []
        }
        return component.licenses.items
            .findAll { it.license != null }
            .collect { it.license.id ?: it.license.name }
    }

    private static String unresolvedMetadata(Component component) {
        return component.properties?.find { it.name == 'cdx:maven:package:metadata-unresolved' }?.value
    }

    private static class Fixture {
        final File projectDir
        final File repository

        Fixture(File projectDir, File repository) {
            this.projectDir = projectDir
            this.repository = repository
        }

        /** Gives a component the pom of another one, with the artifact id swapped, replacing any file in its place. */
        void addPom(String artifactId, String copiedFrom) {
            File source = new File(repository, "com/test/$copiedFrom/1.0.0/$copiedFrom-1.0.0.pom")
            File target = new File(repository, "com/test/$artifactId/1.0.0/$artifactId-1.0.0.pom")
            target.parentFile.mkdirs()
            target.text = source.text.replace(copiedFrom, artifactId)
        }

        /** Replaces the Apache license a fixture pom declares with MIT, in place. */
        void relicenseToMit(String artifactId) {
            File pom = new File(repository, "com/test/$artifactId/1.0.0/$artifactId-1.0.0.pom")
            pom.text = pom.text
                .replace('Apache License, Version 2.0', 'MIT License')
                .replace('https://www.apache.org/licenses/LICENSE-2.0.txt', 'https://opensource.org/licenses/MIT')
        }

        /** The same machine running again: outputs gone, configuration cache kept. */
        void deleteOutputs() {
            assert new File(projectDir, "build").deleteDir()
        }

        /**
         * What a machine that never ran this build starts from: no outputs and no configuration cache entry, so
         * the task's inputs are computed from the repository as it is now. The build cache is the one thing it
         * shares with the run before, which is the point.
         */
        void startAsAnotherMachine() {
            assert new File(projectDir, "build").deleteDir()
            assert new File(projectDir, ".gradle/configuration-cache").deleteDir()
        }

        Bom directBom() {
            return new ObjectMapper().readValue(new File(projectDir, "build/reports/cyclonedx-direct/bom.json"), Bom.class)
        }
    }
}
