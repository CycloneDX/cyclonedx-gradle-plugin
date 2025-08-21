package org.cyclonedx.gradle.utils

import org.cyclonedx.gradle.TestUtils
import spock.lang.Specification
import spock.lang.Unroll;

@Unroll("java version: #javaVersion")
class GitUtilsTest extends Specification {

    @Unroll("java version: #javaVersion, protocol: #protocol")
    def "should sanitize valid non-ssh git remote url without user info"() {
        given: "A valid non-ssh git remote url without user info"

        when:
        def result = GitUtils.sanitizeGitUrl(
            "$protocol://github.com/CycloneDX/cyclonedx-gradle-plugin.git"
        )

        then:
        result == "$protocol://github.com/CycloneDX/cyclonedx-gradle-plugin.git"

        where:
        protocol | _
        "https"  | _
        "http"   | _
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, protocol: #protocol")
    def "should sanitize valid non-ssh git remote url with user info"() {
        given: "A valid non-ssh git remote url with user info"

        when:
        def result = GitUtils.sanitizeGitUrl(
            "$protocol://username:password@github.com/CycloneDX/cyclonedx-gradle-plugin.git"
        )

        then:
        result == "$protocol://github.com/CycloneDX/cyclonedx-gradle-plugin.git"

        where:
        protocol | _
        "https"  | _
        "http"   | _
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, prefix: #prefix")
    def "should sanitize valid ssh git remote url"() {
        given: "A valid ssh git remote url"

        when:
        def result = GitUtils.sanitizeGitUrl(
            "${prefix}git@github.com:barblin/cyclonedx-gradle-plugin.git"
        )

        then:
        result == "ssh://git@github.com:barblin/cyclonedx-gradle-plugin.git"

        where:
        prefix   | _
        "ssh://" | _
        ""       | _
        javaVersion = TestUtils.javaVersion
    }

    @Unroll("java version: #javaVersion, protocol: #protocol")
    def "should fail to sanitize invalid git remote url"() {
        given: "An invalid git remote url"

        when:
        GitUtils.sanitizeGitUrl("${protocol}:${userInfo}github.com:CycloneDX/cyclonedx-gradle-plugin.git")

        then:
        thrown URISyntaxException

        where:
        protocol | userInfo
        "https"  | ""
        "http"   | "username:password@"
        "git"    | ""
        "git"    | "username:password@"
        javaVersion = TestUtils.javaVersion
    }
}
