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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.cyclonedx.Version;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.generators.xml.BomXmlGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;
import org.gradle.api.GradleException;

public class CycloneDxUtils {

    public static final Version DEFAULT_SCHEMA_VERSION = Version.VERSION_16;

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     *
     * @param version the CycloneDX schema version to resolve
     * @return the CycloneDX schema to use
     */
    public static Version schemaVersion(String version) {
        switch (version) {
            case "1.0":
                return Version.VERSION_10;
            case "1.1":
                return Version.VERSION_11;
            case "1.2":
                return Version.VERSION_12;
            case "1.3":
                return Version.VERSION_13;
            case "1.4":
                return Version.VERSION_14;
            case "1.5":
                return Version.VERSION_15;
            case "1.6":
                return Version.VERSION_16;
            default:
                return DEFAULT_SCHEMA_VERSION;
        }
    }

    public static void writeBom(
            final Bom bom,
            final File destination,
            final String outputName,
            final Version version,
            final String formats) {

        if (formats.equals("all") || formats.equals("json")) {
            final File jsonFile = new File(destination, String.format("%s.json", outputName));
            writeJSONBom(version, bom, jsonFile);
        }

        if (formats.equals("all") || formats.equals("xml")) {
            final File xmlFile = new File(destination, String.format("%s.xml", outputName));
            writeXmlBom(version, bom, xmlFile);
        }
    }

    private static void writeJSONBom(final Version schemaVersion, final Bom bom, final File destination) {
        final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion, bom);
        try {
            final String bomString = bomGenerator.toJsonString();
            FileUtils.write(destination, bomString, StandardCharsets.UTF_8, false);
        } catch (Exception e) {
            throw new GradleException("Error writing json bom file", e);
        }

        validateBom(new JsonParser(), schemaVersion, destination);
    }

    private static void writeXmlBom(final Version schemaVersion, final Bom bom, final File destination) {

        final BomXmlGenerator bomGenerator = BomGeneratorFactory.createXml(schemaVersion, bom);
        try {
            final String bomString = bomGenerator.toXmlString();
            FileUtils.write(destination, bomString, StandardCharsets.UTF_8, false);
        } catch (Exception e) {
            throw new GradleException("Error writing xml bom file", e);
        }

        validateBom(new XmlParser(), schemaVersion, destination);
    }

    private static void validateBom(final Parser bomParser, final Version schemaVersion, final File destination) {
        try {
            final List<ParseException> exceptions = bomParser.validate(destination, schemaVersion);
            if (!exceptions.isEmpty()) {
                throw exceptions.get(0);
            }
        } catch (Exception e) {
            throw new GradleException("Error whilst validating XML BOM", e);
        }
    }
}
