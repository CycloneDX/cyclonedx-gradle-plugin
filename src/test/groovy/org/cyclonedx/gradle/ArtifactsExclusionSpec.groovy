/*
 * This file is part of CycloneDX Gradle Plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.cyclonedx.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assumptions
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class ArtifactsExclusionSpec extends Specification {

    @Unroll
    def "should exclude artifacts in aggregate BOM when configured on aggregate task"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'

            subprojects {
                apply plugin: 'java'
                repositories {
                    mavenCentral()
                }
            }

            project(':subA') {
                group = 'com.example'
                version = '1.0.0'
                dependencies {
                    implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
                }
            }

            tasks.cyclonedxBom {
                excludeArtifacts = ['org.apache.logging.log4j:.*:.*']
            }
        """, "include 'subA'")
        new File(testDir, "subA").mkdirs()

        when:
        def runner = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments('cyclonedxBom', '--info', '--stacktrace')
            .withPluginClasspath()
                def result = runner.build()

                then:        result.task(":cyclonedxBom").outcome == TaskOutcome.SUCCESS
        result.task(":subA:cyclonedxDirectBom").outcome == TaskOutcome.SUCCESS

        File subAJsonBom = new File(testDir, "subA/build/reports/cyclonedx-direct/bom.json")
        Bom subABom = new ObjectMapper().readValue(subAJsonBom, Bom.class)
        assert subABom.getComponents().find(c -> c.name == 'log4j-core') == null

        File aggregateJsonBom = new File(testDir, "build/reports/cyclonedx/bom.json")
        Bom aggregateBom = new ObjectMapper().readValue(aggregateJsonBom, Bom.class)
        assert aggregateBom.getComponents().find(c -> c.name == 'log4j-core') == null
    }

    def "should exclude artifacts with regex"() {
        given:
        File testDir = TestUtils.createFromString("""
            plugins {
                id 'org.cyclonedx.bom'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            group = 'com.example'
            version = '1.0.0'
            tasks.cyclonedxDirectBom {
                excludeArtifacts = ['org.apache.logging.log4j:^.*:^.*']
            }
            dependencies {
                implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version:'2.15.0'
                implementation group: 'org.apache.logging.log4j', name: 'log4j-api', version:'2.15.0'
            }""", "rootProject.name = 'hello-world'")

        when:
        def result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(TestUtils.arguments(taskName))
            .withPluginClasspath()
            .build()

        then:
        result.task(":" + taskName).outcome == TaskOutcome.SUCCESS
        File jsonBom = new File(testDir, reportLocation + "/bom.json")
        Bom bom = new ObjectMapper().readValue(jsonBom, Bom.class)
        Component log4jCore = bom.getComponents().find(c -> c.name == 'log4j-core')
        Component log4jApi = bom.getComponents().find(c -> c.name == 'log4j-api')

        assert log4jCore == null
        assert log4jApi == null

        where:
        taskName             | reportLocation
        "cyclonedxDirectBom" | "build/reports/cyclonedx-direct"
        "cyclonedxBom"       | "build/reports/cyclonedx"
        javaVersion = JavaVersion.current()
    }
}
