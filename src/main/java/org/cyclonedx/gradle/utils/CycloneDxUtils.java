package org.cyclonedx.gradle.utils;

import org.cyclonedx.Version;

public class CycloneDxUtils {

    public static final Version DEFAULT_SCHEMA_VERSION = Version.VERSION_16;

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     * @return the CycloneDX schema to use
     */
    public static Version schemaVersion(String version) {
        switch (version) {
            case "1.0": return Version.VERSION_10;
            case "1.1": return Version.VERSION_11;
            case "1.2": return Version.VERSION_12;
            case "1.3": return Version.VERSION_13;
            case "1.4": return Version.VERSION_14;
            case "1.5": return Version.VERSION_15;
            case "1.6": return Version.VERSION_16;
            default: return DEFAULT_SCHEMA_VERSION;
        }
    }


}
