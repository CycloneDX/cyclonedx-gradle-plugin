package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class UnresolvedMetadataSpec extends Specification {

    def "a component whose metadata could not be read says so, instead of looking unlicensed"() {
        given: "twelve components: one whose parent supplies a license, and the rest broken in a different way each"
        File testDir = projectResolvingFromJars('unresolved-metadata', """
                implementation("com.test:child-ok:1.0.0")
                implementation("com.test:jaronly:1.0.0")
                implementation("com.test:badpom:1.0.0")
                implementation("com.test:emptypom:1.0.0")
                implementation("com.test:orphanpom:1.0.0")
                implementation("com.test:badparentchild:1.0.0")
                implementation("com.test:importsbadbom:1.0.0")
                implementation("com.test:importsgoodbom:1.0.0")
                implementation("com.test:importsmissingbom:1.0.0")
                implementation("com.test:okparentbadimport:1.0.0")
                implementation("com.test:ownlicensewithparent:1.0.0")
                implementation("com.test:ownlicensebadparent:1.0.0")""")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then: "the build still succeeds, since a repository that answers poorly is not the build's fault"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Bom bom = parseDirectBom(testDir)

        and: "the component whose parent resolves carries its license and reports nothing unusual"
        licenseIds(component(bom, 'child-ok')) == ['Apache-2.0']
        unresolvedMetadata(component(bom, 'child-ok')) == null

        and: "a component with no pom in the repository is recorded as such, instead of looking unlicensed"
        licenseIds(component(bom, 'jaronly')).isEmpty()
        unresolvedMetadata(component(bom, 'jaronly')) == 'pom-unresolved'

        and: "so is one whose pom is not a pom, which is what an error page served in its place looks like"
        licenseIds(component(bom, 'badpom')).isEmpty()
        unresolvedMetadata(component(bom, 'badpom')) == 'pom-unparseable'

        and: "the same for a pom of zero bytes, which is what a proxy that answers 200 with an empty body leaves behind"
        licenseIds(component(bom, 'emptypom')).isEmpty()
        unresolvedMetadata(component(bom, 'emptypom')) == 'pom-unparseable'

        and: "and one whose own pom is fine but whose parent, where the license lives, cannot be resolved"
        licenseIds(component(bom, 'orphanpom')).isEmpty()
        unresolvedMetadata(component(bom, 'orphanpom')) == 'parent-unresolved'

        and: "a parent that is present but unreadable does not identify itself in the failure, so it is not named"
        licenseIds(component(bom, 'badparentchild')).isEmpty()
        unresolvedMetadata(component(bom, 'badparentchild')) == 'model-incomplete'

        and: "a component with no parent keeps what it declares, since only parents could have added to it"
        licenseIds(component(bom, 'importsbadbom')) == ['MIT']
        unresolvedMetadata(component(bom, 'importsbadbom')) == 'model-incomplete'

        and: "an import contributes no licenses of its own, which is why the case above loses nothing"
        licenseIds(component(bom, 'importsgoodbom')).isEmpty()
        unresolvedMetadata(component(bom, 'importsgoodbom')) == null

        and: "a missing import is the same story as an unreadable one, for a component with no parent"
        licenseIds(component(bom, 'importsmissingbom')) == ['BSD-3-Clause']
        unresolvedMetadata(component(bom, 'importsmissingbom')) == 'model-incomplete'

        and: "a healthy parent and a failing import report an incomplete model, since the parent is not implicated"
        licenseIds(component(bom, 'okparentbadimport')).isEmpty()
        unresolvedMetadata(component(bom, 'okparentbadimport')) == 'model-incomplete'

        and: "a component's own licenses replace what it would inherit, instead of merging with it"
        licenseIds(component(bom, 'ownlicensewithparent')) == ['MIT']
        unresolvedMetadata(component(bom, 'ownlicensewithparent')) == null

        and: "so they survive a parent that cannot be read, while the document still reports the model as incomplete"
        licenseIds(component(bom, 'ownlicensebadparent')) == ['MIT']
        unresolvedMetadata(component(bom, 'ownlicensebadparent')) == 'model-incomplete'

        where:
        javaVersion = JavaVersion.current()
    }

    def "a dependency that did not resolve is absent from the document, and the build still succeeds"() {
        given: "default metadata sources, so a component whose parent pom is missing cannot be resolved at all"
        File testDir = projectReadingMetadata('unresolved-dependency', """
                implementation("com.test:orphanpom:1.0.0")""")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the component is missing from the document, and the log is the only place that says so"
        Bom bom = parseDirectBom(testDir)
        bom.components == null || bom.components.every { it.name != 'orphanpom' }
        result.output.count("The SBOM cannot describe a dependency of compileClasspath, runtimeClasspath, " +
            "testCompileClasspath, testRuntimeClasspath: Could not resolve com.test:orphanpom:1.0.0.") == 1

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, a dependency that did not resolve fails the build and is named once"() {
        given: "default metadata sources, so a component with no pom in the repository cannot be resolved at all"
        File testDir = projectReadingMetadata('fail-on-unresolved-module', """
                implementation("com.test:jaronly:1.0.0")""", "failOnUnresolvedDependencies = true")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .buildAndFail()

        then: "the task fails, not the configuration of the build"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.FAILED

        and: "one failure names the module once, with every configuration it failed in"
        String failure = whatWentWrong(result.output)
        failure.contains("these dependencies did not resolve:")
        failure.count("- Could not find com.test:jaronly:1.0.0.") == 1
        failure.contains("(compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)")

        where:
        javaVersion = JavaVersion.current()
    }

    def "an artifact that did not resolve leaves a component without its artifact, and the log says so"() {
        given: "default metadata sources, and a component whose pom resolves but whose jar is not in the repository"
        File testDir = projectReadingMetadata('unresolved-artifact', """
                implementation("com.test:nojar:1.0.0")""")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the component is listed as a pom without hashes, since no artifact was resolved for it"
        Component nojar = component(parseDirectBom(testDir), 'nojar')
        nojar.purl.endsWith('?type=pom')
        nojar.hashes == null || nojar.hashes.isEmpty()

        and: "the log names the failure once, with the configurations it occurred in"
        result.output.count("The SBOM cannot describe a dependency of compileClasspath, runtimeClasspath, " +
            "testCompileClasspath, testRuntimeClasspath: Could not find nojar-1.0.0.jar (com.test:nojar:1.0.0).") == 1

        where:
        javaVersion = JavaVersion.current()
    }

    def "the warning is logged whenever the task runs, from a reused configuration cache entry included"() {
        given: "a build whose jar did not resolve, run once so that its configuration is cached"
        File testDir = projectReadingMetadata('unresolved-artifact-cached', """
                implementation("com.test:nojar:1.0.0")""")
        def first = runner(testDir).build()
        assert first.output.contains("The SBOM cannot describe a dependency of")
        new File(testDir, "build/reports/cyclonedx-direct").deleteDir()

        when: "the task runs again from the cached configuration"
        def result = runner(testDir).build()

        then:
        result.output.contains("Reusing configuration cache.")
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and: "the warning is there again, since the document still cannot carry the fact"
        result.output.count("The SBOM cannot describe a dependency of compileClasspath, runtimeClasspath, " +
            "testCompileClasspath, testRuntimeClasspath: Could not find nojar-1.0.0.jar (com.test:nojar:1.0.0).") == 1

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, a dependency that stops resolving fails a build that was up to date"() {
        given: "a build that succeeded, with its documents in place"
        File testDir = projectReadingMetadata('fail-on-unresolved-later', """
                implementation("com.test:child-ok:1.0.0")""", "failOnUnresolvedDependencies = true")
        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()
        File jsonBom = new File(testDir, "build/reports/cyclonedx-direct/bom.json")
        File xmlBom = new File(testDir, "build/reports/cyclonedx-direct/bom.xml")
        assert jsonBom.exists() && xmlBom.exists()

        and: "a dependency added that cannot be resolved, which changes no artifact or pom the task reads"
        File buildFile = new File(testDir, "build.gradle")
        buildFile.text = buildFile.text.replace('implementation("com.test:child-ok:1.0.0")',
            'implementation("com.test:child-ok:1.0.0")\n                implementation("com.test:jaronly:1.0.0")')

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .buildAndFail()

        then: "the task runs again instead of being up to date, and fails"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.FAILED
        whatWentWrong(result.output).contains("- Could not find com.test:jaronly:1.0.0.")

        and: "the documents of the earlier run are gone"
        !jsonBom.exists()
        !xmlBom.exists()

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, the failure carries the reason Gradle reported"() {
        given: "a repository nothing listens on"
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                maven {
                    url 'http://127.0.0.1:9/'
                    allowInsecureProtocol = true
                }
            }
            group = 'com.example'
            version = '1.0.0'

            dependencies {
                implementation("com.test:unreachable:1.0.0")
            }

            tasks.named('cyclonedxDirectBom') {
                failOnUnresolvedDependencies = true
            }""", "rootProject.name = 'fail-on-unreachable-repository'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.FAILED

        and: "the reason lives in the causes of Gradle's failure, not in its message, and is listed beneath it"
        String failure = whatWentWrong(result.output)
        failure.count("- Could not resolve com.test:unreachable:1.0.0.") == 1
        failure.contains("Could not GET 'http://127.0.0.1:9/")

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, an artifact that did not resolve fails the build and is named"() {
        given: "default metadata sources, and a component whose pom resolves but whose jar is not in the repository"
        File testDir = projectReadingMetadata('fail-on-unresolved-artifact', """
                implementation("com.test:nojar:1.0.0")""", "failOnUnresolvedDependencies = true")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.FAILED

        and: "the failure names the jar, which the document would otherwise list as a pom component without hashes"
        String failure = whatWentWrong(result.output)
        failure.contains("these dependencies did not resolve:")
        failure.count("- Could not find nojar-1.0.0.jar (com.test:nojar:1.0.0).") == 1
        failure.contains("(compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath)")

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, a build whose dependencies all resolve is unchanged"() {
        given:
        File testDir = projectReadingMetadata('fail-on-unresolved-healthy', """
                implementation("com.test:child-ok:1.0.0")""", "failOnUnresolvedDependencies = true")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        and:
        Bom bom = parseDirectBom(testDir)
        licenseIds(component(bom, 'child-ok')) == ['Apache-2.0']
        unresolvedMetadata(component(bom, 'child-ok')) == null

        where:
        javaVersion = JavaVersion.current()
    }

    def "with failOnUnresolvedDependencies, unresolved metadata still does not fail the build"() {
        given: "a component that resolves from its jar alone, so only its metadata is missing"
        File testDir = projectResolvingFromJars('fail-on-unresolved-metadata', """
                implementation("com.test:jaronly:1.0.0")""", "failOnUnresolvedDependencies = true")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then: "a missing pom is not a defect of the build, so it is reported on the component as before"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        unresolvedMetadata(component(parseDirectBom(testDir), 'jaronly')) == 'pom-unresolved'

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
     * The failure Gradle reported for the build, without the stack trace TestKit appends after it.
     */
    private static String whatWentWrong(String output) {
        def match = output =~ /(?s)\* What went wrong:(.*?)\* Try:/
        assert match.find(): "no failure in the build output"
        return match.group(1)
    }

    /**
     * A build resolving from the jars alone, so a component resolves even when its pom is absent or
     * unusable, which is what leaves metadata resolution to fail on its own.
     */
    private static File projectResolvingFromJars(String rootName, String dependencies, String taskConfiguration = "") {
        return project(rootName, dependencies, "metadataSources { artifact() }", taskConfiguration)
    }

    /**
     * A build resolving as Gradle normally does, where a component whose pom cannot be read does not
     * resolve at all and never reaches the document.
     */
    private static File projectReadingMetadata(String rootName, String dependencies, String taskConfiguration = "") {
        return project(rootName, dependencies, "", taskConfiguration)
    }

    private static File project(
        String rootName, String dependencies, String repositoryOptions, String taskConfiguration = "") {
        String localRepoUri = TestUtils.duplicateRepo("broken-metadata")

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
            }

            tasks.named('cyclonedxDirectBom') {
                $taskConfiguration
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
}
