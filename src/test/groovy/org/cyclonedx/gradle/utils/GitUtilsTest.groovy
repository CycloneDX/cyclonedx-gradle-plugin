package org.cyclonedx.gradle.utils

import spock.lang.Specification;

class GitUtilsTest extends Specification {

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
    }

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
    }

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
    }

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
    }
}
