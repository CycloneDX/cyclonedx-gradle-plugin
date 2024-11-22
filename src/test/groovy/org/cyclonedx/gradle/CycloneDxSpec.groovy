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

import org.cyclonedx.model.Metadata
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Ignore
import spock.lang.Specification

class CycloneDxSpec extends Specification {
    static final String PLUGIN_ID = 'org.cyclonedx.bom'

    def rootProject = ProjectBuilder.builder().withName("root").build()
    def parentProject = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()
    def childProject = ProjectBuilder.builder().withName("child").withParent(parentProject).build()
    def childProject2 = ProjectBuilder.builder().withName("child2").withParent(parentProject).build()
    def leafProject = ProjectBuilder.builder().withName("leaf").withParent(childProject).build()

    def setup() {

        rootProject.apply plugin: PLUGIN_ID
        parentProject.apply plugin: PLUGIN_ID
        childProject.apply plugin: PLUGIN_ID
        childProject2.apply plugin: PLUGIN_ID
        leafProject.apply plugin: PLUGIN_ID

        rootProject.allprojects {
            group = "group"
            version = "1.3"
            description = "description"
            buildDir = "buildDir"
        }
        leafProject.version = "1.3.1"
    }

    def "cyclonedxBom task exists"() {
        expect:
        leafProject.tasks.findByName('cyclonedxBom')
    }
}
