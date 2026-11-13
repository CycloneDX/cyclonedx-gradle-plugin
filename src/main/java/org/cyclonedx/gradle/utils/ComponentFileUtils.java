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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.cyclonedx.model.Property;

/**
 * Describes a Component File in the terms of BSI TR-03183-2, section 5.2.2: its file name and, where the file format
 * determines them, whether it is executable, an archive and structured (sections 8.1.4 to 8.1.7). The property names
 * and values are those of the BSI property taxonomy for CycloneDX. See ADR 0013.
 */
public final class ComponentFileUtils {

    public static final String FILENAME = "bsi:component:filename";
    public static final String EXECUTABLE = "bsi:component:executable";
    public static final String ARCHIVE = "bsi:component:archive";
    public static final String STRUCTURED = "bsi:component:structured";

    /** Zip-format archives of compiled code that the JVM, a servlet container or Android loads as a unit. */
    private static final List<String> JAVA_ARCHIVES = Arrays.asList(".jar", ".war", ".ear", ".aar");

    /**
     * The archive formats section 8.1.6 of the guideline names as structured. Table 7 classifies such a package as not
     * executable: the archive file itself is not run, whatever it contains.
     */
    private static final List<String> GENERAL_ARCHIVES = Arrays.asList(".zip", ".tar", ".tar.gz", ".tgz", ".7z");

    /**
     * Shared libraries, which section 8.1.4 names as executable; a binary carries no metadata of what it was linked
     * from, so section 8.1.6 makes it unstructured, and it is no archive.
     */
    private static final List<String> SHARED_LIBRARIES = Arrays.asList(".so", ".dll", ".dylib");

    private ComponentFileUtils() {}

    /**
     * The properties describing the file: always its name, and the three flags only when the file format determines
     * them. A format outside the table gets no flags rather than a default, since a wrong flag is worse than a missing
     * one and the guideline omits what cannot be established (section 3.2.1).
     *
     * @param fileName the file name of the component, not its path
     * @return the properties, the file name first
     */
    public static List<Property> describe(final String fileName) {
        final List<Property> properties = new ArrayList<>();
        properties.add(property(FILENAME, fileName));
        final String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (JAVA_ARCHIVES.stream().anyMatch(lowerCaseName::endsWith)) {
            properties.add(property(EXECUTABLE, "executable"));
            properties.add(property(ARCHIVE, "archive"));
            properties.add(property(STRUCTURED, "structured"));
        } else if (GENERAL_ARCHIVES.stream().anyMatch(lowerCaseName::endsWith)) {
            properties.add(property(EXECUTABLE, "non-executable"));
            properties.add(property(ARCHIVE, "archive"));
            properties.add(property(STRUCTURED, "structured"));
        } else if (SHARED_LIBRARIES.stream().anyMatch(lowerCaseName::endsWith)) {
            properties.add(property(EXECUTABLE, "executable"));
            properties.add(property(ARCHIVE, "no archive"));
            properties.add(property(STRUCTURED, "unstructured"));
        }
        return properties;
    }

    private static Property property(final String name, final String value) {
        final Property property = new Property();
        property.setName(name);
        property.setValue(value);
        return property;
    }
}
