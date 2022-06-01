package org.cyclonedx.gradle

import java.nio.file.Files

class TestUtils {

    static File duplicate(String testProject) {
        def tmpDir = File.createTempDir( "copy", testProject)
        def baseDir = new File("src/test/resources/test-projects/$testProject").toPath()

        baseDir.eachFileRecurse {path ->
            def relativePath = baseDir.relativize(path)
            def targetPath = tmpDir.toPath().resolve(relativePath)
            if (Files.isDirectory(path)) {
                targetPath.toFile().mkdirs()
            } else {
                Files.copy(path, targetPath)
            }
        }
        return tmpDir
    }

    static File createFromString(String buildContent, String settingsContent) {
        def tmpDir = File.createTempDir( "from-literal")
        def settingsFile = new File(tmpDir, "settings.gradle")
        settingsFile << settingsContent
        def buildFile = new File(tmpDir, "build.gradle")
        buildFile << buildContent
        return tmpDir
    }

}
