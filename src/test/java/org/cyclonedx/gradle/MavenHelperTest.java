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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import org.apache.maven.model.License;
import org.cyclonedx.model.LicenseChoice;
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
}
