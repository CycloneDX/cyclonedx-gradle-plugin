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
package org.cyclonedx.gradle.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.cyclonedx.model.Property;
import org.junit.jupiter.api.Test;

class ComponentFileUtilsTest {

    @Test
    void javaArchivesAreExecutableStructuredArchives() {
        for (final String name :
                new String[] {"lib-1.0.jar", "app-1.0.war", "app-1.0.ear", "lib-1.0.aar", "LIB-1.0.JAR"}) {
            final Map<String, String> properties = describe(name);
            assertEquals(name, properties.get("bsi:component:filename"), name);
            assertEquals("executable", properties.get("bsi:component:executable"), name);
            assertEquals("archive", properties.get("bsi:component:archive"), name);
            assertEquals("structured", properties.get("bsi:component:structured"), name);
            assertEquals(4, properties.size(), name);
        }
    }

    @Test
    void generalArchivesAreNonExecutableStructuredArchives() {
        for (final String name :
                new String[] {"dist-1.0.zip", "dist-1.0.tar", "dist-1.0.tar.gz", "dist-1.0.tgz", "dist-1.0.7z"}) {
            final Map<String, String> properties = describe(name);
            assertEquals(name, properties.get("bsi:component:filename"), name);
            assertEquals("non-executable", properties.get("bsi:component:executable"), name);
            assertEquals("archive", properties.get("bsi:component:archive"), name);
            assertEquals("structured", properties.get("bsi:component:structured"), name);
            assertEquals(4, properties.size(), name);
        }
    }

    @Test
    void sharedLibrariesAreExecutableUnstructuredFiles() {
        for (final String name : new String[] {"lib-1.0.so", "lib-1.0.dll", "lib-1.0.dylib"}) {
            final Map<String, String> properties = describe(name);
            assertEquals(name, properties.get("bsi:component:filename"), name);
            assertEquals("executable", properties.get("bsi:component:executable"), name);
            assertEquals("no archive", properties.get("bsi:component:archive"), name);
            assertEquals("unstructured", properties.get("bsi:component:structured"), name);
            assertEquals(4, properties.size(), name);
        }
    }

    @Test
    void otherFilesGetTheirNameAndNoFlags() {
        for (final String name : new String[] {"data-1.0.json", "lib-1.0.klib", "archive-1.0.gz", "README"}) {
            final Map<String, String> properties = describe(name);
            assertEquals(name, properties.get("bsi:component:filename"), name);
            assertEquals(1, properties.size(), name);
        }
    }

    private static Map<String, String> describe(final String fileName) {
        final Map<String, String> properties = new LinkedHashMap<>();
        for (final Property property : ComponentFileUtils.describe(fileName)) {
            properties.put(property.getName(), property.getValue());
        }
        return properties;
    }
}
