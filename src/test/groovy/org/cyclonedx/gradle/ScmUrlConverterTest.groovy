package org.cyclonedx.gradle

class ScmUrlConverterTest extends GroovyTestCase {

    void testScmConversation() {
        ScmUrlConverter converter = new ScmUrlConverter()

        assertEquals("https://github.com/user/repo.git", converter.convert("scm:git:git@github.com:user/repo.git"))
        assertEquals("https://github.com/user/repo.git", converter.convert("scm:git:git://github.com/user/repo.git"))
        assertEquals("https://github.com/user/repo.git", converter.convert("git@github.com:user/repo.git"))
        assertEquals("https://github.com/user/repo.git", converter.convert("https://github.com/user/repo.git"))
        assertEquals("https://github.com/user/repo", converter.convert("https://github.com/user/repo"))
    }
}
