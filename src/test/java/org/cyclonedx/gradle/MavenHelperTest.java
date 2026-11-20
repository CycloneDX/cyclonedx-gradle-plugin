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
import java.util.List;
import java.util.stream.Collectors;
import org.cyclonedx.Version;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.LicenseItem;
import org.cyclonedx.model.license.Acknowledgement;
import org.junit.jupiter.api.Test;

/**
 * Tests for how {@link MavenHelper} turns the license list of a POM into the licenses of a component.
 */
class MavenHelperTest {

    private static final String EXPRESSION_NAME = "CDDL + GPLv2 with classpath exception";
    private static final String EXPRESSION = "(CDDL-1.0 OR GPL-2.0-with-classpath-exception)";

    private final MavenHelper helper = new MavenHelper(false);

    @Test
    void retainsExpressionOnlyLicenseFromJavaxAnnotationApiPom() {
        final LicenseChoice result = resolve(pomLicense(EXPRESSION_NAME, null));

        assertEquals(EXPRESSION, expression(result));
    }

    @Test
    void joinsLicensesDeclaredBeforeAnExpressionIntoThatExpression() {
        final LicenseChoice result = resolve(pomLicense("Apache-2.0", null), pomLicense(EXPRESSION_NAME, null));

        assertEquals("Apache-2.0 OR " + EXPRESSION, expression(result));
    }

    @Test
    void joinsLicensesDeclaredAfterAnExpressionIntoThatExpression() {
        final LicenseChoice result = resolve(pomLicense(EXPRESSION_NAME, null), pomLicense("Apache-2.0", null));

        assertEquals(EXPRESSION + " OR Apache-2.0", expression(result));
        assertTrue(result.isValidForVersion(Version.VERSION_16));
    }

    @Test
    void dropsAnAlternativeEqualToAnEarlierOne() {
        final LicenseChoice result = resolve(
                pomLicense("Apache-2.0", null), pomLicense("Apache 2.0", null), pomLicense(EXPRESSION_NAME, null));

        assertEquals("Apache-2.0 OR " + EXPRESSION, expression(result));
    }

    @Test
    void namesTheExpressionWhenAnotherLicenseResolvesToNoSpdxId() {
        final LicenseChoice result = resolve(
                pomLicense(EXPRESSION_NAME, null), pomLicense("Some Bespoke License", "https://example.com/LICENSE"));

        final List<License> licenses = licenses(result);
        assertEquals(2, licenses.size());
        assertNull(licenses.get(0).getId());
        assertEquals(EXPRESSION, licenses.get(0).getName());
        assertNull(licenses.get(1).getId());
        assertEquals("Some Bespoke License", licenses.get(1).getName());
        assertEquals("https://example.com/LICENSE", licenses.get(1).getUrl());
        assertTrue(result.isValidForVersion(Version.VERSION_16));
    }

    @Test
    void keepsLicensesThatAllResolveToAnIdAsLicenseObjects() {
        final LicenseChoice result = resolve(pomLicense("Apache-2.0", null), pomLicense("MIT", null));

        assertEquals(
                Arrays.asList("Apache-2.0", "MIT"),
                licenses(result).stream().map(License::getId).collect(Collectors.toList()));
    }

    @Test
    void tagsEveryLicenseReadFromThePomAsDeclared() {
        assertDeclared(resolve(pomLicense("Apache-2.0", null), pomLicense("MIT", null)));
        assertDeclared(resolve(pomLicense("Apache-2.0", null), pomLicense(EXPRESSION_NAME, null)));
        assertDeclared(resolve(pomLicense(EXPRESSION_NAME, null), pomLicense("Some Bespoke License", null)));
    }

    private static void assertDeclared(final LicenseChoice choice) {
        for (LicenseItem item : choice.getItems()) {
            final Acknowledgement acknowledgement = item.getExpression() != null
                    ? item.getExpression().getAcknowledgement()
                    : item.getLicense().getAcknowledgement();
            assertEquals(Acknowledgement.DECLARED, acknowledgement, describe(choice));
        }
    }

    private LicenseChoice resolve(final org.apache.maven.model.License... pomLicenses) {
        final LicenseChoice result = helper.resolveMavenLicenses(Arrays.asList(pomLicenses));
        assertNotNull(result);
        return result;
    }

    private static org.apache.maven.model.License pomLicense(final String name, final String url) {
        final org.apache.maven.model.License license = new org.apache.maven.model.License();
        license.setName(name);
        license.setUrl(url);
        return license;
    }

    /** The value of the one expression the choice holds, failing when it holds anything else. */
    private static String expression(final LicenseChoice choice) {
        assertEquals(1, choice.getItems().size(), describe(choice));
        final LicenseItem item = choice.getItems().get(0);
        assertNotNull(item.getExpression(), describe(choice));
        return item.getExpression().getValue();
    }

    /** The license objects the choice holds, failing when it holds an expression. */
    private static List<License> licenses(final LicenseChoice choice) {
        return choice.getItems().stream()
                .map(item -> {
                    assertNotNull(item.getLicense(), describe(choice));
                    return item.getLicense();
                })
                .collect(Collectors.toList());
    }

    private static String describe(final LicenseChoice choice) {
        return choice.getItems().stream()
                .map(item -> item.getExpression() != null
                        ? "expression " + item.getExpression().getValue()
                        : "license " + item.getLicense().getId() + " "
                                + item.getLicense().getName())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
