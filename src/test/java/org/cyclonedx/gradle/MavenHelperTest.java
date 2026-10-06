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

import java.util.Collections;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MavenHelper}, covering how a declared license name is reconciled with its url.
 */
class MavenHelperTest {

    private final MavenHelper helper = new MavenHelper(false);

    private License resolveSingle(final String name, final String url) {
        final org.apache.maven.model.License declared = new org.apache.maven.model.License();
        declared.setName(name);
        declared.setUrl(url);
        final LicenseChoice choice = helper.resolveMavenLicenses(Collections.singletonList(declared));
        assertNotNull(choice);
        assertEquals(1, choice.getLicenses().size());
        return choice.getLicenses().get(0);
    }

    @Test
    void urlWinsWhenItContradictsTheName() {
        final License license = resolveSingle("BSD", "https://opensource.org/licenses/BSD-2-Clause");

        assertEquals("BSD-2-Clause", license.getId());
    }

    @Test
    void nameIsUsedWhenTheUrlResolvesToNothing() {
        final License license = resolveSingle("Apache-2.0", "https://example.com/LICENSE");

        assertEquals("Apache-2.0", license.getId());
    }

    @Test
    void nameIsUsedWhenNoUrlIsDeclared() {
        final License license = resolveSingle("The Apache Software License, Version 2.0", null);

        assertEquals("Apache-2.0", license.getId());
    }

    @Test
    void unresolvableDeclarationKeepsItsNameAndUrl() {
        final License license = resolveSingle("Some Bespoke License", "https://example.com/LICENSE");

        assertNull(license.getId());
        assertEquals("Some Bespoke License", license.getName());
        assertEquals("https://example.com/LICENSE", license.getUrl());
    }
}
