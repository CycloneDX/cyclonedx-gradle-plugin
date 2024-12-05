package org.cyclonedx.gradle

import java.nio.file.Files

class TestUtils {
    static enum VCS {
        GIT_SSH,
        GIT_HTTPS
    }

    static File duplicate(String testProject) {
        def tmpDir = File.createTempDir("copy", testProject)
        def baseDir = new File("src/test/resources/test-projects/$testProject").toPath()

        baseDir.eachFileRecurse { path ->
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

    static String duplicateRepo(String testProject) {
        def tmpDir = File.createTempDir( "copy", testProject)
        def baseDir = new File("src/test/resources/test-repos/$testProject").toPath()

        baseDir.eachFileRecurse {path ->
            def relativePath = baseDir.relativize(path)
            def targetPath = tmpDir.toPath().resolve(relativePath)
            if (Files.isDirectory(path)) {
                targetPath.toFile().mkdirs()
            } else {
                Files.copy(path, targetPath)
            }
        }

        return """file://${tmpDir.absolutePath.replace("\\","/")}/repository"""
    }

    static File createFromString(String buildContent, String settingsContent, VCS withVCS) {
        File dir = createFromString(buildContent, settingsContent)

        def gitDir = Files.createDirectory(dir.toPath().resolve(".git"))
        def gitConfig = Files.createFile(gitDir.resolve("config")).toFile()

        gitConfig << "[core]\n"
        gitConfig << "  filemode = true\n"
        gitConfig << "  bare = false\n"
        gitConfig << "  logallrefupdates = true\n"
        gitConfig << "[remote \"origin\"]\n"

        if (VCS.GIT_HTTPS == withVCS) {
            gitConfig << "  url = https://github.com/CycloneDX/cyclonedx-gradle-plugin.git\n"
        } else if (VCS.GIT_SSH) {
            gitConfig << "  url = git@github.com:barblin/cyclonedx-gradle-plugin.git\n"
        }

        def gitHead = Files.createFile(gitDir.resolve("HEAD")).toFile()
        gitHead << "ref: refs/heads/master"

        Files.createDirectory(gitDir.toFile().toPath().resolve("objects"))
        def refsDir = Files.createDirectory(gitDir.toFile().toPath().resolve("refs"))
        def headsDir = Files.createDirectory(refsDir.toFile().toPath().resolve("heads"))
        def masterFile = Files.createFile(headsDir.resolve("master")).toFile()
        masterFile << "1"

        return dir
    }

    static File createFromString(String buildContent, String settingsContent) {
        def tmpDir = File.createTempDir("from-literal")
        def settingsFile = new File(tmpDir, "settings.gradle")
        settingsFile << settingsContent
        def buildFile = new File(tmpDir, "build.gradle")
        buildFile << buildContent
        return tmpDir
    }
}
