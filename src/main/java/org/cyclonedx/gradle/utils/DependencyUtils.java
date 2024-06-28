package org.cyclonedx.gradle.utils;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DependencyUtils {

    public static String getDependencyName(ResolvedDependency resolvedDependencies) {
        final ModuleVersionIdentifier m = resolvedDependencies.getModule().getId();
        return getDependencyName(m);
    }

    public static String getDependencyName(ResolvedArtifact artifact) {
        final ModuleVersionIdentifier m = artifact.getModuleVersion().getId();
        return getDependencyName(m);
    }

    public static boolean canBeResolved(Configuration configuration) {
        // Configuration.isCanBeResolved() has been introduced with Gradle 3.3,
        // thus we need to check for the method's existence first
        try {
            Method method = Configuration.class.getMethod("isCanBeResolved");
            try {
                return (Boolean) method.invoke(configuration);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            // prior to Gradle 3.3 all configurations were resolvable
            return true;
        }
    }

    private static String getDependencyName(ModuleVersionIdentifier moduleVersion) {
        return String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
    }



}
