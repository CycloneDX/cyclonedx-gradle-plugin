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

import org.gradle.api.JavaVersion
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ !JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) })
@Unroll("java version: #javaVersion")
class CycloneDxSpec extends Specification {
    static final String PLUGIN_ID = 'org.cyclonedx.bom'

    def "cyclonedxBom task exists"() {
        when:
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def parentProject = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()
        def childProject = ProjectBuilder.builder().withName("child").withParent(parentProject).build()
        def leafProject = ProjectBuilder.builder().withName("leaf").withParent(childProject).build()
        rootProject.apply plugin: PLUGIN_ID
        rootProject.allprojects {
            group = "group"
            version = "1.3"
            description = "description"
            buildDir = "buildDir"
        }
        leafProject.version = "1.3.1"

        then:
        leafProject.tasks.named('cyclonedxDirectBom')

        where:
        javaVersion = JavaVersion.current()
    }
}
