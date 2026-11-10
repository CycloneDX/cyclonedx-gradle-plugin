package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.regex.Matcher
import org.cyclonedx.model.Bom
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A Reproducible SBOM is a Direct or Aggregate SBOM produced with a supplied timestamp: the same evidence, dependency
 * graph, task configuration and plugin version give the same bytes, and the serial number names the content. Without
 * a timestamp the documents keep the current time and a random serial number, as before. The order of a Direct
 * SBOM's dependsOn lists is deterministic either way.
 */
@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class ReproducibleOutputSpec extends Specification {

    private static final String INSTANT = '2026-01-02T03:04:05Z'
    private static final long INSTANT_MILLIS = 1767323045000L
    private static final String EPOCH_SECONDS = '1767323045'

    private static final String AGGREGATE_JSON = 'build/reports/cyclonedx/bom.json'
    private static final String AGGREGATE_XML = 'build/reports/cyclonedx/bom.xml'
    private static final String DIRECT_JSON = 'build/reports/cyclonedx-direct/bom.json'
    private static final String DIRECT_XML = 'build/reports/cyclonedx-direct/bom.xml'
    private static final List<String> OUTPUTS = [
        AGGREGATE_JSON, AGGREGATE_XML, DIRECT_JSON, DIRECT_XML,
        "lib/$DIRECT_JSON".toString(), "lib/$DIRECT_XML".toString(),
    ]

    def "two builds with the same timestamp write identical direct and aggregate documents"() {
        given: "two copies of one build in different directories, each with its own copy of the repository"
        Fixture first = fixture(INSTANT)
        Fixture second = fixture(INSTANT)

        when:
        run(first)
        run(second)

        then:
        OUTPUTS.each { String path ->
            assert first.text(path) == second.text(path): "$path differs between the two builds"
        }

        where:
        javaVersion = JavaVersion.current()
    }

    def "the supplied timestamp is written and the json and xml of a task carry one serial number"() {
        given:
        Fixture fixture = fixture(INSTANT)

        when:
        run(fixture)

        then:
        Bom direct = fixture.bom(DIRECT_JSON)
        Bom aggregate = fixture.bom(AGGREGATE_JSON)
        direct.metadata.timestamp.time == INSTANT_MILLIS
        aggregate.metadata.timestamp.time == INSTANT_MILLIS

        and:
        direct.serialNumber ==~ /urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/
        fixture.xmlSerialNumber(DIRECT_XML) == direct.serialNumber
        fixture.xmlSerialNumber(AGGREGATE_XML) == aggregate.serialNumber

        and: "the aggregate is a different document, since it carries the subproject's test-only component"
        aggregate.components.any { it.name == 'componentb' && it.version == '1.0.1' }
        !direct.components.any { it.name == 'componentb' && it.version == '1.0.1' }
        aggregate.serialNumber != direct.serialNumber

        where:
        javaVersion = JavaVersion.current()
    }

    def "seconds since the epoch name the same instant as the iso form"() {
        given:
        Fixture iso = fixture(INSTANT)
        Fixture epoch = fixture(EPOCH_SECONDS)

        when:
        run(iso)
        run(epoch)

        then:
        iso.text(DIRECT_JSON) == epoch.text(DIRECT_JSON)
        iso.text(AGGREGATE_JSON) == epoch.text(AGGREGATE_JSON)

        where:
        javaVersion = JavaVersion.current()
    }

    def "a different timestamp gives a different serial number"() {
        given:
        Fixture first = fixture(INSTANT)
        Fixture second = fixture('2026-01-02T03:04:06Z')

        when:
        run(first)
        run(second)

        then:
        first.bom(DIRECT_JSON).serialNumber != second.bom(DIRECT_JSON).serialNumber
        first.bom(AGGREGATE_JSON).serialNumber != second.bom(AGGREGATE_JSON).serialNumber

        where:
        javaVersion = JavaVersion.current()
    }

    def "the serial number is the name-based uuid of the document without it"() {
        given:
        Fixture fixture = fixture(INSTANT)

        when:
        run(fixture)

        then:
        [DIRECT_JSON, AGGREGATE_JSON].each { String path ->
            String json = fixture.text(path)
            String withoutSerial = json.replaceFirst(/(?m)^\s*"serialNumber" : "[^"]*",\r?\n/, '')
            assert withoutSerial != json
            assert fixture.bom(path).serialNumber == "urn:uuid:" + UUID.nameUUIDFromBytes(withoutSerial.getBytes('UTF-8'))
        }

        where:
        javaVersion = JavaVersion.current()
    }

    def "without a serial number a timestamp still gives identical documents"() {
        given:
        String configuration = 'includeBomSerialNumber = false'
        Fixture first = fixture(INSTANT, configuration)
        Fixture second = fixture(INSTANT, configuration)

        when:
        run(first)
        run(second)

        then:
        OUTPUTS.each { String path ->
            assert first.text(path) == second.text(path): "$path differs between the two builds"
        }
        first.bom(DIRECT_JSON).serialNumber == null
        first.bom(AGGREGATE_JSON).serialNumber == null

        where:
        javaVersion = JavaVersion.current()
    }

    def "an unparsable timestamp fails the task naming both accepted forms"() {
        given:
        Fixture fixture = fixture('yesterday')

        when:
        BuildResult result = fail(fixture)

        then:
        result.output.contains("cyclonedxDirectBom")
        result.output.contains("ISO 8601")
        result.output.contains("seconds since the epoch")
        result.output.contains("'yesterday'")

        where:
        javaVersion = JavaVersion.current()
    }

    def "without a timestamp the serial number is random and the timestamp is the current time"() {
        given:
        Fixture first = fixture(null)
        Fixture second = fixture(null)
        long before = System.currentTimeMillis() - 1000L

        when:
        run(first)
        run(second)

        then:
        Bom one = first.bom(DIRECT_JSON)
        Bom other = second.bom(DIRECT_JSON)
        one.serialNumber != other.serialNumber
        one.metadata.timestamp.time >= before
        one.metadata.timestamp.time <= System.currentTimeMillis()

        where:
        javaVersion = JavaVersion.current()
    }

    def "the dependsOn lists of a direct sbom are sorted by ref"() {
        given:
        Fixture fixture = fixture(null)

        when:
        run(fixture)

        then:
        Bom direct = fixture.bom(DIRECT_JSON)
        List<List<String>> dependsOn = direct.dependencies.collect { it.dependencies.collect { it.ref } }
        dependsOn.any { it.size() > 1 }
        dependsOn.each { List<String> refs ->
            assert refs == refs.sort(false): "dependsOn is not sorted: $refs"
        }

        where:
        javaVersion = JavaVersion.current()
    }

    private static BuildResult run(Fixture fixture) {
        BuildResult result = runner(fixture).build()
        assert result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        return result
    }

    private static BuildResult fail(Fixture fixture) {
        return runner(fixture).buildAndFail()
    }

    private static GradleRunner runner(Fixture fixture) {
        return GradleRunner.create()
            .withProjectDir(fixture.projectDir)
            .withArguments(TestUtils.arguments("cyclonedxBom"))
            .withPluginClasspath()
    }

    /**
     * A root project with a subproject and external dependencies from the local fake repository, so no network is
     * involved. The root's dependsOn list has several entries, which is what the ordering case needs, and the
     * subproject has a test-only dependency the root never sees, so the Aggregate SBOM differs from the root's Direct
     * SBOM. The timestamp, when given, and any further configuration are applied to every task of the plugin.
     */
    private static Fixture fixture(String timestamp, String configuration = '') {
        String localRepoUri = TestUtils.duplicateRepo("local")
        File projectDir = File.createTempDir("reproducible")
        String configured = (timestamp == null ? '' : "timestamp = '$timestamp'\n") + configuration
        new File(projectDir, 'settings.gradle').text = """
            rootProject.name = 'reproducible'
            include 'lib'
        """
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            allprojects {
                group = 'com.example'
                version = '1.0.0'
                repositories {
                    maven {
                        url '$localRepoUri'
                    }
                }
                tasks.withType(org.cyclonedx.gradle.BaseCyclonedxTask).configureEach {
                    $configured
                }
            }
            dependencies {
                implementation(project(':lib'))
                implementation('com.test:componentc:1.0.0')
                implementation('com.test:componenta:1.0.0')
                implementation('com.test:componentd:1.0.0')
            }
        """
        new File(projectDir, 'lib').mkdirs()
        new File(projectDir, 'lib/build.gradle').text = """
            plugins {
                id 'java'
            }
            dependencies {
                implementation('com.test:componenta:1.0.0')
                implementation('com.test:componentb:1.0.0')
                testImplementation('com.test:componentb:1.0.1')
            }
        """
        return new Fixture(projectDir)
    }

    private static class Fixture {
        final File projectDir

        Fixture(File projectDir) {
            this.projectDir = projectDir
        }

        String text(String path) {
            return new File(projectDir, path).getText('UTF-8')
        }

        Bom bom(String path) {
            return new ObjectMapper().readValue(new File(projectDir, path), Bom.class)
        }

        String xmlSerialNumber(String path) {
            Matcher matcher = text(path) =~ /serialNumber="([^"]+)"/
            assert matcher.find(): "no serial number in $path"
            return matcher.group(1)
        }
    }
}
