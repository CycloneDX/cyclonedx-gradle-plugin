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

/**
 * The component data fields of BSI TR-03183-2 that `includeBsiFields` writes: the Component File as the
 * `bsi:component:*` properties, and the Component Creator as `manufacturer`. See ADR 0013 and ADR 0014.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class BsiFieldsSpec extends Specification {

    private static final String JAR_FLAGS = "executable, archive, structured"
    private static final String GENERAL_ARCHIVE_FLAGS = "non-executable, archive, structured"
    private static final String SHARED_LIBRARY_FLAGS = "executable, no archive, unstructured"

    def "writes no BSI field unless asked"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                organizationalEntity = acme()
            }
            """)

        when:
        def result = runDirect(testDir)

        then: "the document is the one every build gets today, with the entity as the SBOM's manufacturer only"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirect(testDir)
        bom.metadata.manufacturer.name == "ACME"
        bom.metadata.component.manufacturer == null
        bsiProperties(bom.metadata.component).isEmpty()
        bom.components.every { bsiProperties(it).isEmpty() && it.manufacturer == null }

        where:
        javaVersion = JavaVersion.current()
    }

    def "describes the file of every resolved module by its name and format"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
            }
            """)

        when:
        def result = runDirect(testDir)

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirect(testDir)
        fileDescription(bom, "componentf") == "componentf-1.0.0.jar: " + JAR_FLAGS
        fileDescription(bom, "componentc") == "componentc-1.0.0.tgz: " + GENERAL_ARCHIVE_FLAGS
        fileDescription(bom, "componentg") == "componentg-1.0.0.so: " + SHARED_LIBRARY_FLAGS

        and: "a file whose format the table does not know gets its name and no flag"
        fileDescription(bom, "componenti") == "componenti-1.0.0.json"

        and: "a BOM import has no file"
        bsiProperties(component(bom, "componenth")).isEmpty()

        where:
        javaVersion = JavaVersion.current()
    }

    def "names the creator of a resolved module from its POM"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
            }
            """)

        when:
        def result = runDirect(testDir)

        then: "the organization comes first, with its name, then the source repository"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirect(testDir)
        component(bom, "componentf").manufacturer.name == "Example Org"
        component(bom, "componentf").manufacturer.urls == ["https://example.org"]
        component(bom, "componentg").manufacturer.name == null
        component(bom, "componentg").manufacturer.urls == ["https://git.example.org/componentg"]

        and: "a POM declaring neither gets no creator"
        component(bom, "componentc").manufacturer == null
        component(bom, "componenth").manufacturer == null

        where:
        javaVersion = JavaVersion.current()
    }

    def "warns that the creators are left out below CycloneDX 1.6"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
                organizationalEntity = acme()
                schemaVersion = org.cyclonedx.Version.VERSION_15
            }
            """)

        when:
        def result = runDirect(testDir)

        then: "the file properties are written, the manufacturers are not, and the log says so once"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        result.output.count("CycloneDX 1.5 has no component manufacturer") == 1
        Bom bom = parseDirect(testDir)
        fileDescription(bom, "componentf") == "componentf-1.0.0.jar: " + JAR_FLAGS
        component(bom, "componentf").manufacturer == null
        bom.metadata.component.manufacturer == null

        where:
        javaVersion = JavaVersion.current()
    }

    def "describes the main component by the jar it builds and the organizational entity"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
                organizationalEntity = acme()
            }
            """)

        when:
        def result = runDirect(testDir)

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseDirect(testDir)
        fileDescription(bom.metadata.component) == "bsi-fields-1.0.0.jar: " + JAR_FLAGS
        bom.metadata.component.manufacturer.name == "ACME"

        and: "the SBOM's own manufacturer stays"
        bom.metadata.manufacturer.name == "ACME"

        where:
        javaVersion = JavaVersion.current()
    }

    def "names the main component's file as configured"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
                componentFileName = 'bsi-fields-1.0.0-dist.zip'
            }
            """)

        when:
        def result = runDirect(testDir)

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        fileDescription(parseDirect(testDir).metadata.component) == "bsi-fields-1.0.0-dist.zip: " + GENERAL_ARCHIVE_FLAGS

        where:
        javaVersion = JavaVersion.current()
    }

    def "names no file for the main component when the jar task is disabled"() {
        given:
        File testDir = singleProject(TestUtils.duplicateRepo("local"), """
            tasks.jar {
                enabled = false
            }
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
            }
            """)

        when:
        def result = runDirect(testDir)

        then: "a jar the build does not produce is not the main component's file"
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        bsiProperties(parseDirect(testDir).metadata.component).isEmpty()

        where:
        javaVersion = JavaVersion.current()
    }

    def "leaves the main component's file out without the java plugin and a configured name"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
            }
            group = 'com.example'
            version = '1.0.0'
            tasks.cyclonedxDirectBom {
                includeBsiFields = true
            }
            """, "rootProject.name = 'bsi-fields'")

        when:
        def result = runDirect(testDir)

        then:
        result.task(":cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        bsiProperties(parseDirect(testDir).metadata.component).isEmpty()

        where:
        javaVersion = JavaVersion.current()
    }

    def "gives a project of the build the organizational entity and no file"() {
        given:
        File testDir = multiProject("")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(":app-a:cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()

        then: "app-a's own document shows its sibling as the organization's code, whose file it does not resolve"
        result.task(":app-a:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseBom(new File(testDir, "app-a/build/reports/cyclonedx-direct/bom.json"))
        component(bom, "app-b").manufacturer.name == "ACME"
        bsiProperties(component(bom, "app-b")).isEmpty()

        where:
        javaVersion = JavaVersion.current()
    }

    def "carries the fields into the Aggregate SBOM"() {
        given:
        File testDir = multiProject("""
            tasks.cyclonedxBom {
                componentFileName = 'product-1.0.0.zip'
            }
            """)

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
            .build()

        then: "the aggregate's main component is described from its own task"
        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        Bom bom = parseBom(new File(testDir, "build/reports/cyclonedx/bom.json"))
        fileDescription(bom.metadata.component) == "product-1.0.0.zip: " + GENERAL_ARCHIVE_FLAGS
        bom.metadata.component.manufacturer.name == "ACME"

        and: "a subproject keeps the file its own Direct SBOM named, although app-a's document mentions it first"
        fileDescription(bom, "app-b") == "app-b-1.0.0.jar: " + JAR_FLAGS
        fileDescription(bom, "componentf") == "componentf-1.0.0.jar: " + JAR_FLAGS

        where:
        javaVersion = JavaVersion.current()
    }

    /** One java project depending on the local repository's modules: a jar, a tgz, a shared library, a BOM and a JSON file. */
    private static File singleProject(String localRepoUri, String taskConfiguration) {
        return TestUtils.createFromString("""
            import org.cyclonedx.model.OrganizationalEntity

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
                implementation 'com.test:componentf:1.0.0'
                implementation 'com.test:componentc:1.0.0'
                implementation 'com.test:componentg:1.0.0'
                implementation 'com.test:componenth:1.0.0'
                implementation 'com.test:componenti:1.0.0'
            }
            def acme() {
                def entity = new OrganizationalEntity()
                entity.name = 'ACME'
                entity.urls = ['https://acme.example']
                return entity
            }
            $taskConfiguration
            """, "rootProject.name = 'bsi-fields'")
    }

    /**
     * A root without code aggregating app-a, which depends on app-b and on a module of the local repository; every
     * task writes the BSI fields and names the same organizational entity.
     */
    private static File multiProject(String rootTaskConfiguration) {
        String localRepoUri = TestUtils.duplicateRepo("local")
        File testDir = TestUtils.createFromString("""
            import org.cyclonedx.model.OrganizationalEntity

            plugins {
                id 'org.cyclonedx.bom'
            }
            allprojects {
                group = 'com.example'
                version = '1.0.0'
                repositories {
                    maven {
                        url '$localRepoUri'
                    }
                }
                tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask) {
                    includeBsiFields = true
                    organizationalEntity = acme()
                }
            }
            def acme() {
                def entity = new OrganizationalEntity()
                entity.name = 'ACME'
                entity.urls = ['https://acme.example']
                return entity
            }
            $rootTaskConfiguration
            """, "rootProject.name = 'bsi-fields'\ninclude 'app-a', 'app-b'")
        new File(testDir, "app-a").mkdirs()
        new File(testDir, "app-a/build.gradle") << """
            plugins {
                id 'java'
            }
            dependencies {
                implementation project(':app-b')
                implementation 'com.test:componentf:1.0.0'
            }
            """
        new File(testDir, "app-b").mkdirs()
        new File(testDir, "app-b/build.gradle") << """
            plugins {
                id 'java'
            }
            """
        return testDir
    }

    private static def runDirect(File testDir) {
        return GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments("cyclonedxDirectBom"))
            .withPluginClasspath()
            .build()
    }

    private static Bom parseDirect(File testDir) {
        return parseBom(new File(testDir, "build/reports/cyclonedx-direct/bom.json"))
    }

    private static Bom parseBom(File file) {
        return new ObjectMapper().readValue(file, Bom.class)
    }

    private static Component component(Bom bom, String name) {
        def component = bom.components.find { it.name == name }
        assert component != null: "no component named $name among ${bom.components*.name}"
        return component
    }

    private static Map<String, String> bsiProperties(Component component) {
        return (component.properties ?: []).findAll { it.name.startsWith("bsi:") }
            .collectEntries { [(it.name): it.value] }
    }

    private static String fileDescription(Bom bom, String name) {
        return fileDescription(component(bom, name))
    }

    /** The file name followed by the three flags in the taxonomy's order, so one string states the whole description. */
    private static String fileDescription(Component component) {
        def properties = bsiProperties(component)
        def flags = ["bsi:component:executable", "bsi:component:archive", "bsi:component:structured"]
            .findAll { properties.containsKey(it) }
            .collect { properties[it] }
        return properties["bsi:component:filename"] + (flags ? ": " + flags.join(", ") : "")
    }
}
