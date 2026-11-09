package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A Direct SBOM takes component identities, dependency relationships and the test label from the Resolved Dependency
 * Graph, and none of them is a file. A project component in particular has no artifact among the task's inputs. The
 * graph is therefore an input of its own: a change in it must run the task again and must not be answered from the
 * build cache, while an unchanged graph must still be up to date and served from the cache. The project whose
 * document is read is a subproject in every case but the last, because the project the plugin is applied to lists
 * every Contributing Project through its aggregate configuration, whatever it declares.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class ResolvedDependencyGraphSpec extends Specification {

    private static final String EXTERNAL = "com.test:componentb:1.0.0"

    def "a project dependency whose version changes is listed with its new version"() {
        given: "a project that depends on another project of the build"
        Fixture fixture = fixture('graph-project-version')
        fixture.app('implementation project(":lib")')
        fixture.lib('1.0')

        when:
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'lib') == ['1.0']

        and: "nothing of the other project was built to describe it"
        first.tasks*.path == [":app:cyclonedxDirectBom"]

        when: "the other project changes its version, which changes no file the task reads"
        fixture.lib('2.0')
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom")

        then: "the task runs again and the document carries the new version"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'lib') == ['2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a project dependency that is removed leaves the document"() {
        given: "a project that depends on another project, which brings no external component with it"
        Fixture fixture = fixture('graph-project-removed')
        fixture.app('implementation project(":lib")')
        fixture.lib('1.0')

        when:
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'lib') == ['1.0']

        when: "the dependency is removed"
        fixture.app('')
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom")

        then: "the task runs again and the component is gone"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'lib') == []

        where:
        javaVersion = JavaVersion.current()
    }

    def "a relationship added between two components already in the graph reaches the document"() {
        given: "a project that depends on another project and on an external component"
        Fixture fixture = fixture('graph-new-relationship')
        fixture.app("""
            implementation project(":lib")
            implementation "$EXTERNAL"
        """)
        fixture.lib('1.0')

        when:
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        dependenciesOf(fixture.directBom('app'), 'lib') == []

        when: "the other project starts depending on that external component, so no component and no file is new"
        fixture.lib('1.0', "implementation \"$EXTERNAL\"")
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom")

        then: "the task runs again and the document carries the relationship"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        dependenciesOf(fixture.directBom('app'), 'lib') == ['componentb']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a dependency moved to a test configuration is labeled as test"() {
        given: "a project that depends on an external component"
        Fixture fixture = fixture('graph-moved-to-test')
        fixture.app("implementation \"$EXTERNAL\"")
        fixture.lib('1.0')

        when:
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        testLabel(fixture.directBom('app'), 'componentb') == 'false'

        when: "the dependency moves to a test configuration, which keeps the same artifact and pom"
        fixture.app("testImplementation \"$EXTERNAL\"")
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom")

        then: "the task runs again and the component is labeled as test"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        testLabel(fixture.directBom('app'), 'componentb') == 'true'

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced before a project dependency changed version is not served from the build cache"() {
        given: "a build cache, and a project that depends on another project of the build"
        Fixture fixture = fixture('graph-build-cache')
        fixture.app('implementation project(":lib")')
        fixture.lib('1.0')

        when: "the document is produced and stored"
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom", "--build-cache")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        when: "the other project changes its version, and a machine that never ran this build shares the cache"
        fixture.lib('2.0')
        fixture.startAsAnotherMachine()
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom", "--build-cache")

        then: "it computes a key of its own instead of loading the document that lists the earlier version"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'lib') == ['2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a document produced from an unchanged graph is up to date, and is served from the cache"() {
        given: "a build cache, and a project that depends on another project and on an external component"
        Fixture fixture = fixture('graph-unchanged')
        fixture.app("""
            implementation project(":lib")
            testImplementation "$EXTERNAL"
        """)
        fixture.lib('1.0')

        when: "the document is produced and stored"
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom", "--build-cache")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        when: "nothing changes"
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom", "--build-cache")

        then:
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.UP_TO_DATE

        when: "a machine that never ran this build shares the cache"
        fixture.startAsAnotherMachine()
        BuildResult third = run(fixture, ":app:cyclonedxDirectBom", "--build-cache")

        then: "the stored document is served, so declaring the graph did not make the task uncacheable"
        third.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.FROM_CACHE
        versionsOf(fixture.directBom('app'), 'lib') == ['1.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "a project of an included build whose version changes is listed with its new version"() {
        given: "a project that depends on a component which an included build provides"
        Fixture fixture = fixture('graph-included-build', """
            include 'app'
            includeBuild 'provider'""")
        fixture.app('implementation "org.example:provided:1.0"')
        fixture.file('provider/settings.gradle', "rootProject.name = 'provided'")
        fixture.file('provider/build.gradle', providedBy('1.0'))

        when:
        BuildResult first = run(fixture, ":app:cyclonedxDirectBom")

        then:
        first.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'provided') == ['1.0']

        when: "the included build changes its version"
        fixture.file('provider/build.gradle', providedBy('2.0'))
        BuildResult second = run(fixture, ":app:cyclonedxDirectBom")

        then: "the task runs again and the document carries the new version"
        second.task(":app:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.directBom('app'), 'provided') == ['2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    def "an aggregate lists a contributing project once after its version changes"() {
        given: "a build whose root aggregates two projects that do not depend on each other"
        Fixture fixture = fixture('graph-aggregate')
        fixture.app('')
        fixture.lib('1.0')

        when:
        BuildResult first = run(fixture, "cyclonedxBom")

        then:
        first.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        versionsOf(fixture.aggregateBom(), 'lib') == ['1.0']

        when: "one of them changes its version"
        fixture.lib('2.0')
        BuildResult second = run(fixture, "cyclonedxBom")

        then: "the root's own document is produced again, since it lists that project"
        second.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "so the aggregate does not carry the earlier version beside the new one"
        versionsOf(fixture.aggregateBom(), 'lib') == ['2.0']

        where:
        javaVersion = JavaVersion.current()
    }

    private static BuildResult run(Fixture fixture, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(fixture.projectDir)
            .withArguments(TestUtils.arguments(arguments))
            .withPluginClasspath()
            .build()
    }

    /**
     * A build with the plugin applied to the root and a build cache of its own, so that what one run stores is what
     * the next run may load. The projects are written by the feature methods.
     */
    private static Fixture fixture(String rootName, String projects = "include 'app', 'lib'") {
        File projectDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
            }""", """
            rootProject.name = '$rootName'
            $projects
            buildCache {
                local {
                    directory = new File(rootDir, '.build-cache')
                }
            }""")
        return new Fixture(projectDir, TestUtils.duplicateRepo("local"))
    }

    private static String providedBy(String version) {
        return """
            plugins {
                id 'java-library'
            }
            group = 'org.example'
            version = '$version'"""
    }

    private static List<String> versionsOf(Bom bom, String name) {
        return (bom.components ?: []).findAll { it.name == name }*.version
    }

    private static List<String> dependenciesOf(Bom bom, String name) {
        Component component = component(bom, name)
        Dependency dependency = bom.dependencies.find { it.ref == component.bomRef }
        List<String> refs = (dependency?.dependencies ?: [])*.ref
        return bom.components.findAll { it.bomRef in refs }*.name
    }

    private static String testLabel(Bom bom, String name) {
        return component(bom, name).properties?.find { it.name == 'cdx:maven:package:test' }?.value
    }

    private static Component component(Bom bom, String name) {
        Component component = bom.components.find { it.name == name }
        assert component != null: "component $name is missing from the bom"
        return component
    }

    private static class Fixture {
        final File projectDir
        final String repositoryUri

        Fixture(File projectDir, String repositoryUri) {
            this.projectDir = projectDir
            this.repositoryUri = repositoryUri
        }

        /** The project whose Direct SBOM the features read. */
        void app(String dependencies) {
            file('app/build.gradle', project('1.0', dependencies))
        }

        /** The project the other one depends on, which has no external component of its own unless given one. */
        void lib(String version, String dependencies = '') {
            file('lib/build.gradle', project(version, dependencies))
        }

        void file(String path, String content) {
            File file = new File(projectDir, path)
            file.parentFile.mkdirs()
            file.text = content
        }

        /**
         * What a machine that never ran this build starts from: no outputs and no configuration cache entry. The
         * build cache is the one thing it shares with the run before, which is the point.
         */
        void startAsAnotherMachine() {
            ['build', 'app/build', 'lib/build', '.gradle/configuration-cache'].each {
                File dir = new File(projectDir, it)
                assert !dir.exists() || dir.deleteDir()
            }
        }

        Bom directBom(String project) {
            return read("$project/build/reports/cyclonedx-direct/bom.json")
        }

        Bom aggregateBom() {
            return read("build/reports/cyclonedx/bom.json")
        }

        private Bom read(String path) {
            return new ObjectMapper().readValue(new File(projectDir, path), Bom.class)
        }

        private String project(String version, String dependencies) {
            return """
                plugins {
                    id 'java'
                }
                repositories {
                    maven {
                        url '$repositoryUri'
                    }
                }
                group = 'com.example'
                version = '$version'

                dependencies {
                    $dependencies
                }"""
        }
    }
}
