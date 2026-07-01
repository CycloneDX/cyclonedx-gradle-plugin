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
import java.util.Date;
import javax.annotation.Nullable;
import org.gradle.api.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;

/**
 * A {@link PublishArtifact} whose file is resolved lazily from a {@link Provider}. Unlike
 * Gradle's built-in {@code LazyPublishArtifact}, which throws when its provider is absent, this
 * implementation is only ever instantiated by {@link CyclonedxPlugin} when the provider is known
 * to be present, and it carries an explicit {@link TaskDependency} on the producing task so the
 * consumer's task graph correctly builds our BOM.
 *
 * <p>Using this indirection lets us keep the artifact-wiring path fully lazy while still opting
 * out of publication when the user disables a format via {@code unsetConvention()}.
 */
final class ConditionalPublishArtifact implements PublishArtifact {

    private final String name;
    private final String extension;
    private final TaskProvider<?> producer;
    private final Provider<RegularFile> file;

    ConditionalPublishArtifact(
            final String name,
            final String extension,
            final TaskProvider<?> producer,
            final Provider<RegularFile> file) {
        this.name = name;
        this.extension = extension;
        this.producer = producer;
        this.file = file;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getType() {
        return extension;
    }

    @Override
    @Nullable public String getClassifier() {
        return null;
    }

    @Override
    public File getFile() {
        return file.get().getAsFile();
    }

    @Override
    @Nullable public Date getDate() {
        return null;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            @Override
            public java.util.Set<? extends Task> getDependencies(@Nullable final Task task) {
                return java.util.Collections.singleton(producer.get());
            }
        };
    }
}
