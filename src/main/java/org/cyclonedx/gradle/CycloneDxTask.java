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

import java.io.File;
import org.cyclonedx.gradle.model.SerializableComponents;
import org.cyclonedx.gradle.utils.CycloneDxUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class CycloneDxTask extends DefaultTask {

    private final CycloneDxBomBuilder builder;

    public CycloneDxTask() {
        this.builder = new CycloneDxBomBuilder(getLogger());
    }

    @Input
    public abstract Property<SerializableComponents> getComponents();

    @Input
    public abstract Property<File> getDestination();

    @TaskAction
    public void createBom() {

        File destination = new File(getDestination().get(), "bom.json");
        SerializableComponents components = getComponents().get();
        CycloneDxUtils.writeBom(
                builder.buildBom(components.getSerializableComponents(), components.getRootComponent()), destination);
    }
}
