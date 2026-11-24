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
package org.cyclonedx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Property;
import org.junit.jupiter.api.Test;

class MavenHelperTest {

    @Test
    void retainsExpressionOnlyLicenseFromJavaxAnnotationApiPom() {
        final License pomLicense = new License();
        pomLicense.setName("CDDL + GPLv2 with classpath exception");

        final LicenseChoice result = new MavenHelper(false).resolveMavenLicenses(Collections.singletonList(pomLicense));

        assertNotNull(result);
        assertNotNull(result.getExpression());
    }

    @Test
    void keepsLicenseCommentsOnlyOnTheLicenseThatDeclaresThem() {
        final License apache = new License();
        apache.setName("Apache License, Version 2.0");
        final License mit = new License();
        mit.setName("MIT License");
        mit.setComments("Covers the code copied from slf4j.");

        final List<org.cyclonedx.model.License> licenses = new MavenHelper(false)
                .resolveMavenLicenses(Arrays.asList(apache, mit))
                .getLicenses();

        assertEquals("Apache-2.0", licenses.get(0).getId());
        assertNull(licenses.get(0).getProperties());
        assertEquals("MIT", licenses.get(1).getId());
        assertEquals(1, licenses.get(1).getProperties().size());
        assertEquals(
                "cdx:maven:license:comments",
                licenses.get(1).getProperties().get(0).getName());
        assertEquals(
                "Covers the code copied from slf4j.",
                licenses.get(1).getProperties().get(0).getValue());
    }

    @Test
    void collapsesWhitespaceInsideComments() {
        final License mit = new License();
        mit.setName("MIT License");
        // The line break and indentation are how the element is laid out in the logstash-logback-encoder POM.
        mit.setComments("The portion of LogstashBasicMarker.java that has been copied from\n"
                + "                org.slf4j.helpers.BasicMarker is released under the MIT License.");

        final org.cyclonedx.model.License license = new MavenHelper(false)
                .resolveMavenLicenses(Collections.singletonList(mit))
                .getLicenses()
                .get(0);

        assertEquals(
                "The portion of LogstashBasicMarker.java that has been copied from org.slf4j.helpers.BasicMarker is "
                        + "released under the MIT License.",
                license.getProperties().get(0).getValue());
    }

    @Test
    void keepsLicenseCommentsAsPropertyOnLicenseResolvedByUrl() {
        final License pomLicense = new License();
        pomLicense.setName("Some Vendor License");
        pomLicense.setUrl("https://opensource.org/license/MIT");
        pomLicense.setComments("Applies to the bundled fonts only.");

        final org.cyclonedx.model.License license = new MavenHelper(false)
                .resolveMavenLicenses(Collections.singletonList(pomLicense))
                .getLicenses()
                .get(0);

        assertEquals("MIT", license.getId());
        assertEquals(1, license.getProperties().size());
        assertEquals(
                "cdx:maven:license:comments", license.getProperties().get(0).getName());
        assertEquals(
                "Applies to the bundled fonts only.",
                license.getProperties().get(0).getValue());
    }

    @Test
    void keepsLicenseCommentsAsPropertyOnUnresolvedName() {
        final License pomLicense = new License();
        pomLicense.setName("Some Vendor License");
        pomLicense.setComments(" Applies to the bundled fonts only. ");

        final org.cyclonedx.model.License license = new MavenHelper(false)
                .resolveMavenLicenses(Collections.singletonList(pomLicense))
                .getLicenses()
                .get(0);

        assertEquals("Some Vendor License", license.getName());
        assertEquals(1, license.getProperties().size());
        assertEquals(
                "cdx:maven:license:comments", license.getProperties().get(0).getName());
        assertEquals(
                "Applies to the bundled fonts only.",
                license.getProperties().get(0).getValue());
    }

    @Test
    void addsNoPropertyWithoutComments() {
        final License pomLicense = new License();
        pomLicense.setName("MIT");

        final List<Property> properties = new MavenHelper(false)
                .resolveMavenLicenses(Collections.singletonList(pomLicense))
                .getLicenses()
                .get(0)
                .getProperties();

        assertTrue(properties == null || properties.isEmpty());
    }

    @Test
    void addsNoPropertyForBlankComments() {
        final License pomLicense = new License();
        pomLicense.setName("MIT");
        pomLicense.setComments("   ");

        final List<Property> properties = new MavenHelper(false)
                .resolveMavenLicenses(Collections.singletonList(pomLicense))
                .getLicenses()
                .get(0)
                .getProperties();

        assertTrue(properties == null || properties.isEmpty());
    }
}
